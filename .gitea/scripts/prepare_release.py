"""Prepare a reviewed release diff without committing, tagging or publishing."""

import argparse
import datetime
from pathlib import Path
import re

from release import NS, validate
import xml.etree.ElementTree as ET


def version_key(version):
    match = re.fullmatch(r"(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-rc\.([1-9]\d*))?", version)
    if not match:
        raise ValueError("Expected X.Y.Z or X.Y.Z-rc.N")
    major, minor, patch = map(int, match.group(1, 2, 3))
    return major, minor, patch, match[4] is None, int(match[4] or 0)


def prepare(root, version, date):
    date = datetime.date.fromisoformat(date).isoformat()
    pom_path, notes_path = root / "pom.xml", root / "CHANGELOG.md"
    pom_original, notes_original = pom_path.read_bytes(), notes_path.read_bytes()
    pom, notes = pom_original.decode("utf-8").replace("\r\n", "\n"), notes_original.decode("utf-8").replace("\r\n", "\n")
    current = ET.fromstring(pom).findtext("m:properties/m:revision", namespaces=NS)
    if version_key(version) <= version_key(current):
        raise ValueError("New release must be newer than current revision")
    if re.search(r"^## \[" + re.escape(version) + r"\]", notes, re.M):
        raise ValueError("Release section already exists")
    pending = re.search(r"^## \[Unreleased\][ \t]*\n", notes, re.M)
    if not pending:
        raise ValueError("Unreleased heading is required")
    next_heading = re.search(r"^## ", notes[pending.end():], re.M)
    end = pending.end() + next_heading.start() if next_heading else len(notes)
    pending_text = notes[pending.end():end].strip()
    if not re.search(r"^- \S", pending_text, re.M):
        raise ValueError("Unreleased must contain meaningful change bullets")
    updated_notes = (notes[:pending.start()] + "## [Unreleased]\n\n## [" + version
                     + "] - " + date + "\n\n" + pending_text + "\n\n" + notes[end:])
    updated_pom, count = re.subn(r"<revision>[^<]+</revision>", "<revision>" + version + "</revision>", pom)
    if count != 1:
        raise ValueError("Expected exactly one root revision")
    # All input checks complete before touching the two reviewable files.
    try:
        pom_path.write_text(updated_pom, encoding="utf-8")
        notes_path.write_text(updated_notes, encoding="utf-8")
        validate(root, "refs/heads/main")
    except Exception:
        pom_path.write_bytes(pom_original)
        notes_path.write_bytes(notes_original)
        raise


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("version")
    parser.add_argument("--date", default=datetime.date.today().isoformat())
    args = parser.parse_args()
    prepare(Path("."), args.version, args.date)
    print("Prepared release diff; review, commit and merge to main: " + args.version)
