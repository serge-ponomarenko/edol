# AI-Assisted Development

`AGENTS.md` contains persistent repository rules. Project-local Skills in `.agents/skills` provide EDOL-specific workflows. This document records planned integrations; it is not an active instruction source.

## Current Skills

- `edol-change` for non-trivial EDOL changes and documentation-impact review.
- `edol-runtime-contract` for Core runtime, MQTT, or cross-service printer-state contracts.
- `edol-schema-change` for JPA and Flyway changes.
- `edol-commit-preparation` for reviewing a change and preparing a repository-style commit message without committing it.
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
- Design and implement `edol-local-runtime-verification` for local development:
  start the documented runtime, wait for health endpoints, execute read-only API
  checks, capture redacted diagnostics and optionally navigate the Hub UI through
  browser automation.
- Start with a project Skill and existing CLI/browser capabilities. Add a local
  runtime MCP server only if repeatable structured runtime inspection cannot be
  served safely by that workflow.
- The mechanism must allow local targets only. It must not use production or
  shared credentials, DDL/DML, MQTT publishing, physical printer commands or
  unredacted secret values.

## Subagent Status

No persistent subagent configuration is configured. Delegate only when independence adds value:

- **Architecture scout:** read-only tracing of cross-module contracts before a risky change; does not edit.
- **Implementer:** owns one bounded change; does not overwrite concurrent work.
- **Verifier/reviewer:** independently inspects the diff and relevant contracts or runs checks; does not expand scope.
- **Documentation reviewer:** checks documented architectural facts against code only after an architectural change.
