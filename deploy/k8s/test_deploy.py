import os
from pathlib import Path
import subprocess
import tempfile
import textwrap
import unittest


ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/deploy.yml"


class DeploymentRenderingTest(unittest.TestCase):
    def render(self, bucket, frontend_url, mail_enabled="false", environment="staging",
               mail_host="", mail_port="", mail_from=""):
        workflow = WORKFLOW.read_text()
        step = workflow.split("      - name: Render and validate deployment config\n", 1)[1]
        step = step.split("\n      - name:", 1)[0]
        script = textwrap.dedent(step.split("        run: |\n", 1)[1])
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "config.yaml"
            env = os.environ.copy()
            env.update(AWS_S3_BUCKET=bucket, FRONTEND_URL=frontend_url,
                       MAIL_ENABLED=mail_enabled, DEPLOY_ENVIRONMENT=environment,
                       MAIL_HOST=mail_host, MAIL_PORT=mail_port, MAIL_FROM=mail_from,
                       CONFIG_MANIFEST=str(output))
            result = subprocess.run(
                ["bash", "-e", "-o", "pipefail", "-c", script],
                cwd=ROOT,
                env=env,
                text=True,
                capture_output=True,
                check=False,
            )
            return result, output.read_text() if output.exists() else None

    def test_valid_inputs_render_configmap(self):
        result, manifest = self.render("ams-pilot-uploads", "https://races.acme.org/")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout + result.stderr, "")
        self.assertIn('AWS_S3_BUCKET: "ams-pilot-uploads"', manifest)
        self.assertIn('FRONTEND_URL: "https://races.acme.org"', manifest)
        self.assertIn('MAIL_ENABLED: "false"', manifest)
        self.assertNotIn("REPLACE_WITH_", manifest)

    def test_production_requires_configured_mail_delivery(self):
        result, manifest = self.render("ams-pilot-uploads", "https://races.acme.org",
                                       environment="production")
        self.assertNotEqual(result.returncode, 0)
        self.assertIsNone(manifest)
        self.assertIn("MAIL_ENABLED", result.stderr)

        result, manifest = self.render("ams-pilot-uploads", "https://races.acme.org",
                                       mail_enabled="true", environment="production",
                                       mail_host="smtp.acme.org", mail_port="587",
                                       mail_from="noreply@acme.org")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('MAIL_ENABLED: "true"', manifest)
        self.assertIn('MAIL_HOST: "smtp.acme.org"', manifest)
        self.assertIn('MAIL_FROM: "noreply@acme.org"', manifest)

    def test_enabled_mail_requires_valid_host_port_and_sender(self):
        for host, port, sender, missing in (
            ("", "587", "noreply@acme.org", "MAIL_HOST"),
            ("smtp.acme.org", "0", "noreply@acme.org", "MAIL_PORT"),
            ("smtp.acme.org", "587", "noreply@example.com", "MAIL_FROM"),
        ):
            with self.subTest(missing=missing):
                result, manifest = self.render("ams-pilot-uploads", "https://races.acme.org",
                                               mail_enabled="true", environment="production",
                                               mail_host=host, mail_port=port, mail_from=sender)
                self.assertNotEqual(result.returncode, 0)
                self.assertIsNone(manifest)
                self.assertIn(missing, result.stderr)

    def test_missing_inputs_fail_before_writing_manifest(self):
        for bucket, frontend_url, missing in (
            ("", "https://races.acme.org", "AWS_S3_BUCKET"),
            ("ams-pilot-uploads", "", "FRONTEND_URL"),
        ):
            with self.subTest(missing=missing):
                result, manifest = self.render(bucket, frontend_url)
                self.assertNotEqual(result.returncode, 0)
                self.assertIsNone(manifest)
                self.assertIn(missing, result.stderr)

    def test_placeholder_and_invalid_inputs_fail_without_echoing_values(self):
        cases = (
            ("change-me-bucket", "https://races.acme.org"),
            ("your-bucket-name", "https://races.acme.org"),
            ("AMS_UPLOADED", "https://races.acme.org"),
            ("192.168.1.1", "https://races.acme.org"),
            ("ams-pilot-uploads", "https://racetracker.example.com"),
            ("ams-pilot-uploads", "https://your-domain.com"),
            ("ams-pilot-uploads", "http://races.acme.org"),
            ("ams-pilot-uploads", "https://races.acme.org/admin"),
            ("ams-pilot-uploads", "https://races.acme.org:99999"),
            ("ams-pilot-uploads", "https://@races.acme.org"),
            ("ams-pilot-uploads", "https://races.\tacme.org"),
        )
        for bucket, frontend_url in cases:
            with self.subTest(bucket=bucket, frontend_url=frontend_url):
                result, manifest = self.render(bucket, frontend_url)
                self.assertNotEqual(result.returncode, 0)
                self.assertIsNone(manifest)
                self.assertNotIn(bucket, result.stdout + result.stderr)
                self.assertNotIn(frontend_url, result.stdout + result.stderr)

    def test_backend_activates_production_validator(self):
        backend = (ROOT / "deploy/k8s/backend.yaml").read_text()
        validator = (
            ROOT / "apps/backend/src/main/java/com/racetracker/config/ProductionConfigurationValidator.java"
        ).read_text()
        self.assertIn('name: SPRING_PROFILES_ACTIVE\n              value: "prod"', backend)
        self.assertIn('@Profile("prod")', validator)
        self.assertIn("validateConfiguration();", validator)

    def test_rendering_precedes_any_kubernetes_apply(self):
        workflow = WORKFLOW.read_text()
        self.assertLess(
            workflow.index("      - name: Render and validate deployment config"),
            workflow.index("      - name: Configure kubectl"),
        )
        self.assertIn('kubectl apply -f "$RUNNER_TEMP/race-tracker-config.yaml"', workflow)
        self.assertNotIn("kubectl apply -f deploy/k8s/config.yaml", workflow)


if __name__ == "__main__":
    unittest.main()
