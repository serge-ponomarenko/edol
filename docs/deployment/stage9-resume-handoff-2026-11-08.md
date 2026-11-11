# Stage 9 audit and next-chat prompt — 2026-11-08

This document records a read-only local audit and a bounded continuation prompt.
No service was started, deployment performed, firmware flashed, database queried
or modified, or printer command sent during this audit. Remote runtime was not
inspected. Container state below comes from the operator and supplied screenshot.
It is not a diagnosis of the containers' exit codes.

## Verified local state

| Repository | Branch and HEAD | Worktree |
| --- | --- | --- |
| `C:\Users\Ponomarenko\IdeaProjects\edol` | `EDOL-048`, `72a050b366e6e5fa1c225a8b7730aa455c621302` | Two existing modified documentation files; untracked logs and SDK archive |
| `C:\Users\Ponomarenko\Documents\PlatformIO\Projects\EDOL-AGENT` | `master`, `08c10518cef902a402bbd775e0a7a7ab10047deb` | Clean |
| `C:\Users\Ponomarenko\Documents\PlatformIO\Projects\edol-ams-terminal` | `main`, `6d98d6b2dd84000d84d8cec3f7e6871cf42bdb4d` | Clean |

Backend HEAD is two commits ahead of the locally cached `origin/EDOL-048`
(`484b4420ecee734c91dcbcbec330b2955665aa63`). No fetch was performed;
this does not establish the current remote branch position.

Relevant backend commits, in order:

- `d52fdd0` / EDOL-048-73: bounded model retries and dashboard commands.
- `16656bd` / EDOL-048-74: categorized AMS MQTT and terminal HTTPS diagnostics.
- `e517f18` / EDOL-048-75: dynamic-TLS hardware retest record.
- `484b442` / EDOL-048-76: publish Hub runtime map changes after transaction commit.
- `9012e86` / EDOL-048-77: long-context chat handoff guidance.
- `72a050b` / EDOL-048-79: terminal retest documentation and successful MQTT
  validation logging at DEBUG in AMS and Hub.

Existing uncommitted changes are in `docs/ai-workflow.md` and
`docs/deployment/stage9-hardware-remediation.md`: AMS deployment and long-print
evidence, including the DIRECT mapping issue. Preserve them. Also preserve
`Print_27102026/`, `edol-esp32-dynamic-sdk.tar.gz`, `edol-hub/logs/`, ignored
configuration, firmware restoration artifacts and the old worktrees.
This handoff file is a new audit artifact; no commit or push was requested.

Use the main firmware repositories for further local work. The main Agent
repository retains old private history and must not be connected or pushed to
the sanitized publication repository without explicit review.

## Acceptance assessment

Stage 9.3b secure Agent transport is implemented and has partial physical
acceptance. Combined Agent/AMS hardware acceptance and final Stage 9 lockdown
are incomplete. Functional success, offline checks and security acceptance are
separate evidence classes.

The local `Print_27102026` logs were recounted during this audit:

- Approximately 40 minutes 48 seconds, with separate DIRECT and AGENT sessions
  and both print-finished records.
- Agent: 106 camera captures; 107 HTTP successes including one verified
  610,729-byte model transfer with exact FTPS size and final reply 226.
- One earlier Agent FTPS reset (-80) recovered on retry. DIRECT also retried
  after curl exit 35. Neither establishes the underlying failure cause.
- No -28928 appears in these Agent logs. The earlier dynamic-RX bad-input
  anomaly remains unexplained; it is distinct from reset and allocation errors.
- Hub's observed 18Z processing-failure counter remains 2. Both allocation
  previews were created; unresolved filament allocation prevented finalization.
- AMS: ten categorized `tenant_mapping` / `unmapped_printer` failures for DIRECT.
  This is not evidence of invalid envelopes, terminal TLS failure, or permission
  to add a mapping. DIRECT and AGENT represent the same physical P1S.
- Terminal serial was not saved. The logs do not establish continuous terminal
  state requests, uninterrupted servicing of both Agent MQTT clients, transient
  handshake memory minima, leak freedom or physical negative TLS controls.

Earlier terminal evidence establishes successful COM16 firmware identification,
verified HTTPS enrollment/state and ACTIVE/CONSUMED in Hub after the strict
IPv4 SAN fix. It does not cover the full terminal lifecycle or AMS writes.

Source checks confirm that AMS validates printer-to-tenant mappings before
dispatching an event; unknown printers remain rejected. Hub stages runtime map
updates until commit, but published mutable PrintJob objects remain a separate
rollback/concurrency concern. Filament creation still requires a known tenant
material; catalog policy and stale completed-print UI are explicitly deferred.

Existing local Surefire XML reports from October 27 contain Hub 146 tests across
72 reports and AMS 23 tests across 4 reports, with zero failures/errors/skips.
These are surviving historical reports, not a fresh full-reactor or complete
security-suite run. Earlier documented build/vendor/isolated-database checks
also remain historical evidence. No tests were executed during this audit.

## Infrastructure checkpoint and restart risks

The operator reports all Stage 9 containers stopped. The screenshot identifies:

| Component | Retained image/tag |
| --- | --- |
| Core | `edol-stage9-core:d52fdd0e` |
| Hub | `edol-stage9-hub:484b442` |
| AMS | `edol-stage9-ams:72a050b` |
| Notify | `edol-stage9-notify:04ed4cc` |
| Keycloak | `quay.io/keycloak/keycloak:26.7.4` |
| PostgreSQL | `postgres:17` |
| MQTT | `eclipse-mosquitto:2.0.22` |
| HTTPS proxy | standalone `nginx:stable-alpine` |

The retained test directory is `/home/serge/edol-stage9-hardware`; the backend
checkout was last recorded at `72a050b`. Recheck it before relying on it.
Known override files include `compose.agent-proxy-trust.yaml`,
`remediation/compose.hub-484b442.yaml` and
`remediation/compose.ams-72a050b.yaml`. Inspect actual container Compose labels
and image IDs; do not assume all services share one override set.

Core's temporary native-forwarding trust used proxy IP `10.253.90.7` and Core
was exposed at `192.168.0.200:18081`. The HTTPS endpoint was
`192.168.0.200:18443`; Hub was on port 18082. Proxy addresses can change or be
reused after downtime. Check actual network addresses, proxy routing, configured
trusted peers and header sanitization before treating HTTPS as restored. A LAN
address alone is not an authorization boundary. Do not blindly rerun an old
bootstrap, `resume.py` mutation, Compose `up`, identity setup or trust override.

Prefer starting retained containers after inspection to avoid accidental image,
network or configuration recreation. Bring up infrastructure before dependent
applications; inspect bounded readiness after each group. Keep Notify for its
separate test. A startup failure requires redacted diagnosis, not automatic
credential rotation or schema/authority changes. Check current clock and
certificate validity without printing certificate contents or secret config.

## Priority and exit criteria

1. **P0 — Restore a known test baseline.** Inspect retained containers, images,
   labels, networks, mounts and safe status evidence. Start only the scoped
   Stage 9 environment using existing data/identities. Confirm PostgreSQL,
   identity provider, MQTT, proxy and dependent services are ready; verify
   trusted HTTPS succeeds and direct spoofed-forwarding access remains denied.
   Any changed trust/configuration needs a concrete reviewed proposal.
2. **P1 — Resolve AMS logical-printer ownership.** Determine whether DIRECT is
   intentionally outside AMS management or should be managed alongside AGENT.
   Trace shared subscriptions, mappings, terminal binding and staged-spool
   operations. Review duplicate physical write risk before proposing a scoped
   mapping or subscription/handling policy. Preserve cross-tenant rejection;
   do not suppress genuine failures merely to reach a zero counter.
3. **P1 — Instrument and classify Agent stability.** Design offline reproduction
   of -28928 against the exact audited vendor source, and bounded runtime
   diagnostics for both MQTT loops/connections and transient heap. Inspect reset
   and retry behavior independently. Review the design before vendor changes
   or another hardware stress run. Archive-based builds are available; full
   SDK-from-source dependency pinning remains open.
4. **P1 — Complete combined hardware cases.** Following baseline and ownership
   decisions, capture terminal serial and authenticated state; exercise NFC
   owned/unknown-spool boundaries. Run one idle-printer metadata write only
   after an exact inverse is agreed, then independently verify restoration.
   Complete telemetry-confirmed Pause/Resume, recovery/restarts, physical
   TLS-negative controls and active-identity revoke/re-pair in bounded scopes.
   Do not combine all perturbations into one long print.
5. **P2 — Notify.** Diagnose exit 1 independently; later test with a real,
   dedicated test Telegram bot as requested. Obtain safe credentials locally
   and explicit message/recipient scope before delivery. Do not assume the
   historical Telegram problem is the present cause.
6. **P2 — Final security and rollout gate.** Review the migration plan's
   remaining Stage 9 requirements: actual legacy usage window, supported event
   versions, private_key_jwt decision, DB TLS, secret storage/rotation, port and
   proxy isolation, cross-tenant negatives, rollback and restore. Record explicit
   decisions for deferred requirements. The long print still used v2/legacy
   compatibility; it does not authorize removing it. Working dev/prod migration
   and broker cutover remain separately reviewed rollout work.

Catalog bootstrap, automatic filament policy and completed-print dashboard
cleanup stay in Project TODO. Historical Hub exceptions remain unclassified;
if reproduced, gather safe stage-specific evidence rather than replaying old
acknowledged events. Assess mutable runtime references before declaring Hub
rollback hardening complete.

Documentation needs reconciliation: the remediation introduction, combined
runbook results table and older Project TODO paragraphs still contain superseded
FAIL/NOT RUN, MQTT-pause and worktree statements. Use the dated final evidence
sections and this audit for current status; retain older records as history.
No architecture fact changed in this audit, so architecture.md was not edited.

## Copy into the next chat

```text
Continue EDOL Stage 9 from the 2026-11-08 audit.

Reply in Ukrainian; code, comments, documentation and commits in English.
Work in C:\Users\Ponomarenko\IdeaProjects\edol on EDOL-048, using the main
firmware repositories rather than worktrees. Start with AGENTS.md, applicable
skills, current Git status and docs/deployment/stage9-resume-handoff-2026-11-08.md.
Then read Project TODO in docs/ai-workflow.md, the final AMS deployment/long-print
sections of docs/deployment/stage9-hardware-remediation.md, the combined hardware
runbook and the final-lockdown scope of the secure migration plan.

The operator stopped every Stage 9 Docker service. First inspect retained
containers/configuration read-only and present the smallest exact restart plan.
Prior authorization permits scoped test-environment operation, but do not
silently broaden it to new authority, database, certificate or printer changes.
The current request does not authorize production deployment, cleanup or push.
Prefer retained-container startup, preserving image revisions, volumes, realm,
identities, MQTT authority, NVS and CA. Do not bootstrap again. Recheck proxy IP
against Core's temporary forwarded-header trust before resuming device traffic.
Do not run a generic Compose up that loses per-service overrides or recreates
unrelated services. Report startup failures with redacted evidence.

Backend local HEAD at audit was 72a050b; local origin/EDOL-048 was 484b442,
without a fresh fetch. Agent master was clean at 08c1051; terminal main clean at
6d98d6b. Inspect again. Preserve two existing modified documentation files,
Print_27102026/, SDK archives, edol-hub/logs/, ignored local configuration and
restoration artifacts. Never publish the main Agent repository's old history.

The ~41-minute print established 106 snapshots, exact 610729-byte model/FTP226,
both print finishes and no new Hub processing failures. It did not establish
continuous dual MQTT servicing, terminal continuity, memory minima, negative
TLS controls or full acceptance. The earlier -28928 remains unexplained; the
long print's -80 reset/retry is a different error. Terminal verified enrollment
and state already passed; do not replace/reset/re-enroll it merely to resume.

After baseline recovery, investigate AMS ownership first: ten DIRECT
tenant_mapping/unmapped_printer failures occurred while DIRECT and AGENT both
observed the same physical printer. Trace the actual subscription, mapping,
terminal and staged-spool paths; propose the minimum policy that avoids duplicate
physical writes and preserves tenant isolation. Do not automatically add mappings
or hide processing failures. Ask for the ownership decision only after source
and safe configuration evidence make the alternatives concrete.

Then prioritize bounded Agent anomaly/observability work and the remaining AMS,
command, lifecycle and physical negative-security cases. Printer metadata writes,
revocations, certificate/authority changes and stress tests need their own concrete
reviewed scope. Notify remains scheduled for a real dedicated test bot later.
Keep automatic filament/material bootstrap and stale dashboard artifacts deferred.

Reconcile stale current-status introductions/TODO/runbook rows with dated evidence
without rewriting historical outcomes. Distinguish fresh checks, historical test
reports, observed hardware PASS, hypotheses and unexecuted cases. Use targeted
checks and read-only Sonar for modified supported source; do not rerun the full
suite without a concrete need. Do not mark Stage 9 complete before combined
hardware and final-lockdown requirements or explicit deferral decisions are met.
Provide short progress updates and do not leave hanging SSH/async sessions.
```
