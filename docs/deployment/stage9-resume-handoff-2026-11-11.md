# Stage 9 handoff - 2026-11-11: NFC closed, Notify next

## Current outcome

The operator accepted the exercised NFC functional scenarios and explicitly
closed the topic. Do not repeat NFC tests, idle recordings or firmware uploads
unless a new reproducible regression appears. Enrollment/NVS/CA were retained.
The terminal now validates lookup payloads, skips body reads for HTTP 204, runs
transport in one worker, shows immediate pending feedback and coalesces same-ID
scans during lookup while preserving a different pending tag.

Terminal main commit: `276a7820591614ce3115356de1efb3a03ff63cf3`,
`EDOL-048-80: [Terminal] Keep NFC responsive during secure spool lookup`.
Backend EDOL-048: use the commit containing this handoff; parent was `72a050b`.
Recheck Git status/HEAD in both repositories before new work. No push requested.
The main Agent repository was not changed; never publish its private history.

Fresh offline verification: 50 staging scenarios plus HTTP worker, CA-present/
absent and 204 controls passed; normal ESP32 secure build/image audit passed.
Source-only sec06 build passed earlier; this is not a new physical TLS-negative
acceptance. Python runner Sonar had zero findings; C++/PowerShell are unsupported
by the connected snippet analyzer and were checked with compilers/tests/AST.
The backend mapped-mirror test passed in its earlier 13-test targeted run; its
empty-path Java package Sonar finding is an analyzer artifact, not suppressed.
No new full-reactor test was needed for documentation closure.

Final image: `.pio/nfc-feedback-validation-20261111-01/esp32dev` in the main
terminal repository. BIN SHA-256
`df82d5af960b0ceeb5ff216375fe2e29e4bdaa15fd254a39708788da989b3a99`;
ELF SHA-256
`c42230df99d97011010509661252455ba79a211459bf3bf217ec6bce36208ca0`.
Older images and hash-pinned helpers are retained for restoration, not instructions
to flash again. Private evidence, SDK archives, configuration, keys and binaries
remain outside commits.

Final evidence: `terminal-idle-20261111-204054/logs/device-monitor-261111-204054.log`
under `C:\Users\Ponomarenko\AppData\Local\EDOL\stage9-evidence`, SHA-256
`8efa31809d78f38e879b607322bbde34a59faebdc2201924cd1f12544b7f6e17`.
Seven successful finds, operator-confirmed UI/repeat/different-tag behavior.
One state transport -11 recovered; cause unclassified, no investigation now.
The short mixed capture is not a five-minute idle heap gate or boot identity.
See [detailed evidence and closure](stage9-ams-mapping-alignment-2026-11-08.md).

## Retained runtime and authorization

The last bounded restart on November 11 used retained containers, preserving
images, volumes, identities and overrides. Seven test services ran; Notify stayed
stopped on `edol-stage9-notify:04ed4cc` (container prefix `bc8ef663d02b`). This is
historical runtime evidence; this closure did not inspect/start server services.

Server: SSH profile `edol-server`, serge at `192.168.0.200`, retained directory
`/home/serge/edol-stage9-hardware`. The previous assistant had SSH/Docker access
through the user's existing SSH profile/key, without passwordless sudo. Discover
current capabilities; do not promise access before checking. For unavailable or
operator-only actions, prepare reviewed commands/scripts and inspect returned
redacted evidence. Do not leave SSH/tunnel/async processes running.

Core `d52fdd0e`, Hub `484b442`, AMS `72a050b` were retained. AMS maps DIRECT and
AGENT to the same tenant but terminal binding/staging remains DIRECT. The two
logical printers observe the same P1S. The mapped-mirror test preserves tenant
rejection and demonstrates no mirror consumption of DIRECT staging.

Proxy `.7` on `10.253.90.0/24` must match Core's temporary trusted peer; AMS `.6`.
Inspect actual container image IDs, Compose labels/overrides, mounts, network
addresses and routing after downtime. Prefer retained-container start. No generic
Compose up, bootstrap/resume mutation, identity reset or authority expansion.
Previous HTTPS renewal retained CA/key and expires November 15 06:58:07 UTC;
CA expires November 21, MQTT November 20. Recheck clock/validity safely before
operation, without printing cert/key/config contents. Trust/config changes need
a concrete reviewed scope. Device HTTPS uses port 18443, Hub 18082, Core 18081.

## Next scope: Notify and a dedicated test Telegram bot

The operator has a dedicated test bot and is willing to perform a test print.
Start with read-only source/configuration and current retained-container evidence.
Classify current startup/exit 1 independently; historical Telegram failures are
not a diagnosis. Keep credentials local and do not print bot-token-bearing URLs,
Authorization, complete environment/configuration, exception messages or raw
Telegram responses. Do not request the bot token in chat or commit it.

Before sending, establish the exact test recipient/chat and bounded message
types/count, including whether a startup/probe message is permitted. Inspect
the actual tenant-to-recipient resolver, subscriptions, event versions and print
notification cadence first. Preserve cross-tenant rejection and test-only routing.
Do not enable a production recipient or broaden bot/realm/broker authority.

Prepare the minimum scoped start/configuration plan using existing identities.
If runtime changes are needed, produce a concrete reviewable artifact before
approval; do not rotate credentials, change schema or recreate unrelated services
to fix a startup failure. Tell the operator exactly when to start the print only
after readiness and delivery scope are established. The operator starts the print;
the assistant does not issue printer commands implicitly. Capture bounded safe
start/progress/finish evidence and distinguish delivery from ingestion.

Functional print/camera/model and NFC successes do not complete Stage 9. Remaining
gates include Agent -28928 reproduction/observability, continuous dual-MQTT and
transient heap evidence, controlled commands/recovery, physical TLS negatives,
active identity lifecycle, final legacy-use window/version policy, private_key_jwt
decision, DB TLS, secret storage/rotation, proxy/port isolation and rollback/restore.
Consult the migration plan's final lockdown scope. Legacy compatibility remains;
dev/prod migration and broker cutover need separate review. Catalog policy, spool
status discoverability and stale completed-print dashboard UX stay in Project TODO.

## Copy into the next chat

```text
Continue EDOL Stage 9 from docs/deployment/stage9-resume-handoff-2026-11-11.md.
Reply in Ukrainian; code, docs, comments and commits in English.

Work in C:\Users\Ponomarenko\IdeaProjects\edol on EDOL-048. Read AGENTS.md,
applicable skills, current Git status/HEAD, this handoff, Project TODO, the
reconciled combined runbook and final-lockdown migration-plan scope first.
Use main firmware repositories and preserve private history, local config,
evidence, archives, restoration images, old worktrees, CA/NVS and identities.

NFC functional work is CLOSED by the operator. Terminal main commit is
276a7820591614ce3115356de1efb3a03ff63cf3. All exercised NFC scenarios work;
do not request another NFC/idle/flash cycle without a new reproducible issue.
Formal security/lifecycle/memory gates remain separate from functional closure.

Next: diagnose retained Notify safely and prepare a real dedicated test-bot
scenario with an operator-started print. The operator has a test Telegram bot.
First inspect source and current redacted retained-container evidence. Do not
assume the historical exit 1 cause. Inspect Compose labels/images/networks and
trusted proxy addressing; prefer retained-container startup over recreation.
No generic Compose up/bootstrap, production change, authority/schema change,
credential rotation, printer command or push is authorized implicitly.

Obtain safe bot credentials locally, never in chat/output/Git. Establish exact
test recipient/chat and allowed message types/count before any delivery. Trace
tenant routing/subscriptions/version compatibility and notification cadence.
Prepare a concrete scoped configuration/start plan, complete safe autonomous
checks, and provide commands for operator-only actions. Tell the operator when
to start the print only after readiness and delivery scope are established.
Keep short updates and clean up owned SSH/async sessions. Do not mark Stage 9
complete from a functional Notify print; retain the remaining lockdown gates.
```
