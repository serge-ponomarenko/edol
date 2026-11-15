"""Release guard regressions; no registry or runtime access."""

import importlib.util
from pathlib import Path
import shutil
import sys
import tempfile
import unittest
from unittest.mock import patch
from urllib.error import HTTPError


SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))
SPEC = importlib.util.spec_from_file_location("edol_release", SCRIPTS / "release.py")
RELEASE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(RELEASE)
ROOT = SCRIPTS.parents[1]
import prepare_release


class ReleaseValidationTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.version = RELEASE.ET.parse(ROOT / "pom.xml").getroot().findtext(
            "m:properties/m:revision", namespaces=RELEASE.NS)
        shutil.copy(ROOT / "pom.xml", self.root)
        (self.root / "CHANGELOG.md").write_text(
            "# Changelog\n\n## [Unreleased]\n\n## [" + self.version
            + "] - 2026-11-15\n\n### Added\n\n- Released fixture feature.\n",
            encoding="utf-8")
        for pom in ROOT.glob("edol-*/pom.xml"):
            target = self.root / pom.parent.name
            target.mkdir()
            shutil.copy(pom, target)

    def test_matching_tag(self):
        version, notes = RELEASE.validate(self.root, "refs/tags/v" + self.version)
        self.assertEqual(version, self.version)
        self.assertTrue(notes)

    def test_wrong_tag(self):
        with self.assertRaisesRegex(ValueError, "Tag and Maven"):
            RELEASE.validate(self.root, "refs/tags/invalid")

    def test_parent_drift(self):
        pom = self.root / "edol-hub/pom.xml"
        pom.write_text(pom.read_text().replace("${revision}", "999.0.0"))
        with self.assertRaisesRegex(ValueError, "parent version"):
            RELEASE.validate(self.root, "refs/heads/main")

    def test_missing_release_notes(self):
        (self.root / "CHANGELOG.md").write_text("# Changelog\n")
        with self.assertRaisesRegex(ValueError, "Missing dated"):
            RELEASE.validate(self.root, "refs/tags/v" + self.version)

    def test_independent_module_version(self):
        pom = self.root / "edol-hub/pom.xml"
        pom.write_text(pom.read_text().replace("<name>EdolHub</name>",
                                             "<version>2.0.0</version><name>EdolHub</name>"))
        with self.assertRaisesRegex(ValueError, "inherit"):
            RELEASE.validate(self.root, "refs/heads/main")

    def test_prepare_moves_pending_and_changes_only_root_revision(self):
        notes = self.root / "CHANGELOG.md"
        notes.write_text(notes.read_text().replace("## [Unreleased]", "## [Unreleased]\n\n### Fixed\n\n- Example fix."))
        before = (self.root / "edol-hub/pom.xml").read_bytes()
        prepare_release.prepare(self.root, "999.0.0", "2026-11-15")
        self.assertEqual(RELEASE.validate(self.root, "refs/tags/v999.0.0")[0], "999.0.0")
        self.assertEqual(before, (self.root / "edol-hub/pom.xml").read_bytes())
        self.assertIn("Example fix", RELEASE.validate(self.root, "")[1])

    def test_prepare_rejects_empty_pending_without_writes(self):
        pom = (self.root / "pom.xml").read_bytes()
        notes = (self.root / "CHANGELOG.md").read_bytes()
        with self.assertRaisesRegex(ValueError, "Unreleased"):
            prepare_release.prepare(self.root, "999.0.0", "2026-11-15")
        self.assertEqual(pom, (self.root / "pom.xml").read_bytes())
        self.assertEqual(notes, (self.root / "CHANGELOG.md").read_bytes())

    def test_version_order(self):
        self.assertLess(prepare_release.version_key("1.0.0-rc.2"),
                        prepare_release.version_key("1.0.0"))
        self.assertLess(prepare_release.version_key("1.0.9"),
                        prepare_release.version_key("1.0.10"))

    @patch.dict(RELEASE.os.environ, {"RELEASE_REF": "refs/heads/main",
                                   "REGISTRY_TOKEN": "test", "REGISTRY_USER": "test"})
    def test_existing_tag_skips_publication(self):
        with patch.object(RELEASE, "release_api", return_value=("https://example.com", "owner/edol", "test")), \
                patch.object(RELEASE, "request", side_effect=[{}, {"draft": False}]), \
                patch.object(RELEASE, "publish") as publish:
            RELEASE.release_main(self.version, "notes")
            publish.assert_not_called()

    @patch.dict(RELEASE.os.environ, {"RELEASE_REF": "refs/heads/main"})
    def test_existing_draft_blocks_instead_of_reporting_success(self):
        with patch.object(RELEASE, "release_api", return_value=("https://example.com", "owner/edol", "test")), \
                patch.object(RELEASE, "request", side_effect=[{}, {"draft": True}]), \
                patch.object(RELEASE, "publish") as publish:
            with self.assertRaisesRegex(ValueError, "incomplete"):
                RELEASE.release_main(self.version, "notes")
            publish.assert_not_called()

    @patch.dict(RELEASE.os.environ, {"RELEASE_REF": "refs/heads/main",
                                   "REGISTRY_TOKEN": "test", "REGISTRY_USER": "test"})
    def test_new_version_tags_verified_commit_before_publication(self):
        missing = HTTPError("https://example.com", 404, "Not found", {}, None)
        self.addCleanup(missing.close)
        with patch.object(RELEASE, "release_api", return_value=("https://example.com", "owner/edol", "test")), \
                patch.object(RELEASE, "request", side_effect=[missing, {}]) as request, \
                patch.object(RELEASE, "run", return_value="a" * 40), \
                patch.object(RELEASE, "publish") as publish:
            RELEASE.release_main(self.version, "notes")
            self.assertEqual(request.call_args.args[3]["target"], "a" * 40)
            publish.assert_called_once_with(self.version, "notes", release_ref="refs/tags/v" + self.version)

    @patch.dict(RELEASE.os.environ, {"RELEASE_REF": "refs/heads/main"})
    def test_auth_error_does_not_create_tag(self):
        forbidden = HTTPError("https://example.com", 403, "Forbidden", {}, None)
        self.addCleanup(forbidden.close)
        with patch.object(RELEASE, "release_api", return_value=("https://example.com", "owner/edol", "test")), \
                patch.object(RELEASE, "request", side_effect=forbidden) as request:
            with self.assertRaises(HTTPError):
                RELEASE.release_main(self.version, "notes")
            self.assertEqual(request.call_count, 1)
