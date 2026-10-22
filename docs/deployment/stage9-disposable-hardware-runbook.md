# Stage 9 Combined Disposable Hardware Runbook

## Gate and scope

The source/disposable MQTT gate is ready for supervised hardware acceptance.
Physical acceptance is **NOT RUN**. On 2026-10-22 the operator requested one
combined session with separate Agent and AMS terminal firmware flashes and a
real printer. This document controls that session. Execute one action, inspect
the result, then continue. Do not start hardware from a successful build alone.

The AMS device here is the EDOL ESP32/NFC terminal, not the printer's AMS unit.
Reuse accepted Stage 8 TLS/terminal evidence; this session adds the real printer
path. The historical simulator is not evidence for that path.

Read the [MQTT contract](stage9-agent-mqtt-contract.md) and use the
[Agent workbook](stage9-agent-disposable-smoke-workbook.md) for detailed HTTPS
cases. Its historical all-MQTT-off configuration is an isolation reference;
it must not replace the combined settings below.

No production, dev/prod DB or accepted Stage 7 infrastructure changes, pushes,
eFuse operations, erase-flash, filesystem uploads or secret extraction are
authorized. Keep Home contracts, the open Agent setup AP and existing NVS
semantics. Printer LAN MQTT/FTPS/camera certificate verification remains
disabled; secure EDOL transport does not change this LAN limitation.

## Candidate and private execution record

Record actual values in an access-controlled run directory outside Git. Never
record pairing codes, tokens, Authorization headers, Wi-Fi/printer passwords,
private keys or issued secrets in chat, screenshots, transcripts or wire logs.

| Field | Required value before execution |
| --- | --- |
| Run/operator/time | Unique `stage9-hardware-<UTC>` identifier |
| Backend | Full commit containing this runbook; branch and status |
| Agent | `97e64b159691910c32f08ae5285fdd588eb1a67f`, `stage9-agent-pairing`, clean status |
| AMS terminal | `0749cecdf9120dd5f120804bead5cdd3370739a7`, branch and clean status |
| Agent USB | Physically identified board and current `<AgentPort>` |
| Terminal USB | Separately identified board and current `<TerminalPort>` |
| Printer | Exact idle physical printer, AMS unit 0, approved slot and owner |
| Images | SHA256 of each test image and each restoration image |
| Trust | Public CA hashes, SANs, validity, device time source |
| Resources | Disposable hosts/ports, realm, fresh PostgreSQL cluster, volumes and accounts |
| Restoration | Approved firmware/settings targets and identity recovery for both devices |

COM16 was historically an AMS terminal port; it identifies neither device
today. Never infer one port from the other. Confirm the port immediately before
each flash. Keep unrelated USB devices out of the upload selection.

Previous source evidence: full backend reactor 294 tests passed without skips;
the subsequent lifecycle guard change passed 16 targeted tests without skips,
including real PostgreSQL/Mosquitto fixtures. These are separate runs, not a
new full-reactor total. Agent evidence is eight native executables, three Python
trust-hook tests, Home/secure/secure_smoke builds and missing-CA negative builds.
Stage 8 terminal evidence remains accepted. Actual run-specific firmware/CA
builds and all physical observations below still require execution.

## PREP — isolated infrastructure before connecting boards

Use one run-owned Mosquitto for internal services and Agent traffic. The Agent
connects outbound over TLS. Core, Hub, AMS and Notify use separate service
accounts. The terminal connects over HTTPS to AMS and receives no MQTT password.

1. Resolve the actual hosts, reachable ports and certificates. Use
   `eclipse-mosquitto:2.0.22` and the production TLS-only configuration and
   authority generator from `docker/mosquitto`. Record the image digest. Use
   new unique service secrets and a new dynamic-security state file; never
   initialize over an existing file. Keep private keys/auth state outside Git.
2. Prepare run-owned launch settings. Do not launch the base Compose project
   unchanged: its parent environment files and persistent volumes can refer to
   working resources. Compose merge/interpolation has not been executed on this
   Windows host because its Docker CLI lacks the Compose plugin. Validate the
   resolved configuration on the execution host, or review equivalent isolated
   process/container settings before starting. Do not print resolved secrets.
3. Use a fresh disposable PostgreSQL **cluster**, with new restricted migration,
   runtime and authentication-owner roles. Roles are cluster-wide. Follow the
   existing secure bootstrap contracts for Core/Hub and AMS, not manual Flyway
   history repair. Apply Core through V11, the current Hub chain and AMS through
   V3 only here. Verify forced RLS, V10 authentication function ownership and V11
   MQTT authority metadata/tombstones with restricted runtime access.
4. Create a disposable realm/client configuration and two test tenants. The
   Hub service must both be authorized for and explicitly request
   `core.agent.manage` and `ams.terminal.manage`. AMS needs
   `tenant.context,core.state.read,hub.spool.read,hub.spool.change`. Include both
   Hub and AMS in Core's trusted service clients, and configure the existing
   Hub trusted AMS client contract. Do not broaden route allowlists as a shortcut.
5. Run the normal secure multi-tenant services. Set
   `edol-ams.terminal-simulator.enabled=false`; do not activate
   `stage8-terminal-simulator`. Confirm the production
   `DefaultTerminalOperationalService` path uses real Core printer state.
6. For combined Core set `edol-core.runtime.mqtt-enabled=true`,
   `edol-core.runtime.integration-mqtt-enabled=true`,
   `edol-core.runtime.printer-runtime-enabled=true`,
   `edol-core.agent.enabled=true`, and `edol-core.agent.mqtt.enabled=true`.
   Enable the corresponding isolated Hub/service MQTT consumers. Every service
   uses the same `ssl://` broker with its own account and JVM CA trust. Disable
   external notifications. A smoke profile is only a baseline after inspecting
   every override; do not copy an MQTT-disabled profile unchanged.
7. Seed only approved AGENT-mode printers and disposable owned spools. Keep
   enabled DIRECT printers out of this Core catalog. Test tenant A owns the
   physical printer; tenant B has no authority over it.
8. Provide separate correctly routed HTTPS entry points for Core
   `/api/agent/v1/**` and AMS `/api/terminal/v1/**`. The former Agent-only proxy
   does not expose terminal routes. Verify trusted forwarding and unreachable
   untrusted upstream listeners. Certificates must cover actual Agent Core,
   terminal AMS, and broker external/internal hostnames. Provision JVM trust
   independently from each firmware public CA header.
9. Before either flash verify service authentication, tenant isolation,
   migration histories, broker account/ACL configuration, TLS and health.
   Retain disposable broker evidence for invalid/revoked identities,
   foreign/wildcard topics, spoofing and actual active-session disconnection.
   Do not repeat attack traffic on a working broker or connected printer.

PREP is BLOCKED until every actual value and readiness check is recorded. A
source build or parsed YAML does not establish runtime readiness.

## FLASH-A — Agent, then real telemetry

Use the existing `.worktrees/edol-agent-stage9` worktree, never the original
secret-bearing repository or published main. Preserve the approved ordinary
restoration image/settings first. Build `lolin_d32_secure_smoke` with
`EDOL_AGENT_ROOT_CA_FILE` pointing to the run's public CA file. Scope environment
changes to the build and restore their previous values afterwards. Record image
and CA hashes. Keep the build-local CA and `.pio` output out of Git.

Only after confirming `<AgentPort>`, upload that environment using the existing
PlatformIO procedure with an explicit port. If upload rebuilds, retain the same
CA/environment and verify the resulting image hash. Do not invent flash offsets,
use erase flags or upload a filesystem. Keep a successful boot powered for at
least 15 seconds: five short unsuccessful boots can trigger the existing reset
detector. Avoid repeated blind resets.

Use the existing open setup AP. Enter only the disposable Core endpoint and
the approved printer's LAN settings. Owner-issue the Agent pairing code through
Hub. Its 12-character code and Agent identity are independent of AMS terminal
enrollment. Observe HTTPS enrollment followed by separate one-time MQTT
credential provisioning. Never copy the HTTPS secret into MQTT settings.

First observe printer report -> Agent -> authorized MQTT topic -> persisted
Core tenant/printer -> Hub. Compare changing, physically observable values.
Missing optional printer metrics remain unavailable, not numeric zero.

Next issue one approved Core snapshot command and observe the actual camera
read and credential-bound HTTPS upload. Read/upload one existing small model
using the Agent workbook. Distinguish MQTT-triggered actions from USB lab
commands; USB success alone does not prove Core routing. No print start,
pause/cancel, motion, load/unload or unrelated printer commands are in scope.

## FLASH-T — AMS terminal, then real read-only state

Use the clean terminal candidate repository. Preserve its ordinary restoration
binary **before** building: both normal and CA-enabled builds use `esp32dev`
and can overwrite the same build output. The normal operational UI is required;
the `sec06` environment is a diagnostic harness and cannot test this path.

Build `esp32dev` with `EDOL_TERMINAL_SECURE_BUILD=1` and
`EDOL_AMS_ROOT_CA_FILE=<run-public-CA-file>`. Restore the prior environment
after the build. Record the binary/CA hashes. Use
`C:\Users\Ponomarenko\.platformio\penv\Scripts\platformio.exe`
with the normal `run -e esp32dev` procedure. Upload only after separately
confirming `<TerminalPort>`, with an explicit upload port and the same secure
build inputs if upload rebuilds. Recheck the image hash. Never upload LittleFS,
erase flash or extract Preferences credentials.

Before enrolling, settle restoration: a new pairing overwrites the terminal's
stored credential. Reflashing the old binary does **not** restore that secret.
Use a spare terminal or agree how its original owner will issue a fresh pairing
after restoring settings. Do not revoke accepted Stage 7/8 records or copy their
credentials into the disposable run. If identity restoration is unresolved,
stop before changing terminal enrollment.

Configure secure mode and the disposable AMS HTTPS URL. Leave the legacy
`printerId` setting empty: secure routes derive the printer from the enrolled
terminal credential, not this Home setting. Owner-issue a separate
11-uppercase-letter terminal pairing
code bound to the same test printer. Do not reuse the Agent code or identity.
Observe terminal -> AMS -> Core read-only state. Compare slot metadata and the
active slot with the printer UI; compare temperature/humidity only when the
real device reports them. Reject simulator constants as evidence.

Use a blank test NFC tag and an owned disposable spool matching a real supported
material/color/profile. Never overwrite a working tag. First test lookup and
selection without assignment; an unknown/unowned spool must not mutate state.

## AMS-WRITE — one controlled metadata assignment and immediate restoration

This step writes real printer metadata. It is separately operator-confirmed at
execution after the two read-only paths pass. Keep the printer idle. Restrict
the minimum case to AMS unit 0 and one of its four trays: UI A1/A2/A3/A4 maps to
backend slot 0/1/2/3. External spool slots and other AMS units are outside this
minimum case.

Before confirming, privately record original tray material, color, profile ID
and nozzle temperature bounds from an authoritative printer view. Identify and
approve an exact inverse using the printer UI or another already approved
metadata path. Assigning the previous spool through EDOL is not guaranteed to
restore every field: the current command factory emits nozzle bounds as zero.
If exact restoration cannot be established, mark this case BLOCKED and stop
before the write.

The expected production path is:

`terminal POST /api/terminal/v1/set-spool` ->
`DefaultTerminalOperationalService` -> AMS trusted-printer spool handler ->
Hub `POST /s/{printerId}/{spoolId}/{slot}` ->
Core `POST /api/printers/{printerId}/commands/spool-change` ->
Agent `/commands/printer` -> printer `ams_filament_setting`.

Select the prepared spool and confirm exactly one assignment. This changes tray
metadata; it does not select the active tray or load filament. A successful
HTTP response or terminal staging view is insufficient because device delivery
is asynchronous. Require printer UI and new telemetry through Core, AMS and
terminal to agree. Observe that unrelated trays/printers remain unchanged.
Immediately restore all captured original metadata and verify the same path
reports the restored values. Record both write and restoration independently.
On unexpected movement or state, stop the sequence and isolate Agent power or
network; do not improvise a printer command as cleanup.

## RECOVERY — complete after restoring printer metadata

Perform one perturbation at a time, with a positive trusted control afterwards:

1. Wi-Fi interruption, one deliberate Agent power cycle, Core restart and
   broker restart. Confirm persisted credentials, resubscription, telemetry and
   terminal real state recover. The single broker restart affects internal
   service communication too; observe both paths, not only Agent reconnect.
2. Run-owned unknown-CA, wrong-SAN and expired-certificate controls for Agent
   HTTPS/MQTT with no plaintext fallback. For terminal, establish the real AMS
   trusted positive path and record any new negative controls separately from
   accepted Stage 8 SEC-06 evidence. Never change working listeners to do this.
3. Revoke the connected disposable Agent. Observe actual broker disconnect,
   denial of old MQTT/HTTPS authority and no further command delivery. Replace
   only after broker retirement completes. A broker outage or durable pending
   retirement is not success; disabling the feature flag does not clear it.
   Re-pair through the existing portal and recover the real path.
4. Revoke/re-pair only the new disposable terminal binding and observe access
   loss/recovery. Do not revoke a historical accepted binding for this case.

No legacy compatibility path is removed on the basis of this short session.
Absent counters or observation windows remain unknown, never numeric zero.

## RESTORE — devices first, infrastructure last

Restore printer metadata while the authorized command path is still available.
Restore the agreed ordinary Agent and terminal images/settings with separately
confirmed ports, without filesystem erasure. Complete any required fresh owner
pairing for the terminal's original environment. Confirm ordinary operation
before retiring disposable identities. If a destination or restoration state
is uncertain, leave the affected device safely offline and record BLOCKED.

Retire only run-owned identities, verify broker session retirement, stop only
run-owned processes and restore temporary environment/trust settings. Preserve
database/auth state and redacted evidence until cleanup is separately approved;
this runbook does not authorize deletion. Never tear down the shared working
broker, accepted services or their data.

## Results worksheet

| Case | Required evidence | Status |
| --- | --- | --- |
| PREP | Isolated resources, resolved config, TLS, RLS, scopes and restoration plan | NOT RUN |
| FLASH-A | Confirmed Agent port, secure_smoke image/hash, successful boot | NOT RUN |
| AGENT-STATE | Real printer telemetry routed to the bound tenant/printer | NOT RUN |
| AGENT-UPLOAD | MQTT snapshot trigger, real camera and HTTPS/model upload | NOT RUN |
| FLASH-T | Confirmed terminal port, operational secure esp32dev image/hash | NOT RUN |
| AMS-READ | Terminal enrollment and real printer/slot state without simulator | NOT RUN |
| AMS-NFC | Blank test tag and owned/unknown spool lookup boundaries | NOT RUN |
| AMS-WRITE | One approved tray metadata change observed on the real printer | NOT RUN |
| AMS-INVERSE | Full original metadata restored and independently observed | NOT RUN |
| RECOVERY | Reconnect/restarts and both service/Agent paths recover | NOT RUN |
| TLS | Actual negative controls and trusted recovery; Stage 8 evidence identified | NOT RUN |
| AGENT-LIFECYCLE | Active-session revoke, denied old identity, replace recovery | NOT RUN |
| TERMINAL-LIFECYCLE | Disposable binding revoke/re-pair and real state recovery | NOT RUN |
| RESTORE | Both devices, original identities/settings and printer restored | NOT RUN |

Record timestamp, observation window, redacted evidence and operator decision
per case. Mark PASS only on actual observations; distinguish FAIL, BLOCKED,
unsupported and NOT RUN. An Agent PASS does not imply AMS PASS. Do not declare
combined acceptance while a required case or restoration remains unobserved.
