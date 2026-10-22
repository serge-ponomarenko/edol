# Stage 9.3b secure MQTT contract

## Approved decision and boundary

On 2026-10-22 the operator selected one Mosquitto broker for both internal
service events and Core/Agent communication. Server-authenticated TLS plus
independent per-client credentials is the selected MQTT identity model.
Per-Agent mTLS is not required for this increment. This explicitly resolves
the previous client-certificate alternative; it does not waive server CA,
hostname, expiry checks, ACLs, active-session revocation, or later database TLS
and service asymmetric-authentication decisions.

The source candidate is `eclipse-mosquitto:2.0.22` with its bundled Dynamic
Security plugin. Record the resolved image digest with acceptance/rollout
evidence. The broker remains a shared availability boundary. No running
NanoMQ, accepted Stage 7 installation, working database, hardware or public
repository is changed by these source files.

## One broker, separate identities

The production policy generator is `docker/mosquitto/initialize_security.py`.
It creates a NEW authority file exclusively, from seven distinct process-local
passwords of at least 32 characters. It stores salted PBKDF2 verifiers, never
plaintext. Its output is outside Git, in an operator-restricted directory
writable by the broker account. Never regenerate it over an existing file:
that file contains Agent authorities and revocation state too.

| Identity | Publish | Subscribe/receive |
| --- | --- | --- |
| `core-events` | `edolcore/#` | none |
| `hub-events` | none | `edolcore/#` |
| `notify-events` | none | Existing eleven declared printer/print topics |
| `ams-events` | none | `edolcore/ams`, `edolcore/print/ams` |
| `core-agent-ingress` | none | Three report topics below, across Agents |
| `core-agent-commands` | Two command topics below, across Agents | none |
| `core-agent-control` | Dynamic Security control request | Control response |
| `agent:{UUID}` | Own three report topics | Own two literal command topics |

All unspecified send, receive, subscribe and unsubscribe access is denied.
The control identity has broker-wide administrative impact, including service
accounts, and is never issued to firmware or a browser. Core uses this
identity only for narrow hard-coded Agent operations; protect and rotate its
deployment credential independently. Broker administration and its response
topic cannot be reached with an Agent or service-event credential.

Secure Agent client ID is exactly `edol-agent:{UUID}`; Dynamic Security binds
it to the username/password. Secure topics are:

- Agent publish: `edol/agent/v1/{UUID}/printer/report`, `/heartbeat`, `/events`.
- Agent subscribe/receive: `edol/agent/v1/{UUID}/commands/printer`, `/commands/agent`.

No tenant-selected topics, wildcard/shared Agent subscriptions, legacy Agent
namespace, `$SYS`, `$CONTROL`, or `edolcore` permissions are granted.
The secure listener disables retained messages, has an 8192-byte payload
limit and bounded connection/queue limits. Client MQTT remains 3.1.1 with
clean sessions; firmware publication remains QoS 0. Commands are not retained
or durable/offline-queued. A successful publish is not printer execution proof.

## Provisioning, retirement and recovery

Existing one-time pairing issues HTTPS identity only. Authenticated
`POST /api/agent/v1/mqtt/credentials` issues an independent random 32-byte
Base64url MQTT secret once. Device authentication supplies the persisted
Agent/tenant/printer binding; body/header identities cannot select it.
The route requires HTTPS and uses the existing no-store/stateless filter.

V11 adds forced-RLS `core.agent_mqtt_authorities`; it has lifecycle metadata,
not passwords, and deliberately retains tombstones after printer/Agent deletion.
The fixed metadata/reconciliation functions belong to the existing constrained
`core_agent_authenticator`, have no PUBLIC execution, and grant runtime no
role membership or RLS bypass. Accepted V10 is unchanged.

Provisioning commits `PROVISIONING` before broker side effects. It then locks
printer, active Agent and authority in that order, creates a role and client
without a password, sets the new password, and commits `READY` before returning
the secret. A concurrent retirement cannot silently reactivate the authority.
Uncertain provisioning is never retried to retrieve a secret. Expired
provisioning becomes revocation-pending after one minute and is reconciled.
Owner replacement is required after a lost response or storage failure.

Agent retirement and printer disable, mode/tenant change or deletion persist
`REVOKE_PENDING` in the same database transaction. Core immediately rejects
subsequent messages/commands. `disableClient` disconnects active broker sessions
and prevents reconnect. Only a confirmed response marks the tombstone revoked.
The reconciler retries every two seconds while Core is available. A broker or
Core outage can extend this interval: pending is not completed MQTT revocation.

Management revoke commits Core retirement, then flushes broker revocations.
Broker failure returns 503 and Hub shows pending. Replace issues no new code
until that flush succeeds; a concurrent new live pairing returns conflict.
Disabling the MQTT feature after credentials were issued does not complete
broker revocation. Management still checks durable pending obligations when
the MQTT component is absent: revoke returns 503, and Pair/Replace cannot issue
a new code until broker retirement is confirmed. Code creation repeats that
check under the printer lock, so a separate route cannot bypass the guard.
Packets/commands admitted before retirement may already be in flight; revoke
cannot undo a command already received or executed by a printer.

Firmware retains HTTPS identity and MQTT secret separately in `edol_agent_sec`.
It writes an attempt marker before the single provisioning POST and a readiness
marker after a complete MQTT-secret write. Re-pairing invalidates those MQTT
markers. Reconnect/restored firmware uses the retained MQTT secret, revalidates
TLS and resubscribes; network/auth failures do not erase identity or downgrade.
The secure default broker port is 8883; explicitly stored ports remain explicit.

## Core routing and service compatibility

Secure ingress parses the exact versioned topic, resolves an active usable
persisted authority, and opens its tenant scope before existing telemetry,
heartbeat or event processing. Contradictory top-level identity fields and
retained reports are rejected. The MQTT message does not carry authenticated
publisher metadata: this boundary depends on the tested broker ACL and Core's
exclusive access to its ingress account. Ownership never comes from payloads
or legacy `PrinterConnectionConfiguration.agentId`.

Secure command routing resolves the current authority from the persisted
tenant/printer binding. Home alone retains configured IDs, old topics and
trusted-network transport. Home NVS, HTTP routes, setup AP and portal procedure
are preserved. Local printer MQTT/FTPS/camera certificate verification remains
a separate, unchanged LAN limitation.

Service event topics, v2 publication and v2/v3 readers are unchanged. Their
secure Paho connections require `ssl://host:port`, credentials and hostname
verification. JVM trust comes from the default trust store or separately
configured JVM trust-store properties; private trust must be supplied to all
four services. A CA/SAN-valid connection must be checked from each service
network, including internal hostname `mqtt` where used.

`docker/compose.yaml` and `compose.home.yaml` select Mosquitto with the trusted
Home listener. Secure deployment MUST additionally apply
`compose.mosquitto-secure.yaml`, which replaces that configuration mount and
uses the TLS-only listener on the same broker service. Do not run the base
composition as a secure deployment. The historical Stage 6 NanoMQ fixture is
retained as previous acceptance evidence; its plaintext client configuration
does not satisfy the new secure TLS prerequisites.

## Acceptance and rollback

Run the new broker/routing/migration tests through the remote Docker skill.
The fixture generates run-owned TLS material and service authorities, and uses
the production configuration/policy generator in fresh containers only.
Synthetic commands never reach a real printer. Report exact Surefire totals
and distinguish fake firmware transport tests from actual broker handshakes.

Required source/disposable cases include shared service traffic; paired
TLS/auth; unknown/revoked credentials; foreign/wildcard/shared topics; spoofed
identity; current-session disconnect; broker restart persistence; Core routing;
provisioning/revoke failures; and Home builds/contracts. Physical execution,
power-cycle behavior and real printer effects remain NOT RUN until the combined
[Agent + AMS hardware runbook](stage9-disposable-hardware-runbook.md) is executed
with the operator.

Verification record, 2026-10-22 (at verification time, a local increment over backend
`d701243e6da9e6bed86baecefb561c480a3f6b62` and firmware
`a51249d9f00d3a008cb9a23df468418764fac344`):

- Remote-Docker `mvn -B clean verify`: 294 tests, zero failures/errors/skips.
  Core API 11, Core 93, Hub 127, Notify 15, AMS 48. Real PostgreSQL and
  Mosquitto containers executed, including paired identity through broker to
  persisted Core routing, active-session revoke, and service recovery.
  Service broker recovery uses Paho clients with the production service
  accounts/ACLs; coordinated four-service runtime cutover remains a rollout
  rehearsal. After the final analyzer timing fixes, nine targeted broker,
  migration and management tests passed with no failures/errors/skips.
- Resolved broker image digest:
  `eclipse-mosquitto@sha256:199ea8ef2e35ec2b1b37e59cfd1dbae538ed4dfa4a2251a121a52215a6248a21`.
- Firmware: eight native executables, three Python trust-hook tests, and all
  Home/secure/secure_smoke builds passed. Both secure builds without CA failed
  as required. The temporary public CA fixture was removed.
- Read-only Sonar covered all supported modified Java/Python/HTML files.
  Actionable findings were fixed. Remaining Java S1598 reports have an empty
  analyzer path and are the known snippet artifact; no suppression was added.
  Available snippet analysis does not support C++ or SQL.
- Static SnakeYAML parsing passed for the three changed/new Compose files and
  Core secure YAML. Docker Compose merge/interpolation is NOT RUN because the
  local CLI has no Compose plugin; it remains a rollout preparation check.
- At verification time no physical observation, working DB migration, live
  broker change, commit or push had occurred. Subsequent local commits are
  recorded in the hardware runbook execution record; no continuous
  legacy-traffic observation window is claimed.

Resumed-session review on 2026-10-22 found and fixed a feature-disable lifecycle
gap: a missing MQTT component could previously bypass the broker retirement
check. Core now consults durable pending state independently of that component
and rechecks before code insertion under the printer lock. HTTPS retirement
still commits before the management route reports pending broker work.
The targeted remote-Docker regression passed 16 tests with zero failures,
errors or skips: management 2, real broker 2, mode isolation 2, secure HTTP 3,
PostgreSQL pairing/migration 7. This is a targeted continuation, not another
full-reactor run. Read-only Sonar rechecked the four changed Java files; only
the known pathless S1598 artifact remained. Firmware and physical observations
were unchanged, and no running installation was modified.

Working migration is a later Stage 9 gate: inventory clients/settings, preserve
the existing broker rollback configuration, prepare roles/V11 and authority
storage, verify TLS/ACL/reconnect for every service, and explicitly approve the
cutover. Do not copy NanoMQ password files or invent mappings to secure Agents.
Keep feature flags OFF until coordinated acceptance. No observed legacy-usage
window is established by synthetic tests.

Rollback disables Agent features and restores the coordinated release/broker
configuration, preserving V11 and the latest authority/tombstone data. Never
restore stale authority data that revives revoked identities, remove migrations,
open anonymous secure access, erase NVS or flash implicitly. Home restoration
requires reviewing its saved destinations because it can resume legacy traffic.
Retire broker authorities while broker control is available before disabling
the MQTT component. A disabled feature alone does not close existing sockets;
an unfinished disconnection must remain explicitly pending.
