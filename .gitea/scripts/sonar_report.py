"""Publish this scanner task's gate results without changing the scanner exit status."""

import json
import os
from pathlib import Path
import sys
from urllib.parse import urlencode
from urllib.request import HTTPRedirectHandler, Request, build_opener


MARKER = "<!-- EDOL_SONAR_REPORT -->"


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def request(url, token, method="GET", payload=None, auth_scheme="Bearer"):
    data = json.dumps(payload).encode() if payload is not None else None
    req = Request(url, data=data, method=method, headers={
        "Authorization": auth_scheme + " " + token, "Content-Type": "application/json"})
    with build_opener(NoRedirect()).open(req, timeout=30) as response:
        return json.load(response)


def collect_gate(host, token, metadata):
    properties = {}
    for line in metadata.splitlines():
        key, separator, value = line.partition("=")
        if separator:
            properties[key] = value
    task_id = properties["ceTaskId"]
    if not task_id:
        raise ValueError("Missing scanner task identity")
    # Use the configured server, never a credential-bearing URL from report metadata.
    task = request(host + "/api/ce/task?" + urlencode({"id": task_id}), token)["task"]
    if task["status"] != "SUCCESS":
        raise ValueError("Scanner task has not completed successfully")
    analysis_id = task["analysisId"]
    if not analysis_id:
        raise ValueError("Missing analysis identity")
    return request(host + "/api/qualitygates/project_status?" + urlencode({"analysisId": analysis_id}), token)["projectStatus"]


def cell(value):
    return str(value).replace("|", "\\|").replace("\n", " ").replace("\r", " ")


def render_report(gate, public_url, revision):
    lines = [MARKER, "", "## Sonar Results", "", "Commit: `" + cell(revision) + "`", ""]
    if gate is None:
        lines += ["**Quality Gate unavailable.** Scanner failed, timed out or its report could not be retrieved."]
    else:
        lines += ["**Quality Gate: " + cell(gate["status"]) + "**", "",
                  "| Metric | Actual | Threshold | Operator | Status |", "| --- | --- | --- | --- | --- |"]
        for condition in gate.get("conditions", []):
            lines.append("| " + " | ".join(cell(condition.get(key, "-")) for key in
                         ("metricKey", "actualValue", "errorThreshold", "comparator", "status")) + " |")
    if public_url:
        query = urlencode({"id": "org.spon:edol"})
        lines += ["", f"[Dashboard]({public_url}/dashboard?{query}) · "
                  f"[Issues]({public_url}/project/issues?{query}&inNewCodePeriod=true) · "
                  f"[Security hotspots]({public_url}/security_hotspots?{query}&inNewCodePeriod=true)"]
    return "\n".join(lines) + "\n"


def publish_comment(api, repository, number, token, body):
    base = f"{api}/repos/{repository}/issues"
    page = 1
    while True:
        comments = request(f"{base}/{number}/comments?limit=100&page={page}", token, auth_scheme="token")
        for comment in comments:
            if MARKER in (comment.get("body") or ""):
                request(f"{base}/comments/{comment['id']}", token, "PATCH", {"body": body}, auth_scheme="token")
                return
        if len(comments) < 100:
            break
        page += 1
    request(f"{base}/{number}/comments", token, "POST", {"body": body}, auth_scheme="token")


def main():
    host = os.environ["SONAR_HOST_URL"].rstrip("/")
    gate = None
    try:
        gate = collect_gate(host, os.environ["SONAR_TOKEN"], Path("target/sonar/report-task.txt").read_text())
    except (OSError, ValueError, KeyError):
        print("Sonar gate details unavailable; preserving scanner failure and publishing an explicit unavailable result.")
    public_url = (os.environ.get("SONAR_PUBLIC_URL") or host).rstrip("/")
    body = render_report(gate, public_url, os.environ.get("GITHUB_SHA", "unknown"))
    Path("sonar-results.md").write_text(body, encoding="utf-8")
    with Path(os.environ["GITHUB_STEP_SUMMARY"]).open("a", encoding="utf-8") as stream:
        stream.write(body)
    if os.environ.get("GITHUB_EVENT_NAME") == "pull_request":
        token = os.environ["GITEA_TOKEN"]
        if not token:
            raise ValueError("Missing PR report token")
        event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text())
        number = int(event["number"])
        publish_comment(os.environ["GITHUB_API_URL"].rstrip("/"), os.environ["GITHUB_REPOSITORY"], number, token, body)


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, KeyError) as error:
        # HTTP bodies, tokens and exception text must not reach Actions logs.
        print(f"Sonar report publication failed: {type(error).__name__}", file=sys.stderr)
        sys.exit(1)
