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

- **Stage 8 UX, high priority:** verify immediate authorization-loss feedback
  and Settings transition while the terminal is running. Distinguish 401/403
  from network failure; do not clear enrollment for generic connectivity errors.
- **Stage 8 UX, high priority:** preserve the printer Edit GET context across
  Hub reauthentication and explain interrupted lifecycle actions without replaying
  destructive POSTs. The disposable Stage 6 smoke profile sets a 60-second maximum
  session age; distinguish that setting from production session behavior.
- **Stage 8 UX, medium priority:** refresh pairing status automatically while
  visible, including success/expiry and regeneration. Stop polling after
  completion/navigation and never re-expose consumed pairing secrets.
- **Stage 8 UX, medium priority:** keep Replace and Refresh prominent, move
  Revoke into Advanced/Security, remove Rotate/Factory reset from primary UI
  while retaining backend capabilities, clarify server reset versus physical
  device erasure, and remove the primary Copy code button.
- **Stage 8 UX, lower priority:** distinguish unknown NFC spool, network failure,
  authentication loss and server error with actionable touchscreen feedback.

- **High priority — persistence identity convention:** before the next
  cross-module persistence expansion, assess a shared UUIDv7 identity strategy.
  Hub currently uses its local `PersistableEntity`, AMS Stage 8 generates the
  same UUIDv7 values explicitly, and Core has no equivalent superclass. Do not
  move the JPA superclass into `edol-core-api` by default: first decide whether
  that module may own Jakarta Persistence/Lombok dependencies, inventory every
  entity and explicit ID producer, and prepare a separately reviewed,
  compatibility-preserving migration with JPA and Testcontainers evidence.
  Preserve existing UUID values and do not mix generated-ID behavior with a
  schema or tenant-isolation change.
- Consider a separate Home-only terminal printer-selection UX if manual
  `printerId` entry proves impractical in the planned Home smoke test. It may
  exchange a short-lived local code for a printer ID, but is not terminal
  authentication: it must not reuse secure `/api/terminal/v1/enroll`, create
  terminal/pairing persistence, issue or store a secret, or imply protection
  beyond the owner-controlled trusted network. Keep it opt-in and mode-local;
  secure multi-tenant enrollment must remain unavailable in Home mode.
- Centralize application time handling through a shared `Clock` or time
  configuration, then migrate direct `now()` calls consistently. Preserve the
  current `Europe/Kyiv` business-time behavior until a persistence-time
  strategy is explicitly decided.
- Restore and test AGENT camera visibility as an explicit end-to-end contract.
  Stage 9.3b.1 now supplies opt-in Core/Hub pairing and HTTPS upload APIs; finish
  the coordinated EDOL-AGENT firmware and broker identity/ACL/lifecycle work
  under `docs/deployment/stage9-agent-pairing-contract.md`. Pairing alone is not
  secure MQTT acceptance. Keep both feature flags off until those gates pass;
  preserve Home and NVS, never flash/publish or migrate working DBs implicitly.
  The sanitized firmware baseline is private GitHub `serge-ponomarenko/EDOL-AGENT`
  `main`, commit `14da63f360d4a288c7207340dc60cd9e78ce7907`, checked out at
  `C:\Users\Ponomarenko\Documents\PlatformIO\Projects\EDOL-AGENT-publication-20261021`.
  That working tree was verified clean at handoff. The original
  `C:\Users\Ponomarenko\Documents\PlatformIO\Projects\EDOL-AGENT` retains
  secret-bearing local ancestors and must not be used as a push source.
  Publication/remote-SHA verification and credential-rotation needs were reported
  by the cleanup session; no live credential is rotated by Stage 9 source work.
  The backend session owns the coordinated secure implementation after this
  handoff. Its isolated firmware worktree is `.worktrees/edol-agent-stage9`,
  branch `stage9-agent-pairing`; it does not modify the published `main` checkout.
  The firmware HTTPS pairing/upload increment is now implemented there; MQTT
  authorization/lifecycle and physical acceptance remain pending. The owner
  explicitly keeps the existing open setup Wi-Fi AP unchanged for this stage;
  do not reintroduce an AP password/label requirement without a later request.
  The operator-authorized real Agent/printer disposable smoke is prepared in
  `docs/deployment/stage9-agent-disposable-smoke-workbook.md`, not executed.
  The opt-in lab USB console triggers production HTTPS uploads; full secure
  MQTT and normal secure re-pair UX remain separate open gates. Confirm the
  physical Agent port before upload; do not assume the AMS terminal's COM16.
  The external agent implementation is not in this repository. Its existing
  Home contract uploads JPEG snapshots to `POST /api/agent/camera/snapshot`
  with `X-Agent-Id`; Core
  resolves that ID to its printer and Hub reads the printer-scoped snapshot.
  The legacy connection form accepts manually entered `agentId`; the new
  secure identity is separate and is not yet provisioned onto firmware.
  Determine from agent
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
- Before introducing an operational retention policy or deletion job for Hub's
  `core_mqtt_event_receipts` idempotency ledger, approve the policy and
  validate it in a fresh disposable MQTT/PostgreSQL environment. Stage 6
  source/disposable acceptance does not authorize receipt deletion, a cleanup
  job, a live-broker change, production credentials, or removal of receipt
  history without a separate reviewed change.
- Diagnose the Hub-to-Core media proxy `403` responses observed during Stage 6
  disposable acceptance as a separate application-integration task. Establish
  whether the cause is service authorization, tenant propagation, or the
  intentionally disabled synthetic printer before changing proxy handling,
  credentials, or runtime configuration. Do not use production resources or
  broaden authorization as a diagnostic shortcut.
- Before upgrading to a Spring Boot release that removes Jackson 2 support,
  migrate Core and Hub `com.fasterxml.jackson.databind` usages and Stage 6 MQTT
  serialization to Jackson 3. Until then, retain the explicitly tested
  `spring-boot-jackson2` bridges; do not replace them with ad hoc mapper beans.
- Design a separate post-Stage-7 Telegram recipient enrollment feature before
  replacing deployment-managed recipient mappings: require an authenticated
  EDOL user, a short-lived single-use link, tenant-scoped persisted mappings,
  revocation, and replay/cross-tenant rejection. Do not treat static smoke
  mappings as end-user onboarding.
- Decide separately whether user-facing spool references need a tenant-scoped
  display identifier while retaining the current technical ID. The observed
  global sequence is not evidence of an RLS bypass.
## Subagent Status

Project-scoped Codex roles are configured under `.codex/agents`. They are
limited to commit execution, targeted verification, and read-only database
evidence. The active routing rules and full role contracts are in
`AGENTS.md` and `docs/codex-subagents.md`.
