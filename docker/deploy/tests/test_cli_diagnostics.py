"""Exercise the real script/import boundary without accessing Docker or instance state."""

from pathlib import Path
import subprocess
import sys
import unittest


class CliDiagnosticsTests(unittest.TestCase):
    def test_production_exception_retains_safe_stage_when_launched_as_script(self):
        script = Path(__file__).resolve().parents[1] / "dev_deploy.py"
        harness = """
import runpy
import sys
from unittest.mock import patch
sys.path.insert(0, sys.argv[1])
def fail_before_configuration(*args, **kwargs):
    from prod_deploy import DeploymentFailure
    raise DeploymentFailure('smtp-config', ('keycloak',), status=403)
with patch('argparse.ArgumentParser.parse_args', side_effect=fail_before_configuration):
    runpy.run_path(sys.argv[2], run_name='__main__')
"""
        result = subprocess.run([sys.executable, "-B", "-c", harness, str(script.parent), str(script)],
                                capture_output=True, text=True, check=False)
        self.assertEqual(result.returncode, 1)
        self.assertEqual(result.stderr.strip(),
                         "Deployment stopped: stage=smtp-config services=keycloak last_http_status=403")
        self.assertEqual(result.stdout, "")
