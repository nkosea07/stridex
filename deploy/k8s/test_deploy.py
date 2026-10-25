import base64
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

import deployment


ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/deploy.yml"
CI_WORKFLOW = ROOT / ".github/workflows/pilot-validation.yml"
SHA = "a" * 40


def inputs(**overrides):
    values = {
        "AWS_S3_BUCKET": "ams-pilot-uploads",
        "FRONTEND_URL": "https://races.acme.org/",
        "MAIL_ENABLED": "false",
        "DEPLOY_ENVIRONMENT": "staging",
        "IMAGE_TAG": SHA,
    }
    values.update(overrides)
    return values


class DeploymentRenderingTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.manifest_dir = Path(self.temp.name) / "manifests"

    def render(self, **overrides):
        deployment.render(self.manifest_dir, inputs(**overrides))
        return {name: (self.manifest_dir / name).read_text() for name in deployment.MANIFEST_NAMES}

    def test_valid_inputs_render_all_manifests_with_matching_host_and_image(self):
        manifests = self.render(FRONTEND_URL="https://races.acme.org:443/")
        self.assertEqual(set(manifests), set(deployment.MANIFEST_NAMES))
        self.assertIn('AWS_S3_BUCKET: "ams-pilot-uploads"', manifests["config.yaml"])
        self.assertIn('FRONTEND_URL: "https://races.acme.org"', manifests["config.yaml"])
        self.assertIn('MAIL_ENABLED: "false"', manifests["config.yaml"])
        self.assertIn("- races.acme.org", manifests["ingress.yaml"])
        self.assertIn("- host: races.acme.org", manifests["ingress.yaml"])
        for name in ("backend.yaml", "web.yaml"):
            self.assertIn(f":{SHA}", manifests[name])
        for manifest in manifests.values():
            self.assertNotIn("REPLACE_WITH_", manifest)

    def test_render_cli_uses_workflow_inputs(self):
        result = subprocess.run(
            [sys.executable, str(ROOT / "deploy/k8s/deployment.py"), "render", str(self.manifest_dir)],
            cwd=ROOT, env={**os.environ, **inputs()}, capture_output=True, text=True, check=False,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout + result.stderr, "")
        self.assertIn("host: races.acme.org", (self.manifest_dir / "ingress.yaml").read_text())

    def test_production_requires_configured_mail_delivery(self):
        with self.assertRaisesRegex(deployment.DeploymentError, "MAIL_ENABLED"):
            self.render(DEPLOY_ENVIRONMENT="production")
        self.assertFalse(self.manifest_dir.exists())

        manifests = self.render(DEPLOY_ENVIRONMENT="production", MAIL_ENABLED="true",
                                MAIL_HOST="smtp.acme.org", MAIL_PORT="587",
                                MAIL_FROM="noreply@acme.org")
        self.assertIn('MAIL_ENABLED: "true"', manifests["config.yaml"])
        self.assertIn('MAIL_HOST: "smtp.acme.org"', manifests["config.yaml"])

    def test_invalid_mail_inputs_fail_before_writing_manifests(self):
        for host, port, sender, field in (
            ("", "587", "noreply@acme.org", "MAIL_HOST"),
            ("smtp.acme.org", "0", "noreply@acme.org", "MAIL_PORT"),
            ("smtp.acme.org", "587", "noreply@example.com", "MAIL_FROM"),
        ):
            with self.subTest(field=field):
                with self.assertRaisesRegex(deployment.DeploymentError, field):
                    self.render(MAIL_ENABLED="true", MAIL_HOST=host, MAIL_PORT=port, MAIL_FROM=sender)
                self.assertFalse(self.manifest_dir.exists())

    def test_invalid_inputs_fail_before_writing_manifests(self):
        cases = (
            ({"AWS_S3_BUCKET": ""}, "AWS_S3_BUCKET"),
            ({"AWS_S3_BUCKET": "change-me-bucket"}, "AWS_S3_BUCKET"),
            ({"AWS_S3_BUCKET": "your-bucket-name"}, "AWS_S3_BUCKET"),
            ({"AWS_S3_BUCKET": "AMS_UPLOADED"}, "AWS_S3_BUCKET"),
            ({"AWS_S3_BUCKET": "192.168.1.1"}, "AWS_S3_BUCKET"),
            ({"FRONTEND_URL": ""}, "FRONTEND_URL"),
            ({"FRONTEND_URL": "https://racetracker.example.com"}, "FRONTEND_URL"),
            ({"FRONTEND_URL": "https://races.example.co.uk"}, "FRONTEND_URL"),
            ({"FRONTEND_URL": "https://placeholder-races.acme.org"}, "FRONTEND_URL"),
            ({"FRONTEND_URL": "https://your-domain.com"}, "FRONTEND_URL"),
            ({"FRONTEND_URL": "http://races.acme.org"}, "FRONTEND_URL"),
            ({"FRONTEND_URL": "https://races.acme.org:8443"}, "FRONTEND_URL"),
            ({"FRONTEND_URL": "https://races.acme.org:99999"}, "FRONTEND_URL"),
            ({"FRONTEND_URL": "https://races.acme.org/admin"}, "FRONTEND_URL"),
            ({"FRONTEND_URL": "https://@races.acme.org"}, "FRONTEND_URL"),
            ({"FRONTEND_URL": "https://races.\tacme.org"}, "FRONTEND_URL"),
            ({"IMAGE_TAG": "latest"}, "IMAGE_TAG"),
            ({"IMAGE_TAG": "A" * 40}, "IMAGE_TAG"),
            ({"DEPLOY_ENVIRONMENT": "preview"}, "DEPLOY_ENVIRONMENT"),
        )
        for overrides, field in cases:
            with self.subTest(overrides=overrides):
                with self.assertRaisesRegex(deployment.DeploymentError, field) as error:
                    self.render(**overrides)
                invalid_value = next(iter(overrides.values()))
                if invalid_value:
                    self.assertNotIn(invalid_value, str(error.exception))
                self.assertFalse(self.manifest_dir.exists())

    def test_storage_config_and_resource_namespace_fail_closed(self):
        manifests = self.render()
        config = deployment.config_values(manifests["config.yaml"])
        self.assertEqual(deployment.validate_storage(config), ("AWS_S3_ACCESS_KEY", "AWS_S3_SECRET_KEY"))
        config["AWS_S3_ENABLED"] = "false"
        with self.assertRaisesRegex(deployment.DeploymentError, "storage config"):
            deployment.validate_storage(config)
        with self.assertRaisesRegex(deployment.DeploymentError, "namespace"):
            deployment.validate_resources("backend.yaml", manifests["backend.yaml"].replace(
                "namespace: race-tracker", "namespace: wrong-namespace", 1))

    def test_backend_activates_production_validator(self):
        backend = (ROOT / "deploy/k8s/backend.yaml").read_text()
        validator = (
            ROOT / "apps/backend/src/main/java/com/racetracker/config/ProductionConfigurationValidator.java"
        ).read_text()
        self.assertIn('name: SPRING_PROFILES_ACTIVE\n              value: "prod"', backend)
        self.assertIn('@Profile("prod")', validator)
        self.assertIn("validateConfiguration();", validator)


class DeploymentPreflightTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.manifest_dir = Path(self.temp.name) / "manifests"
        deployment.render(self.manifest_dir, inputs())

    @staticmethod
    def encoded_secret(**overrides):
        keys = {key: "real-value" for key in (
            "DB_USERNAME", "DB_PASSWORD", "JWT_SECRET", "AWS_S3_ACCESS_KEY", "AWS_S3_SECRET_KEY")}
        keys.update(overrides)
        return json.dumps({"data": {key: base64.b64encode(value.encode()).decode()
                                    for key, value in keys.items()}})

    def mock_kubectl(self, secret=None, missing_service=None, fail_dry_run=None):
        calls = []

        def fake_kubectl(*args):
            calls.append(args)
            if "secret" in args:
                return secret if secret is not None else self.encoded_secret()
            if missing_service and missing_service in args:
                raise deployment.DeploymentError(f"Missing service: {missing_service}")
            if fail_dry_run and f"--dry-run={fail_dry_run}" in args:
                raise deployment.DeploymentError(f"Dry run failed: {fail_dry_run}")
            return "ok"

        return calls, fake_kubectl

    def test_preflight_checks_dependencies_then_both_dry_run_modes(self):
        calls, fake = self.mock_kubectl()
        with patch.object(deployment, "kubectl", side_effect=fake):
            deployment.preflight(self.manifest_dir)
        self.assertEqual(calls[:4], [
            ("get", "namespace", "race-tracker", "-o", "name"),
            ("-n", "race-tracker", "get", "secret", "race-tracker-secrets", "-o", "json"),
            ("-n", "race-tracker", "get", "service", "postgres", "-o", "name"),
            ("-n", "race-tracker", "get", "service", "redis", "-o", "name"),
        ])
        self.assertEqual(calls[4:], [
            ("apply", "--dry-run=client", "--validate=strict", "-f", str(self.manifest_dir)),
            ("apply", "--dry-run=server", "--validate=strict", "-f", str(self.manifest_dir)),
        ])

    def test_missing_secret_or_service_prevents_dry_runs(self):
        for secret, missing_service, expected in (
            (self.encoded_secret(JWT_SECRET="CHANGE_ME"), None, "JWT_SECRET"),
            (None, "redis", "redis"),
        ):
            with self.subTest(expected=expected):
                calls, fake = self.mock_kubectl(secret=secret, missing_service=missing_service)
                with patch.object(deployment, "kubectl", side_effect=fake):
                    with self.assertRaisesRegex(deployment.DeploymentError, expected):
                        deployment.preflight(self.manifest_dir)
                self.assertFalse(any("--dry-run=client" in call for call in calls))

    def test_missing_namespace_stops_preflight_before_any_dry_run_or_apply(self):
        calls = []

        def fake_kubectl(*args):
            calls.append(args)
            if args[:2] == ("get", "namespace"):
                raise deployment.DeploymentError(
                    "Kubernetes preflight namespace lookup failed: resource not found (exit 1)")
            return "ok"

        with patch.object(deployment, "kubectl", side_effect=fake_kubectl):
            with self.assertRaisesRegex(deployment.DeploymentError, "resource not found"):
                deployment.preflight(self.manifest_dir)

        self.assertEqual(calls, [
            ("get", "namespace", "race-tracker", "-o", "name"),
        ])

    def test_server_dry_run_failure_stops_preflight(self):
        calls, fake = self.mock_kubectl(fail_dry_run="server")
        with patch.object(deployment, "kubectl", side_effect=fake):
            with self.assertRaisesRegex(deployment.DeploymentError, "server"):
                deployment.preflight(self.manifest_dir)
        self.assertEqual(calls[-1][1], "--dry-run=server")

    def test_kubectl_failure_reports_bounded_reason_without_secret_output(self):
        cases = (
            (("-n", "race-tracker", "get", "secret", "race-tracker-secrets", "-o", "json"),
             "Error from server (Forbidden): token=super-secret-value",
             '{"data":{"JWT_SECRET":"super-secret-value"}}', "access forbidden"),
            (("apply", "--dry-run=server", "--validate=strict", "-f", str(self.manifest_dir)),
             "Error from server (Invalid): password=super-secret-value" * 1000,
             "", "manifest rejected as invalid"),
            (("-n", "race-tracker", "get", "service", "redis", "-o", "name"),
             "", "Error from server (NotFound): secret=super-secret-value", "resource not found"),
            (("apply", "--dry-run=client", "--validate=strict", "-f", str(self.manifest_dir)),
             "opaque kubectl failure: super-secret-value", "", "unclassified kubectl error"),
        )
        for args, stderr, stdout, reason in cases:
            with self.subTest(reason=reason):
                result = subprocess.CompletedProcess(args, 1, stdout=stdout, stderr=stderr)
                with patch.object(deployment.subprocess, "run", return_value=result):
                    with self.assertRaises(deployment.DeploymentError) as error:
                        deployment.kubectl(*args)
                message = str(error.exception)
                self.assertIn(reason, message)
                if "service" in args:
                    self.assertIn("redis service lookup", message)
                self.assertNotIn("super-secret-value", message)
                self.assertLess(len(message), 160)

    def test_invalid_manifest_reports_allowlisted_resource_and_field(self):
        args = ("apply", "--dry-run=server", "--validate=strict", "-f", str(self.manifest_dir))
        stderr = ('Error from server (Invalid): Deployment.apps "race-tracker-backend" is invalid: '
                  'spec.template.spec.containers[0].image: Invalid value: "super-secret-value"')
        result = subprocess.CompletedProcess(args, 1, stdout="", stderr=stderr)
        with patch.object(deployment.subprocess, "run", return_value=result):
            with self.assertRaises(deployment.DeploymentError) as error:
                deployment.kubectl(*args)
        message = str(error.exception)
        self.assertIn("manifest rejected as invalid", message)
        self.assertIn("Deployment/race-tracker-backend", message)
        self.assertIn("field spec.template.spec.containers[0].image", message)
        self.assertNotIn("super-secret-value", message)
        self.assertLess(len(message), 256)

    def test_bad_request_strict_decoding_reports_allowlisted_resource_and_field(self):
        args = ("apply", "--dry-run=server", "--validate=strict", "-f", str(self.manifest_dir))
        stderr = ('Error from server (BadRequest): error when creating "/tmp/backend.yaml": '
                  'Deployment in version "v1" cannot be handled as a Deployment: '
                  'strict decoding error: unknown field "spec.template.spec.containers[0].bogusField"; '
                  'value="super-secret-value"')
        result = subprocess.CompletedProcess(args, 1, stdout="", stderr=stderr)
        with patch.object(deployment.subprocess, "run", return_value=result):
            with self.assertRaises(deployment.DeploymentError) as error:
                deployment.kubectl(*args)
        message = str(error.exception)
        self.assertIn("strict decoding rejected manifest", message)
        self.assertIn("Deployment/race-tracker-backend", message)
        self.assertIn("field spec.template.spec.containers[0].bogusField", message)
        self.assertNotIn("super-secret-value", message)
        self.assertNotIn("/tmp/backend.yaml", message)
        self.assertLess(len(message), 256)

    def test_unsafe_resource_and_field_are_not_echoed(self):
        args = ("apply", "--dry-run=server", "--validate=strict", "-f", str(self.manifest_dir))
        stderr = ('Error from server (BadRequest): Secret "super-secret-value" was rejected: '
                  'strict decoding error: unknown field "spec.token=super-secret-value"')
        result = subprocess.CompletedProcess(args, 1, stdout="", stderr=stderr)
        with patch.object(deployment.subprocess, "run", return_value=result):
            with self.assertRaises(deployment.DeploymentError) as error:
                deployment.kubectl(*args)
        message = str(error.exception)
        self.assertIn("strict decoding rejected manifest", message)
        self.assertNotIn("Secret/", message)
        self.assertNotIn("field spec.token", message)
        self.assertNotIn("super-secret-value", message)

    def test_tampered_ingress_fails_before_cluster_access(self):
        ingress = self.manifest_dir / "ingress.yaml"
        ingress.write_text(ingress.read_text().replace("host: races.acme.org", "host: other.acme.org"))
        with patch.object(deployment, "kubectl") as command:
            with self.assertRaisesRegex(deployment.DeploymentError, "Ingress host"):
                deployment.preflight(self.manifest_dir)
        command.assert_not_called()

    def test_tampered_image_fails_before_cluster_access(self):
        backend = self.manifest_dir / "backend.yaml"
        backend.write_text(backend.read_text().replace(SHA, "latest"))
        with patch.object(deployment, "kubectl") as command:
            with self.assertRaisesRegex(deployment.DeploymentError, "image tag"):
                deployment.preflight(self.manifest_dir)
        command.assert_not_called()

    def test_mail_enabled_requires_mail_secret_keys(self):
        with tempfile.TemporaryDirectory() as directory:
            manifest_dir = Path(directory) / "manifests"
            deployment.render(manifest_dir, inputs(MAIL_ENABLED="true", MAIL_HOST="smtp.acme.org",
                                                   MAIL_PORT="587", MAIL_FROM="noreply@acme.org"))
            calls, fake = self.mock_kubectl()
            with patch.object(deployment, "kubectl", side_effect=fake):
                with self.assertRaisesRegex(deployment.DeploymentError, "MAIL_USERNAME"):
                    deployment.preflight(manifest_dir)
            self.assertEqual(len(calls), 2)

    def test_workflows_gate_apply_and_run_tests_on_push_and_pr(self):
        workflow = WORKFLOW.read_text()
        self.assertLess(workflow.index("Render and validate deployment manifests"),
                        workflow.index("Preflight deployment"))
        self.assertLess(workflow.index("Preflight deployment"), workflow.index("Apply manifests"))
        self.assertNotIn("kubectl apply -f deploy/k8s/", workflow)
        self.assertIn('kubectl apply -f "$manifest_dir/ingress.yaml"', workflow)
        ci = CI_WORKFLOW.read_text()
        self.assertIn("  push:\n", ci)
        self.assertIn("  pull_request:\n", ci)
        self.assertIn("python3 -m unittest discover -s deploy/k8s -p 'test_*.py' -v", ci)
        self.assertIn("scripts/release-gate.sql", ci)


if __name__ == "__main__":
    unittest.main()
