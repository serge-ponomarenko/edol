# AI-Assisted Development

`AGENTS.md` contains persistent repository rules. Project-local Skills in `.agents/skills` provide EDOL-specific workflows. This document indexes current tooling and MCP configuration; the live work backlog and handoffs are maintained through Home MCP Obsidian.

## Current Skills

- `edol-change` for non-trivial EDOL changes and documentation-impact review.
- `edol-runtime-contract` for Core runtime, MQTT, or cross-service printer-state contracts.
- `edol-schema-change` for JPA and Flyway changes.
- `edol-commit-preparation` for reviewing a change and preparing a repository-style commit message without committing it.
- `edol-local-runtime-verification` for starting one EDOL module in the isolated local `dev` environment and checking bounded runtime readiness.
- `edol-remote-docker-verification` for Docker/Testcontainers verification from Windows through the temporary remote Docker tunnel.
- `edol-knowledge-management` for scoped EDOL Obsidian context, backlog, evidence, handoffs, and documentation ownership.

## MCP Status

The local PostgreSQL MCP server is configured in `.ai/mcp/mcp.json` as
`edol-postgres`. It starts `@microsoft/postgres-mcp` through `npx` with
telemetry disabled and uses connection profiles stored outside the repository
in `%USERPROFILE%\.postgres-mcp\connections.yaml`.

- Use only a local development profile. Discover profiles with
  `postgres_mcp_list_connection_profiles` before connecting, and require the
  profile's `access_mode: ro`. Do not configure shared, staging, or production
  database credentials.
- Connection profiles and passwords are user-local and must never be committed.

The read-only SonarQube MCP server is registered for JetBrains AI Assistant in
`.ai/mcp/mcp.json` and for Codex in `.codex/config.toml`. Both registrations
start `%USERPROFILE%\.edol\sonarqube\start-sonarqube-mcp.ps1`, which reads
the untracked `%USERPROFILE%\.edol\sonarqube\edol.env` file. That file must
contain `SONARQUBE_URL`, `SONARQUBE_TOKEN`, and `SONARQUBE_PROJECT_KEY`; it
must never be copied into the repository or a Codex configuration file.

- Create a SonarQube user token in **User > My Account > Security** and save it
  only to the user-local environment file. SonarQube shows the token once.
- The launcher uses the official SonarQube MCP JAR in
  `%USERPROFILE%\.codex\mcp`, creates its storage under
  `%USERPROFILE%\.edol\sonarqube\storage`, and forces read-only mode.
- Restart the relevant Codex or JetBrains session after changing its MCP
  configuration or the user-local environment file.

- Prefer Git CLI for local history and diffs. The repository remote is Gitea, so a GitHub integration is not a default fit.
- Consider a read-only Gitea integration only when issue or pull-request context is regularly required.
- Consider read-only, tenant-scoped PostgreSQL or runtime diagnostics only after a concrete recurring diagnostic need. Such tooling must not expose DDL/DML, MQTT publishing, printer commands, or production deployment actions.
- Keep versioned project documentation as the primary engineering context unless an external documentation system is explicitly adopted as authoritative.

## Project Knowledge

The EDOL work backlog, temporary plans, operator evidence and session handoffs live in the dedicated ChatGPT Obsidian vault, project `edol`, through the authenticated Home MCP integration. Use the project-specific `edol-knowledge-management` Skill; a local repository checkout does **not** imply access to the private vault.

Current EDOL-048 Obsidian entry points:

- `projects/edol/work/EDOL-048/context.md` — current Stage 9 engineering status.
- `projects/edol/work/EDOL-048/backlog.md` — open/deferred work, and closed/superseded items.
- `projects/edol/work/EDOL-048/test-stand-closure.md` — bounded security/lifecycle closure and deferrals.
- `projects/edol/work/EDOL-048/tls-negatives.md` — physical TLS evidence and remaining verification.
- `projects/edol/archive/`, `projects/edol/sessions/` and `projects/edol/work/EDOL-048/source/` — historical evidence, handoffs and exact 2026-11-12 local WIP source snapshots.

Authoritative code/API/MQTT/tenant contracts, supported runnable verification instructions and security constraints remain in Git. Never interpret Obsidian plans as permission for deployment or a physical test. If Home MCP is unavailable, report that persistence did not occur and provide a redacted in-conversation handoff rather than recreating a repository work log.

## Subagent Status

Project-scoped Codex roles are configured under `.codex/agents`. They are
limited to commit execution, targeted verification, and read-only database
evidence. The active routing rules and full role contracts are in
`AGENTS.md` and `docs/codex-subagents.md`.
