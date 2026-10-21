# Stage 8 Terminal Acceptance Record

Reconciled on 2026-10-21 from the 2026-10-12 operator smoke report and recorded
developer execution results. Historical test results below were not rerun for
this documentation update.

**Status: FUNCTIONAL SMOKE PASSED — SEC-06 FINAL SECURITY ACCEPTANCE PENDING.**

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
| SEC-06 HTTPS fail-closed | BLOCKED | Prior physical test lacks explicit TLS verification failure and measured no-downgrade evidence; see below. Terminal unavailable during 2026-10-21 reconciliation. |
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

## SEC-06 evidence correction and minimal completion procedure

On 2026-10-12 an operator-authorized CA-enabled image was flashed to confirmed
COM16. The endpoint at 192.168.0.110:18443 used a different CA. The terminal
remained at Loading AMS, and TCP sessions were observed. No explicit ESP32
certificate-verification error or TLS alert was captured. The debug Caddy process
failed to bind because another listener already owned the port; its output
cannot establish successful request counts. Absence of traffic to HTTP/18088
was not measured. Consequently the previous conversational SEC-06 PASS and
Stage 8 ACCEPTED declarations are superseded by this record.

Restoration succeeded: normal firmware was flashed, the mock stopped, the
temporary Caddy root removed from CurrentUser trust, and the operator restored
explicit HTTP/18088 and observed active slots 1 and 3. Preserve that enrollment.

To close only the missing proof:

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

Once SEC-06 has the execution evidence above, record ACCEPTED for this stated
scope and commit the documentation. Until then, Stage 9 may perform discovery
and planning only; its implementation prerequisite remains open. See the
[next-stage prompt](stage9-discovery-handoff-prompt.md).
