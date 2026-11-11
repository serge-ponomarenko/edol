# Stage 9 full-cycle observation — 2026-11-08

This is a passive physical-print observation following
[retained-container recovery](stage9-baseline-recovery-2026-11-08.md).
All required test services were ready before the operator started the print.
Notify remained stopped. No deployment, configuration change, printer command,
database query, NFC/spool operation or device re-enrollment was performed during
this analysis. Production EDOL was not inspected or modified.

## Evidence and time bases

Private serial evidence remains outside Git under
`C:\Users\Ponomarenko\AppData\Local\EDOL\stage9-evidence`:

| Device | Relative file | Bytes | SHA-256 |
| --- | --- | --- | --- |
| Agent | `agent-20261108-094909/logs/device-monitor-261108-094909.log` | 152620 | `14b78c98efed740efec4b79cd4d9feffaac341017cc6b4dc6c6e6458c1cf0add` |
| Terminal | `terminal-20261108-094948/logs/device-monitor-261108-094948.log` | 672701 | `4683c31ee5ab8aa090ff0791e1a689ff6f9c37bbe9c2917a42e60cf085bfd4aa` |

Agent capture spans Windows local timestamps 09:49:09.974–10:51:14.752;
terminal capture spans 09:49:49.200–10:50:55.554. These captures include time
before and after the print. Their timestamps must not be aligned naively with
server UTC: after subtracting the local UTC+02 offset, Windows log timestamps
are approximately 32–33 seconds ahead of the terminal's recorded `utc_epoch`
(median 32.514 seconds). This is an observed relative clock difference, not an
independent clock-accuracy diagnosis. Server chronology below uses server UTC.

Server evidence was read from retained Core, Hub and AMS Docker logs beginning
2026-11-08T07:49:00Z. Completion was checked at approximately 08:52 UTC. Raw
payloads, credentials, printer access codes and exception messages were not
included in tool output or copied into this record.

## Print chronology

DIRECT is `01a168b6-1317-73ea-83eb-55785be588f3`; AGENT is
`01a16493-e4ed-766b-8e75-53e168d39efa`. The terminal remains bound to DIRECT;
the AMS MQTT mapping still names AGENT. No ownership configuration changed.

| Server UTC | Evidence |
| --- | --- |
| 07:50:15.820 / 07:50:15.873 | Core DIRECT / AGENT print-start markers |
| 07:50:15.868 / 07:50:15.887 | Hub created independent DIRECT / AGENT print jobs |
| 07:50:19.735 | DIRECT metadata acquisition failed with `ModelTransferException`; one 30-second retry scheduled |
| 07:50:43.566 | AGENT metadata parsed successfully |
| 07:50:43.612 | Hub AGENT allocation snapshot created |
| 07:50:52.951 | DIRECT metadata parsed successfully after retry |
| 07:50:52.972 | Hub DIRECT allocation snapshot created |
| 08:49:50.682 / 08:49:50.838 | Core DIRECT / AGENT print-finished markers |
| 08:49:50.706 / 08:49:50.856 | Hub recorded both print finishes |

The observed Core print interval was approximately 59 minutes 35 seconds.
Both Hub allocation previews existed, but both remained unresolved and their
allocation finalization was skipped. This is separate from physical print
completion; no spool inventory or allocation was invented to close it.

Hub recorded 93 progress updates for each logical printer, from 0% / layer
0 of 185 to 99% / layer 184 of 185, followed by the finish markers above.
The largest intervals between these progress-change observations were 278.876
seconds (DIRECT) and 278.861 seconds (AGENT). These are intervals between
derived progress events, not measured MQTT outages or raw telemetry gaps.

## Agent serial results

- 124 camera snapshots, ranging from 50,440 to 87,899 bytes.
- One verified model transfer: `expected=228110 transferred=228110 ftp_code=226`.
- 125 HTTP parsed results, all status 200 with expected status 200; 125 HTTP
  finish-success and bulk-success records; zero bulk-failed records.
- 125 snapshot deferrals for busy/model-pending/cooldown admission. These are
  separate from failures of accepted bulk operations.
- All 248 captured certificate-depth verification flags were zero. Capture
  started during an existing operation, so these are not an assertion of a
  complete per-handshake certificate log for every successful operation.
- No `-28928`, `-80`, failure/error, disconnect/reconnect, reset or panic marker
  was found in this capture. There were no MQTT-specific serial observations,
  so this does not establish uninterrupted servicing of both MQTT clients.
- Across 499 stage samples, observed free heap ranged from 182,756 to 192,844 B;
  the smallest observed largest block was 94,196 B. After-handshake samples
  began at 188,584 B and ended at 188,592 B; their largest block was consistently
  110,580 B. These are stage samples, not transient handshake minima or proof
  of leak freedom.

## Terminal serial results

- 1,204 HTTP-response records, all `http_result=200`.
- 1,204 transport-result records, all `verified_connection=1`, `tls_error=0`,
  and `verify_flags=0x00000000`.
- 1,203 request-category records, all `state`. The first captured response
  precedes its request-start/category because monitoring began mid-request.
- Response-to-response interval: median 3.028 s, maximum 6.190 s; 11 intervals
  exceeded 5 s and none exceeded 10 s.
- No transport/precheck/begin failure, invalid-credential, parse-failure,
  enrollment or reboot marker was found.
- Precheck heap ranged from 164,696 to 167,024 B, first/last 167,020 B.
  Connect-start heap ranged from 166,812 to 166,868 B, first/last 166,844 B.
  All captured largest-block values were 110,580 B. These observations do not
  measure transient TLS minima or replace physical state-content validation.

## Retry context and remaining failures

The operator reports three clients competing for a printer FTP service that
allows only one connection: production EDOL, Stage 9 DIRECT, and Stage 9 AGENT.
Treat this as operator-supplied contention context. The successful DIRECT
retry is observed resilience under the stated setup. The logs expose only
`ModelTransferException` for the failed DIRECT attempt, not a definitive FTP
cause or curl exit code; no production inspection or deliberate contention
injection was performed. Agent serial contains no failed transfer in this run.

Hub had no processing-failure/rejected-envelope marker in the analyzed run.
AMS recorded ten processing failures, all for DIRECT at
`tenant_mapping` / `unmapped_printer`; these remain genuine rejections under
the current configuration. No unknown/cross-tenant rejection was suppressed.

This run demonstrates both logical metadata paths and Hub previews when Hub
is ready before the print. It does not fix or disprove the earlier late-Hub
metadata-reconciliation gap. The prior dynamic-RX `-28928` remains unexplained,
although it was not reproduced here.

## Acceptance boundary and next step

Observed PASS for this bounded run: both print lifecycles, metadata acquisition
and preview creation on both logical paths, Agent snapshot/model upload, exact
model/FTP completion, and the captured terminal verified-HTTPS state polling.
This is stronger physical evidence than the earlier run without terminal serial.

Next, prepare a concrete alignment of the existing DIRECT terminal binding and
AMS event mapping, preserving validation for unknown/foreign printers and
printer-scoped staging. The terminal supports either DIRECT or AGENT; a rebind
is not intrinsically required. Any mapping/rebind deployment and NFC or tray
write must retain its own reviewed scope and, for writes, an exact inverse.

Still open: dual-MQTT loop/connection instrumentation, transient heap minima,
controlled `-28928` reproduction, NFC boundaries, metadata write/restoration,
Pause/Resume, deliberate recovery, active-identity revoke/re-pair, physical
negative TLS controls, Notify, final lockdown and rollout. No full Stage 9
completion is claimed. No source code changed; tests and Sonar were not run
for this evidence-only analysis. No architectural contract changed.
