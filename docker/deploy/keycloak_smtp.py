"""Apply only the managed SMTP map; never round-trip a masked realm password."""

import http.client
import json
from urllib.parse import urlencode

from model import smtp_settings


def request(address, method, path, body, headers, expected):
    connection = http.client.HTTPConnection(address, 8080, timeout=15)
    try:
        connection.request(method, path, body=body, headers=headers)
        response = connection.getresponse()
        if response.status != expected:
            # Error bodies can echo credentials or protected configuration.
            raise RuntimeError("Keycloak SMTP configuration request failed")
        return response.read()
    finally:
        connection.close()


def admin_token(config, address):
    body = urlencode({"grant_type": "password", "client_id": "admin-cli",
                      "username": config["keycloak_admin_user"], "password": config["secrets"]["keycloak_admin"]})
    response = request(address, "POST", "/realms/master/protocol/openid-connect/token", body,
                       {"Content-Type": "application/x-www-form-urlencoded"}, 200)
    token = json.loads(response)["access_token"]
    if not isinstance(token, str) or not token:
        raise ValueError("Keycloak admin token is absent")
    return token


def configure(config, address):
    settings = smtp_settings(config)
    token = admin_token(config, address)
    request(address, "PUT", "/admin/realms/edol", json.dumps({"smtpServer": settings}),
            {"Content-Type": "application/json", "Authorization": "Bearer " + token}, 204)
