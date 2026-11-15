"""Validate the reactor release identity and publish Gitea release artifacts."""

import datetime
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
from urllib.parse import urlsplit
from urllib.error import HTTPError
import xml.etree.ElementTree as ET

from sonar_report import request


NS = {"m": "http://maven.apache.org/POM/4.0.0"}
VERSION_ELEMENT = "m:version"
TAG_PREFIX = "refs/tags/"
REPO_API_PREFIX = "/api/v1/repos/"
SERVICES = ("core", "hub", "notify", "ams")


def validate_module(root, module):
    child = ET.parse(root / module / "pom.xml").getroot()
    if child.findtext("m:parent/m:version", namespaces=NS) != "${revision}":
        raise ValueError("Module parent version differs: " + module)
    if child.find(VERSION_ELEMENT, NS) is not None:
        raise ValueError("Module must inherit its version: " + module)
    for dep in child.findall("m:dependencies/m:dependency", NS):
        if dep.findtext("m:artifactId", namespaces=NS) == "edol-core-api":
            if dep.find(VERSION_ELEMENT, NS) is not None:
                raise ValueError("Internal dependency version must be managed by root")


def validate(root, ref):
    pom = ET.parse(root / "pom.xml").getroot()
    if pom.findtext(VERSION_ELEMENT, namespaces=NS) != "${revision}":
        raise ValueError("Root version must use revision")
    version = pom.findtext("m:properties/m:revision", namespaces=NS)
    managed = pom.find("m:dependencyManagement/m:dependencies", NS)
    api = next(d for d in managed if d.findtext("m:artifactId", namespaces=NS) == "edol-core-api")
    if api.findtext(VERSION_ELEMENT, namespaces=NS) != "${project.version}":
        raise ValueError("Root must manage internal API at project.version")
    if not re.fullmatch(r"(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-rc\.[1-9]\d*)?", version or ""):
        raise ValueError("Expected a release version or rc version")
    for module in pom.findall("m:modules/m:module", NS):
        validate_module(root, module.text)
    notes = (root / "CHANGELOG.md").read_text(encoding="utf-8")
    match = re.search(r"^## \[" + re.escape(version) + r"\] - (\d{4}-\d{2}-\d{2})\n(.*?)(?=^## |\Z)", notes, re.M | re.S)
    if not match:
        raise ValueError("Missing dated changelog section for " + version)
    datetime.date.fromisoformat(match[1])
    if ref.startswith(TAG_PREFIX) and ref != "refs/tags/v" + version:
        raise ValueError("Tag and Maven version differ")
    return version, match[2].strip()


def run(*args, **kwargs):
    return subprocess.check_output(args, text=True, **kwargs).strip()


def release_api():
    server = os.environ["RELEASE_SERVER"].rstrip("/")
    url = urlsplit(server)
    if url.scheme != "https" or not url.netloc or url.username or url.path:
        raise ValueError("Release server must be an HTTPS origin")
    repository = os.environ["RELEASE_REPOSITORY"]
    if not re.fullmatch(r"[\w.-]+/[\w.-]+", repository):
        raise ValueError("Invalid repository")
    token = os.environ["RELEASE_TOKEN"]
    if not token:
        raise ValueError("Release token is required")
    return server, repository, token


def release_main(version, notes):
    if os.environ["RELEASE_REF"] != "refs/heads/main":
        raise ValueError("Automatic releases require main")
    server, repository, token = release_api()
    api = server + REPO_API_PREFIX + repository + "/tags"
    tag = "v" + version
    try:
        request(api + "/" + tag, token, auth_scheme="token")
    except HTTPError as error:
        if error.code != 404:
            raise
    else:
        release = request(server + REPO_API_PREFIX + repository + "/releases/tags/" + tag,
                          token, auth_scheme="token")
        if release.get("draft", True):
            raise ValueError("Existing release is incomplete; inspect before operator recovery")
        print("Version already tagged; no new release: " + tag)
        return
    if not all(os.environ.get(key) for key in ("REGISTRY_TOKEN", "REGISTRY_USER")):
        raise ValueError("Registry secrets are required before creating a tag")
    sha = run("git", "rev-parse", "HEAD")
    request(api, token, "POST", {"tag_name": tag, "target": sha,
                               "message": "Release " + version}, auth_scheme="token")
    publish(version, notes, release_ref=TAG_PREFIX + tag)


def publish(version, notes, release_ref=None):
    server, repository, token = release_api()
    url = urlsplit(server)
    registry_token = os.environ["REGISTRY_TOKEN"]
    user = os.environ["REGISTRY_USER"]
    if not all((token, registry_token, user)):
        raise ValueError("Release and registry secrets are required")
    sha = run("git", "rev-parse", "HEAD")
    tag = "v" + version
    if (release_ref or os.environ["RELEASE_REF"]) != TAG_PREFIX + tag:
        raise ValueError("Publishing requires a matching release tag")
    api = server + REPO_API_PREFIX + repository + "/releases"
    # Create a draft before publishing packages; failures never advertise a complete release.
    release = request(api, token, "POST", {
        "tag_name": tag, "target_commitish": sha, "name": tag,
        "body": notes, "draft": True, "prerelease": "-rc." in version,
    }, auth_scheme="token")
    manifests = []
    with tempfile.TemporaryDirectory(prefix="edol-release-") as config:
        env = dict(os.environ, DOCKER_CONFIG=config)
        subprocess.run(["docker", "login", url.netloc, "--username", user,
                        "--password-stdin"], input=registry_token, text=True, env=env, check=True)
        for service in SERVICES:
            image = url.netloc + "/" + repository.split("/")[0].lower() + "/edol-" + service
            version_tag, sha_tag = image + ":" + version, image + ":sha-" + sha
            run("docker", "build", "--label", "org.opencontainers.image.version=" + version,
                "--label", "org.opencontainers.image.revision=" + sha,
                "--label", "org.opencontainers.image.source=" + server + "/" + repository,
                "-f", "docker/edol-" + service + ".Dockerfile",
                "-t", version_tag, "-t", sha_tag, ".", env=env)
            subprocess.run(["docker", "push", version_tag], env=env, check=True)
            subprocess.run(["docker", "push", sha_tag], env=env, check=True)
            digests = json.loads(run("docker", "image", "inspect", version_tag,
                                     "--format", "{{json .RepoDigests}}", env=env))
            digest = next(d for d in digests if d.startswith(image + "@sha256:"))
            manifests.append("- `" + digest + "`")
    body = notes + "\n\nCommit: `" + sha + "`\n\n### Images\n\n" + "\n".join(manifests)
    request(api + "/" + str(release["id"]), token, "PATCH",
            {"body": body, "draft": False}, auth_scheme="token")


if __name__ == "__main__":
    action = sys.argv[1]
    if action not in ("check", "publish", "release-main"):
        raise ValueError("Expected check, publish or release-main")
    identity, changelog = validate(Path("."), os.environ.get("RELEASE_REF", ""))
    if action == "release-main":
        release_main(identity, changelog)
    if action == "publish":
        publish(identity, changelog)
    print("Release identity validated: " + identity)
