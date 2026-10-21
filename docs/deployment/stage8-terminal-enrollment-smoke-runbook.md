# Stage 8 AMS Terminal Enrollment Smoke Runbook

## Scope and safety boundary

This runbook validates the AMS Terminal pairing workflow in an isolated Stage 8 environment. It does not authorize firmware upload, physical printer commands, eFuse changes, production deployment, or changes to Stage 7 infrastructure.

The ordinary workflow uses Hub and the terminal touchscreen only. Do not use browser developer tools, direct browser-to-AMS requests, PowerShell enrollment calls, temporary reverse proxies, or manually generated certificates.

## Security and transport model

Hub creates a random eleven-character uppercase Latin-letter (A-Z) pairing code for one printer. The code is valid for five minutes, is single-use, and is never an operational credential. The terminal submits it once to AMS. AMS finds the persisted pairing by its HMAC digest, derives the tenant and printer from that pairing, and returns a random `terminalId.secret` credential once with `Cache-Control: no-store`.

The terminal stores the returned credential in ESP32 Preferences (NVS). Secure Boot, Flash Encryption, encrypted NVS, eFuse provisioning, and hardware-backed storage are not prerequisites for pairing. Do not print a pairing code or terminal credential in logs.

The transport selector on the terminal is explicit:

| Mode | Use | Result |
| --- | --- | --- |
| HTTP | Isolated trusted LAN smoke environment | Requests use `http://`; no CA provisioning is required. Anyone able to intercept that LAN traffic can obtain a pairing code or terminal credential. |
| HTTPS | Deployment with a trusted CA available to the firmware | Requests use `https://`; certificate validation remains required. TLS failure fails the request and never retries over HTTP. |

Never use HTTP on an untrusted or Internet-exposed network.

## Preconditions

1. Start the disposable `secure-multi-tenant` Stage 8 services and sign in as the owner of the selected printer. AMS must listen on the LAN interface of the computer running it; the default `EDOL_AMS_BIND_ADDRESS` is `0.0.0.0`. Use a restricted address only when no physical terminal is being tested.
2. Build the terminal firmware from its repository without upload:

   ```powershell
   & "$env:USERPROFILE\.platformio\penv\Scripts\platformio.exe" run
   ```

3. Flashing is a separate, operator-approved action. Do not upload from this runbook until the board, serial port, and recovery path are confirmed.
4. In the terminal Settings screen, set Wi-Fi, **AMS host** to the LAN IP address or DNS name of the computer running `edol-ams` (never the Hub address), **AMS port** to the actual listening AMS port, and the desired transport. For the current local smoke layout, this is `192.168.0.110` and `18088`; choose HTTP by leaving HTTPS switched off. Leave **Printer ID (legacy Home)** empty for this workflow.

## Hub enrollment workflow

1. Open **Printers → Edit → AMS Terminal** for the intended printer.
2. Select **Pair AMS Terminal**. Hub displays one code and a five-minute countdown.
3. Enter the 11-letter uppercase code in the terminal's **Pairing code** field and select **ENROLL**.
4. Confirm that the terminal reports enrollment completion after its settings restart.
5. Refresh the Hub printer page. It must show the terminal identity, lifecycle, enrollment time, and last authenticated time.

After **Revoke enrollment**, Hub shows **Pair replacement terminal**. That action issues a new pairing without restoring the revoked credential. When the terminal next receives HTTP 401 or 403, it clears its local credential and opens Settings; it must be paired again.

The code must succeed exactly once. Do not record it in command history, screenshots, source files, or logs.

## Lifecycle and negative checks

| Case | Expected result |
| --- | --- |
| Expired pairing, then **Regenerate expired pairing** | Hub creates a new pending terminal and code; the old code cannot enroll. |
| Repeated valid-code enrollment | First request activates one terminal; replay receives `401` and no credential. |
| Invalid code attempts | AMS reveals no printer or tenant state. Source rate limiting eventually returns `429`. |
| Device restart | Stored NVS credential authenticates terminal API calls without repeating pairing. |
| Revoke, replace, rotate, or factory reset in Hub | The retired credential receives `401`; the terminal requires a new pairing. |
| Tenant B management request for tenant A printer | Denied server-side. |
| HTTPS validation error | Request fails; the terminal does not switch to HTTP. |

Use only the harmless AMS state/find/set-spool stub during this smoke. Do not cause a printer command, Core printer runtime command, or MQTT action.

## Disposable operational simulator

The current synthetic printer has no Core runtime, so its normal AMS state is intentionally empty. For the physical-terminal operational checks, AMS provides a deterministic simulator. It is an AMS-only adapter behind the already authenticated `/api/terminal/v1/state`, `/find`, and `/set-spool` routes: terminal credential verification, printer binding, and tenant context remain unchanged.

The simulator never calls Core, Hub, MQTT, or a printer command endpoint. It is therefore safe for the synthetic printer and cannot operate a physical printer.

Enable it only for this disposable environment by adding these four values to the AMS launch configuration:

```text
SPRING_PROFILES_ACTIVE=secure-multi-tenant,stage8-terminal-simulator
EDOL_AMS_TERMINAL_SIMULATOR_ENABLED=true
EDOL_AMS_TERMINAL_SIMULATOR_TENANT_ID=da4b2480-4774-4eb3-bc88-c625497ce5d1
EDOL_AMS_TERMINAL_SIMULATOR_PRINTER_ID=01a12a14-289f-7843-8036-7a450c9567f5
```

Do not add these values to shared, development, or production configuration. The profile alone leaves the normal adapter active; setting the flag without the profile makes AMS fail at startup rather than enabling a simulator unexpectedly.

The deterministic fixture starts with these values after every AMS restart:

| Location | Spool ID | Metadata |
| --- | ---: | --- |
| A1 | 800001 | Bambu Lab Basic PLA, red, 780 g |
| A2 | empty | — |
| A3 | 800002 | Bambu Lab Basic PETG, blue, 640 g |
| A4 | empty | — |
| External tray | empty | — |

The simulated environment reports 23.5 °C and humidity raw value 35. It accepts those two spool IDs in AMS slots A1–A4 or the External tray, returns `404` for another spool ID, and returns `400` for an invalid slot. A terminal whose persisted tenant or printer binding differs from the explicit fixture binding receives `403` and cannot read or mutate the fixture.

### Physical operational sequence

1. Restart AMS after adding the four environment values above. Wait for normal AMS startup; do not restart Core or Hub.
2. On the already enrolled terminal, wait for the next state refresh. A1 and A3 must show the two fixture spools, and the environment must show 23.5 °C / humidity 35.
3. Open the A1 spool details and use its NFC write control on a blank supported tag. This writes the terminal's normal EDOL v1 tag for spool `800001`; no manual tag payload is needed.
4. Return Home and present that tag. The staging area must show the A1 spool metadata.
5. Select the staging area, choose A2, and wait for the next refresh. A2 must now show spool `800001` and be active. This is an in-memory simulator state update, not a printer command.
6. Present an operator-provided EDOL tag containing an unknown spool ID. The terminal must not stage a spool. Current firmware reports the lookup failure only through its normal generic request-failure path; a dedicated on-screen error remains a firmware acceptance gap.

Disable the simulator by removing the `stage8-terminal-simulator` profile and its three `EDOL_AMS_TERMINAL_SIMULATOR_*` values, then restart AMS. No database data or external service state is created by this simulator.

## Automated checks

Run from the EDOL repository:

```powershell
mvn -B -pl edol-ams,edol-hub -am test
& .\.agents\skills\edol-remote-docker-verification\scripts\with-remote-docker.ps1 mvn -B -pl edol-ams -am '-Dtest=AmsTerminalMigrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

The second command requires the approved temporary remote Docker tunnel. A local Testcontainers skip is unexecuted rather than a pass. Verify the firmware separately with the PlatformIO build command above.

## Acceptance record and remaining gate

The physical trusted-LAN enrollment and operational simulator smoke completed on
2026-10-12. Reuse that evidence; do not repeat provisioning, enrollment, NFC,
or lifecycle testing unless a relevant change invalidates it.

See [the Stage 8 acceptance record](stage8-terminal-acceptance-record.md) for
immutable revisions, executed checks, the security matrix, and the remaining
SEC-06 runtime evidence. The earlier conversational declaration of full
acceptance was premature: TCP connections and a Loading screen alone do not
prove certificate validation failure or absence of an HTTP retry.

Current status: **FUNCTIONAL SMOKE PASSED — SEC-06 FINAL SECURITY ACCEPTANCE
PENDING**. The 2026-10-21 closure attempt found no terminal on the previously
confirmed COM16 port; a different CH340 device must not be assumed to be it.

Only the focused SEC-06 procedure in the acceptance record remains for this
secure/disposable scope. Home compatibility and production deployment are
separate gates. Firmware upload still requires an identified device and
operator authorization; no printer commands or eFuse operations are needed.
