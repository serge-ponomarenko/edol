"""Stage a Dev MQTT server certificate using the existing CA and server key."""

import argparse
import hashlib
import ipaddress
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import tempfile
from urllib.parse import urlsplit


SERVER_CERTIFICATE = "server.pem"


def openssl(*args):
    result = subprocess.run(["openssl", *map(str, args)], input=b"", capture_output=True,
                            check=False, timeout=20)
    if result.returncode:
        raise ValueError("MQTT certificate validation/signing failed; protected diagnostics suppressed")
    return result.stdout


def regular(path):
    if path.is_symlink() or not path.is_file() or path.resolve() != path.absolute():
        raise ValueError("MQTT certificate inputs must be regular files without symlink traversal")
    return path


def public_host(config):
    host = urlsplit(config["hub_origin"]).hostname or ""
    if not re.fullmatch(r"[a-zA-Z0-9](?:[a-zA-Z0-9.-]{0,251}[a-zA-Z0-9])?", host):
        raise ValueError("Dev MQTT requires a valid public hostname")
    return host


def inputs(config, state):
    if config["environment"] != "dev" or config["mode"] != "secure" or config["project"] != "edol-dev-secure":
        raise ValueError("Automatic MQTT certificate preparation is restricted to Secure Dev")
    directory = Path(config["mqtt_cert_dir"])
    ipaddress.IPv4Address(config["bind_address"])
    if state.is_symlink() or state.resolve() != state.absolute():
        raise ValueError("MQTT certificate state must not traverse symlinks")
    return directory, regular(directory / "ca.pem"), regular(directory / "server-key.pem")


def verify(config, state, certificate, external=True):
    _, authority, key = inputs(config, state)
    regular(certificate)
    openssl("verify", "-purpose", "sslserver", "-CAfile", authority, certificate)
    openssl("verify", "-CAfile", authority, "-verify_hostname", "mqtt", certificate)
    openssl("verify", "-CAfile", authority, "-verify_ip", config["bind_address"], certificate)
    if external:
        openssl("verify", "-CAfile", authority, "-verify_hostname", public_host(config), certificate)
    if openssl("x509", "-in", certificate, "-noout", "-pubkey") != openssl("pkey", "-in", key, "-pubout"):
        raise ValueError("MQTT server certificate does not match the preserved server key")


def alternate_names(config, certificate):
    text = openssl("x509", "-in", certificate, "-noout", "-ext", "subjectAltName").decode("ascii")
    names = {"DNS:mqtt", "DNS:" + public_host(config), "IP:" + config["bind_address"]}
    for dns, address in re.findall(r"DNS:([^,\s]+)|IP Address:([^,\s]+)", text):
        if address:
            names.add("IP:" + str(ipaddress.ip_address(address)))
        elif re.fullmatch(r"(?:\*\.)?[a-zA-Z0-9][a-zA-Z0-9.-]*", dns):
            names.add("DNS:" + dns)
        else:
            raise ValueError("Unsupported existing MQTT certificate SAN")
    return ",".join(sorted(names))


def write_new(path, data):
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0), 0o600)
    with os.fdopen(descriptor, "wb") as stream:
        stream.write(data)


def stage(config, state):
    """No active certificate, private key, CA or broker authority is changed."""
    if config["mode"] == "home":
        return None
    directory, authority, key = inputs(config, state)
    current = regular(directory / SERVER_CERTIFICATE)
    verify(config, state, current, external=False)
    try:
        verify(config, state, current)
        return None
    except ValueError:
        pass
    pending = state / "mqtt-server.pending.pem"
    if pending.exists() or pending.is_symlink():
        verify(config, state, pending)
        return pending
    signer = regular(state.parent / "ca/ca-key.pem")
    with tempfile.TemporaryDirectory(prefix="mqtt-certificate-", dir=state) as work:
        work = Path(work)
        extension = work / "server.ext"
        extension.write_text("basicConstraints=critical,CA:FALSE\nkeyUsage=critical,digitalSignature,keyEncipherment\n"
                             "extendedKeyUsage=serverAuth\nsubjectAltName=" + alternate_names(config, current) + "\n",
                             encoding="ascii")
        openssl("req", "-new", "-key", key, "-subj", "/CN=mqtt", "-out", work / "server.csr")
        openssl("x509", "-req", "-in", work / "server.csr", "-CA", authority, "-CAkey", signer,
                "-set_serial", "0x" + secrets.token_hex(16), "-days", "365", "-sha256",
                "-extfile", extension, "-out", work / SERVER_CERTIFICATE)
        verify(config, state, work / SERVER_CERTIFICATE)
        write_new(pending, (work / SERVER_CERTIFICATE).read_bytes())
    return pending


def activate(config, state):
    """Called only after the Dev broker has stopped under the deployment lock."""
    pending = stage(config, state)
    if pending is None:
        return
    directory, _, _ = inputs(config, state)
    current = regular(directory / SERVER_CERTIFICATE)
    previous = current.read_bytes()
    backup = state / ("mqtt-server.previous-" + hashlib.sha256(previous).hexdigest()[:16] + ".pem")
    if not backup.exists():
        write_new(backup, previous)
    elif regular(backup).read_bytes() != previous:
        raise ValueError("MQTT certificate backup differs; refusing replacement")
    with tempfile.NamedTemporaryFile(prefix="server-", suffix=".pem", dir=directory, delete=False) as output:
        replacement = Path(output.name)
        output.write(pending.read_bytes())
    try:
        os.chmod(replacement, 0o600)
        verify(config, state, replacement)
        os.replace(replacement, current)
    finally:
        replacement.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, required=True)
    args = parser.parse_args()
    if os.name != "posix" or args.config.stat().st_mode & 0o077:
        raise ValueError("Use the protected Linux instance config")
    config = json.loads(args.config.read_text())
    state = args.config.resolve().parent / "state"
    import fcntl
    with (state.parent.parent / ".dev-deploy.lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        stage(config, state)
    print("Secure Dev MQTT certificate ready or staged. Active broker/configuration unchanged.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        raise SystemExit("MQTT certificate preparation failed: " + type(error).__name__) from None
