# Stage 9 retained-container recovery — 2026-11-08

This record follows [the read-only audit](stage9-resume-handoff-2026-11-08.md).
It records fresh server inspection and scoped test-environment recovery. It does
not replace historical hardware outcomes or establish full Stage 9 acceptance.

## Preserved baseline

- Local backend remains `EDOL-048` at `72a050b366e6e5fa1c225a8b7730aa455c621302`;
  the server backend checkout has the same HEAD and no tracked changes.
- Existing local documentation edits, logs, SDK archive, firmware repositories,
  identities, realm, broker authority and retained data were preserved.
- Containers were started by their existing IDs. No Compose `up`, image build,
  pull, container recreation, bootstrap, explicit SQL, enrollment, revocation,
  firmware flash or operator-requested printer command was performed.
- Application startup resumed existing background workflows. Startup logs
  contained Core model-transfer failures and AMS `EdolCore unavailable!`
  messages; their underlying causes have not been established. A later bounded
  check found no Core model-transfer failures in its preceding 120 seconds and
  no AMS Core-unavailable or MQTT-processing failures in its preceding 90
  seconds. These windows are not lifetime-zero counters or hardware acceptance.

## Certificate recovery

The server clock was 2026-11-08 UTC. The retained HTTPS certificate had expired
on 2026-11-06 at 12:00:15 UTC; `openssl verify` returned error 10. The operator
verified the MQTT certificate against the retained CA and IPv4 identity:
`OK`, valid until 2026-11-20 at 11:24:24 UTC.

The operator renewed only the HTTPS leaf certificate, reusing the existing CA
and HTTPS server key. The installed certificate passed server-purpose chain
and IPv4 identity verification, with SAN `IP:192.168.0.200`:

- Valid from 2026-11-08 06:58:07 UTC to 2026-11-15 06:58:07 UTC.
- SHA-256 fingerprint:
  `DB:7D:AE:97:0E:B2:79:9C:71:28:16:1B:33:E7:58:57:37:C9:06:AA:8B:A0:07:03:C7:96:4B:1A:60:81:F0:C3`.
- Previous certificate retained at
  `certs/agent-https/renewal-20261108T065807Z-Dwh5Yz/previous-server.crt`
  beneath `/home/serge/edol-stage9-hardware`.

CA fingerprint remains
`A4:B9:C4:EF:FD:45:EE:13:5E:37:EB:62:53:28:0A:6F:FD:2D:61:92:39:88:4D:8B:B4:47:65:ED:8E:52:28:39`.
The CA expires on 2026-11-21 at 11:23:40 UTC. No CA, private key, MQTT
certificate, truststore or device trust was replaced.

## Network and startup

The retained network `edol-stage9-hardware_stage9` was empty before recovery.
Core still uses native forwarding and the exact trusted-peer pattern
`10[.]253[.]90[.]7`. With all services stopped, the operator disconnected and
reconnected the existing standalone HTTPS proxy with static IP `10.253.90.7`.
Core trust configuration was not broadened.

The agent started PostgreSQL and waited for healthy status, then started MQTT
and Keycloak, Core, AMS, the HTTPS proxy, and finally Hub. Before starting Core,
the trusted address was unoccupied; after starting the proxy its actual address
was verified as `.7`.

| Service | Retained image | Observed address | Result |
| --- | --- | --- | --- |
| PostgreSQL | `postgres:17` | `10.253.90.2` | Running; Docker health healthy |
| MQTT | `eclipse-mosquitto:2.0.22` | `10.253.90.3` | Running; CA/IPv4-verified TLS handshake |
| Keycloak | `quay.io/keycloak/keycloak:26.7.4` | `10.253.90.4` | Running; master and configured application realm discovery HTTP 200 |
| Core | `edol-stage9-core:d52fdd0e` | `10.253.90.5` | Running; actuator health UP |
| AMS | `edol-stage9-ams:72a050b` | `10.253.90.6` | Running; startup complete; malformed terminal credential rejected with HTTP 401 |
| HTTPS proxy | `nginx:stable-alpine` | `10.253.90.7` | Running; `nginx -t` exit 0 |
| Hub | `edol-stage9-hub:484b442` | `10.253.90.8` | Running; startup complete; root HTTP 302 to `/printers`, favicon HTTP 200 |
| Notify | `edol-stage9-notify:04ed4cc` | Not assigned | Remains stopped |

AMS has no actuator dependency: `/actuator/health` returns 404. The initial
health readiness probe was unsuitable for AMS. Hub's root redirects to its
protected printer page; requiring a final public-root HTTP 200 was also an
unsuitable readiness criterion. Neither probe timeout establishes a startup
failure. Authenticated browser operation remains an operator check.

The proxy network change is retained container configuration, not a reconciled
declarative deployment. Inspect addressing again before any future recreation
or restart; do not infer safety solely from the intended static address.

## Fresh HTTPS checks

The server-side client verified the retained CA and IPv4 identity. GET probes
used no real credentials and made no enrollment or printer-command requests.

| Probe | Observed result |
| --- | --- |
| HTTPS `/` | 404, as configured |
| HTTPS `/api/agent/v1/upload`, no credential | 401 |
| HTTPS `/api/terminal/v1/state`, malformed synthetic credential | 401 |
| Direct Core HTTP Agent route, no credential | 403 |
| Same direct route with spoofed `X-Forwarded-Proto`, `Forwarded`, and `X-Forwarded-For` | 403 |

The proxy overwrites `X-Forwarded-Proto` with `https` and clears
`X-Forwarded-For` and `Forwarded` for both device routes. Access logging is
disabled. An empty access-log summary cannot establish zero device requests,
terminal continuity, or authenticated successful state responses.

## Next evidence and ownership boundary

AMS has one configured printer-to-tenant mapping, for printer
`01a16493-e4ed-766b-8e75-53e168d39efa` and tenant
`7159f3c8-1976-4c3e-ad4f-124bc1a9e44a`. Terminal simulation is disabled.
No mapping or subscription policy was changed.

Source review confirms subscriptions are shared AMS topics, with mapping
validation before event dispatch. A successful spool lookup stages a spool in
RAM under its logical printer ID for 60 seconds. A mapped `ams.slot.loaded`
event can consume that staging and call Hub's spool-change POST, which calls
the selected Core printer command path. An explicit terminal spool assignment
can also call this write path. Therefore successful NFC/spool lookup is not a
passive observation when real slot events may follow.

Before NFC, tray interaction or metadata tests, align the terminal's selected
logical printer with AMS MQTT handling and obtain an exact inverse for any write. For the next
operator check, observe only Hub login/printer status and the existing terminal
state display. Do not re-enroll, scan NFC, set a spool, or operate trays.
Authenticated terminal state, current binding and physical telemetry remain
unverified in this recovery record. Combined hardware and final lockdown gates
remain open.

No implementation or architectural contract changed. `architecture.md` remains
unchanged; source tests and Sonar were not run for this operational record.

## Operator observations and late-start metadata investigation

The operator supplied screenshots at approximately 09:13–09:16 local time and
confirmed that the physical print was already running when services started.
The Hub printer list showed both DIRECT and AGENT printing the same model at
69%, layer 91/175. Dashboard screenshots showed RUNNING/Online, real camera
images, layers 92/175 and 93/175, and matching AMS colors/materials. One dashboard
still waited for print information; the other displayed PETG metadata with
32.4 g required and zero allocated. Metadata availability and actual spool
allocation are separate outcomes.

The terminal management screenshot showed the existing identity
`01a17e58-762b-7d89-a320-61ef3bed4ebe` as ACTIVE/CONSUMED, enrolled on October 27,
with a last connection of 2026-11-08T07:14:27.281143Z. The physical terminal
photograph showed Wi-Fi connected, layer 101/175, four PETG slots, external tray
empty, temperature 24.5 C and humidity 38%. This is fresh operator evidence of
working terminal state after HTTPS recovery, not serial continuity or a full
terminal lifecycle test. The operator reports the terminal is bound to DIRECT
and supplied the exact Hub URLs, confirming DIRECT as
`01a168b6-1317-73ea-83eb-55785be588f3` and AGENT as
`01a16493-e4ed-766b-8e75-53e168d39efa`.

Redacted current-start Core and Hub logs establish this timeline (UTC):

| Time | Observed event |
| --- | --- |
| 07:03:07.742 | Printer `01a16493-e4ed-766b-8e75-53e168d39efa` model-upload failure; first bounded retry scheduled |
| 07:03:11.185 | Printer `01a168b6-1317-73ea-83eb-55785be588f3` metadata parsed successfully; acquisition stopped |
| 07:03:49.130 / 07:05:04.142 | Further upload failures for `01a16493-e4ed-766b-8e75-53e168d39efa`; retry categories IllegalStateException |
| 07:05:38.018 | Hub MQTT ingress initialized |
| 07:05:39.768 | Hub application startup completed |
| 07:05:45.270 / 07:05:45.313 | Hub created current print jobs for the two logical printers |
| 07:07:40.870 | Printer `01a16493-e4ed-766b-8e75-53e168d39efa` metadata parsed successfully; acquisition stopped |
| 07:07:40.975 | Hub allocation snapshot created |

Both Core metadata parses succeeded. The upload-oriented path and source routing
identify `01a16493-e4ed-766b-8e75-53e168d39efa` as the Agent path; the other used
the synchronous DIRECT path. No Core integration-publication failure marker or
Hub/AMS processing-failure marker was found in current-start Docker logs. This
does not prove broker delivery of the earlier event or explain the initial
Agent upload failures.

The strongest current explanation for the DIRECT waiting display is a missed
metadata integration event before Hub subscribed, followed by missing secure
reconciliation. Source review confirms that `SecureMqttEventListener` recovers a
job on progress events but calls `metadataLoaded` only for a metadata event;
`PrintJobService.updateProgress` does not build a missing allocation preview.
`PrintJobRecoveryService` is Home-only. This is a confirmed source gap and a
timeline-supported explanation, not a database-receipt audit or a controlled
reproduction. No events were replayed and no live recovery was triggered.

Terminal authority and MQTT mapping are distinct inputs: authenticated terminal
operations use the credential's stored printer ID, while MQTT dispatch uses
the configured mapping. The current MQTT mapping for the Agent path therefore
does not disprove the operator's report of a DIRECT terminal binding. A DIRECT
staged-spool lookup could be accepted through terminal authority while DIRECT
slot events are rejected by the separate MQTT mapping. Align these paths before
any NFC or write acceptance case. The product supports terminal binding to
either DIRECT or AGENT; one terminal selects one logical printer. The operator
permits a test rebind if necessary, but no rebind or mapping change was made.

## Completed print and next full-cycle observation

Fresh read-only logs after the operator reported completion establish:

- Core recorded DIRECT `PRINT_FINISHED` at 2026-11-08T07:32:09.059Z and AGENT
  `PRINT_FINISHED` at 07:32:09.105Z. Hub recorded both print finishes.
- DIRECT had no allocation preview at finalization. AGENT had one preview,
  but unresolved allocation prevented finalization. Neither is a failed
  physical print or permission to invent spool allocation.
- AMS recorded four DIRECT `tenant_mapping` / `unmapped_printer` processing
  failures between 07:31:30 and 07:32:00. Hub had no processing-failure marker
  in current-start Docker logs. The known mapping mismatch remains visible.
- At 07:46:32 UTC PostgreSQL, MQTT, Keycloak, Core, AMS, proxy and Hub were
  running; Core health was UP and proxy retained `.7`. Notify stayed stopped.

The next operator-owned print is a passive full-cycle observation with all
services already ready. Keep terminal binding and mappings unchanged for this
baseline. Capture existing Agent and terminal serial output before starting,
using 115200 baud and initial DTR/RTS zero; do not flash or send serial input.
At preparation, COM9 was present as USB-SERIAL CH340 and COM16 was absent.
Confirm the Agent port and connect/enumerate the terminal before opening it.
This observation covers start, metadata/preview availability on both logical
printers, camera, terminal state and completion; it does not include NFC,
spool assignment, Pause/Resume, restart, revocation or negative TLS controls.
Existing firmware logs cannot prove uninstrumented dual-MQTT loop continuity
or transient heap minima. Do not restart services just to reproduce the
late-subscriber gap during this baseline.

The operator subsequently completed that full cycle. See
[the full-cycle evidence record](stage9-full-cycle-2026-11-08.md) for fresh
Agent/terminal serial hashes, both metadata previews and print finishes,
observed DIRECT retry recovery, and the remaining AMS mapping rejections.
