"""Offline checks for scanner-task identity and PR report lifecycle."""

import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


spec = importlib.util.spec_from_file_location("sonar_report", Path(__file__).resolve().parents[1] / "sonar_report.py")
report = importlib.util.module_from_spec(spec)
spec.loader.exec_module(report)


class SonarReportTests(unittest.TestCase):
    def test_gate_is_bound_to_scanner_task_not_latest_project_analysis(self):
        gate = {"status": "ERROR", "conditions": []}
        with patch.object(report, "request", side_effect=[
                {"task": {"status": "SUCCESS", "analysisId": "exact-analysis"}}, {"projectStatus": gate}]) as request:
            result = report.collect_gate("https://sonar.test", "synthetic-token",
                                         "ceTaskId=exact-task\nceTaskUrl=https://untrusted.test/task")
        self.assertEqual(result, gate)
        self.assertEqual(request.call_args_list[0].args[0], "https://sonar.test/api/ce/task?id=exact-task")
        self.assertEqual(request.call_args_list[1].args[0],
                         "https://sonar.test/api/qualitygates/project_status?analysisId=exact-analysis")

    def test_pending_task_cannot_report_latest_unrelated_gate(self):
        with patch.object(report, "request", return_value={"task": {"status": "PENDING"}}) as request:
            with self.assertRaises(ValueError):
                report.collect_gate("https://sonar.test", "synthetic-token", "ceTaskId=pending")
        self.assertEqual(request.call_count, 1)

    def test_failed_gate_report_contains_conditions_and_links(self):
        body = report.render_report({"status": "ERROR", "conditions": [{
            "metricKey": "new_violations", "actualValue": "83", "errorThreshold": "0",
            "comparator": "GT", "status": "ERROR"}]}, "https://sonar.test", "a" * 40)
        self.assertIn("Quality Gate: ERROR", body)
        self.assertIn("| new_violations | 83 | 0 | GT | ERROR |", body)
        self.assertIn("https://sonar.test/security_hotspots?", body)

    def test_unavailable_result_is_explicit_not_a_pass(self):
        body = report.render_report(None, "https://sonar.test", "a" * 40)
        self.assertIn("Quality Gate unavailable", body)
        self.assertNotIn("Quality Gate: OK", body)

    def test_existing_marked_comment_is_updated_on_later_page(self):
        with patch.object(report, "request", side_effect=[
                [{"id": i, "body": "unrelated"} for i in range(100)],
                [{"id": 123, "body": report.MARKER}], {}]) as request:
            report.publish_comment("https://gitea.test/api/v1", "owner/repo", 8, "synthetic-token", "report")
        self.assertIn("page=2", request.call_args_list[1].args[0])
        self.assertEqual(request.call_args_list[2].args[2:], ("PATCH", {"body": "report"}))
        self.assertTrue(request.call_args_list[2].args[0].endswith("/issues/comments/123"))

    def test_missing_comment_is_created(self):
        with patch.object(report, "request", side_effect=[[], {}]) as request:
            report.publish_comment("https://gitea.test/api/v1", "owner/repo", 8, "synthetic-token", "report")
        self.assertEqual(request.call_args.args[2:], ("POST", {"body": "report"}))
        self.assertEqual(request.call_args.kwargs["auth_scheme"], "token")

    def test_summary_is_published_even_when_scanner_report_is_missing(self):
        with tempfile.TemporaryDirectory() as directory:
            summary = Path(directory) / "summary.md"
            event = Path(directory) / "event.json"
            event.write_text('{"number": 8}')
            env = {"SONAR_HOST_URL": "https://sonar.test", "SONAR_TOKEN": "synthetic-token",
                   "GITHUB_SHA": "a" * 40, "GITHUB_STEP_SUMMARY": str(summary),
                   "GITHUB_EVENT_NAME": "pull_request", "GITHUB_EVENT_PATH": str(event),
                   "GITHUB_API_URL": "https://gitea.test/api/v1", "GITHUB_REPOSITORY": "owner/repo",
                   "GITEA_TOKEN": "synthetic-token"}
            with patch.dict(report.os.environ, env, clear=True), \
                    patch.object(report, "collect_gate", side_effect=OSError), \
                    patch.object(report, "publish_comment") as publish, \
                    patch.object(report.Path, "read_text", return_value="ceTaskId=test"), \
                    patch.object(report.json, "loads", return_value={"number": 8}), \
                    patch.object(report.Path, "write_text"):
                report.main()
            self.assertIn("Quality Gate unavailable", summary.read_text())
            publish.assert_called_once()


if __name__ == "__main__":
    unittest.main()
