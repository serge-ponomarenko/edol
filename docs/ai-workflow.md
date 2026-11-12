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

- Improve spool-status discoverability in Hub and the AMS terminal. On
  2026-11-11 the operator created spools but saw every populated tray as `NEW`;
  changing the matching spools from SEALED to ACTIVE made them appear. Explain
  on spool creation/edit that AMS automatic identification requires an ACTIVE
  spool with the matching printer profile and color in the current tenant.
  Replace or clarify `NEW` so users can distinguish an unidentified inventory
  spool from a physically empty tray or a newly purchased spool. Provide a
  clear, deliberate activation action and explain its inventory consequences.
  Preserve SEALED as a valid stock state, tenant isolation and existing matching
  semantics; do not auto-activate stock, silently choose among duplicate matches,
  or send printer metadata commands as part of this UX improvement.

- **NFC functional topic CLOSED - 2026-11-11 operator acceptance.** The
  uploaded terminal fixes preserve enrollment, reject ID-zero/empty/204 staging,
  avoid HTTP 204 body waits, keep HTTP/TLS off the UI loop and show immediate
  lookup feedback while coalescing in-flight same-tag repeats. Final verification
  passed 50 staging scenarios plus HTTP worker/TLS fixtures and ESP32 image audit.
  The operator confirmed read/write/erase, owned-spool EXT metadata observed in
  EDOL and slicer with previous state restored, immediate feedback, repeated
  presentations and a different tag working. Do not reopen or run more NFC
  acceptance now; address a new reproducible regression if one appears.
  Evidence: docs/deployment/stage9-ams-mapping-alignment-2026-11-08.md.
  Formal cross-tenant/physical TLS negatives, exact-field independent restoration,
  full lifecycle and long idle/transient heap observations are not promoted to
  PASS by this functional closure; retain them in the final Stage 9 security gate.

- The final NFC capture includes one state transport timeout (-11), followed by
  recovery and successful finds. Cause is unclassified. Record it for recurrence;
  do not launch another terminal investigation without a new reproducible issue.

- **Next active scope: Notify.** Inspect current retained-container status and
  redacted startup evidence before diagnosing historical exit 1. The operator
  has a dedicated test Telegram bot and is willing to run a test print. Establish
  the exact test chat/recipient and allowed message types/count before delivery;
  obtain credentials locally without printing or committing them. Diagnose/start
  only the scoped disposable Notify service after inspecting its actual image,
  labels, mounts and network. Preserve service identities, tenant rejection,
  subscriptions and existing infrastructure. No generic Compose up, authority
  broadening, production recipient, credential rotation or printer command is
  implicit. Use the 2026-11-11 handoff for the next chat.
  On 2026-11-12 read-only inspection classified retained exit 1 as missing
  recipient mappings, not an established Telegram API failure. The operator
  chose tenant-owned Hub Telegram settings managed by OWNER before any real
  print test. Source configuration, V10 encrypted storage and the exact Notify
  read API are described in
  [tenant settings and pending rollout](deployment/stage9-notify-tenant-settings-2026-11-12.md).
  Runtime V10, secret-key provisioning, narrow Notify Hub scope/audience and
  Hub/Notify image changes need a concrete reviewed retained-environment plan.
  Do not turn a nominal progress cadence into a hard message cap; agree an
  enforceable test-only bound including the DIRECT/AGENT mirror before enabling
  the dedicated bot. Retain the separate Stage 9 security/lifecycle gates.

- Reconcile secure Hub metadata when it joins an already running print after
  Core parsed the model. The 2026-11-08 recovery logs show one successful parse
  before Hub startup and a waiting DIRECT allocation display, while a later
  Agent parse created a preview. Secure progress handling restores jobs but
  does not build a missing metadata preview; Home-only recovery does not cover
  this case. Reproduce offline and design idempotent reconciliation from trusted
  Core state for the same tenant/printer/session, preserving terminal-job and
  rollback boundaries. Do not replay acknowledged events, force a model download,
  alter live jobs, or deploy during the active print. See
  `docs/deployment/stage9-baseline-recovery-2026-11-08.md` for evidence and limits.

- Complete Stage 9 AMS terminal acceptance using the final AMS section of
  `docs/deployment/stage9-hardware-remediation.md`. An IPv4 SAN verification
  mismatch was reproduced and corrected; COM16 reported the audited firmware
  identity, enrollment HTTP 200 and subsequent verified HTTPS state HTTP 200.
  Hub shows ACTIVE/CONSUMED and the operator's terminal displays AMS slots and
  sensor data. Physical negative controls and broader terminal lifecycle remain
  separate. Preserve existing
  CA/NVS/settings; no insecure mode, proxy change, code replay, arbitrary
  credential deletion or bootstrap.
- Identify AMS `processingFailures` using event/printer/tenant UUID, allowlisted
  type, tenant-mapping/handler stage, exception/cause class and upstream status.
  An unmapped printer or tenant mismatch fails after envelope validation and
  must remain rejected. Do not auto-add mappings or weaken isolation; payload,
  persistence and Hub upstream failures require actual categorized evidence.
  The 2026-10-27 long print produced ten `unmapped_printer` failures for the
  DIRECT logical printer; assess intended AMS ownership and duplicated events
  before changing mappings. This does not classify earlier failures and is
  independent of terminal HTTPS and the existing Hub processing failure.

- Stage 9 Agent dynamic-TLS retest: operator evidence on 2026-10-27 confirms
  repeated HTTPS snapshot uploads and one 37,672-byte Unicode model transfer,
  including FTP 226 and HTTP 200, using Agent `f558127` and a custom SDK. One
  camera handshake returned `-28928`; determine its cause and test recovery
  before claiming stable acceptance. The normal PlatformIO SDK does not enable
  this optimization. Preserve MQTT, certificate validation, identities, NVS,
  CA, volumes and restoration files. Measure transient memory and MQTT servicing;
  test larger models and negative TLS controls only with separate hardware
  approval. DIRECT/AGENT Pause/Resume, revoke/re-pair and AMS acceptance remain
  open. See the final dynamic-TLS section of the hardware remediation runbook.
- The main local Agent repository now has an explicit isolated dynamic-TLS smoke
  build using the retained hardware-tested SDK archive. Its archive hash, DIO
  settings, CA hash, linker flags and used ELF wrappers are checked; the stock
  environments remain separate. Rebuilding that SDK from source with fully
  pinned dependencies remains open. Do not commit SDK archives, certificates,
  credentials or firmware binaries. A normal PlatformIO upload does not use
  the experimental SDK, and any flash requires separate approval. The observed
  `-28928` handshake failure remains an independent stability gate.
- Investigate Hub `processingFailures=3`: event
  `5513f575-2a14-4254-8ca4-309cdc5a5c1a` was `print.metadata.loaded` for the
  Agent printer and failed with `IllegalArgumentException` during recovery.
  Earlier two categories are not established. The waiting filament display
  may be related but is not proven. Source and offline tests identify two
  distinct `IllegalArgumentException` candidates: an unknown or foreign Hub
  printer at the receipt boundary, and missing tenant material identification
  during allocation snapshot creation. The listener also counts and
  acknowledges an allocation `IllegalArgumentException` after recovery start.
  These controls do not establish which path occurred on the device. Design
  safe stage-specific diagnostics before a separately authorized runtime
  investigation; preserve tenant isolation, receipt transactions and ACK rules.
  An isolated PostgreSQL/JPA test reproduced a stale in-memory current job after
  receipt and new-job rollback. Hub now stages current-job, allocation-ready and
  allocation-preview updates until transaction commit; targeted tests cover
  rollback, success, an existing job and failure after readiness is set. These
  source and disposable tests do not identify the physical failure; obtain
  categorized runtime evidence only within separately authorized deployment.
  Separately investigate mutable `PrintJob` references already published in
  runtime state: finish, cancel and progress can modify the referenced object
  before commit, so staging map updates alone does not isolate those mutations
  from rollback or concurrent readers. Preserve per-printer and tenant scope.
- Investigate automatic tenant catalog creation for model metadata. In the
  2026-10-27 Hub `484b442` print, DIRECT and AGENT metadata events both failed
  with `IllegalArgumentException`; both dashboards stayed pending and final
  allocation reported no preview. The operator reports an empty catalog. Source
  and offline tests show that an unknown material type fails before vendor and
  filament creation; the physical exception branch is not yet confirmed. Assess
  tenant-scoped bootstrap of basic materials such as PLA, PETG and ABS, and
  whether other materials should be added automatically or with user approval;
  do not select a policy before review. Preserve existing matching semantics,
  avoid inventing spool inventory and never share tenant catalog data.
  Distinguish missing metadata from an unknown catalog entry and handle duplicate
  creation safely. Existing failed events are acknowledged; do not replay or
  alter historical jobs automatically.
- Investigate Notify startup exit code 1 independently; do not attribute it to
  the historical Telegram startup issue without redacted runtime evidence.
  The operator plans a later end-to-end test with a real, dedicated test
  Telegram bot. Do not expose bot credentials or send messages without the
  separately authorized test scope.
- Clear stale current-print dashboard artifacts after a print finishes: the
  model image and name, current job and completed progress can remain visible
  while the printer is Ready. Determine the appropriate lifecycle and display
  behavior for DIRECT and AGENT separately; preserve print history, stored
  records and media, tenant isolation and recovery of an actually active print.
- Separate dashboard legacy hardening: existing full-file Sonar warnings cover
  CDN integrity, accessibility and formatting in unchanged sections. Review
  those together; do not mix a dependency/style rollout into command routing.
  The snippet analyzer's empty-path Java package warning and top-level-await
  advice for classic browser scripts are analysis artifacts, not code fixes.

- **Historical October remediation chronology, superseded by the November
  evidence and current checkpoint above; do not replay its deployment steps.**
  Stage 9 hardware preparation failed on Hub navigation, service audiences and
  disabled Notify startup after the earlier source gate. Continue via
  `docs/deployment/stage9-hardware-remediation.md` and `tools/stage9/resume.py`,
  preserving the existing run and checkpoints. A future fresh-environment
  bootstrap should reuse these exact contracts, but must not recreate this
  operator's realm, DB, authorities or certificates. No redeploy/hardware
    operation or cleanup is implicit in source regression checks.
  The October 24 operator checkpoint confirms Agent enrollment/provisioning,
  IPv4 SAN TLS, DIRECT/AGENT telemetry and DIRECT live camera physical PASS.
  Current source fixes cover metadata retry tenant scope, DIRECT model RLS
  lookup, transport-specific snapshot commands, telemetry-derived printing,
  asynchronous Agent camera and pairing status polling. Agent DOWNLOAD_FAILED
  has no proven physical substage yet; bounded FTPS diagnostics and protocol
  corrections need an approved firmware retest. The complete print lifecycle,
  Agent model/camera acceptance and AMS hardware remain open. Existing
  CA/certificates and endpoints must stay unchanged. Do not
  bootstrap the run again, infer zero verifier flags from unavailable state,
  bypass TLS/RLS or reset existing identities to make acceptance pass.
  The subsequent `5aba847` hardware report confirms new pairing, automatic Hub
  status updates and real JPEG capture, but upload fails with NOT_TRUSTED (flag
  0x8). Source now corrects SDK repeated-stop descriptor ownership and timeout
  units; the exact trust-flag cause needs peer fingerprint/chain-depth evidence
  from the bounded lab diagnostics. Do not declare the upload physically fixed.
  Automatic re-enrollment after revoke is deferred: first define an explicit
  authenticated revocation confirmation protocol. MQTT disconnect, auth failure
  and generic HTTPS 401 must not erase credentials or open setup automatically.
  Preserve saved Wi-Fi/LAN/endpoints/NVS; current recovery uses explicit SETUP.
  Retest at `0252423` still fails camera trust for the same leaf accepted by
  the probe; Agent model data TLS now explicitly reports allocation failure.
  The exact vendor verifier can mask signer-verification allocation errors as
  leaf-only NOT_TRUSTED. Current firmware reserves heap by pausing printer MQTT
  during secure transfers, then reconnects after bulk cleanup. Hardware proof
  is pending; do not advance to revoke/re-pair until two real JPEG uploads and
  a changing dashboard image pass. DIRECT Core metadata now has operator PASS.
  Hub processingFailures=1 after metadata is separate unresolved evidence:
  obtain a safe exception category before claiming complete Hub metadata PASS;
  do not infer a tenant/security failure from the aggregate counter alone.

- Stage 9.3b source now implements the approved single-Mosquitto MQTT path;
  see `docs/deployment/stage9-agent-mqtt-contract.md`. Earlier MQTT-disabled
  notes below describe the HTTPS foundation. The operator-requested combined
  Agent/AMS real-printer session is prepared in
  `docs/deployment/stage9-disposable-hardware-runbook.md`; dated functional observations are recorded in the 2026-11-11 handoff;
  combined security/lifecycle acceptance remains incomplete. Keep that session, working dev/prod migrations, live broker cutover and operator-led
  credential rotation separate from source/disposable acceptance. No legacy
  compatibility removal is justified by synthetic traffic.

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
- Verify AGENT camera, telemetry and controlled commands in the combined physical
  session under `docs/deployment/stage9-agent-disposable-smoke-workbook.md`.
  Source/disposable secure MQTT now uses one Mosquitto with independent service
  and Agent identities, persisted Core routing and active-session revocation;
  see `docs/deployment/stage9-agent-mqtt-contract.md`. Physical observations,
  normal secure re-pair/recovery UX, live credential rotation, working broker
  cutover and dev/prod migrations remain separate gates. Keep feature flags off
  in working installations. Preserve Home/NVS and the existing open setup AP.
  Historical firmware work used `.worktrees/edol-agent-stage9` on
  `stage9-agent-pairing`, not the sanitized publication checkout's `main`.
  The original EDOL-AGENT checkout retains secret-bearing ancestors and must
  never be a publication/push source. Confirm the actual Agent USB port; do not
  assume the AMS terminal's COM16. Source tests do not authorize flash, serial,
  printer commands, migration, production changes or push.
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
