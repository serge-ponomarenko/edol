# Stage 8 AMS Terminal Enrollment Smoke Runbook

## Scope and safety boundary

This runbook validates the AMS Terminal pairing workflow in an isolated Stage 8 environment. It does not authorize firmware upload, physical printer commands, eFuse changes, production deployment, or changes to Stage 7 infrastructure.

The ordinary workflow uses Hub and the terminal touchscreen only. Do not use browser developer tools, direct browser-to-AMS requests, PowerShell enrollment calls, temporary reverse proxies, or manually generated certificates.

## Security and transport model

Hub creates a random ten-character Crockford Base32 pairing code for one printer. The code is valid for five minutes, is single-use, and is never an operational credential. The terminal submits it once to AMS. AMS finds the persisted pairing by its HMAC digest, derives the tenant and printer from that pairing, and returns a random `terminalId.secret` credential once with `Cache-Control: no-store`.

The terminal stores the returned credential in ESP32 Preferences (NVS). Secure Boot, Flash Encryption, encrypted NVS, eFuse provisioning, and hardware-backed storage are not prerequisites for pairing. Do not print a pairing code or terminal credential in logs.

The transport selector on the terminal is explicit:

| Mode | Use | Result |
| --- | --- | --- |
| HTTP | Isolated trusted LAN smoke environment | Requests use `http://`; no CA provisioning is required. Anyone able to intercept that LAN traffic can obtain a pairing code or terminal credential. |
| HTTPS | Deployment with a trusted CA available to the firmware | Requests use `https://`; certificate validation remains required. TLS failure fails the request and never retries over HTTP. |

Never use HTTP on an untrusted or Internet-exposed network.

## Preconditions

1. Start the disposable `secure-multi-tenant` Stage 8 services and sign in as the owner of the selected printer.
2. Build the terminal firmware from its repository without upload:

   ```powershell
   & "$env:USERPROFILE\.platformio\penv\Scripts\platformio.exe" run
   ```

3. Flashing is a separate, operator-approved action. Do not upload from this runbook until the board, serial port, and recovery path are confirmed.
4. In the terminal Settings screen, set Wi-Fi, AMS host, AMS port, and the desired transport. Leave **Printer ID (legacy Home)** empty for this workflow.

## Hub enrollment workflow

1. Open **Printers → Edit → AMS Terminal** for the intended printer.
2. Select **Pair AMS Terminal**. Hub displays one code and a five-minute countdown.
3. Enter the code in the terminal's **Pairing code** field and select **ENROLL**.
4. Confirm that the terminal reports enrollment completion after its settings restart.
5. Refresh the Hub printer page. It must show the terminal identity, lifecycle, enrollment time, and last authenticated time.

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

## Automated checks

Run from the EDOL repository:

```powershell
mvn -B -pl edol-ams,edol-hub -am test
& .\.agents\skills\edol-remote-docker-verification\scripts\with-remote-docker.ps1 mvn -B -pl edol-ams -am '-Dtest=AmsTerminalMigrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

The second command requires the approved temporary remote Docker tunnel. A local Testcontainers skip is unexecuted rather than a pass. Verify the firmware separately with the PlatformIO build command above.

## Physical-device acceptance still required

After explicit upload approval, an operator may upload to a confirmed serial target and perform the Hub workflow above. Record only redacted evidence for HTTP/HTTPS transport selection, first enrollment, restart recovery, replay denial, revocation denial, and tenant isolation. Do not flash a device, modify eFuses, or issue printer commands as part of automated validation.

Stage 8 is not accepted until those real-device checks complete.