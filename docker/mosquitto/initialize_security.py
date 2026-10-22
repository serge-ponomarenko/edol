"""Create a new dynsec file from process-local secrets; never prints credentials."""

import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import secrets


def acl(kind, topic):
    return {"acltype": kind, "topic": topic, "allow": True}


def subscriber(topics):
    return [entry for topic in topics for entry in
            (acl("subscribePattern", topic), acl("publishClientReceive", topic))]


def build_config(passwords):
    """Only declared service accounts exist; agents are provisioned by Core."""
    if len(set(passwords.values())) != len(passwords):
        raise ValueError("Service accounts require distinct passwords")
    permissions = {
        "core-events": [acl("publishClientSend", "edolcore/#")],
        "hub-events": subscriber(["edolcore/#"]),
        "notify-events": subscriber([
            "edolcore/printer/online", "edolcore/printer/offline",
            *[f"edolcore/print/{event}" for event in (
                "started", "running", "paused", "finished", "failed", "error",
                "progress", "metadata", "timelapse")]]),
        "ams-events": subscriber(["edolcore/ams", "edolcore/print/ams"]),
        "core-agent-ingress": subscriber([
            f"edol/agent/v1/+/{suffix}" for suffix in ("printer/report", "heartbeat", "events")]),
        "core-agent-commands": [acl("publishClientSend", f"edol/agent/v1/+/commands/{kind}")
                                for kind in ("printer", "agent")],
        "core-agent-control": [acl("publishClientSend", "$CONTROL/dynamic-security/v1"),
                               *subscriber(["$CONTROL/dynamic-security/v1/response"])],
    }
    clients = []
    for username in permissions:
        password = passwords[username]
        if len(password) < 32:
            raise ValueError("Every service password must contain at least 32 characters")
        salt = secrets.token_bytes(12)
        iterations = 210000
        digest = hashlib.pbkdf2_hmac("sha512", password.encode(), salt, iterations)
        clients.append({"username": username, "password": base64.b64encode(digest).decode(),
                        "salt": base64.b64encode(salt).decode(), "iterations": iterations,
                        "roles": [{"rolename": username}]})
    return {"defaultACLAccess": {"publishClientSend": False, "publishClientReceive": False,
                                 "subscribe": False, "unsubscribe": False},
            "clients": clients, "groups": [],
            "roles": [{"rolename": name, "acls": rules} for name, rules in permissions.items()]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    names = {"core-events": "CORE_EVENTS", "hub-events": "HUB", "notify-events": "NOTIFY",
             "ams-events": "AMS", "core-agent-ingress": "INGRESS", "core-agent-commands": "COMMAND",
             "core-agent-control": "CONTROL"}
    config = build_config({user: os.environ[f"EDOL_MQTT_{suffix}_PASSWORD"] for user, suffix in names.items()})
    # Exclusive creation prevents silently resetting the persistent credential/revocation ledger.
    with args.output.open("x", encoding="utf-8") as stream:
        json.dump(config, stream)
    print("Created broker authority file. Restrict its directory to the broker account before use.")


if __name__ == "__main__":
    main()
