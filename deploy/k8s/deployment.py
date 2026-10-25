"""Render and preflight the Kubernetes deployment before any resource is applied."""

import argparse
import base64
import binascii
import ipaddress
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import urlsplit


NAMESPACE = "race-tracker"
TEMPLATES = Path(__file__).resolve().parent
MANIFEST_NAMES = ("namespace.yaml", "config.yaml", "backend.yaml", "web.yaml", "ingress.yaml")
EXPECTED_RESOURCES = {
    "namespace.yaml": (("Namespace", NAMESPACE),),
    "config.yaml": (("ConfigMap", "race-tracker-config"),),
    "backend.yaml": (("Deployment", "race-tracker-backend"), ("Service", "race-tracker-backend")),
    "web.yaml": (("Deployment", "race-tracker-web"), ("Service", "race-tracker-web")),
    "ingress.yaml": (("Ingress", "race-tracker"),),
}


class DeploymentError(Exception):
    pass


def invalid(name):
    raise DeploymentError(f"Invalid or missing deployment input: {name}")


def validate_bucket(bucket):
    if (bucket != bucket.strip()
            or not re.fullmatch(r"[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]", bucket)
            or ".." in bucket or ".-" in bucket or "-." in bucket
            or any(word in bucket for word in ("example", "placeholder", "change-me", "replace-with", "your-bucket"))
            or bucket.endswith(("-s3alias", "--ol-s3", "--x-s3", ".mrap", "--table-s3"))):
        invalid("AWS_S3_BUCKET")
    try:
        ipaddress.ip_address(bucket)
    except ValueError:
        return
    invalid("AWS_S3_BUCKET")


def validate_frontend_url(value):
    if value != value.strip() or re.search(r"[\x00-\x20\x7f]", value) or not value.startswith("https://"):
        invalid("FRONTEND_URL")
    try:
        origin = urlsplit(value)
        host = origin.hostname or ""
        port = origin.port
    except ValueError:
        invalid("FRONTEND_URL")
    if (not re.fullmatch(r"[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+", host)
            or len(host) > 253 or host.endswith((".example", ".invalid", ".test", ".local"))
            or any(word in host for word in ("example", "placeholder", "change-me", "changeme",
                                            "replace-with", "your-domain"))
            or origin.netloc not in (host, f"{host}:443")
            or port not in (None, 443)
            or origin.path not in ("", "/") or origin.query or origin.fragment
            or "?" in value or "#" in value):
        invalid("FRONTEND_URL")
    return f"https://{host}", host


def deployment_inputs(env):
    bucket = env.get("AWS_S3_BUCKET", "")
    validate_bucket(bucket)
    frontend_url, host = validate_frontend_url(env.get("FRONTEND_URL", ""))
    environment = env.get("DEPLOY_ENVIRONMENT", "")
    if environment not in ("staging", "production"):
        invalid("DEPLOY_ENVIRONMENT")
    mail_enabled = env.get("MAIL_ENABLED", "")
    if mail_enabled not in ("true", "false") or (environment == "production" and mail_enabled != "true"):
        invalid("MAIL_ENABLED")
    mail_host = env.get("MAIL_HOST", "") if mail_enabled == "true" else "localhost"
    mail_port = env.get("MAIL_PORT", "") if mail_enabled == "true" else "587"
    mail_from = env.get("MAIL_FROM", "") if mail_enabled == "true" else "noreply@localhost"
    if mail_enabled == "true":
        if (not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9.-]+", mail_host)
                or "placeholder" in mail_host.lower() or "example" in mail_host.lower()):
            invalid("MAIL_HOST")
        if not mail_port.isdigit() or not 1 <= int(mail_port) <= 65535:
            invalid("MAIL_PORT")
        if (not re.fullmatch(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}", mail_from)
                or "example" in mail_from.lower()):
            invalid("MAIL_FROM")
    image_tag = env.get("IMAGE_TAG", "")
    if not re.fullmatch(r"[0-9a-f]{40}", image_tag):
        invalid("IMAGE_TAG (expected a 40-character commit SHA)")
    return {
        "AWS_S3_BUCKET": bucket,
        "FRONTEND_URL": frontend_url,
        "MAIL_ENABLED": mail_enabled,
        "MAIL_HOST": mail_host,
        "MAIL_PORT": mail_port,
        "MAIL_FROM": mail_from,
        "INGRESS_HOST": host,
        "COMMIT_SHA": image_tag,
    }


def replace_markers(template, values):
    for name, value in values.items():
        marker = f"REPLACE_WITH_{name}"
        expected = 2 if name == "INGRESS_HOST" else 1
        if template.count(marker) not in (0, expected):
            raise DeploymentError(f"Unexpected marker count for {name}")
        if marker in template:
            template = template.replace(marker, value if name in ("INGRESS_HOST", "COMMIT_SHA") else json.dumps(value))
    if "REPLACE_WITH_" in template:
        raise DeploymentError("Unresolved deployment marker")
    return template


def validate_resources(name, manifest):
    documents = [part for part in re.split(r"(?m)^---\s*$", manifest) if part.strip()]
    if len(documents) != len(EXPECTED_RESOURCES[name]):
        raise DeploymentError(f"Unexpected resources in {name}")
    for document, (kind, resource_name) in zip(documents, EXPECTED_RESOURCES[name]):
        if not re.search(rf"(?m)^kind: {kind}$", document):
            raise DeploymentError(f"Unexpected resource kind in {name}")
        if not re.search(rf"(?m)^metadata:\n  name: {resource_name}$", document):
            raise DeploymentError(f"Unexpected resource name in {name}")
        if kind != "Namespace" and re.findall(r"(?m)^  namespace: (.+)$", document) != [NAMESPACE]:
            raise DeploymentError(f"Unexpected namespace in {name}")


def config_values(manifest):
    values = {}
    for key, raw in re.findall(r"(?m)^  ([A-Z][A-Z0-9_]*): (.+)$", manifest):
        try:
            value = json.loads(raw)
        except json.JSONDecodeError as exc:
            raise DeploymentError(f"Invalid ConfigMap value: {key}") from exc
        if not isinstance(value, str):
            raise DeploymentError(f"Invalid ConfigMap value: {key}")
        values[key] = value
    return values


def validate_storage(config):
    provider = config.get("STORAGE_PROVIDER")
    if provider == "aws-s3":
        if (config.get("AWS_S3_ENABLED") != "true" or config.get("MINIO_ENABLED") != "false"
                or not config.get("AWS_S3_REGION") or not config.get("AWS_S3_BUCKET")):
            raise DeploymentError("Invalid aws-s3 storage config")
        validate_bucket(config["AWS_S3_BUCKET"])
        return ("AWS_S3_ACCESS_KEY", "AWS_S3_SECRET_KEY")
    if provider == "minio":
        endpoint = config.get("MINIO_ENDPOINT", "")
        parsed = urlsplit(endpoint)
        if (config.get("MINIO_ENABLED") != "true" or config.get("AWS_S3_ENABLED") != "false"
                or not config.get("MINIO_BUCKET") or not parsed.hostname
                or parsed.scheme not in ("http", "https") or not config.get("MINIO_REGION")):
            raise DeploymentError("Invalid minio storage config")
        return ("MINIO_ACCESS_KEY", "MINIO_SECRET_KEY")
    raise DeploymentError("Unsupported STORAGE_PROVIDER")


def validate_ingress(manifest, host):
    tls_hosts = re.findall(r"(?m)^        - ([^\s]+)$", manifest)
    rule_hosts = re.findall(r"(?m)^    - host: ([^\s]+)$", manifest)
    services = re.findall(r"(?m)^                name: ([a-z0-9-]+)$", manifest)
    if (tls_hosts != [host] or rule_hosts != [host]
            or services != ["race-tracker-backend", "race-tracker-backend", "race-tracker-web"]):
        raise DeploymentError("Ingress host or service does not match rendered deployment")


def validate_images(manifests):
    for name, image in (("backend.yaml", "race-tracker-backend"), ("web.yaml", "race-tracker-web")):
        if (len(re.findall(rf"(?m)^          image: ghcr.io/nkosea07/{image}:[0-9a-f]{{40}}$",
                           manifests[name])) != 1
                or len(re.findall(r"(?m)^\s+image:", manifests[name])) != 1):
            raise DeploymentError(f"Invalid image tag in {name}")


def render(output_dir, env):
    values = deployment_inputs(env)
    rendered = {}
    for name in MANIFEST_NAMES:
        template = (TEMPLATES / name).read_text()
        expected = {"config.yaml": ("AWS_S3_BUCKET", "FRONTEND_URL", "MAIL_ENABLED", "MAIL_HOST",
                                    "MAIL_PORT", "MAIL_FROM"),
                    "backend.yaml": ("COMMIT_SHA",), "web.yaml": ("COMMIT_SHA",),
                    "ingress.yaml": ("INGRESS_HOST",)}.get(name, ())
        for marker in expected:
            count = 2 if marker == "INGRESS_HOST" else 1
            if template.count(f"REPLACE_WITH_{marker}") != count:
                raise DeploymentError(f"Missing deployment marker in {name}: {marker}")
        manifest = replace_markers(template, values)
        validate_resources(name, manifest)
        rendered[name] = manifest
    config = config_values(rendered["config.yaml"])
    if config.get("FRONTEND_URL") != values["FRONTEND_URL"] or config.get("MAIL_ENABLED") != values["MAIL_ENABLED"]:
        raise DeploymentError("Rendered ConfigMap differs from validated inputs")
    validate_storage(config)
    validate_images(rendered)
    validate_ingress(rendered["ingress.yaml"], values["INGRESS_HOST"])
    output_dir.mkdir(mode=0o700, parents=True, exist_ok=False)
    for name, manifest in rendered.items():
        (output_dir / name).write_text(manifest)


def safe_manifest_error_context(source):
    resource = None
    for expected in EXPECTED_RESOURCES.values():
        for kind, name in expected:
            if re.search(rf'\b{kind}(?:\.[A-Za-z0-9.]+)? "{re.escape(name)}"', source):
                resource = f"{kind}/{name}"
                break
        if resource:
            break
    if not resource:
        match = re.search(r'error when (?:creating|patching|replacing) "([^"\n]+)"', source)
        if match:
            filename = Path(match.group(1)).name
            for kind, name in EXPECTED_RESOURCES.get(filename, ()):
                if re.search(rf'\b{kind} in version\b', source):
                    resource = f"{kind}/{name}"
                    break

    field_match = re.search(r'strict decoding error:\s*unknown field "([^"\n]{1,120})"', source, re.I)
    if not field_match:
        field_match = re.search(
            r'\bis invalid:\s*([^:\n]{1,120}):\s*(?:Invalid value|Required value|Forbidden|Unsupported value)',
            source, re.I,
        )
    field = field_match.group(1) if field_match else ""
    if not re.fullmatch(r'(?:spec|metadata|data)(?:\.[A-Za-z][A-Za-z0-9_-]*|\[[0-9]{1,3}\])+', field):
        field = ""
    return " ".join(part for part in (resource, f"field {field}" if field else "") if part)


def kubectl(*args):
    try:
        result = subprocess.run(("kubectl", *args), capture_output=True, text=True,
                                check=False, timeout=60)
    except subprocess.TimeoutExpired as exc:
        raise DeploymentError("Kubernetes preflight timed out") from exc
    if result.returncode:
        source = result.stderr or ("" if "secret" in args else result.stdout)
        output = source[-8192:].lower()
        reasons = (
            ("forbidden", "access forbidden"),
            ("unauthorized", "authentication rejected"),
            ("notfound", "resource not found"),
            ("not found", "resource not found"),
            ("strict decoding error", "strict decoding rejected manifest"),
            ("badrequest", "manifest rejected as bad request"),
            ("invalid", "manifest rejected as invalid"),
            ("error validating", "manifest validation failed"),
            ("no matches for kind", "resource kind unavailable"),
            ("connection refused", "API server connection refused"),
            ("context deadline exceeded", "API server timed out"),
            ("no such host", "API server DNS lookup failed"),
        )
        reason = next((description for marker, description in reasons if marker in output),
                      "unclassified kubectl error")
        if "--dry-run=server" in args:
            operation = "server dry-run"
        elif "--dry-run=client" in args:
            operation = "client dry-run"
        elif "secret" in args:
            operation = "secret lookup"
        elif "service" in args:
            service = args[args.index("service") + 1]
            operation = f"{service} service lookup" if service in ("postgres", "redis") else "service lookup"
        else:
            operation = "namespace lookup"
        context = safe_manifest_error_context(source) if "dry-run" in operation else ""
        detail = f"; {context}" if context else ""
        raise DeploymentError(f"Kubernetes preflight {operation} failed: {reason}{detail} (exit {result.returncode})")
    return result.stdout


def preflight(manifest_dir):
    if not manifest_dir.is_dir() or {path.name for path in manifest_dir.iterdir()} != set(MANIFEST_NAMES):
        raise DeploymentError("Rendered manifest directory is incomplete")
    manifests = {}
    for name in MANIFEST_NAMES:
        manifest = (manifest_dir / name).read_text()
        validate_resources(name, manifest)
        if "REPLACE_WITH_" in manifest:
            raise DeploymentError("Unresolved deployment marker")
        manifests[name] = manifest
    validate_images(manifests)
    config = config_values(manifests["config.yaml"])
    validate_storage(config)
    if config.get("MAIL_ENABLED") not in ("true", "false"):
        raise DeploymentError("Invalid MAIL_ENABLED in rendered ConfigMap")
    frontend_url, host = validate_frontend_url(config.get("FRONTEND_URL", ""))
    if frontend_url != config["FRONTEND_URL"]:
        raise DeploymentError("Ingress host does not match ConfigMap FRONTEND_URL")
    validate_ingress(manifests["ingress.yaml"], host)

    kubectl("get", "namespace", NAMESPACE, "-o", "name")
    secret = json.loads(kubectl("-n", NAMESPACE, "get", "secret", "race-tracker-secrets", "-o", "json"))
    if not isinstance(secret, dict) or not isinstance(secret.get("data"), dict):
        raise DeploymentError("race-tracker-secrets has no data")
    secret_data = secret["data"]
    required = ["DB_USERNAME", "DB_PASSWORD", "JWT_SECRET", *validate_storage(config)]
    if config.get("MAIL_ENABLED") == "true":
        required.extend(("MAIL_USERNAME", "MAIL_PASSWORD"))
    for key in required:
        try:
            encoded = secret_data.get(key, "")
            value = base64.b64decode(encoded, validate=True).decode("utf-8") if isinstance(encoded, str) else ""
        except (binascii.Error, UnicodeError, ValueError):
            value = ""
        if not value.strip() or value.strip().lower().startswith((
                "change_me", "change-me", "replace_with", "replace-with", "placeholder", "your-")):
            raise DeploymentError(f"race-tracker-secrets is missing required key: {key}")
    for service in ("postgres", "redis"):
        kubectl("-n", NAMESPACE, "get", "service", service, "-o", "name")
    for mode in ("client", "server"):
        kubectl("apply", f"--dry-run={mode}", "--validate=strict", "-f", str(manifest_dir))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("render", "preflight"))
    parser.add_argument("manifest_dir", type=Path)
    args = parser.parse_args()
    try:
        if args.action == "render":
            render(args.manifest_dir, os.environ)
        else:
            preflight(args.manifest_dir)
    except (DeploymentError, OSError, ValueError) as exc:
        sys.exit(str(exc))


if __name__ == "__main__":
    main()
