"""Readiness protocol checks using mocks and an owned localhost HTTP fixture only."""

from contextlib import contextmanager, redirect_stderr
import http.client
import http.server
import io
import os
from pathlib import Path
import shutil
import subprocess
import sys
import threading
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import dev_deploy
import model
from test_deployment import fixture


@contextmanager
def terminal_http():
    received = []

    class Handler(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            authorization = self.headers.get("Authorization")
            received.append(authorization)
            self.send_response(401 if authorization == model.AMS_READINESS_HEADERS["Authorization"] else 400)
            self.send_header("WWW-Authenticate", "EDOL-Terminal")
            self.end_headers()

        def log_message(self, *args):
            pass

    server = http.server.HTTPServer(("127.0.0.1", 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        yield server.server_port, received
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=5)


class ReadinessTests(unittest.TestCase):
    def test_python_probe_sends_the_invalid_terminal_header(self):
        deployment = dev_deploy.Deployment(fixture(), Path("/state"))
        with terminal_http() as (port, received), patch.object(deployment, "ip", return_value="127.0.0.1"):
            deployment.wait_http("edolams", port, "/api/terminal/v1/state", {401}, headers=model.AMS_READINESS_HEADERS)
        self.assertEqual(received, ["EDOL-Terminal readiness-probe"])

    def test_unexpected_status_codes_are_not_ready(self):
        for status in (200, 400, 403, 404, 500, 503):
            with self.subTest(status=status):
                connection = Mock()
                connection.getresponse.return_value.status = status
                error = self.failed_http(connection)
                self.assertEqual(error.status, status)
                self.assertIn("services=edolams", dev_deploy.failure_summary(error))
                self.assertIn(f"last_http_status={status}", dev_deploy.failure_summary(error))
                connection.close.assert_called_once()

    def test_connection_and_protocol_failures_are_not_ready(self):
        for problem in (ConnectionRefusedError("synthetic-sensitive-detail"),
                        http.client.BadStatusLine("synthetic-sensitive-detail")):
            connection = Mock()
            connection.request.side_effect = problem
            error = self.failed_http(connection)
            self.assertIsNone(error.status)
            self.assertNotIn("synthetic-sensitive-detail", dev_deploy.failure_summary(error))
            connection.close.assert_called_once()

    def failed_http(self, connection):
        deployment = dev_deploy.Deployment(fixture(), Path("/state"))
        with patch.object(deployment, "ip", return_value="127.0.0.1"), \
                patch("dev_deploy.http.client.HTTPConnection", return_value=connection), \
                patch("dev_deploy.time.monotonic", side_effect=(0, 0, 2)), patch("dev_deploy.time.sleep"):
            with self.assertRaises(dev_deploy.DeploymentFailure) as captured:
                deployment.wait_http("edolams", 8099, "/api/terminal/v1/state", {401}, timeout=1,
                                     headers=model.AMS_READINESS_HEADERS)
        return captured.exception

    def test_header_is_added_only_to_secure_ams_healthcheck(self):
        secure = model.compose(fixture(), Path("/source"), Path("/state"))["services"]
        home = model.compose(fixture("home"), Path("/source"), Path("/state"))["services"]
        self.assertIn("Authorization: EDOL-Terminal readiness-probe", secure["edolams"]["healthcheck"]["test"][-1])
        for name, service in secure.items():
            if name != "edolams":
                self.assertNotIn("readiness-probe", str(service["healthcheck"]))
        self.assertNotIn("readiness-probe", str(home))

    def test_compose_failure_reports_unhealthy_service_without_original_diagnostics(self):
        deployment = dev_deploy.Deployment(fixture(), Path("/state"))
        results = [RuntimeError("Authorization: secret; SQL parameter secret"), "core-id", "running healthy",
                   "ams-id", "running unhealthy", "notify-id", "running healthy"]
        with patch("dev_deploy.run", side_effect=results):
            with self.assertRaises(dev_deploy.DeploymentFailure) as captured:
                deployment.dc("up", "-d", "--wait", "edolcore", "edolams", "edolnotify")
        summary = dev_deploy.failure_summary(captured.exception)
        self.assertEqual(summary, "Deployment stopped: stage=compose-up services=edolams")
        self.assertNotIn("secret", summary)

    def test_failed_diagnostics_keep_original_service_scope(self):
        deployment = dev_deploy.Deployment(fixture(), Path("/state"))
        with patch("dev_deploy.run", side_effect=RuntimeError("sensitive command output")):
            with self.assertRaises(dev_deploy.DeploymentFailure) as captured:
                deployment.dc("up", "--wait", "edolams")
        self.assertEqual(captured.exception.services, ("edolams",))
        self.assertNotIn("sensitive", dev_deploy.failure_summary(captured.exception))

    def test_unstructured_exception_details_stay_hidden(self):
        summary = dev_deploy.failure_summary(ValueError("Authorization: secret; SQL parameter secret"))
        self.assertIn("ValueError", summary)
        self.assertNotIn("secret", summary)

    def test_cleanup_failure_does_not_replace_original_failure(self):
        deployment = dev_deploy.Deployment(fixture(), Path("/state"))
        original = dev_deploy.DeploymentFailure("http-readiness", ("edolams",), status=400)

        def compose_command(*args):
            if args[0] == "stop":
                raise RuntimeError("sensitive cleanup output")
            return ""

        output = io.StringIO()
        with patch("dev_deploy.run"), patch("dev_deploy.stop_dev_containers"), \
                patch.object(deployment, "dc", side_effect=compose_command), \
                patch.object(deployment, "sql"), patch.object(deployment, "guard_database"), \
                patch.object(deployment, "migrate", side_effect=original), redirect_stderr(output):
            with self.assertRaises(dev_deploy.DeploymentFailure) as captured:
                deployment.apply()
        self.assertIs(captured.exception, original)
        self.assertNotIn("sensitive cleanup output", output.getvalue())

    def test_native_healthcheck_sends_header_and_rejects_missing_header_400(self):
        bash = str(Path("C:/Program Files/Git/bin/bash.exe")) if os.name == "nt" else shutil.which("bash")
        if not bash or not Path(bash).is_file():
            self.skipTest("Bash is unavailable")
        with terminal_http() as (port, received):
            for headers, expected_exit in ((model.AMS_READINESS_HEADERS, 0), (None, 1)):
                command = model.http_healthcheck(port, "/api/terminal/v1/state", (401,), headers)["test"][-1]
                result = subprocess.run([bash, "-ec", command], capture_output=True, check=False, timeout=5)
                self.assertEqual(result.returncode, expected_exit)
        self.assertEqual(received, ["EDOL-Terminal readiness-probe", None])


if __name__ == "__main__":
    unittest.main()
