"""Install public MQTT TLS and attach renewal to the existing host Certbot lifecycle."""

import argparse
import hashlib
import ipaddress
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import tempfile
from urllib.parse import urlsplit


ROOTS = Path(__file__).parent / "trust/isrg-roots.pem"
SERVER_CERTIFICATE = "server.pem"
SERVER_KEY = "server-key.pem"
WORKERS = Path("/usr/local/lib/edol-public-tls")
SYSTEMD = Path("/etc/systemd/system")


def enabled(config):
    return config.get("mode") == "secure" and config.get("mqtt_tls") == "public"


def host(config):
    name = config.get("mqtt_public_host") or urlsplit(config["hub_origin"]).hostname or ""
    if len(name) > 253 or "." not in name or any(
            not re.fullmatch(r"[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?", label)
            for label in name.split(".")):
        raise ValueError("Public MQTT requires a DNS hostname")
    try:
        ipaddress.ip_address(name)
    except ValueError:
        return name.lower()
    raise ValueError("Public MQTT requires a DNS hostname, not an IP address")


def port(config):
    value = config.get("mqtt_public_port", config["mqtt_port"])
    if type(value) is not int or not 1 <= value <= 65535:
        raise ValueError("Invalid public MQTT port")
    return value


def directory(config):
    return Path(config["mqtt_cert_dir"]).parent / "public-mqtt"


def command(*args, cwd=None):
    result = subprocess.run(list(map(str, args)), cwd=cwd, input=b"", capture_output=True, check=False, timeout=180)
    if result.returncode:
        raise ValueError("Public TLS operation failed; protected command diagnostics suppressed")
    return result.stdout


def regular(path):
    if not path.is_file() or path.is_symlink() or path.resolve() != path.absolute():
        raise ValueError("Installed TLS material must be regular files without symlink traversal")
    return path


def verify(config, certificate, key, roots=None):
    regular(certificate)
    regular(key)
    # fullchain supplies intermediates; only release-owned public roots establish trust.
    command("openssl", "verify", "-x509_strict", "-purpose", "sslserver", "-verify_hostname", host(config),
            "-CAfile", regular(roots or ROOTS), "-untrusted", certificate, certificate)
    command("openssl", "x509", "-in", certificate, "-checkend", "86400", "-noout")
    if command("openssl", "x509", "-in", certificate, "-noout", "-pubkey") != \
            command("openssl", "pkey", "-in", key, "-pubout"):
        raise ValueError("Public MQTT certificate/key mismatch")


def validate_installed(config):
    target = directory(config)
    verify(config, target / SERVER_CERTIFICATE, target / SERVER_KEY)


def atomic_write(path, data, uid=None, gid=None, mode=0o600):
    if path.is_symlink() or path.parent.resolve() != path.parent.absolute():
        raise ValueError("TLS destination must not traverse symlinks")
    descriptor, name = tempfile.mkstemp(prefix=".tls-", dir=path.parent)
    temporary = Path(name)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.chmod(temporary, mode)
        if uid is not None:
            os.chown(temporary, uid, gid)
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def lineage(config):
    return certificate_root(config) / "live" / host(config)


def certificate_root(config):
    return Path(config.get("public_tls", {}).get("certbot_config_dir", "/srv/nginx/certbot"))


def install(config, state, source, uid, gid):
    target = directory(config)
    if not target.absolute().is_relative_to(state.parent.absolute()):
        raise ValueError("Public TLS must stay inside the backed-up instance")
    if target.resolve() != target.absolute():
        raise ValueError("Public TLS destination must not traverse symlinks")
    target.mkdir(parents=True, exist_ok=True, mode=0o700)
    if target.resolve() != target.absolute():
        raise ValueError("Public TLS destination must not traverse symlinks")
    # Certbot's managed live/ symlinks are intentionally read only at this boundary.
    # Snapshot and verify their bytes before changing any installed file.
    with tempfile.TemporaryDirectory(prefix=".candidate-", dir=target) as work:
        candidate = Path(work)
        for source_name, name in (("fullchain.pem", SERVER_CERTIFICATE), ("privkey.pem", SERVER_KEY)):
            data = (source / source_name).read_bytes()
            if len(data) > 65536:
                raise ValueError("Oversized certificate input")
            (candidate / name).write_bytes(data)
        verify(config, candidate / SERVER_CERTIFICATE, candidate / SERVER_KEY)
        changed = any(not (target / name).is_file() or
                      (target / name).read_bytes() != (candidate / name).read_bytes()
                      for name in (SERVER_CERTIFICATE, SERVER_KEY))
        for name in (SERVER_KEY, SERVER_CERTIFICATE):
            atomic_write(target / name, (candidate / name).read_bytes(), uid, gid)
        atomic_write(target / "roots.pem", ROOTS.read_bytes(), uid, gid, 0o644)
    os.chown(target, uid, gid)
    os.chmod(target, 0o700)
    return changed


def reload_broker(config):
    identities = command("docker", "ps", "-q", "--filter", "label=com.docker.compose.project=" + config["project"],
                         "--filter", "label=com.docker.compose.service=mqtt").decode().split()
    if len(identities) > 1:
        raise ValueError("Ambiguous MQTT instance")
    if identities:
        command("docker", "kill", "--signal", "HUP", identities[0])
    return bool(identities)


def bootstrap(config_path, config):
    """Run as root once after entering instance data; existing Certbot timer owns renewal."""
    settings = config["public_tls"]
    email, webroot = settings["email"], Path(settings["webroot"])
    compose_dir = Path(settings.get("certbot_directory", "/srv/nginx"))
    if not email or "@" not in email or not webroot.is_absolute() or not compose_dir.is_absolute() or not compose_dir.is_dir():
        raise ValueError("Configure ACME email and existing nginx ACME webroot")
    issue_certificates(config, email, webroot, compose_dir)
    register_renewal(config_path, config)


def issue_certificates(config, email, webroot, compose_dir):
    names = list(dict.fromkeys([urlsplit(config[k]).hostname for k in ("hub_origin", "auth_origin")] + [host(config)]))
    if any(not name for name in names):
        raise ValueError("Missing HTTPS certificate hostname")
    covered = set()
    for name in names:
        if name in covered:
            continue
        certificate = certificate_root(config) / "live" / name / "cert.pem"
        domains = {name}
        if certificate.is_file():
            extensions = command("openssl", "x509", "-in", certificate, "-noout", "-ext", "subjectAltName").decode("ascii")
            domains.update(re.findall(r"DNS:([^,\s]+)", extensions))
        args = ["docker", "compose", "run", "--rm", "certbot", "certonly", "--non-interactive", "--agree-tos",
                "--email", email, "--webroot", "--webroot-path", str(webroot), "--cert-name", name,
                "--keep-until-expiring", "--no-directory-hooks"]
        for domain in sorted(domains):
            args.extend(["-d", domain])
        command(*args, cwd=compose_dir)
        covered.update(domains)
    command("docker", "compose", "exec", "-T", "nginx", "nginx", "-t", cwd=compose_dir)
    command("docker", "compose", "exec", "-T", "nginx", "nginx", "-s", "reload", cwd=compose_dir)


def register_renewal(config_path, config):
    # Stable worker survives removal of source release directories. No new renewal scheduler.
    settings = config["public_tls"]
    worker = WORKERS / config["project"]
    worker.mkdir(parents=True, exist_ok=True, mode=0o700)
    if worker.resolve() != worker.absolute():
        raise ValueError("TLS worker must not traverse symlinks")
    (worker / "trust").mkdir(exist_ok=True, mode=0o700)
    atomic_write(worker / "public_tls.py", Path(__file__).read_bytes())
    atomic_write(worker / "trust/isrg-roots.pem", ROOTS.read_bytes())
    service = settings.get("renewal_service", "certbot-docker-renew.service")
    if not re.fullmatch(r"[a-zA-Z0-9_-]+\.service", service):
        raise ValueError("Invalid existing Certbot renewal service")
    # Certbot runs in Docker; the existing host service owns post-renewal work.
    hooks = SYSTEMD / (service + ".d")
    hooks.mkdir(parents=True, exist_ok=True)
    hook = hooks / ("edol-" + config["project"] + ".conf")
    invocation = shlex.join([shutil.which("python3") or "/usr/bin/python3", str(worker / "public_tls.py"),
                             "refresh", "--config", str(config_path)]).replace("%", "%%")
    unit = "edol-public-tls-" + config["project"] + ".service"
    atomic_write(SYSTEMD / unit, ("[Unit]\nDescription=Refresh public MQTT TLS for " + config["project"]
                                 + "\n[Service]\nType=oneshot\nExecStart=" + invocation + "\n").encode(), mode=0o644)
    # Dispatch independent oneshots so a Dev refresh failure cannot skip Prod refresh.
    atomic_write(hook, ("[Service]\nExecStartPost=/usr/bin/systemctl --no-block start " + unit + "\n").encode(), mode=0o644)
    command("systemctl", "daemon-reload")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("bootstrap", "refresh"))
    parser.add_argument("--config", type=Path, required=True)
    args = parser.parse_args()
    if os.name != "posix" or os.geteuid() != 0:
        raise ValueError("Public TLS bootstrap/renewal requires the Linux root operator")
    config_path = regular(args.config.absolute())
    metadata = config_path.stat()
    if metadata.st_mode & 0o077:
        raise ValueError("Protect instance configuration with mode 0600")
    config = json.loads(config_path.read_text())
    if not enabled(config) or config["project"] != f"edol-{config['environment']}-secure" \
            or config["environment"] not in ("dev", "prod"):
        raise ValueError("Explicit public TLS secure instance required")
    state = config_path.parent / "state"
    import fcntl
    lock = config_path.parent.parent / f".{config['environment']}-deploy.lock"
    if lock.is_symlink() or lock.resolve() != lock.absolute():
        raise ValueError("Instance lock must not traverse symlinks")
    with lock.open("a") as handle:
        # Root bootstrap must not prevent the normal deployment user reopening its lock.
        os.fchown(handle.fileno(), metadata.st_uid, metadata.st_gid)
        os.fchmod(handle.fileno(), 0o600)
        fcntl.flock(handle, fcntl.LOCK_EX)
        if args.action == "bootstrap":
            bootstrap(config_path, config)
        install(config, state, lineage(config), metadata.st_uid, metadata.st_gid)
        state.mkdir(mode=0o700, exist_ok=True)
        os.chown(state, metadata.st_uid, metadata.st_gid)
        fingerprint = hashlib.sha256((directory(config) / SERVER_CERTIFICATE).read_bytes()).hexdigest().encode()
        applied = state / "public-tls-applied.sha256"
        if not applied.is_file() or applied.read_bytes() != fingerprint:
            if reload_broker(config):
                atomic_write(applied, fingerprint, metadata.st_uid, metadata.st_gid)
        print("Public MQTT TLS installed; existing authority and device identities retained.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        raise SystemExit("Public TLS operation failed: " + type(error).__name__) from None
