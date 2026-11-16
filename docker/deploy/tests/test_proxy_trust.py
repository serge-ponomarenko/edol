"""Proxy drift must block either secure environment before stopping its services."""

import json
from pathlib import Path
import unittest
from unittest.mock import patch

from dev_deploy import Deployment, DeploymentFailure, failure_summary
from test_deployment import fixture


class ProxyTrustTests(unittest.TestCase):
    def test_exact_running_proxy_is_required_in_both_environments(self):
        for environment in ("dev", "prod"):
            config = fixture(environment=environment)
            network = [{"Containers": {"proxy": {"Name": "nginx-home",
                        "IPv4Address": config["proxy_address"] + "/24"},
                        "core": {"Name": "edol-core-dev", "IPv4Address": "192.0.2.99/24"}}}]
            deployment = Deployment(config, Path("/state"))
            with patch("dev_deploy.run", side_effect=[json.dumps(network), "true"]) as command:
                deployment.validate_proxy_trust()
                self.assertEqual(command.call_count, 2)
            for response in ("false", "null"):
                with patch("dev_deploy.run", side_effect=[json.dumps(network), response]):
                    with self.assertRaises(DeploymentFailure):
                        deployment.validate_proxy_trust()

    def test_wrong_missing_or_malformed_proxy_blocks_before_any_deployment_command(self):
        for environment in ("dev", "prod"):
            deployment = Deployment(fixture(environment=environment), Path("/state"))
            for metadata in ("not-json", "[]", '[{"Containers": {}}]', json.dumps([
                    {"Containers": {"proxy": {"Name": "nginx-home", "IPv4Address": "192.0.2.99/24"}}}])):
                with patch("dev_deploy.run", return_value=metadata) as command, \
                        patch.object(deployment, "stop_instances") as stop:
                    with self.assertRaises(DeploymentFailure) as caught:
                        deployment.apply(environment=environment)
                    self.assertEqual(failure_summary(caught.exception),
                                     "Deployment stopped: stage=proxy-trust services=deployment")
                    stop.assert_not_called()
                    self.assertEqual(command.call_count, 1)

    def test_home_never_requires_or_inspects_a_public_proxy(self):
        with patch("dev_deploy.run") as command:
            Deployment(fixture("home"), Path("/state")).validate_proxy_trust()
            command.assert_not_called()

    def test_dev_and_prod_uploads_share_long_transfer_budget(self):
        root = Path(__file__).resolve().parents[2] / "nginx"
        for environment in ("dev", "prod"):
            template = (root / f"{environment}.conf.template").read_text()
            block = template.split("location /api/agent/v1/ {", 1)[1].split("}", 1)[0]
            for directive in ("proxy_request_buffering off;", "proxy_read_timeout 960s;",
                              "proxy_send_timeout 60s;", "client_body_timeout 60s;"):
                self.assertIn(directive, block)
