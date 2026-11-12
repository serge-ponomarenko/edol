# Stage 9 Notify tenant settings - 2026-11-12

## Scope and authorization

The operator chose tenant-owned Telegram configuration, managed by an active
OWNER in Hub, before the dedicated test-bot print. This change implements source
and isolated checks only. No retained container, identity, volume, per-service
override, CA/NVS, Keycloak authority or server schema was changed. No Telegram
message, printer command, firmware upload, commit or push is part of this work.
NFC functional acceptance remains closed.

## Retained Notify diagnosis

Read-only inspection through the existing `edol-server` SSH profile found:

- Container `bc8ef663d02b`, `/edol-stage9-hardware-edolnotify-1`, exited `1`.
- Image `edol-stage9-notify:04ed4cc`, image ID
  `sha256:6ddc8f538584791ca40a5531d86a95a01e6b594221dda31e71c85d9e1f6d38a3`.
- Last attempt: `2026-10-27T11:59:50.365522852Z` to
  `2026-10-27T11:59:56.660564282Z`; no new startup was attempted.
- Compose project/service: `edol-stage9-hardware` / `edolnotify`, config
  `/home/serge/edol-stage9-hardware/compose.backend.yaml`.
- Retained bind mounts: `certs/stage9-mqtt-truststore.p12` read-only at
  `/app/certs/stage9-mqtt-truststore.p12`; `logs/notify` read/write at `/app/logs`.
- Network `edol-stage9-hardware_stage9`; exited-container IP was empty.

The bounded startup-log classifier found `BeanCreationException`,
`UnsatisfiedDependencyException` and `IllegalStateException`, with the exact
known validation marker `secure-multi-tenant Notify requires recipient mappings`.
It found neither the missing-token validation marker nor the issuer-resolution
marker. This classifies that retained startup failure as missing recipient
configuration; it does not prove Telegram connectivity or delivery. Raw logs,
environment values, token-bearing URLs and Telegram responses were not printed.

The Telebof `System.exit(1)` finding is a separate source/lifecycle issue, not
the cause assigned to this retained startup.

## Implemented configuration contract

- Hub OWNER page: `/settings/telegram`, linked under Tenant. Saves use CSRF.
  Token input is password-only and is never pre-filled or returned on failure.
  Saving sends no probe/startup message. Enable deliberately authorizes event
  delivery and commands in that tenant's chosen chat.
- Token, chat and enable are tenant-owned. Empty token input retains it;
  replacement is explicit; removal requires a disabled configuration.
- Hub V10 stores authenticated encrypted tokens with forced tenant RLS and
  prevents sharing a token across tenants. A separate UUID-only directory is
  intentionally readable before opening an individual tenant scope.
- `EDOL_HUB_TELEGRAM_ENCRYPTION_KEY`: one locally generated, base64-encoded
  32-byte installation key. Keep it out of chat, logs, Git and database dumps.
  Preserve it alongside backups; do not auto-regenerate it on restart or rotate
  it without a reviewed re-encryption/re-entry procedure.
- Notify secure profile: `edol-notify.telegram.settings-source=hub`,
  `EDOL_HUB_URL` selects Hub, and `TELEGRAM_ENABLE=false` remains a master switch.
  Global `TELEGRAM_BOT_TOKEN`, `TELEGRAM_ADMIN_ID` and static recipient mappings
  do not route Hub-managed bots. Home retains its old environment configuration.
- Existing Notify OAuth identity is retained. The new exact internal GET routes
  need scope `hub.notify.read` in addition to `tenant.context` and a token valid
  for the Hub audience. The expected `azp` is controlled by
  `EDOL_NOTIFY_SERVICE_CLIENT_ID`, default `edol-notify-service`.
- Notify refreshes every 15 seconds by default and owns separate bot/chat
  sessions. Settings/directory failures suspend affected bots without fallback.
  No polling thread can terminate the Notify JVM through Telebof's lifecycle.
  In-flight requests and refresh timing are distinct from immediate revocation.

## Source verification

Java 21 clean compilation of Hub, Notify and their shared dependency passed.
The full Hub test reactor passed through the existing temporary remote-Docker
wrapper, with zero failures/errors/skips in its final Maven console summary.
The shared API summary was 11 tests and the Hub summary was 299. The current
74 Hub XML reports aggregate to 153 tests with zero failures/errors/skips;
that report-count discrepancy is recorded without assigning an unverified
cause. In particular, the independently readable reports confirm
`HubMigrationTest` 25/25, `Stage5HubFlywayPrivilegeTest` 1/1,
`CoreMqttEventReceiptServicePostgresTest` 3/3, new
`TelegramSettingsPersistenceTest` 3/3, `TelegramTokenCipherTest` 2/2 and
`SecureHubWebSecurityTest` 10/10. The disposable database exercised an existing
V9 tenant domain upgraded to V10, non-bypass RLS reads/writes, encrypted token
retention/replacement, disable/removal, and duplicate-bot rejection.

The full Notify suite passed 24 tests with no failures/errors/skips. The final
three-test startup harness also passed after it was strengthened to load the
actual secure YAML. Empty Hub settings need neither a global token nor static
recipient mappings. Registry tests use mocked Telegram contexts/schedulers;
they exercise bot/chat isolation, replacement, suspension, master disable and
discarding historical commands without contacting Telegram. No circular
dependency was observed in the startup harness. The Docker tunnel was cleaned
up after verification.

Commands (source tests, not deployment):

```powershell
$env:JAVA_HOME = 'C:/Java/jdk-21'
$env:PATH = "$env:JAVA_HOME/bin;$env:PATH"
& .\.agents\skills\edol-remote-docker-verification\scripts\with-remote-docker.ps1 `
    mvn -B -pl edol-hub -am test
mvn -B -pl edol-notify -am test
```

Read-only Sonar snippet analysis covered all 19 modified/new supported Java
and HTML files. Actionable complexity/accessibility findings were corrected.
The remaining Java `S1598` empty-file-path/package messages are snippet-tool
artifacts: the actual package paths are correct and compile. Nothing was
suppressed. SQL was checked by Flyway/PostgreSQL; YAML was loaded by the startup
harness because these languages are not supported by this snippet analyzer.
`git diff --check` passed. This evidence does not validate real polling/delivery,
deployment transport or production/physical Stage 9 gates.

## Pending retained rollout and real print test

Before applying anything, prepare and review a Hub/Notify-only retained-runtime
artifact with immutable image IDs, V10 privilege/migration checks and rollback
boundaries. Inspect each service's actual Compose labels, overrides, mounts,
networks, current clock/certificate validity and the Core trusted proxy address.
Preserve the old images/configuration. Never use generic Compose up, bootstrap,
identity reset, authority expansion to other services or migration rollback by
dropping a table. Schema changes and the narrow Notify Hub scope/audience need
their own explicit retained-environment approval; production remains excluded.

Configure the installation encryption key locally. Initially keep tenant
notifications disabled. Enter the dedicated test-bot token and exact chat ID
locally in the Hub form; never paste the token into a conversation or command
argument. Do not enable a real recipient before agreeing delivery scope.

Before the operator starts a print, confirm the exact tenant, recipient/chat,
logical printer(s), event types, media allowance and message maximum. Both DIRECT
and AGENT observe the same physical P1S, so one physical print may produce two
logical notification streams. Current Notify also emits metadata/running/pause,
online/offline, timelapse and progress notifications; a nominal six-message
start/20/40/60/80/finish sequence is not a hard message cap. Arrange a concrete
test-only bound before enabling delivery; do not silently authorize all events
or enable printer-control tests. Startup/probe delivery requires explicit
agreement even though normal activation sends no startup message.

Only announce readiness and tell the operator to start printing after source,
migration, authenticated configuration refresh, bot readiness and the agreed
delivery boundary have been verified. Distinguish validated MQTT ingestion,
Telegram API acknowledgement and operator-observed delivery. A functional test
does not close Stage 9 security, lifecycle, TLS, rotation or memory gates.
