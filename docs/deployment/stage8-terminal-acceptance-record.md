# Stage 8 Terminal Acceptance Record

Closed on 2026-10-21 using the 2026-10-12 operator smoke report, recorded backend
test results and a new real-ESP32 SEC-06 regression. Historical backend results
were not rerun for this firmware-only delta.

**Status: ACCEPTED — functional and security acceptance completed for the
secure/disposable scope described here.**

## Baselines and scope

- EDOL branch `EDOL-048`: `73969225f0e5872c42b92bcd946374518b7d10f4`,
  including security regressions; previously confirmed on `origin/EDOL-048`.
- Physical functional smoke baseline:
  `972e23be06db2ad82b3105663466df04ac0a1146`.
- Firmware functional smoke baseline on `main`:
  `33b228680087ca0b99d167821ed7b126dc9ad5c3`.
- Firmware opt-in CA build hook:
  `fddb88107670e5d9cbdda155777a9bcc3d99893d`; local commit, push not established
  by this record.
- Final firmware fix/regression: `0749cecdf9120dd5f120804bead5cdd3370739a7`,
  local `main` commit. This corrects the old CA hook and adds an opt-in hardware
  verification environment; ordinary enrollment and operational code is unchanged.

Evidence covers the isolated secure multi-tenant deployment and real ESP32
using a deterministic AMS simulator. It does not accept production deployment,
Home compatibility, real printer connectivity, or commands to physical printers.
No hardware security provisioning is required for enrollment.

## Completed physical smoke

The operator confirmed Hub owner pairing, physical enrollment over explicit
trusted-LAN HTTP, ACTIVE/CONSUMED persistence, reboot credential reuse,
authenticated polling and advancing last-connection time. The physical terminal
rendered the simulator, encoded/read NFC tags, resolved/staged spools, assigned
a simulated slot, and refreshed state. Revocation, replacement after revocation,
rotation and re-enrollment, server factory-reset lifecycle and re-enrollment,
pairing expiry, and regeneration were exercised successfully.

The fixture is tenant A `da4b2480-4774-4eb3-bc88-c625497ce5d1`, synthetic printer
`01a12a14-289f-7843-8036-7a450c9567f5`. It is disabled in Core and uses dummy
connectivity. AMS exposes its simulator behind real terminal authentication and
tenant/printer binding, with spools 800001/800002 in A1/A3, 23.5 degrees C,
humidity 35. Assignments mutate simulator memory only.

## Security matrix

| ID | Status | Recorded evidence and limits |
| --- | --- | --- |
| SEC-01 consumed-code replay | PASS | PostgreSQL integration tests exercise single consume and concurrent consumption; no second activation. |
| SEC-02 expired-code denial | PASS | PostgreSQL regression leaves terminal PENDING and marks pairing EXPIRED; physical regeneration also passed. |
| SEC-03 invalid-attempt limiting | PASS | Controller/real limiter regression: five 401 responses, sixth 429 without a sixth enrollment call. |
| SEC-04 revoked credential | PASS | PostgreSQL authentication regression rejects REVOKED; controller regression maps invalid credential to 401 with WWW-Authenticate. This is not a separately captured HTTP test of every retirement cause. |
| SEC-05 cross-tenant management | PASS | Tenant B user stage7-user-b requested tenant A printer status at 2026-10-12 18:51:45 EEST: live 404 without metadata. Hub regression additionally verifies no downstream AMS management call. Do not repeat unchanged logic. |
| SEC-06 HTTPS fail-closed | PASS | 2026-10-21 real ESP32 production transport: trusted HTTPS 200 before/after three denials, explicit -9984 certificate error, zero plaintext on TLS ports, explicit HTTP 200. |
| SEC-07 simulator isolation | PASS | Recorded automated profile/enable-flag and exact tenant/printer binding checks; simulator has no Core/Hub/MQTT command dependencies. |

## Executed automated checks

Recorded on the security-test revision above:

```powershell
mvn -B -pl edol-ams -am '-Dtest=TerminalEnrollmentControllerTest,TerminalOperationalControllerTest,TerminalEnrollmentRateLimitHttpContractTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
& .\.agents\skills\edol-remote-docker-verification\scripts\with-remote-docker.ps1 mvn -B -pl edol-ams -am '-Dtest=AmsTerminalMigrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

First command: 6 passed, no failures/errors/skips. Second: 9 passed, no
failures/errors/skips; actual remote Testcontainers PostgreSQL 17-alpine and
Ryuk, with Flyway V1-V3. Relevant tests are `TerminalEnrollmentControllerTest`,
`TerminalOperationalControllerTest`, `TerminalEnrollmentRateLimitHttpContractTest`,
and `AmsTerminalMigrationTest`. These are targeted checks, not a fresh full-reactor
acceptance claim.

Firmware `platformio.exe run` succeeded without upload: normal image flash 94.1%,
CA-enabled isolated build 94.3%, RAM 34.4%. A PEM marker was verified in the secure
ELF. Explicit secure build without CA failed before image generation. Build
results and host-side curl certificate rejection do not prove ESP32 TLS behavior.

## SEC-06 evidence correction and completed regression

On 2026-10-12 an operator-authorized CA-enabled image was flashed to confirmed
COM16. The endpoint at 192.168.0.110:18443 used a different CA. The terminal
remained at Loading AMS, and TCP sessions were observed. No explicit ESP32
certificate-verification error or TLS alert was captured. The debug Caddy process
failed to bind because another listener already owned the port; its output
cannot establish successful request counts. Absence of traffic to HTTP/18088
was not measured. Consequently the previous conversational SEC-06 PASS and
Stage 8 ACCEPTED declarations lacked sufficient evidence at that time; the
measured 2026-10-21 results below supersede them.

Restoration succeeded: normal firmware was flashed, the mock stopped, the
temporary Caddy root removed from CurrentUser trust, and the operator restored
explicit HTTP/18088 and observed active slots 1 and 3. Preserve that enrollment.

### Final execution, 2026-10-21

The operator reconnected the same COM16 ESP32. The explicit `sec06` PlatformIO
environment compiled the unchanged production HttpService and TerminalApi with
real Arduino ESP32 WiFiClientSecure. It mounted LittleFS without formatting,
read existing Wi-Fi settings, changed targets/transport in RAM only, never saved
settings and never opened the enrolled credential store. Only synthetic,
credential-free requests reached the local fixture; no AMS or printer APIs.
The test clock was initialized from build time to exclude NTP failure.

The first execution discovered malformed PEM in the old CA command-line hook:
Windows/SCons converted escaped newlines into /n. The old image's PEM marker
did not prove it was usable. The corrected hook emits a C++ header under ignored
.pio build output. The passing run used that fix and independently generated,
short-lived trusted and untrusted certificates with matching host names. No test
CA was installed in Windows trust or added to the working HTTP image.

Final captured device output:

```text
SEC06 PASS trusted-HTTPS-positive-control
SEC06 result code=-1 success=0 https=1  (three production requests)
SEC06 TLS connected=0 error=-9984 reason=X509 - Certificate verification failed, e.g. CRL, CA or signature check failed
SEC06 PASS untrusted-certificate-verification-error
SEC06 PASS HTTPS-still-selected-after-failures
SEC06 PASS explicit-HTTP-positive-control
SEC06 COMPLETE PASS
```

The precise certificate-error diagnostic is a separate real WiFiClientSecure
connection with the same CA. The three denied requests and HTTPS controls use
production HttpService; no mocked TLS methods are involved. HTTPS remains
selected during failures; HTTP is deliberately selected for the last control.

| Port | Purpose | TLS HTTP requests | TLS failures | Plaintext arrivals | Other errors |
| --- | --- | ---: | ---: | ---: | ---: |
| 18443 | Trusted certificate, before and after denials | 2 | 0 | 0 | 0 |
| 18444 | Untrusted certificate | 0 | 3 | 0 | 1 |
| 18445 | Explicit HTTP control | 0 | 0 | 1 | 0 |

The diagnostic connection at 18444 closed with a TCP reset, counted as the
additional error rather than a server TLS alert. The device explicitly recorded
-9984. The fixture sniffs/counts plaintext on its TLS ports too; responses are
read only after a successful handshake. It logs no headers/payloads, never
proxies to AMS and issues no credential. This closes SEC-06A/B/C.

Reproduction is in firmware docs/sec06-regression.md. Executed from that repo:

```powershell
$python = "$env:USERPROFILE\.platformio\penv\Scripts\python.exe"
$pio = "$env:USERPROFILE\.platformio\penv\Scripts\platformio.exe"
& $python scripts/sec06_endpoint.py prepare --directory C:\Temp\edol-sec06-20261021-final --openssl 'C:\Program Files\Git\usr\bin\openssl.exe'
& $python scripts/sec06_endpoint.py serve --directory C:\Temp\edol-sec06-20261021-final --duration 1200
# In another shell, after identifying/authorizing COM16:
$env:EDOL_TERMINAL_SECURE_BUILD = '1'
$env:EDOL_AMS_ROOT_CA_FILE = 'C:\Temp\edol-sec06-20261021-final\trusted.pem'
& $pio run -e sec06 --target upload --upload-port COM16
& $python scripts/sec06_capture.py --port COM16
```

Test build/upload and serial capture passed: RAM 14.4%, flash 70.5%. Normal
default build/upload passed: RAM 34.4%, flash 94.1%. Python syntax, counter
assertions and git diff --check passed. Read-only Sonar snippet analysis of all
four modified/added Python scripts reported zero issues. The available tool
does not support C++, so the test entry point was reviewed and compiled without
claiming a C++ Sonar pass. Backend code did not change.

### Restoration and current runtime limit

Normal esp32dev firmware was restored to COM16 with verified flash hashes and
without erasing NVS/LittleFS or persisting target changes. All three owned
listeners were stopped and confirmed closed. The current AMS port 18088 is also
closed, so normal firmware reports network error -1. Fresh live state recovery
is not claimed on 2026-10-21; it requires starting the existing disposable AMS.
This does not invalidate the earlier physical functional PASS or the measured
SEC-06 result. No new pairing or infrastructure recreation is required.

### Procedure retained for future TLS changes

1. Identify the connected ESP32 anew. On 2026-10-21 COM16 was absent and only
   another CH340 on COM9 was listed; do not flash a guessed port.
2. Use an isolated on-device test build running the production HttpService and
   ESP32 WiFiClientSecure, with a public test CA and a valid clock. Use synthetic
   request data only; never load, print, or erase the enrolled credential. Avoid
   native stubs that bypass the real TLS stack. Upload requires authorization.
3. Start one bounded local endpoint with three measured modes: valid certificate
   as a positive control, invalid/untrusted certificate, and explicit HTTP.
   Count TLS connections, handshake failures, and plaintext HTTP arrivals on the
   same test port. A second observer is needed if any code can target a different
   HTTP port. Do not log headers, payloads, or credentials; do not proxy to AMS.
4. Require a successful HTTPS positive control and capture an explicit ESP32
   certificate-verification error or matching TLS alert for the negative case.
   Require request failure and zero plaintext requests after it, with HTTPS
   selection unchanged over multiple attempts. Explicit HTTP must succeed only
   after deliberate selection. A timeout or Loading screen is insufficient.
5. Restore normal firmware without erasing NVS/LittleFS; stop owned endpoints
   and remove only trust entries created by this test. Confirm existing
   authentication and AMS state recovery. Record revision, counters, and redacted
   error evidence here, then change SEC-06 and the stage status if all pass.

No additional enrollment, real printer, custom CA in the working HTTP
deployment, eFuse change, or Stage 7 resource modification is needed.

## Follow-up work and closure rule

The prioritized UX backlog is tracked in [Project TODO](../ai-workflow.md#project-todo).
Unknown-spool feedback, session return context, automatic pairing refresh, and
action simplification do not block this security gate. Physical immediate
authorization-loss feedback remains unconfirmed; server rejection is tested.

SEC-01 through SEC-07 now have PASS evidence for this scope. Stage 9 may proceed
to discovery and a reviewable plan; implementation requires its own approval.
See the [next-stage prompt](stage9-discovery-handoff-prompt.md).
