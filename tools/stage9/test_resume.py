"""Offline tests; never contact Docker, Keycloak, MQTT or hardware."""
import argparse
import base64
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import resume


class ResumeTest(unittest.TestCase):
    def test_existing_audiences_are_not_duplicated_on_rerun(self):
        clients = {name: name for name in resume.AUDIENCES}
        writes = []
        def admin(path, data=None):
            if data is not None:
                writes.append(data)
                return None
            client = path.split("/")[2]
            return [{"protocolMapper": "oidc-audience-mapper", "config": {
                "included.custom.audience": audience, "access.token.claim": "true"}}
                for audience in resume.AUDIENCES[client]]
        resume.ensure_audiences(admin, clients)
        self.assertEqual(writes, [])

    def test_missing_audiences_receive_only_access_token_mappers(self):
        clients = {name: name for name in resume.AUDIENCES}
        writes = []
        def admin(path, data=None):
            if data is not None:
                writes.append(data)
            return []
        resume.ensure_audiences(admin, clients)
        self.assertEqual(len(writes), 5)
        self.assertTrue(all(m["config"]["access.token.claim"] == "true" for m in writes))
        self.assertTrue(all(m["config"]["id.token.claim"] == "false" for m in writes))

    def test_missing_authority_cannot_report_readiness(self):
        self.assertFalse(resume.ready({"checks": {"required_authority": "BLOCKED"}}))

    def test_redirect_is_never_followed_with_service_credentials(self):
        self.assertIsNone(resume.NoRedirect().redirect_request(None, None, 302, "", {}, "http://other-host"))

    def test_prepare_preserves_existing_files_and_generates_only_candidates(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            existing = root / "compose.backend.yaml"
            existing.write_text("existing configuration", encoding="utf-8")
            args = argparse.Namespace(run_dir=root, image_tag="fixed")
            rows = {"edolhub": {"Config": {"Env": ["EDOL_KEYCLOAK_ISSUER_URI=http://test/realms/edol"]}}}
            with patch.object(resume, "command", return_value="2.24.4"):
                resume.prepare(args, rows)
                override = (root / "remediation/compose.resume.yaml").read_text()
                self.assertIn("127.0.0.1:18081:8080", override)
                self.assertIn("ports: !override", override)
                self.assertIn("core.agent.manage", override)
                self.assertNotIn("PASSWORD", override)
                self.assertEqual(existing.read_text(), "existing configuration")
                with self.assertRaises(FileExistsError):
                    resume.prepare(args, rows)

    def test_old_compose_cannot_generate_an_unsafe_port_merge(self):
        with patch.object(resume, "command", return_value="2.23.0"):
            with self.assertRaises(resume.DiagnosticError):
                resume.prepare(argparse.Namespace(), {})

    def test_claim_summary_does_not_return_tokens_or_other_claims(self):
        payload = {"aud": "edol-core-api", "scope": "tenant.context", "secret": "never print"}
        encoded = base64.urlsafe_b64encode(json.dumps(payload).encode()).decode().rstrip("=")
        summary = resume.claim_summary("header." + encoded + ".signature")
        self.assertEqual(summary["audiences"], ["edol-core-api"])
        self.assertNotIn("never print", json.dumps(summary))

    def test_identity_mutation_requires_explicit_opt_in(self):
        with self.assertRaises(resume.DiagnosticError):
            resume.identity(argparse.Namespace(allow_identity_update=False), {})

    def test_nonzero_command_never_leaks_stderr_or_environment(self):
        import subprocess
        with patch.object(resume.subprocess, "run", return_value=subprocess.CompletedProcess(
                ["docker"], 1, "secret stdout", "secret stderr")):
            with self.assertRaisesRegex(resume.DiagnosticError, "^Command failed: docker inspect$"):
                resume.command(["docker", "inspect", "container"])


if __name__ == "__main__":
    unittest.main()
