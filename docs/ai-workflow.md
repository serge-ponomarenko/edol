# AI-Assisted Development

`AGENTS.md` contains persistent repository rules. Project-local Skills in `.agents/skills` provide EDOL-specific workflows. This document records planned integrations; it is not an active instruction source.

## Current Skills

- `edol-change` for non-trivial EDOL changes and documentation-impact review.
- `edol-runtime-contract` for Core runtime, MQTT, or cross-service printer-state contracts.
- `edol-schema-change` for JPA and Flyway changes.
- `edol-commit-preparation` for reviewing a change and preparing a repository-style commit message without committing it.
- `edol-local-runtime-verification` for starting one EDOL module in the isolated local `dev` environment and checking bounded runtime readiness.
- `edol-remote-docker-verification` for Docker/Testcontainers verification from Windows through the temporary remote Docker tunnel.

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

## Project TODO

- Centralize application time handling through a shared `Clock` or time
  configuration, then migrate direct `now()` calls consistently. Preserve the
  current `Europe/Kyiv` business-time behavior until a persistence-time
  strategy is explicitly decided.
- Restore and test AGENT camera visibility as an explicit end-to-end contract.
  The external agent implementation is not in this repository. It must upload
  JPEG snapshots to `POST /api/agent/camera/snapshot` with `X-Agent-Id`; Core
  resolves that ID to its printer and Hub reads the printer-scoped snapshot.
  Hub currently accepts `agentId` as manually entered connection data and does
  not generate, provision, or rotate it on the device. Determine from agent
  HTTP and MQTT evidence whether its configured ID matches Core, whether it
  receives `ENABLE_SNAPSHOT_SCHEDULER`, and whether it uses a removed legacy
  camera-read route. Do not publish commands or change a live device merely to
  diagnose this; establish contract coverage before changing either side.
- Perform and record an isolated restore drill of the 2026-09-28 Stage 3
  production backup under the backup-recovery procedure. The backup was created,
  checksummed, and parsed successfully, but no full restore was performed. This
  is an operational recovery exercise following Stage 3 acceptance, not
  authorization to alter the production database or reopen accepted Flyway
  history.
- Before accepting Stage 6, approve an operational retention policy for Hub's
  `core_mqtt_event_receipts` idempotency ledger and validate it in a fresh
  disposable MQTT/PostgreSQL environment. Do not add a cleanup job, alter a
  live broker, use production credentials, or remove receipt history without a
  separate reviewed change.
## Subagent Status

Project-scoped Codex roles are configured under `.codex/agents`. They are
limited to commit execution, targeted verification, and read-only database
evidence. The active routing rules and full role contracts are in
`AGENTS.md` and `docs/codex-subagents.md`.
