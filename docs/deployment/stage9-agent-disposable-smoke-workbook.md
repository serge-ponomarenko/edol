# Stage 9 Agent Disposable Hardware Smoke Workbook

## Purpose and current gate

This workbook prepares a supervised physical Agent-to-printer-to-disposable-Core
test. The operator authorized real Agent firmware and a real printer for this
test on 2026-10-21. Execute one operator action at a time, inspect its result,
and stop on an unexpected result. Preparation is not execution or acceptance.
No hardware, listener, realm, database or printer was changed while writing it.

The implemented gate is **9.3b HTTPS enrollment and uploads**. Secure EDOL MQTT
currently fails closed in firmware. It cannot carry telemetry, receive remote
commands, or establish broker ACL/session-revocation acceptance. The lab console
below invokes production upload services directly; label its evidence **USB
trigger → real printer read → real HTTPS upload**, never MQTT end-to-end PASS.
Full Stage 9 remains open even if every executable case here passes.

Home remains supported without pairing/CA prerequisites. Keep its existing open
setup AP and connection procedure unchanged. Local printer certificate checks
remain disabled in MQTT/FTPS/camera clients. Neither local trust limitation is
evidence of server-authenticated printer transport. Login/registration UX is
outside this gate. Reuse accepted Stage 8 evidence; do not flash the AMS terminal.

## Execution worksheet — complete before starting

Use an ignored, access-controlled operator directory outside Git for secrets,
certificates, logs and binaries. Put only redacted results in the final record.
Do not use transcripts, HTTP wire/debug logging, browser HAR exports, header
dumps, packet payload capture, or screenshots containing setup/code forms.
Do not paste codes, Wi-Fi/printer credentials, tokens, Authorization headers or
issued secrets into chat. No secret needs to be extracted from Agent NVS.

| Field | Actual value / evidence required |
| --- | --- |
| Run ID and operator | `stage9-agent-<UTC timestamp>`; operator name |
| Backend commit / clean status | `git rev-parse HEAD`; `git status --short` |
| Firmware commit / branch | Sanitized `stage9-agent-pairing`; full SHA and status |
| Firmware baseline | Published sanitized `14da63f360d4a288c7207340dc60cd9e78ce7907` |
| Firmware binary SHA256 | Lab and restoration image hashes, public CA SHA256 |
| Exact Agent board and USB port | Identify the Agent physically; **do not assume COM16** |
| Printer / model / camera support | Operator-confirmed physical device; idle state |
| Existing harmless model | Plain ASCII `.3mf` basename, size and local SHA256; no upload to printer needed |
| Docker Engine and isolated PG container | Existing approved Engine; new run-owned container/volume names or existing **dedicated Stage 9** cluster |
| Disposable database | Exact name; DB/cluster identity; must not be dev/prod/Stage 7 |
| Disposable IdP realm and clients | Issuer, browser client, service client, audiences and registered callback; identifiers only |
| Tenant A/B and users | Two isolated owners plus non-owner A; printer A/B UUIDs |
| Core/Hub/TLS listener addresses | Actual reachable values; owner/PID/container and ports |
| Public TLS DNS name / CA | SAN match, chain issuer, validity interval, private material stored only locally |
| Core working directory | New run-owned absolute directory; holds relative `models/` |
| Snapshot directory / logs | Explicit run-owned absolute paths |
| Restoration plan | Previously working firmware image, target environment and settings; no erase |
| Observation clock | UTC on server/operator/Agent; monotonic start/end offsets if clocks differ |
| Result | NOT RUN initially; never prefill PASS or zero traffic |

Candidate ports `19080` (Core loopback), `19090` (Hub loopback), `19443`
(device-facing TLS) are suggestions, not reserved/observed ports. Inventory
listeners and existing disposable resources read-only first. On Windows use
`Get-NetTCPConnection -State Listen`; on the approved Linux host use `ss -ltn`.
If a proposed resource belongs to Stage 5–8 or another session, leave it alone.
Record exact replacement names/ports before creating anything. Do not recreate,
stop, migrate or clean up accepted infrastructure. A fresh PG container on the
existing test Docker Engine is preferable to sharing cluster-wide roles.

## Prerequisites and service preparation

### 1. Verify source and isolation

Run repository checks before flashing. Expected source checks are a full
`mvn -B clean verify` through the remote-Docker skill, zero skipped migration
tests, five native firmware executables, three Python cases, Home/secure/lab
ESP32 builds and `git diff --check`. Tests do not establish physical TLS.
Use the sanitized firmware worktree, never publish the original secret-bearing
repository history. No push is part of this workbook.

The remote Docker verification wrapper manages a temporary tunnel for
Testcontainers. It is **not** a launcher for persistent smoke applications.
Likewise, the local `dev` runtime wrapper is not disposable: do not use it here.
Inventory existing launch mechanisms before choosing run-owned containers or
processes. Write their exact commands and resource ownership into the worksheet.
Infrastructure-specific addresses/secrets are deliberately not invented here;
if an isolated prerequisite is unavailable, mark PREP BLOCKED and resolve that
single prerequisite before connecting hardware.

### 2. Prepare PostgreSQL and V10 function-owner prerequisites

Use a fresh isolated cluster, not merely a new database in a shared Stage 7
cluster: PostgreSQL roles are cluster-wide. Pin and record the approved PG
image/version. Bind its port only to the approved test host/tunnel. Keep its
volume and passwords under the run's ownership; do not delete data at the end
without separate approval.

Use [Stage 5 bootstrap SQL](stage5-disposable-postgres-bootstrap.sql) on that
fresh disposable cluster/database, with its exact `stage5_smoke_database` guard.
It deliberately rejects pre-existing roles. Do not weaken that guard or grant
CREATEROLE/BYPASSRLS to runtime users. Inject login passwords through the approved
secret mechanism, not SQL history or shell arguments.

Before Core starts, the disposable cluster administrator must additionally
create the V10 function owner and grant it only to the schema owner. Example
for an administrator's `psql` session, after setting the exact database variable:

```sql
\set ON_ERROR_STOP on
-- Set this variable to the exact worksheet database name before this block.
SELECT current_database() = :'stage9_smoke_database' AS stage9_database_matches \gset
\if :stage9_database_matches
BEGIN;
CREATE ROLE core_agent_authenticator NOLOGIN NOSUPERUSER NOCREATEROLE
    NOCREATEDB NOINHERIT NOBYPASSRLS;
GRANT core_agent_authenticator TO core_schema_owner;
COMMIT;
\else
\echo 'Wrong database: Stage 9 bootstrap refused'
\quit 3
\endif
```

This block is only for a fresh run where that role does not yet exist. On resume,
inspect role attributes/membership instead of repeating CREATE or replacing it.
Core Flyway logs in separately and uses `SET ROLE core_schema_owner`; runtime
must remain a non-superuser with no membership in `core_agent_authenticator`.
V10 transfers only its two fixed SECURITY DEFINER functions to that role and
revokes schema CREATE afterwards. Never run V10 against dev/prod in this gate.

After startup verify successful Core V10 and Hub Flyway histories, forced RLS on
`core.agents`, function ownership, no PUBLIC EXECUTE, and runtime non-membership.
Read only IDs/state/timestamps/key versions; never select digest or secret data
for screenshots. A migration failure stops preparation; do not repair history,
edit an applied migration or escalate runtime privileges.

### 3. Prepare disposable identity and browser access

Use a dedicated Stage 9 realm/client namespace on existing disposable identity
infrastructure. Do not change accepted Stage 7 clients, scopes or users. If no
dedicated namespace exists, record the exact proposed isolated resources as the
next setup step; do not modify a shared realm as a workaround.

Provide browser OIDC login with its **actual** registered callback, two tenants,
owner A, non-owner A and owner B. Seed memberships through the established
disposable tenant/bootstrap procedure, not production user data. Provide the
trusted Hub service client, Core audience and `tenant.context`. For this run,
authorize **and request** `core.agent.manage` in addition to the service client's
existing scopes. The default Hub registration scope list does not include it;
enabling feature flags alone will produce a management failure.

Override this property in the disposable Hub configuration, retaining its
existing required scopes:

```properties
spring.security.oauth2.client.registration.edol-hub-service.scope=tenant.context,core.printer.read,core.printer.manage,core.state.read,core.command.execute,core.media.read,ams.terminal.manage,core.agent.manage
```

Check audience, azp/trusted-client and scope presence without printing tokens.
Use no password-grant shortcut or shared production service secret. Do not
request `private_key_jwt` or a new per-client MQTT certificate merely for this
HTTPS gate; those Stage 9 deployment decisions remain explicit later work.

### 4. Configure the isolated Core and Hub

Reuse the **configuration** of profiles `secure-multi-tenant,stage5-smoke`, not
accepted running services. Supply every Stage 5 environment variable listed in
the [Stage 5 runbook](stage5-disposable-core-hub-smoke-runbook.md), pointing to
this run's DB, roles, issuer and ports. Set `EDOL_DEPLOYMENT_MODE=secure-multi-tenant`
explicitly as well; profile selection alone is not deployment-mode selection.
Catalog must use the same disposable
database through its separate restricted catalog login. Supply secure-profile
secret placeholders through the approved local environment/secret mechanism.
Never start with `dev`, Home, an omitted JDBC URL or a default database fallback.

Use per-process external configuration files with restricted access. Core adds:

```properties
server.address=127.0.0.1
server.port=19080
server.forward-headers-strategy=framework
edol-core.agent.enabled=true
edol-core.agent.key-version=1
edol-core.agent.pairing-keys[1]=${STAGE9_AGENT_PAIRING_KEY}
edol-core.agent.credential-keys[1]=${STAGE9_AGENT_CREDENTIAL_KEY}
edol-core.runtime.mqtt-enabled=false
edol-core.runtime.integration-mqtt-enabled=false
edol-core.runtime.printer-runtime-enabled=false
camera.store-snapshots=false
camera.snapshot-dir=${STAGE9_SNAPSHOT_DIR}
logging.file.name=${STAGE9_CORE_LOG}
logging.level.org.spon.edolcore.controller.SecureAgentController=DEBUG
```

Generate two independent random Base64 keys with at least 32 decoded bytes into
the operator secret mechanism; do not echo or commit them. Retain them across
service restarts in this run. Do not enable global Spring Security, HTTP wire,
environment or SQL parameter debug logging.

Hub adds its actual port, `server.address=127.0.0.1`, run-owned log path,
`edol-hub.agent.enabled=true`, `edol-hub.runtime.mqtt-enabled=false`, the scope
override above and `edol-hub.session.maximum-age=PT30M`. Its Core URL is the
loopback HTTP backend, never a working installation. Use the same established
browser tunnel/callback method as the disposable BFF tests. Do not expose Hub
or its backend listener to the printer network to solve a callback mismatch.

Start each built JAR as a separate owned process, from separate run-owned
working directories, with explicit absolute paths and the two profiles:

```text
java -jar <absolute-built-jar> --spring.profiles.active=secure-multi-tenant,stage5-smoke --spring.config.additional-location=file:<absolute-local-properties>
```

Resolve placeholders before launch; angle-bracket values are not runnable
commands. On Windows a background launch must use `Start-Process -WindowStyle
Hidden` and record its PID. Give readiness a bounded deadline (for example 90s),
then stop on failure. Confirm startup reports all three runtime switches off,
Hub MQTT off, correct disposable DB/schema, and no printer/broker connections.
Logs must be separate from the accepted Stage 5 smoke log filenames.

### 5. Establish the device-facing TLS endpoint

Prefer a run-owned listener on the existing approved TLS proxy. It must reach
only this loopback Core. Core's forwarded-header trust is acceptable here only
because no untrusted client can reach its raw HTTP port. Never enable forwarded
header trust on an externally reachable backend. The proxy must overwrite
forwarded headers rather than append/trust client input. No HTTP redirect
fallback listener is needed. Agent setup takes a DNS name/IPv4 address, no URL
scheme; certificate SAN must match that exact host.

Example location policy for an existing nginx test listener; substitute the
actual run values and certificate paths before syntax validation/reload:

```nginx
# Define in http{}; URI excludes query strings, bodies and Authorization.
log_format stage9_agent '$time_iso8601 $request_method $uri $status $request_length';
server {
    listen 19443 ssl;
    server_name <agent-test-dns>;
    ssl_certificate <run-owned-server-chain.pem>;
    ssl_certificate_key <run-owned-private-key.pem>;
    ssl_protocols TLSv1.2 TLSv1.3;
    access_log <run-owned-access.log> stage9_agent;
    error_log <run-owned-tls-error.log> warn;
    client_max_body_size 260m;
    location /api/agent/v1/ {
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header X-Forwarded-For "";
        proxy_set_header Forwarded "";
        proxy_request_buffering off;
        proxy_read_timeout 60s;
        proxy_pass http://127.0.0.1:19080;
    }
    location / { return 404; }
}
```

Use a run-owned existing valid certificate/CA or prepare a disposable CA and
SAN-correct server certificate through the approved certificate procedure.
Keep private keys on the server/operator storage; the firmware receives **only
the public CA**. Record cert issuer/SAN/validity/fingerprints, listener ownership
and config validation result. A successful operator HTTPS probe is readiness
only, not an ESP32 acceptance result. Do not use `-k`, `setInsecure()` or a proxy
that terminates TLS before a shared untrusted HTTP network.

Before hardware, verify a loopback HTTP `/api/agent/v1/enroll` request without
forwarding headers returns 403. From every untrusted surface verify the raw
backend is unreachable, including attempts with spoofed forwarding headers.
If it is reachable, **STOP** and fix network isolation; framework forwarding
on loopback is deliberately trusted and cannot defend an exposed backend.
TLS proxy should return the expected app denial for a synthetic
invalid code without logging its request body. Validate that malformed plaintext
on the TLS port is rejected and that the observer can count it safely.

## Firmware build and operator boundary

Use the isolated sanitized firmware worktree. Build first, without a device:

```powershell
$env:EDOL_AGENT_ROOT_CA_FILE = '<absolute-public-CA-path>'
& 'C:\Users\Ponomarenko\.platformio\penv\Scripts\platformio.exe' run -e lolin_d32_secure_smoke
Get-FileHash .pio\build\lolin_d32_secure_smoke\firmware.bin -Algorithm SHA256
```

Preserve/restore any previous environment value afterwards. Keep the resulting
firmware binary, normal restoration binary and public CA hash with this run.
The lab build enables **only** bounded USB commands at 115200 baud with LF:

| Command | Actual action |
| --- | --- |
| `STATUS` | Reports Wi-Fi/setup flags; no identity/secret output |
| `SNAPSHOT` | Reads one real printer camera frame and invokes production HTTPS upload |
| `MODEL sample.3mf` | Reads that existing file over printer FTPS and invokes production HTTPS upload |
| `SETUP` | Disconnects MQTT and opens the unchanged setup portal; preserves settings and identity |

The lab build is distinct from normal `lolin_d32_secure`; Home remains the default
`lolin_d32`. Do not set the smoke macro on Home. USB commands are not authentication
or a production command interface. The console never sends print/motion commands
or publishes an EDOL MQTT command. Existing Bambu MQTT connection/subscription
and local FTPS/camera authentication still occur in real firmware; record them
as real printer traffic, not a network-free test. Model and camera data leave
the printer and are stored/processed only in this disposable Core.

**First physical step:** operator identifies and confirms the exact Agent USB
port and printer while the printer is idle. Only then may the operator flash:

```powershell
# Replace with the physically confirmed Agent port; never reuse an AMS guess.
& 'C:\Users\Ponomarenko\.platformio\penv\Scripts\platformio.exe' run -e lolin_d32_secure_smoke -t upload --upload-port '<confirmed-Agent-port>'
```

No erase/erase-flash, NVS extraction, eFuse or factory reset. Keep each boot up
for at least 15 seconds before another reset; five unsuccessful short boots can
trigger the existing factory-reset detector. Open the serial monitor only after
port confirmation. Avoid terminal input echo/history for sensitive setup data;
codes go into the existing setup form, never USB commands. Once paired, `SETUP`
is the lab recovery path for replacement; ordinary secure firmware does not yet
provide equivalent user-facing re-pair controls. Do not claim that UX complete.

## Ordered manual cases

Before every case record UTC start/end, expected action count, endpoint and
baseline counters/artifact hashes. Close a bounded observation window after the
case (at least the device's 10s connection/read timeouts plus 5s). For large
uploads record a longer agreed deadline rather than assuming a 15s transfer cap.
There is no overall upload deadline in current firmware. An unresponsive camera
or FTPS stage must not be diagnosed as Core authentication failure.

| ID | One-step action, then observe | Expected result and required evidence |
| --- | --- | --- |
| PREP-01 | Owner A creates a disposable enabled AGENT printer through Hub; then owner B creates its own printer | UUIDs/tenants distinct; no Core direct printer connection; no secrets in read DTOs |
| AUTH-01 | Non-owner A opens A's agent management page | Denied; no pairing created |
| AUTH-02 | Owner B opens A's agent page | Denied/404; A's state unchanged |
| PAIR-01 | Owner A issues a code on A's page | PENDING, expiry about 5min, no-store; code only in POST response, not status GET/flash/log |
| PAIR-02 | Owner A requests another ordinary pairing while PENDING | 409; no extra live row; original code still pending |
| ENROLL-01 | On the Agent's unchanged AP, submit Wi-Fi/printer settings, exact TLS host/port and PAIR-01 code | Real `Enrollment HTTP status=200`, portal success/reboot, ACTIVE in owner page; no code/secret printed |
| UP-01 | Send `SNAPSHOT` once over USB | Camera supported/authenticated, real JPEG bytes, HTTP 200 and `SMOKE snapshot=PASS`; disposable Core safe acceptance log and snapshot changes only for A |
| UP-02 | Send `MODEL <approved-existing-basename.3mf>` once | Real FTPS read, HTTP 200 and `SMOKE model=PASS`; `models/<printerA>/<basename>` in run-owned Core cwd; byte count/hash matches source; no staged temp file remains |
| ENROLL-02 | Use `SETUP`, then submit the already-consumed code once | Enrollment 401, ACTIVE identity unchanged; no new identity; existing secret not exposed or cleared |
| RECOVER-01 | After that failed setup attempt, perform one deliberate restart, keeping boot alive >15s | Existing saved identity reloads; `SNAPSHOT` again succeeds without new code; no factory reset |
| RESTART-01 | Power-cycle once, wait for successful boot | Identity/settings retained; Wi-Fi/time and real HTTPS snapshot recover; secure EDOL MQTT remains disabled |
| REVOKE-01 | Owner A clicks revoke | Status REVOKED; no live credential digest; only A affected |
| REVOKE-02 | Send `SNAPSHOT` with the unchanged revoked Agent | HTTP 401 and FAIL; A's last accepted snapshot unchanged across full window, no B changes |
| ROTATE-01 | Owner A requests replacement, then use USB `SETUP` and submit the new code | Old identity stays revoked; new row ACTIVE, same owner/printer; identity not leaked |
| ROTATE-02 | Send `SNAPSHOT`, then one approved `MODEL` | Both real uploads recover with new identity; compare artifacts and counters |
| EXPIRE-01 | Revoke, request another code, wait beyond its recorded 5min DB expiry, then submit it via `SETUP` once | Owner status EXPIRED; HTTP 401; no ACTIVE identity; issue a replacement and re-pair to recover |
| BADCODE-01 | With one fresh PENDING code, submit a different syntactically valid 12-character synthetic code once | 401, no binding or pending-code consumption; subsequent correct pairing succeeds |

Do not refresh/re-submit a successful pairing POST accidentally. The per-source
enrollment limiter counts successes and failures: five attempts per five-minute
window. Separate enrollment cases across fresh windows; do not restart Core to
bypass the limiter. Behind this proxy all clients may share its source address.
If 429 occurs, record it, wait for the real window and repeat the intended case;
429 is not code-expiry or TLS evidence. Automated limiter coverage already exists.

For UP-01 inspect the snapshot through the authenticated, tenant-scoped Hub/Core
media path used by the existing application. Core's endpoint is
`GET /api/printers/{printerId}/camera/snapshot` with the established service
authentication and `core.media.read`; do not expose it publicly or copy tokens
to a terminal. Idle snapshots normally live in runtime memory even with disk
storage off; absence of a new JPEG file is not failure. Record timestamp/hash
through the approved observer, not by enabling disk/session state artificially.
Use acceptance log counts plus a before/after fingerprint, not a visual guess.
If the observer cannot read the snapshot, mark that evidence BLOCKED.

For UP-02 choose a small valid printer-generated Bambu `.3mf` with required
metadata, below the configured limit; do not start a print to obtain one. Core
validates archive paths/counts/decompressed metadata before promotion. An
unsupported/malformed model or unsupported printer camera is a compatibility
finding, not successful transport acceptance; retain the exact generic status.
Avoid names the lab console rejects (spaces, Unicode, slashes, leading dot).
These console restrictions do not redefine the general backend filename contract.

## Physical TLS rejection matrix

Run after a successful trusted snapshot with a valid active identity. Prepare
separate run-owned TLS fixtures/listeners on the same disposable host: unknown
CA, trusted CA but wrong host SAN, and expired leaf signed by the trusted CA.
Record each fixture's chain/SAN/validity/fingerprint and actual listener
readiness. Do not change the board's clock or weaken certificate validation.
Prefer switching only the certificate on the run-owned listener, so the stored
host/port and NVS stay unchanged. Reload only that isolated listener after a
successful syntax check; if this cannot be isolated, stop before changing it.

| ID | One-step action | Acceptance |
| --- | --- | --- |
| TLS-00 | On the trusted fixture, `SNAPSHOT` once | Actual production WiFiClientSecure upload HTTP 200 before denials |
| TLS-01 | Operator activates unknown-CA fixture; after confirmation, `SNAPSHOT` once | FAIL with a nonzero numeric TLS verification error; no HTTP arrival at Core; no plaintext fallback |
| TLS-02 | Operator activates trusted-but-wrong-SAN fixture; after confirmation, `SNAPSHOT` once | Same fail-closed proof, linked to known wrong-SAN certificate |
| TLS-03 | Operator activates expired-leaf fixture; after confirmation, `SNAPSHOT` once | Same fail-closed proof, linked to known expiry and synchronized clock |
| TLS-04 | Operator restores trusted fixture; after confirmation, `SNAPSHOT` once | HTTP 200 and real image recovers using retained identity |

An HTTPClient negative status, `HTTP connect failed`, TCP timeout, DNS failure,
offline printer or closed TLS port alone is **insufficient** verification proof.
Numeric TLS errors plus certificate provenance and server observations must
agree. A generic mbedTLS certificate-verification error can cover several
reasons; do not claim it independently identifies SAN versus expiry. Firmware
logs only numeric status/error, never certificate contents or Authorization.

Count plaintext attempts on **each tested TLS port** with a safe observer that
classifies malformed HTTP/TLS without retaining payloads. An nginx TLS/access
log alone is not automatically a complete plaintext-arrival counter: validate
the classifier with one synthetic, credential-free HTTP probe before the
device window, then baseline/reset only its run-local counters. If no reliable
observer is ready, mark plaintext observation UNTESTED; do not write zero.
Correlate device actions, proxy TLS errors, HTTP arrivals/denials and Core safe
acceptance logs over an explicit window. No broad packet capture is authorized.

## API security and size checks — source evidence versus live evidence

Source/disposable automated tests cover wrong/missing agent auth, spoofed
identity/tenant headers, dedicated management scope, tenant isolation, atomic
one-time consume, expired/rotated/revoked credentials, non-superuser RLS,
malformed/bounded enrollment and uploads. Preserve their actual test report;
do not duplicate those requests by extracting a real device secret.

Current defaults: model 256MiB, JPEG 8MiB/40M pixels, metadata entry 16MiB,
metadata total 64MiB, archive 10,000 entries. Record effective configuration.
The proxy's 260MiB cap permits the backend's 256MiB boundary to be reached.
For a physical size-boundary observation, a safe optional case is to restart
only disposable Core with `edol-core.upload.model-max-bytes` below the measured
size of the harmless existing printer model; then `MODEL` should fail with 413,
leave the prior artifact hash unchanged and remove its staged file. Restore
the original limit and prove recovery. Coordinate that one restart explicitly;
an early proxy rejection or broken socket without observed Core 413 does not
prove the backend bound. Do not manufacture giant files on the real printer.
Unexecuted variants remain source/disposable evidence only.

## Observation sheet and acceptance decision

Create one row per case; retain this template without invented observations:

| Case | UTC start/end | Actions | TLS accepts/denials | HTTP status counts | Plaintext on TLS ports | Core accepted model/snapshot counts | A/B artifact changes | Result / reason |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Example placeholder | NOT RUN | unknown | untested | untested | untested | untested | untested | NOT RUN |

Use PASS, FAIL, BLOCKED, NOT RUN or N/A with a reason. Count deltas over the same
window, record observation coverage and any server restart/counter reset. A
missing dashboard/metric is not numeric zero. Model acceptance is INFO; snapshot
acceptance is DEBUG only for `SecureAgentController` in this disposable run.
No pre-existing production traffic window or compatibility-removal threshold is
established by this workbook. Synthetic/manual traffic cannot justify removing
legacy Home ingress or v2 compatibility readers.

Accept **9.3b physical HTTPS gate** only when owner/foreign-owner boundaries,
real camera and model uploads, retained identity after restart, consume/replay,
expiry, revoke/replace, all three certificate denials and trusted recovery have
their required observations. Record unsupported camera/model cases explicitly;
they leave the corresponding gate open. Record upload-limit live cases separately
from automated boundary coverage. The lab console trigger is part of the label.

These remain **BLOCKED / future gates**, not waived requirements:

- Secure agent MQTT connect, per-agent broker identity and transport choice,
  topic ACLs, foreign/wildcard topic denial and live-session revocation.
- Secure Core MQTT ingress/command egress bound to persisted agent identity,
  actual telemetry forwarding/reconnect and coordinated v2/v3 rollout.
- Normal secure-firmware re-pair/recovery UX, service credential rotation,
  database TLS deployment choice and private_key_jwt assessment/acceptance.
- Production/accepted Stage 7 changes, final dev then prod DB migration and
  rollout/rollback approval after Stage 9 completion.

## Restoration and rollback

At any unexpected printer behavior, stop issuing USB commands. Disconnect the
Agent from the printer network or power it off deliberately; do not send a
printer stop/print/motion command as cleanup. Preserve evidence securely.
Do not erase device settings to troubleshoot. Do not repeatedly power-cycle.

Restore the exact previously agreed normal firmware to the confirmed Agent
port without erase. If the agreed target is Home, explicitly verify its stored
Home broker/Core destinations first: rollback may resume legacy connections.
If those settings are uncertain, keep the Agent offline until the operator
resolves them. If the target is ordinary secure firmware, remember MQTT remains
disabled; a restored image is not complete bridge recovery. Record binary SHA,
boot/Wi-Fi outcome and observed live state separately. Do not claim restored
availability from a successful flash or a closed test server.

Once device recovery is observed, revoke only the disposable identity and
disable only run-owned agent flags/listeners as agreed. Stop only recorded
run-owned processes/containers. Retain test DB/volumes and logs until reviewed;
no DROP, prune, recursive delete, credential rotation, eFuse operation or push is
authorized here. Leave accepted Stage 5–8 infrastructure unchanged. Record
remaining resources and follow-up approval needed for cleanup.

Publish a redacted acceptance record with exact commits/binary hashes, actual
executed cases, counters/windows, failures, restoration status and open gates.
Only then decide the next secure MQTT increment; this source/disposable gate
does not authorize a production rollout or the planned dev/prod migration.
