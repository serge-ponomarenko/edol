---
name: edol-local-runtime-verification
description: Smoke-check one EDOL service in the isolated local dev environment with bounded readiness, read-only checks, and workflow-owned process cleanup.
---

# EDOL Local Runtime Verification

Use this skill to start exactly one EDOL module for a local runtime smoke check
in the repository's dedicated `dev` environment. Its local secret files and
`application-dev.yaml` configuration are the approved source for the separate
development MQTT broker and local PostgreSQL database. Follow `AGENTS.md` and
read `docs/ai-workflow.md` before using it.

Run the versioned wrapper from the repository root:

```powershell
.\.agents\skills\edol-local-runtime-verification\scripts\Start-EdolLocalSmoke.ps1 -Module Ams
```

For an explicitly requested Core telemetry observation, add
`-WaitForTelemetry`. The wrapper polls Core's read-only
`/api/printers/state` endpoint and succeeds only after an online printer has a
populated runtime telemetry field. It never prints the response payload.

The supported module names are `Core`, `Hub`, `Ams`, and `Notify`. The wrapper
always uses the `dev` profile, loads `.env_secret` first and the selected
module's secret file second, and passes parsed values only to the Maven child
process. It never dot-sources or executes `.env` content.

## Preconditions and stopping conditions

Do not bypass a wrapper failure. Stop and report the failed boundary when any
of the following occurs:

- the common or module-specific secret file is absent, malformed, or lacks a
  required variable;
- `application-dev.yaml` is absent or Core/Hub no longer point at the expected
  local development database endpoint;
- the selected web module's expected port is already listening;
- the process exits, readiness times out, the expected listener is absent, or
  a configured read-only check fails.

The `dev` environment is expected to be isolated from production, staging, and
shared services. Core and Hub run their normal dev startup behavior, including
the configured runtime, MQTT, and Flyway behavior. Stop rather than use this
skill if that environment no longer has the stated isolation.

## Module contract

- **Core:** Maven directory `edol-core`; port comes from `SERVER_PORT`; no
  verified actuator dependency. Required names: `SERVER_PORT`, `POSTGRES_DB`,
  `POSTGRES_USER`, `POSTGRES_PASSWORD`, `WEB_ADMIN_NAME`, and
  `WEB_ADMIN_PASSWORD`.
- **Hub:** Maven directory `edol-hub`; port `8090`; `GET /` is the
  parameter-free read-only check. Required names: `POSTGRES_DB`,
  `POSTGRES_USER`, `POSTGRES_PASSWORD`, and `QR_URL`.
- **Ams:** Maven directory `edol-ams`; port `8099`; no parameter-free safe
  endpoint; no required environment names.
- **Notify:** Maven directory `edol-notify`; no web listener
  (`web-application-type: none`). Required names: `TELEGRAM_BOT_TOKEN` and
  `TELEGRAM_ADMIN_ID`.

`GET /` is the only currently identified parameter-free Hub endpoint and is
read-only by mapping. Do not call state, printer, command, camera, spool, or
UI action endpoints: a `GET` route is not automatically safe.

## Safe execution and reporting

The wrapper reports variable presence only as `NAME=SET` or `NAME=MISSING`.
Never print environment contents, command lines with values, connection
strings, passwords, tokens, source logs, or captured diagnostics. Do not add
extra Maven goals, standalone Flyway or SQL commands, browser/UI mutations, or
authentication. The application may use the isolated dev integrations defined
by its selected profile.

Readiness is bounded by `-TimeoutSeconds` (default 90, maximum 300). A web
module requires an expected local listener and its configured read-only
readiness endpoint. Notify requires the Spring startup marker only. If a safe
HTTP endpoint is added later, add it to the wrapper's module descriptor before
using it.

`-WaitForTelemetry` is valid only with `-Module Core`; its separate timeout is
`-TelemetryTimeoutSeconds` (default 120, maximum 300). A timeout is an
application runtime failure, distinct from an infrastructure/configuration or
startup failure.

The wrapper creates one controlled temporary directory for application output
and removes only that directory. Its `finally` block terminates the Maven
process tree and the service process that bound its previously free port, then
confirms that its expected port is free. It never stops a process discovered
during preflight. If it cannot confirm cleanup, report that fact without
stopping anything else.

Before editing this skill or its script, run its no-secret static check:

```powershell
.\.agents\skills\edol-local-runtime-verification\scripts\Start-EdolLocalSmoke.ps1 -StaticCheck
```
