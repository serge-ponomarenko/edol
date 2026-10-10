# Stage 8 Terminal Enrollment Disposable and Physical Smoke Runbook

## Purpose and boundary

This runbook controls Stage 8 acceptance for AMS Terminal enrollment. It
covers source checks, an isolated disposable secure-service exercise, and a
separately approved physical-terminal check. It is not standing authorization
to deploy a service, alter Keycloak, create a database role, publish MQTT,
flash a terminal, provision ESP32 eFuses, or operate a printer.

A PASS is limited to the gates actually executed. Source and Testcontainers
evidence is not physical-terminal, development, or production acceptance. The
accepted Stage 7 disposable contract remains unchanged.

The secure test environment must contain only synthetic tenants, disabled
synthetic printers, and a safe Core/Hub command stub. It must not target a
development or production database, a live printer, a production Keycloak
realm, an existing MQTT broker, or a real customer terminal.

## Approval gates

| Gate | Separate approval required before | Stop if |
| --- | --- | --- |
| A | Creating disposable infrastructure, external secrets, Keycloak clients, or processes | A recorded target is not new and isolated, a secret may enter source control or evidence, or a listener is not loopback-only. |
| B | Uploading firmware to a physical terminal | The serial target, board, partition, firmware revision, and recovery image are not recorded. |
| C | Enabling secure boot or flash encryption eFuses | The board-specific irreversible-provisioning procedure and recovery consequences have not been reviewed. |
| D | Invoking a terminal operation which could reach a printer command boundary | The Core/Hub command sink is not demonstrably harmless. |

No run can use a live `set-spool` path. A Stage 8 operational request ends at
the approved stub, not at a printer, agent, MQTT publish, or device action.

## Security and evidence rules

- Never record pairing codes, terminal secrets, client secrets, bearer tokens,
  complete authorization headers, Wi-Fi passwords, database credentials, or
  full credential-bearing URLs.
- Record only redacted terminal IDs, response-status summaries, lifecycle audit
  IDs, and source revision.
- Capture the enrollment response headers only as a redacted summary:
  `Cache-Control: no-store` is required. Do not retain the response body after
  the terminal has consumed it.
- A secure request has only `Authorization: EDOL-Terminal
  <terminalId>.<secret>`. It never carries a tenant identifier or an
  operational printer identifier.
- A TLS validation error, unavailable clock, absent encrypted storage, or
  incomplete secure configuration must fail closed; it must never fall back to
  legacy `/ams/**` routes.

## Preconditions

Record the EDOL and terminal repository revisions and both clean/known-dirty
statuses. Preserve unrelated changes. The following inputs are required for a
disposable secure-service run but must be supplied outside the repositories:

| Input | Required condition |
| --- | --- |
| AMS database | Fresh disposable PostgreSQL with Flyway V1, `ams_runtime` without `SUPERUSER` or `BYPASSRLS`, and Stage 8 RLS/function grants. |
| Tenant data | Two synthetic tenants and one disabled synthetic printer per tenant. Create one terminal only for each printer. |
| Hub identity | A disposable Hub service client accepted by AMS with narrow terminal-management scope and trusted tenant propagation. |
| AMS identity | Existing independent AMS service identity for Core/Hub calls; do not use it on the terminal. |
| TLS | A disposable HTTPS hostname whose complete certificate chain validates. Build its root CA into the terminal through protected build input, never source control. |
| Enrollment keys | Current and, when rotation is tested, previous HMAC keys supplied by the approved secret mechanism. |
| Command boundary | A reviewed harmless Core/Hub stub for `set-spool`; it proves that no printer command, MQTT action, or live device interaction occurs. |
| Physical board | Exact board, serial port, partition layout, recovery firmware, and operator. Read its current security state before considering irreversible provisioning. |

Do not synthesize terminal credentials or enroll an existing terminal merely to
make the test environment look populated.

## Gate 8.0: source and protocol checks

Run from the EDOL repository without creating runtime state:

```powershell
mvn -B -pl edol-ams,edol-hub -am test
& .\.agents\skills\edol-remote-docker-verification\scripts\with-remote-docker.ps1 mvn -B -pl edol-ams -am '-Dtest=AmsTerminalMigrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

The second command is Testcontainers-only and uses the reviewed temporary
remote-Docker tunnel. A local Docker/Testcontainers skip is unexecuted, not a
pass. From the terminal repository, build without upload:

```powershell
& "$env:USERPROFILE\.platformio\penv\Scripts\platformio.exe" run
```

Verify and retain redacted output for:

- 10-character Crockford Base32 generation, five-minute expiry, one atomic
  consumption, maximum five failed attempts, rate limit, expiry, replay, and
  concurrent-consume behavior.
- HMAC-only pairing and credential storage, configured pairing-key overlap,
  constant-time comparison, credential versioning, and `no-store` response.
- Owner authorization before lifecycle management; cross-tenant denial;
  revoke, rotate, replacement, factory-reset, re-pair, pending-pairing
  invalidation, and denial of every retired credential.
- Flyway tables, forced RLS, tenant/printer uniqueness, no-tenant-context
  denial, and runtime-role function privileges in fresh PostgreSQL.
- Firmware build only: secure mode has no HTTP fallback, requires HTTPS/CA/
  clock/encrypted NVS, keeps the secret out of LittleFS settings, and preserves
  the trusted-network `printerId` workflow only in Home mode.

The current firmware image is close to its partition limit. Record RAM and
flash use for every candidate; stop if the selected board/partition does not
leave the project-approved recovery margin.

## Gate 8.2: disposable secure-service flow

After Gate A approval, use a new isolated secure profile and execute the cases
with redacted clients. Owner calls enter Hub; Hub uses its own service identity
and trusted tenant context to call AMS. The terminal never has Hub, Core,
Keycloak, or service credentials.

| Case | Required result |
| --- | --- |
| Owner creates pairing for tenant A/printer A | Exactly one `PENDING` terminal and a five-minute, single-use code are created. Display the code only in the approved owner UI. |
| Terminal enrollment | `POST /api/terminal/v1/enroll` over valid TLS consumes the code once and returns `terminalId.secret` once with `no-store`. A concurrent or replayed request does not obtain another secret. |
| Enrollment failures | Malformed, expired, already-used, wrong-printer, and five-times-invalid codes disclose neither tenant nor terminal state. Rate-limited requests return `429`; invalid terminal credentials return `401`. |
| Tenant isolation | Tenant B cannot manage printer A, and terminal B cannot read, find, or set spool data for terminal A. Verify RLS and HTTP behavior. |
| Narrow operation surface | A valid terminal can call only terminal `state`, `find`, and `set-spool`; AMS derives tenant and printer from the credential. An added tenant/printer parameter cannot select another printer. |
| Safe mutation | `set-spool` succeeds only through the approved harmless command stub. Preserve a stub receipt, not a printer command. |
| Lifecycle | Revoke, rotate, replace, and factory-reset deny the prior secret with `401`, invalidate pending pairings as applicable, and create no automatic replacement credential. Explicit re-pair is required. |
| Key rotation | Current and configured previous pairing-key digests work only during the approved overlap. Retired key versions fail closed after overlap removal. |

Inspect audit/metric summaries for pairing creation, failed verification,
rate-limit, expiry, consumption, authentication failure, lifecycle operation,
and redaction behavior. Do not use raw request/response logging.

## Gate 8.3: physical secure-terminal check

This gate begins only after Gate B approval. Gate C is additionally required
before any secure-boot or flash-encryption eFuse action. Firmware upload alone
does not prove hardware protection; eFuse operations are irreversible and
board-specific.

### Prepare

1. Record terminal and EDOL revisions, board, serial port, partition table,
   current firmware version, and a tested recovery image. Confirm the target
   is the explicitly approved physical terminal.
2. Read and record existing security state using board-vendor read-only tools.
   Do not burn an eFuse from this runbook. Stop if the state conflicts with the
   reviewed provisioning plan.
3. Supply the disposable HTTPS root CA through protected build input. Confirm
   source, `platformio.ini`, logs, and screenshots contain no secret or private
   key. Build once and record memory use.
4. After upload approval, upload only to the recorded serial target:

   ```powershell
   & "$env:USERPROFILE\.platformio\penv\Scripts\platformio.exe" run --target upload --upload-port <approved-serial-port>
   ```

   Replace the placeholder only after operator verification. Do not copy a
   pairing code or enrollment response into console history.

### Execute

| Test | Expected physical result |
| --- | --- |
| First enrollment | The owner creates a disposable pairing. The settings UI accepts the 10-character code and stores only the returned terminal secret in encrypted NVS. The code is not stored. |
| Restart recovery | After power cycle, secure mode restores only encrypted credentials and can call `state` and `find` through valid HTTPS. No human or service credential exists on the device. |
| Secure request shape | Packet/log summaries show narrow terminal authorization and no tenant or operational printer ID. Do not capture the secret itself. |
| TLS failures | Wrong/absent CA, hostname mismatch, expired/untrusted certificate, and unavailable clock all block requests without HTTP fallback. Restore the disposable endpoint before continuing. |
| Enrollment failures | Invalid, expired, replayed, and attempt-exhausted codes fail without creating a usable credential. |
| Operational boundary | `state`, `find`, and `set-spool` run only against the safe stub. Confirm no action reaches a printer, agent, Core printer runtime, or MQTT broker. |
| Owner lifecycle | Hub revoke, rotate, replacement, and factory-reset cause the old terminal to receive `401`. It cannot recover access after reboot. |
| Local disposal | After owner revoke/reset, use **FORGET SECURE**. It clears local encrypted NVS and reboots; it does not revoke remotely. Re-pair explicitly and verify the new secret works. |

If flash encryption and secure boot are not both already enabled, report the
physical check as partial. Do not claim secure credential-at-rest acceptance.

## Gate 8.4: future Home compatibility smoke

Run this only as a separately approved Home-mode check. Home remains a
trusted-network deployment: the user manually enters `printerId`, and legacy
`/ams/state`, `/ams/find`, and `/ams/set-spool` retain their existing shape.
It does not receive Keycloak, terminal credentials, tenant mappings, secure
broker prerequisites, or an emulated secure-enrollment endpoint.

In a Home build, secure `/api/terminal/v1/**` enrollment must fail closed
because its prerequisites are absent. A future convenient Home printer-code UX
is not terminal authentication and needs separate design review.

## Pass, rollback, and incident handling

Pass only when every approved gate has redacted evidence and every negative
case fails closed. Record unexecuted gates explicitly; never promote a
source/disposable pass to physical or production acceptance.

For suspected credential exposure, unexpected terminal access, TLS bypass, or
cross-tenant behavior:

1. Stop the affected disposable test; do not retry against a live target.
2. Revoke or factory-reset the terminal through the authorized Hub owner path;
   this removes its credential digest and denies the old secret.
3. Invalidate the pairing and rotate the deployment HMAC key under the approved
   secret-management procedure if the pairing-key boundary may be exposed.
4. Preserve redacted audit/service evidence; do not retain the secret or raw
   enrollment body.
5. Roll back by disabling new enrollment while retaining terminal records and
   revocation. Never restore a retired secret, accept a pairing code as an
   operational credential, or reopen anonymous secure terminal routes.

Delete disposable resources, erase external secrets, reflash a device, or
alter eFuses only under separately approved cleanup or recovery procedures.
