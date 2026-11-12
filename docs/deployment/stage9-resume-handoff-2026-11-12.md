# Stage 9 handoff - 2026-11-12: Notify accepted, readiness audit next

## Accepted functional scope

The operator explicitly accepted Notify functional testing after
STATUS_OVERLAY_HTTP_UPDATED. Status sends photos with the previous overlay;
the operator also started a print and confirmed overlay photos for DIRECT and
AGENT. Both logical printers observe one physical P1S. Tenant Telegram settings
belong to the tenant and are managed by OWNER in Hub. Test credentials were
entered locally in Hub, never in chat or Git. The operator authorized all test
message types in the dedicated bot/chat; no production recipient is authorized.

The NFC functional topic remains closed by the November 11 operator decision.
Do not repeat NFC acceptance or flash firmware without a new reproducible defect.
Notify functional acceptance is also closed; do not start another broad test
merely to refresh evidence. It does not imply every notification type or the
completion message for the latest ongoing print was independently observed.

Backend tenant-settings commit: `869df1ea2c6f2d3af94aa0aa6b351a20df8e6be7`.
Terminal accepted commit: `276a7820591614ce3115356de1efb3a03ff63cf3`.
The backend commit containing this handoff adds scheduling, bounded authorized
HTTP status-image delivery, text fallback and rollout/acceptance evidence.
Discover current Git HEAD/status rather than treating deployed patch images
as full checkouts of the newest backend commit. No push was requested.

## Current retained runtime

Server SSH profile: `edol-server`, retained directory
`/home/serge/edol-stage9-hardware`, project `edol-stage9-hardware`.
Read-only SSH/Docker inspection worked on November 12. Operator actions were
performed by the operator, not by the assistant.

| Service | Current inspected container | Image / baseline |
| --- | --- | --- |
| Core | `4b12bb792091` | `sha256:e264d311a0b05a719412d076b387c1c9d84c4ecbda7cd72c0b0d924675285ac3` |
| Notify | `f2f578abb130` | `sha256:e10862b0217f286e44e674ecb95425847944deabd7b875ebc929119c4a12f4fd` |
| Hub | Last rollout `305e017c3c52` | `sha256:78e311fcacf44753b9d24adb794f31ce09acb6e903946f7cce64a0fa09506b1a`; not re-inspected for this closure |

Core builds from retained d52fdd0e source plus CameraController only; Notify
builds from 869df1e plus the scoped scheduling/HTTP patches. Runtime layers reuse
the exact prior images to preserve JRE/fonts/dependencies. Each service retains
its own Compose file chain, with `remediation/compose.status-overlay1.yaml` last.
Inspect actual container labels before any later deployment; never use generic
Compose up or bootstrap.

The proxy drift was resolved earlier: standalone HTTPS proxy
`edol-stage9-agent-https` owns `.7`; Core is pinned to `.9`, Notify to `.8` on
`10.253.90.0/24`. Core trusts only proxy `.7`; do not broaden that pattern.
Proxy/other-service readiness was not rechecked during this bounded log review.
See [proxy recovery](stage9-proxy-drift-2026-11-12.md).

Hub V10 and the preserved local `secrets/hub-telegram.env` encryption key were
provisioned in the operator rollout. Do not print, replace or regenerate the key.
Notify has the narrow Hub read scope/audience and retains existing Core scopes,
including core.media.read. Its trusted service-client allowlist entry is retained.
The Notify-only truststore overlay adds public Telegram CAs while preserving the
private MQTT CA. Do not replace the original shared store or lose this overlay.
The detailed file chains, source hashes, drafts and rollout steps are in
[rollout evidence](stage9-hub-notify-rollout-869df1e-plan.md).

## Fresh evidence and limitations

At 12:48:17 UTC, logs since Core startup 12:43:35 and Notify startup 12:43:54
showed both containers running. Notify had no WARN/ERROR, exceptions, 403,
polling/configuration or temporary-file cleanup errors. Three Status callbacks
were recorded. Latest MQTT counters: validatedV2=14, rejectedEnvelope=0,
processingFailures=0. Photo/overlay delivery is operator-observed, supported by
screenshots, rather than inferred solely from ingress counters.

Core had two camera SSLHandshakeException errors, one FTPS_CONNECT_FAILED model
transfer error and two metadata retry warnings. Later metadata/transfer success
markers were observed. Their root causes remain unclassified and are recorded
in Project TODO; no TLS/retry changes were attempted. There was also a Hibernate
dialect deprecation warning. Do not report all Core logs as clean.

Latest unchanged-source checks: Notify 31 tests; Core CameraControllerTest 4
and CorePrinterHttpAuthorizationTest 7, all passed. Sonar Java S1598 is the
empty-path snippet artifact; S5443 temporary-file use was reviewed safe (atomic
random NIO creation, owner-only POSIX defaults, finally cleanup), not suppressed.
Runtime Dockerfiles had no Sonar findings. No full reactor or new physical test
was needed for this closure. Source changes preserve the renderer, tenant
ownership, service authorization, bounded HTTP reads and text fallback.

## Next task: read-only Stage 9 readiness audit

Start with AGENTS.md, current branch/status, this handoff and the current
[combined runbook](stage9-disposable-hardware-runbook.md). Compare the
[multi-tenant migration plan](../migrations/secure-multi-tenant-migration-plan.md),
[architecture](../architecture.md), [full-cycle evidence](stage9-full-cycle-2026-11-08.md),
[AMS/NFC evidence](stage9-ams-mapping-alignment-2026-11-08.md), pairing/MQTT
contracts and [Project TODO](../ai-workflow.md) against implementation and
existing evidence. Historical preparation/rollout instructions must not be
executed as audit steps.

Produce a concise evidence matrix: requirement, source/disposable evidence,
runtime/physical evidence, PASS/PARTIAL/NOT RUN/BLOCKED, gap/risk and smallest
next action. Reconcile stale claims explicitly. Separate required Stage 9
security/lifecycle gates from UX and operational backlog. Identify which
requirements truly remain mandatory before declaring readiness; do not infer
acceptance from a build or a running container.

Audit candidates include active-identity revoke/re-pair and credential rotation,
physical negative TLS/tenant controls, approved token/config transport, final
lockdown/migration/rollback/restoration, deliberate recovery and Pause/Resume,
continuous dual-MQTT servicing and transient/idle heap evidence. These are
candidate gaps from existing records, not a new claim that all are unexecuted.
Classify them against current evidence before proposing tests.

Keep deferred duplicate-looking Notify cadence, Agent/printer OFFLINE semantics,
powered-off Core log noise, fresh Core transport errors and spool-status UX
distinct. Preserve DIRECT/AGENT identities and tenant routing; screenshots alone
do not prove duplicate MQTT delivery or Agent connectivity failure.

Safe focused read-only inspection is authorized. Give commands for operator
actions. No new print, control command, power-cycle, firmware upload, NFC test,
credential rotation, deployment, generic Compose up/down, bootstrap, production
change or push is implied. Preserve volumes, identities, CA/NVS, encryption key
and all per-service overrides. Avoid redundant broad checks in this disposable
test environment. Leave unrelated SDK archives/logs and ignored build artifacts
outside commits. The main Agent repository's old private history must never be
published.
