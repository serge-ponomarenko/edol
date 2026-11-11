# Stage 9 AMS mapping alignment — 2026-11-08

## Reviewed scope

The existing terminal is bound to DIRECT. The user confirmed that terminal
selection supports either DIRECT or AGENT and authorized continuation of the
mapping alignment. This is one test-environment change, not production rollout.

Add the already known DIRECT printer
`01a168b6-1317-73ea-83eb-55785be588f3` to tenant
`7159f3c8-1976-4c3e-ad4f-124bc1a9e44a`. Retain the existing AGENT mapping for
`01a16493-e4ed-766b-8e75-53e168d39efa` to that same tenant. These printer IDs and
their common tenant are established by operator URLs and validated Core events.
No automatic discovery, unknown-printer acceptance or foreign-tenant acceptance
is introduced.

The map authorizes processing of trusted Core events for these logical printer
IDs. It is distinct from the authenticated terminal's stored allowed printer.
The existing terminal remains bound to DIRECT and its identity is preserved.

## Safety evidence

`AmsSpoolChangerService` keys RAM staging by logical printer ID. The added
`MqttEventListenerTest` uses the actual resolver, listener and staging service
with two mapped printers and a mock Hub endpoint. It verifies that:

- An AGENT slot event cannot consume staging belonging to DIRECT.
- Unknown-printer and wrong-tenant envelopes remain rejected before a Hub POST.
- The matching DIRECT event consumes its own staged spool and makes one POST.
- A subsequent event with no remaining staging does not make another POST.
- The tenant scope is present at the mock request and closed afterwards.

This covers the current single-terminal, printer-scoped staging scenario. It
does not establish global physical-printer write arbitration for independently
staged aliases, concurrent terminal actions, or every MQTT redelivery sequence.
NFC and physical writes remain separate cases with an exact restoration plan.

Fresh targeted verification passed:

```text
mvn -B -pl edol-ams -am -Dtest=MqttEventListenerTest,AmsPrinterTenantResolverTest,AmsSpoolChangerServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

13 tests, zero failures/errors/skips: listener 9, resolver 3, staging service 1.
Initial sandbox-token artifact/classpath access failed; rerunning the normal
test command with the user token passed. No clean/ACL workaround was used for
the successful run. No Docker-dependent or full-reactor test was needed.

Read-only Sonar analyzed the entire changed Java test with TEST scope. Its
sole finding was S1598 for empty snippet file path; the real path
`edol-ams/src/test/java/org/spon/edolams/event/MqttEventListenerTest.java`
matches package `org.spon.edolams.event`. This is an MCP artifact; no suppression
or package change was made. No production Java source changed.

## Runtime plan and rollback

The retained AMS container was created from precisely:

1. `/home/serge/edol-stage9-hardware/compose.backend.yaml`
2. `/home/serge/edol-stage9-hardware/remediation/compose.ams-72a050b.yaml`

Read-only Compose rendering matched its effective image/default/environment
configuration without drift. A proposed override rendered with changes only to
the two new mapping variables and AMS network addressing. The image tag still
resolved to the retained image ID:
`sha256:bfe4d5a869c301415940d7721d0848d7e9ab9339d02b2315fa1f17cd8ff5376a`.

Use a new `remediation/compose.ams-direct-mapping-20261108.yaml` override:

```yaml
services:
  edolams:
    environment:
      EDOL_AMS_PRINTER_TENANTS_MAPPINGS_1_PRINTER_ID: '01a168b6-1317-73ea-83eb-55785be588f3'
      EDOL_AMS_PRINTER_TENANTS_MAPPINGS_1_TENANT_ID: '7159f3c8-1976-4c3e-ad4f-124bc1a9e44a'
    networks:
      stage9:
        ipv4_address: '10.253.90.6'
```

Pinning the existing AMS address preserves nginx's currently resolved upstream.
The proxy stays at `.7`; Core's temporary trusted-peer pattern is unchanged.
This is retained test-network configuration, not a durable production isolation
design. The container must be recreated to change its environment. Use only
`up -d --no-deps --no-build --pull never edolams` with the exact files above
and this override; do not run a generic multi-service Compose command.

Before applying, verify the live image, current mapping, addresses, Compose/env
drift, and that both last observed Core print lifecycles are terminal, with
operator confirmation that the physical printer is idle. Reject
recent staged-spool activity. Record IDs and start timestamps for every other
retained test container, then verify they are unchanged afterwards.

Retain `remediation/compose.ams-ip-rollback-20261108.yaml` with only the same
AMS static-address section and no new mapping. To roll back this alignment:

```bash
cd /home/serge/edol-stage9-hardware
docker compose -p edol-stage9-hardware \
  -f compose.backend.yaml \
  -f remediation/compose.ams-72a050b.yaml \
  -f remediation/compose.ams-ip-rollback-20261108.yaml \
  up -d --no-deps --no-build --pull never edolams
```

This restores the prior mapping set with the existing `.6` upstream address.
It preserves the database, identities and images. Keep both override files as
evidence; no deletion or cleanup is part of this operation.

## Runtime outcome

### Initial attempt — stopped before mutation

The initial scoped rollout stopped at its read-only idle
precondition: a new physical print started while offline preparation was in
progress. Core recorded DIRECT `PRINT_STARTED` at 2026-11-08T09:28:45.111Z and
AGENT `PRINT_STARTED` at 09:28:45.217Z, with no later finish marker at inspection.
No override file was created and no Compose mutation ran. AMS retained container
ID `d444f06925116542efd4805d565ccd2656746b507b4d1f86ca409fe586f00e0d`
and start time 2026-11-08T07:03:06.969741127Z.

### Applied after operator idle confirmation

The operator subsequently confirmed the printer was free. The last observed
DIRECT and AGENT lifecycles ended with `PRINT_FAILED` at
2026-11-08T10:48:02.318Z and 10:48:02.440Z. A success-only idle guard initially
stopped before mutation; it was corrected to accept terminal FINISHED, FAILED
or CANCELED lifecycles alongside the operator's idle confirmation. The cause of
that print outcome was not diagnosed or inferred from the marker.

Fresh preflight passed: no current configuration/image drift, no recent
staged-spool scan, retained AMS `.6`, proxy `.7`, and exactly the expected two
original AMS Compose files. The mapping and rollback override files were
created exclusively, without overwriting existing files or writing secrets.
Only `edolams` was recreated using the scoped command above.

- New AMS container:
  `efc2215707133084eb1c6a0476ffc9a2d1ba06d7ee765c27050b8b34c0e7c455`.
- Started at 2026-11-08T10:51:05.596428269Z, on unchanged image
  `sha256:bfe4d5a869c301415940d7721d0848d7e9ab9339d02b2315fa1f17cd8ff5376a`.
- Actual AMS address remains `10.253.90.6`; both DIRECT and AGENT environment
  mappings name tenant `7159f3c8-1976-4c3e-ad4f-124bc1a9e44a`.
- Every other retained test container had unchanged ID, start time, image ID
  and running state. Notify remained stopped.
- CA/IPv4-verified HTTPS to terminal state with a malformed synthetic
  credential returned 401 at readiness verification, 10:51:14 UTC.
- At 10:52:22 UTC AMS startup was complete, with no startup ERROR,
  Core-unavailable, MQTT-processing-failure or rejected-envelope markers.
  Core health remained UP. This short observation window is not a real
  event-processing acceptance result.

Existing database state, terminal enrollment and credentials, broker authority,
certificates, proxy configuration, Core and Hub were not changed by this
operation. Existing AMS RAM staging was intentionally not restored; preflight
required no recent staging and the operator's idle confirmation. No real
credential, NFC/spool lookup, assignment or printer command was used as a probe.

### Operator read-only verification after restart

The operator confirmed current AMS slots and sensor values on the terminal.
The supplied Hub screenshot at approximately 13:11 local time shows the DIRECT
printer selected, unchanged terminal ID
`01a17e58-762b-7d89-a320-61ef3bed4ebe`, lifecycle ACTIVE, pairing CONSUMED,
original enrollment 2026-10-27T17:12:15.237306Z, and a fresh last connection of
2026-11-08T11:10:56.310527Z. This confirms the existing terminal's authenticated
state path recovered after the AMS-only restart without re-enrollment.

Successful processing of subsequent real DIRECT/AGENT AMS MQTT events remains
to be observed separately. Current terminal state alone does not exercise that
event-dispatch path.

### NFC negative-case preparation — not executed

Read-only terminal source tracing confirms background scans enqueue a lookup
only when tag magic, version and checksum pass validation. Invalid or blank
tags return before a pending spool scan is created. They are currently ignored
without a distinct UI error; unchanged UI alone is not proof that the reader
detected the tag. A positive reader control is needed for complete NFC acceptance.

A correctly encoded EDOL tag invokes secure terminal `/find`; Hub scopes its
lookup to the authenticated tenant. Source reinspection on 2026-11-11 corrected
the earlier claim that a missing/foreign spool yields 404: Hub currently returns
204, and AMS propagates no-content without creating server staging. The pre-fix
terminal accepts all 2xx responses and marks an empty response as a loaded local
spool with ID 0. The local source fix and offline verification are recorded
below; the installed device firmware has not yet been updated. This source
finding is not a physical PASS or evidence of a cross-tenant printer write.
An owned successful lookup creates server staging for
60 seconds and terminal staging for 30 seconds, subject to terminal keep-alive.
It must therefore retain the separately reviewed write/restoration boundary.

Tag writing is a different UI-triggered state machine. Do not enter Write/Erase
mode or reuse a working tag to prepare a negative case. Before a test, select a
spare tag, confirm the terminal is on its normal home screen, and let any prior
server staging expire. Do not operate trays or invoke slot assignment. The next
physical case depends on the operator's available test tags; no NFC/tag or
printer write has been authorized or performed by this preparation.

### Tag format and fresh infrastructure inspection — 2026-11-11

The main terminal repository remains at `6d98d6b2dd84000d84d8cec3f7e6871cf42bdb4d`.
Its packed NFC record is 16 bytes on NTAG pages 8–11: magic `EDOL` (4),
version 1 (1), type 1 (1), reserved zero (2), uint32 spool ID (4), and a uint32
non-cryptographic checksum named `crc` (4). There is no terminal ID/hash, tenant
ID, server identity, credential or cryptographic signature. Spool details are
resolved through the current authenticated backend. A new terminal does not
automatically make old tags foreign; authorization uses the current tenant.
Numeric IDs can collide between separate databases, so a production tag must
not be assumed to identify the same spool in a disposable database.

The operator reported downtime. A fresh read-only inspection at
2026-11-11T15:27:27Z found PostgreSQL, Keycloak and MQTT running; Core, Hub, AMS,
HTTPS proxy and Notify were stopped. Exit codes alone were not diagnosed.
AMS retains static `.6`, the proxy static `.7`, and the known image tags remain
present. Public certificate metadata remains unchanged: HTTPS expires November
15, MQTT November 20, and CA November 21. No service was started, no config or
identity was changed, and no tag or printer command was sent during this check.

### Local staging fix verified offline — 2026-11-11

The user requested the no-content staging fix before service restoration.
The main terminal source now accepts a spool lookup for local staging only on
HTTP 200 with nonempty content and a positive extracted spool ID. Loaded state
is published after validation and field population. Any rejected lookup also
clears older local staging; assignment then sends no request. Generic HTTP
2xx semantics and backend 204 responses remain unchanged.

The new native suite compiles the actual StagingService, HttpService and JSON
helpers. Before the fix it reproduced the 204 defect at `!hasSpool()`; after
the fix all 44 Home/secure cases passed. Existing HTTP diagnostics passed with
and without CA. Warning-as-error checks remain enabled. A single existing
float-helper narrowing was made explicit for the strict native compilation.

The secure esp32dev build and existing CA/image audit passed in isolated
`.pio/staging-validation-20261111-/esp32dev`, preserving ordinary build output
and restoration artifacts. BIN SHA-256 is
`d76d7ed6e937dd7a605c4e86de1392f711cc0d76e244a55ca4e05e4077ec5874`;
ELF SHA-256 is
`e544e653499dddacc25d517121b7ea2394ca1f68d95085658db8f6710261795c`.
The retained CA DER fingerprint matched the established authority; its PEM file
hash is a different representation. No trust material changed. Read-only Sonar
found no issue in the modified Python runner; C++ is unsupported by this MCP
and was checked through native tests and the real ESP32 build.

This is not a physical NFC PASS. Firmware upload, service restoration and
physical negative controls were not performed. Previously created server
staging still expires according to its existing timeout; clearing local state
does not cancel that server entry. See `docs/spool-lookup-validation.md` in the
terminal repository for the complete offline evidence and artifact directory.

### Retained-service restoration and upload instructions — 2026-11-11

The user authorized restoring services and requested operator commands to
upload the fixed terminal image while preserving NVS/enrollment. Existing SSH
profile `edol-server` connects as `serge` to `192.168.0.200`; that account has
Docker access. Recovery uses bounded remote CLI operations, not a persistent
agent or a newly exposed Docker API.

Fresh preflight verified retained container/image identities, exact Core
forwarding trust, free proxy `.7`, AMS static `.6`, and the still-valid existing
HTTPS certificate/CA. PostgreSQL, Keycloak and MQTT were already running and
were not restarted. PostgreSQL was healthy; MQTT TLS verified the retained CA
and IPv4 identity; configured OIDC realm discovery succeeded.

Only retained Core, AMS, proxy and Hub containers were started with
`docker start`. No Compose recreation, image pull/build, bootstrap, mapping,
database, broker-authority or certificate change was performed. Starts (UTC):

- Core 2026-11-11T15:55:22.311439906Z at `.5`, actuator health UP.
- AMS 2026-11-11T15:55:34.776122476Z at `.6`, both mappings retained and malformed
  synthetic terminal credentials rejected with 401.
- HTTPS proxy 2026-11-11T15:57:34.493492795Z at `.7`, `nginx -t` passed.
- Hub 2026-11-11T15:57:34.766711184Z at `.8`, root 302 to `/printers` and favicon 200.

At 15:57:45 UTC seven test services were running and Notify remained stopped.
CA/IPv4-verified HTTPS Agent and terminal routes returned 401 without valid
credentials; a direct Core request with spoofed forwarding headers returned
403. These are routing/authentication probes, not physical NFC acceptance.

Prepared `tools/stage9/flash-terminal-staging-fix-20261111.ps1` for operator use.
PowerShell AST parsing and `-CheckOnly` passed without accessing hardware. It
checks exact BIN/ELF/config/CA identities and verifies that sector-aligned upload
ranges do not overlap NVS/filesystem. In upload mode it requires an explicitly
selected USB COM port, reads only the public partition table and requires an
exact match before `nobuild`/`upload` of the audited image. Environment overrides
are process-scoped and restored afterwards. It does not use erase, filesystem,
sec06 or enrollment operations. Standard write-flash sector erases are expected
only for the verified image ranges.

No USB serial device was connected during preparation; actual table read,
upload and post-upload terminal identity/state verification remain operator
actions. No NVS backup or credential extraction is part of this helper. Retain
any partition-read mismatch/error and stop instead of erasing or re-enrolling.

### Post-upload operator evidence — 2026-11-11

The operator reported successful upload, current terminal slot/sensor display,
Hub ACTIVE/CONSUMED, and no NFC scan. The saved log is
`terminal-fixed-20261111-180926/logs/device-monitor-261111-180926.log` beneath
the existing private `stage9-evidence` directory. It contains 10,212 bytes,
SHA-256 `fd9f87b4a62c264e0d8c47496628438e1f77d5a3a016b3ee697d5940cd6be674`,
covering Windows timestamps 18:09:27.422–18:10:30.774.

The device reports the expected esp32dev environment, normalized CA PEM hash
`a83bd645d1319b7427c0af43585971c0bf1ee8437d70b4a3ec5e25d28d7e7b38`, and
ELF identity `e544e653499dddacc25d517121b7ea2394ca1f68d95085658db8f6710261795c`.
`Settings loaded` and successful authenticated state retrieval corroborate the
operator's report that enrollment/settings survived. No replacement pairing or
credential operation was performed by the assistant.

There is one initial `clock_not_ready` precheck at epoch 5, before any TLS connect
attempt for that request. After time becomes ready, all 17 state responses are
HTTP 200 with verified_connection=1, tls_error=0 and zero verification flags.
All 17 have `AMS state updated`; no transport/begin failure, additional boot,
lookup, staging or NFC write/erase marker appears. This is successful uploaded
image identification and state-path recovery, not physical 204/NFC acceptance.

Memory requires an additional idle observation: clock-ready precheck free heap
falls from 172,764 to 167,012 B. After the first successful request, precheck heap
falls from 170,368 to 167,012 B over approximately 45 seconds, mostly about
224 B per polling cycle. Largest block remains 110,580 B. This short boot-time
capture does not distinguish warm-up/caching from a continuing leak and cannot
establish stability or transient TLS minima. Earlier firmware capture had a
steady-state precheck value around 167,020 B; that is historical comparison,
not evidence that the new image already reached a plateau. Observe the current
image for five more idle minutes without NFC, reset or firmware/config changes
before progressing to tag tests.

### Extended idle observation — heap plateau observed

The operator captured the current image without reset or NFC in
`terminal-idle-20261111-181809/logs/device-monitor-261111-181810.log` beneath
the private evidence directory. The file has 96,707 bytes, SHA-256
`14126e5bf930d98303c1e3fe677d471cb95983a0ebd7b69c29ceb7ce9fef341b`, and
covers host timestamps 18:18:10.578–18:26:54.291, approximately 8 min 44 s.

- 173 HTTP 200 responses and 173 `AMS state updated` records.
- All 173 transport results have verified_connection=1, tls_error=0 and zero
  certificate flags. No boot, clock/precheck/transport/begin failure, lookup,
  staging or NFC write/erase marker occurs.
- Precheck heap: first/last 167,044 B; 172 samples at that value and one at
  167,020 B. Connect-start heap: first/last 166,868 B; 172 samples at that value
  and one at 166,844 B. Both series recover from their 24 B dips with zero net
  loss across the window.
- Largest block is 110,580 B in every sampled stage. Every one-minute bucket
  ends at its initial steady-state free-heap value.

This confirms a plateau for the observed state-polling workload; the first
minute's continuing drop did not persist into this observation. It does not
identify the boot-time allocation cause or establish transient TLS minima or
leak freedom under other operations.

One apparent 15.107 s gap in host response timestamps contains a batch of
serial lines stamped at the same host time; their device `utc_epoch` values
advance through multiple polling cycles. Do not classify it as a terminal/TLS
outage from the monitor timestamps. Physical NFC acceptance remains pending,
but the idle-heap observation no longer blocks its next scoped case.

For any future retry or rollback, rerun live preconditions and drift checks;
do not rely on an earlier dry render after an intervening runtime change.
A reset processing counter after restart is not proof that real event handling
or the mapping issue passed acceptance.

The preceding mapping/204/idle work changed no MQTT event schema, runtime
contract or architectural responsibility. Architecture was unchanged at that
checkpoint; the later terminal HTTP-worker change is documented below.

### Positive NFC observation and restoration — 2026-11-11

The operator erased a spare tag, wrote the first slot's spool tag, scanned it
and assigned that spool to EXT through the DIRECT-bound terminal. Screenshots
at 18:39 show black PETG metadata on EXT in both EDOL and the slicer. The
operator subsequently confirmed that the previous state was already restored.
Read-only AMS evidence recorded spool 2 scanned for DIRECT at
2026-11-11T16:36:53.033Z. The slot-change INFO records lack printer identity;
they do not independently establish the dispatch alias for each command.

This is operator-observed positive tag write/read and EXT metadata behavior,
not physical filament motion, an AGENT-path test, a missing/unowned-spool 204
negative, or independent verification of every restored field. No additional
restoration command was sent by the assistant. SEALED spools initially appeared
as NEW; the operator changed them to ACTIVE and observed matching inventory on
the display. The discoverability improvement is recorded in Project TODO.

### Terminal responsiveness — HTTP-worker source fix

The operator reported missed taps (three or four attempts), delayed tag reads
and writes after the multi-tenant changes. Source tracing found synchronous
HTTP/TLS inside App::update before NFC and uiLoop. Earlier captured state
requests lasted approximately 1.4–1.5 seconds; initial clock readiness could
also wait. Those operations blocked the main loop. This identifies a concrete
source of stalls without excluding other touch/NFC latency causes.

Terminal source now uses one persistent FreeRTOS HTTP worker with an 8192-byte
stack and the existing two-slot capacity. Request fields are copied before
atomic publication; the worker publishes completion after closing/destroying
clients. Running slots cannot be reused. Touch, LVGL, NFC, response application,
settings and credential persistence stay on the main loop. CA/TLS checks,
transport selection and the polling interval are unchanged; no POST retry was
added. A new scan clears old local staging before waiting for its lookup,
preventing stale assignment while transport runs in the background.

Fresh offline verification passed:

- Native blocked-transport tests: 100 main-loop service calls proceed while
  transport waits; two-slot capacity, cancellation/ownership, task-creation
  failure/recovery and a single POST without replay.
- 46 staging scenarios across Home and secure modes, including 204/empty/error
  responses and no stale assignment during a blocked new lookup.
- Secure esp32dev build and image audit; RAM 112,848/327,680 B, flash
  1,238,885/1,310,720 B. The dynamic worker stack is additional runtime heap use.
- Source-only sec06 build after adapting its diagnostic harness to await async
  completion with a bounded deadline. This is not a new physical TLS PASS.
- Python runner read-only Sonar: zero findings. The connected analyzer does not
  support C++/PowerShell; those use compiler/tests and PowerShell AST/preflight.
  Terminal and backend diff whitespace checks passed.

Normal candidate artifacts are isolated in
`.pio/http-worker-validation-20261111-01/esp32dev` in the main terminal repo:

- BIN 1,245,456 B, SHA-256
  `54e232bd1d9c7abbbbdd629c6f61ea4894e33e005691bb7d7ef65284e4c33423`.
- ELF SHA-256
  `5c42db59a484f99ac38ff6c6a9919e56d815af87283a5dee9f62efcf414e8ff9`.
- Retained CA DER and normalized PEM identities remain as recorded above.

Prepared `tools/stage9/flash-terminal-http-worker-20261111.ps1`, separately
pinned to these artifacts. Its AST and local `-CheckOnly` preflight passed,
including persistent-data sector exclusion. The older staging-fix helper and
its accepted image are retained for rollback. No upload/device access, server
restart, printer write, credential or authority change occurred during this fix.

Physical acceptance is pending: verify image identity, authenticated state and
ACTIVE/CONSUMED, exercise ordinary view navigation while state polling runs,
then capture at least five idle minutes for heap/largest-block and resets.
Do not include lifecycle controls, NFC writes or printer metadata changes in
that first observation. The previous image's plateau does not establish the
new worker image's memory behavior. See terminal repository
`docs/http-worker-validation.md`. Architecture now records HTTP worker/main-loop
ownership; backend contracts and Stage 9 completion gates remain unchanged.

### HTTP-worker upload observation and 204 transport follow-up

The operator reported restored single-tap navigation and tag erase/write on the
worker image, but no visible response when presenting a tag. The supplied
`terminal-idle-20261111-190713/logs/device-monitor-261111-190713.log` is 13,875 B,
SHA-256 `efb18c0c03330437430c5cb5821a41a31380b855577b870bd28403b2aa8a9c02`.
It covers 19:07:14.950–19:09:24.983 (about 2 min 10 s), with NFC operations;
it is not a five-minute idle capture and starts without a boot/image identity.

There are 24 HTTP 200 responses, one HTTP 204 response, 25 verified TLS results
with zero error/verification flags, 22 state updates, three find requests and
three successful tag writes (including one erase operation). No boot, assignment
or transport-failure marker appears. Precheck heap first/last is 159,552/157,696 B,
minimum 157,264 B; sampled largest block is 69,620 B. This mixed, short workload
does not establish a steady idle plateau or transient handshake minima.

The tag reader did detect the erase sentinel 4294967295 at 19:08:02.900. Its
find response headers reported HTTP 204 at 19:08:05.829, but staging rejected
the result only at 19:09:10.912, a delay of 65.083 seconds. During that interval
tag writing remained usable while subsequent HTTP work waited. Two later
lookups of spool 3 returned HTTP 200 and loaded staging at 19:09:14.686 and
19:09:18.069. This confirms successful reads in the capture, rather than total
reader failure. The sentinel was rejected without local ID-zero staging, but
the completion delay is unacceptable. It is not a cross-tenant negative test.

The installed Arduino HTTPClient source calls writeToStream for getString when
Content-Length is unknown. Its identity-transfer loop can wait while the socket
remains connected without available bytes. Our transport called getString for
all 2xx statuses, including bodyless 204. That source path explains the observed
worker hold; a packet capture was not taken to independently identify which
peer closed the connection after 65 seconds.

A new native regression first failed at fakeNoContentBodyReads == 0. The minimal
transport fix now skips getString for 204, preserves successful status with an
empty response, and proceeds to normal close/completion publication. The test
checks both GET and POST, release/reuse of the two slots and a subsequent HTTP
200 response. NFC format, read/write hardware code, enrollment, CA, server
configuration and printer metadata are unchanged. This is a transport correction
within the documented worker ownership, with no new architecture responsibility.

Fresh verification after that fix passed both HTTP CA variants, the new 204
no-body/following-request control, worker ownership tests and all 46 staging
cases. Source-only esp32dev and sec06 builds succeeded in the new isolated
`.pio/http-204-validation-20261111-01` output directory. Secure image audit passed.

- Normal BIN: 1,245,472 B, SHA-256
  `c47afe5ba77f19841b0ac9f923afd21db37594330cff33d5f7e8808883a638a0`.
- Normal ELF: SHA-256
  `7b23ae3893b834a0d0e6ecb7a385dac2fa40563b882032ccc2a8a1272c6fd308`.
- The previous worker candidate BIN/ELF hashes remain unchanged. CA is retained.
- Prepared `tools/stage9/flash-terminal-http-204-20261111.ps1`; PowerShell AST
  and local CheckOnly artifact/CA/layout preflight passed. Older helpers remain
  pinned to their own preserved images. No device/server operation was performed.

The operator's screen context at presentation remains to be clarified: staging
is rendered on the main AMS screen, not in spool details. After the scoped
no-erase upload, first check the already written tag on the main screen without
assignment, and capture a separate five-minute idle window. Physical latency
after 204 and memory behavior of this final image remain unexecuted checks.

### Silent lookup and repeated scan UX — 2026-11-11

The next operator capture is
`terminal-idle-20261111-202623/logs/device-monitor-261111-202623.log`, 35,579 B,
SHA-256 `b49ea1ad91dc14fd53c2513064dcc7eebc197eed8c37dcbbb26549cb828b808c`.
It covers about 2 min 25 s, begins with a partial/garbled serial line and ends
during a request. No boot/image identity is available in this capture.

There are 62 HTTP 200/verified TLS results and no HTTP 204 response, 17 find
requests with 17 successful staging completions, and five successful tag writes.
Spools 3, 2 and 1 are observed. Lookup enqueue-to-staging latency ranges from
1.734 to 3.641 s (median 3.182 s); these are not measured LVGL render times.
Seven repeated searches start 76–97 ms after the previous result loads. No
transport-failure, assignment or boot marker occurs. The mixed NFC workload
does not constitute an idle heap acceptance window or a new physical 204 test.

The operator clarified that reading appears normal: the silent 2–3 s wait
invited repeated presentations. This supersedes the initial suspicion of a
reader failure. Source tracing confirms that the one-entry NFC mailbox can
retain a repeat during an in-flight lookup. Once its first response loads,
that repeat launches a second lookup and clears the just-loaded local spool.
The main view also refreshed only on its one-second timer and had no pending
lookup feedback. Do not attribute the observed UX to RF/presence failures.

The next source change therefore stays above the hardware layer:

- Staging exposes resolving state. During an active lookup it discards only
  pending scans of that same requested ID, including at the completion boundary.
  A different pending tag remains for the next lookup. Later deliberate rescans
  still query the server; no inventory cache or ownership bypass was added.
- Main-screen pending state reads `Tag scanned` / `Finding spool...`, with
  assignment unavailable until a valid response. The application watches the
  existing staging version and readies the existing LVGL timer on transitions.
  It does not increase periodic card-refresh frequency or move LVGL to a worker.
- PN532 polling, tag encoding, TLS, credentials and printer commands are unchanged.

A native regression first failed because the duplicate remained pending. After
the fix, all 50 staging cases passed, including repeats during blocked transport,
the response-consumption boundary, retention of a different ID, no assignment
while pending, and the previous 204/error controls. HTTP worker/CA-present and
CA-absent controls also passed. The real esp32dev build and secure image audit
passed in `.pio/nfc-feedback-validation-20261111-01/esp32dev`:

- BIN 1,245,680 B, SHA-256
  `df82d5af960b0ceeb5ff216375fe2e29e4bdaa15fd254a39708788da989b3a99`.
- ELF SHA-256
  `c42230df99d97011010509661252455ba79a211459bf3bf217ec6bce36208ca0`.
- The previous HTTP-204 image and retained CA remain unchanged.
- `tools/stage9/flash-terminal-nfc-feedback-20261111.ps1` is separately pinned;
  PowerShell AST and local CheckOnly artifact/CA/layout preflight passed.

No Python source changed in this follow-up. C++ and PowerShell are unsupported
by the connected Sonar snippet analyzer; native/ESP32 compilation and script
AST/preflight are the available checks. Architecture records the new pending
view and coalescing flow. No upload, device access, server restart or printer
metadata write was performed during implementation.

Physical validation remains operator-led: upload without erasure/re-enrollment,
present an already written owned tag on the main AMS screen, check immediate
pending feedback, then re-present that same tag while pending and verify one
result remains visible. A different owned tag should still be accepted for
the next lookup. Do not assign a slot or rewrite tags for this check. Actual
HTTPS lookup latency is unchanged; visual/physical acceptance and a separate
idle-memory window are still required.

### Final operator acceptance: NFC functional topic CLOSED

On 2026-11-11 the operator uploaded the pinned NFC-feedback image, confirmed
immediate pending feedback, harmless repeated scans and successful lookup of
a different tag, and accepted all exercised NFC functional scenarios. The
operator explicitly closed this topic: do not run another NFC/firmware cycle
unless a new reproducible issue is reported. Terminal changes are committed as
`276a7820591614ce3115356de1efb3a03ff63cf3` (EDOL-048-80).

Final capture: `terminal-idle-20261111-204054/logs/device-monitor-261111-204054.log`,
18,997 B, SHA-256
`8efa31809d78f38e879b607322bbde34a59faebdc2201924cd1f12544b7f6e17`.
It spans 20:40:55.159–20:42:19.586, starts after boot and ends during a request.
Seven finds for spools 1/2/3 each loaded staging successfully. There are 32 HTTP
200 responses, 33 verified TLS results, no HTTP 204 response, and two successful
tag writes (erase/write). One state request returned transport -11, then state
polling and find operations continued. Its cause remains unclassified; record
for recurrence without reopening terminal work now. No assignment/boot marker
or UI-render/duplicate-suppression marker occurs. The operator confirms the
visual/repeat behavior; the log alone cannot prove it or identify the ELF.

This closes the functional read/write/erase, owned lookup, immediate-feedback
and repeated/different-tag issues. Earlier positive EXT metadata behavior and
operator restoration remain recorded above. It does not close combined Stage 9,
formal security negatives, full lifecycle, independent exact-field restoration
or long idle/transient memory gates. No extra test is requested now. Notify,
using a dedicated test bot and an agreed print/message scope, is the next topic.
