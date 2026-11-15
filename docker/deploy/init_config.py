"""Create a new protected dev instance configuration; never replace existing secrets."""

import argparse
import base64
import json
import os
from pathlib import Path
import secrets


ROOT = Path(__file__).resolve().parents[2]
CRYPTO_KEYS = ("agent_pairing", "agent_credential", "terminal_pairing", "terminal_credential", "telegram")


def new_config(mode, environment="dev"):
    config = json.loads((Path(__file__).parent / "instance.example.json").read_text())
    config.update(mode=mode, environment=environment, project=f"edol-{environment}-{mode}")
    if environment == "prod":
        config.update(subnet="10.253.94.0/24", hub_port=8090, core_port=8091, ams_port=8099,
                      mqtt_port=18885, hub_origin="https://edol.s-pon.dev", auth_origin="https://auth.edol.s-pon.dev",
                      mqtt_cert_dir="/srv/edol/secure/certs/mqtt", mqtt_truststore="/srv/edol/secure/certs/combined.p12")
    config["secrets"] = {key: secrets.token_hex(32) for key in config["secrets"]}
    for key in CRYPTO_KEYS:
        config["secrets"][key] = base64.b64encode(secrets.token_bytes(32)).decode()
    if mode == "home":
        config["subnet"] = "10.253.95.0/24" if environment == "prod" else "10.253.93.0/24"
        config["hub_origin"] = f"http://{config['bind_address']}:{config['hub_port']}"
        for key in ("auth_origin", "keycloak_admin_user", "mqtt_cert_dir", "mqtt_truststore", "smtp", "proxy_address"):
            config.pop(key)
        config["secrets"] = {"postgres": secrets.token_hex(32), "home": secrets.token_hex(32),
                             "core_web_admin": secrets.token_hex(32)}
    return config


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=("secure", "home"), required=True)
    parser.add_argument("--environment", choices=("dev", "prod"), default="dev")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    output = args.output.absolute()
    if os.name != "posix":
        raise ValueError("Create protected instance configuration on the Linux operator host")
    if ROOT == output or ROOT in output.parents:
        raise ValueError("Instance secrets must be outside the source checkout")
    if output.parent.resolve() != output.parent:
        raise ValueError("Instance destination must not traverse symlinks")
    descriptor = os.open(output, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
        json.dump(new_config(args.mode, args.environment), stream, indent=2)
        stream.write("\n")
    print("Created new protected instance configuration. Review addresses, ports and SMTP locally before rendering.")


if __name__ == "__main__":
    main()
