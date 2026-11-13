# Agent MQTT Contract

Secure mode uses one Mosquitto broker with Dynamic Security for service
events and Agent traffic. Independent per-client credentials authenticate
clients over server-authenticated TLS. Agent client certificates are optional
in the architectural decision. The broker is a shared availability boundary.
The [pairing contract](agent-pairing.md) defines the separate HTTPS identity.

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
composition as a secure deployment.

## Verification and rollback

Verify service traffic, per-Agent ACL isolation, denied unknown/revoked
credentials, foreign and wildcard topic rejection, spoofed identities,
active-session disconnect, broker restart persistence, Core routing,
provisioning/revoke failure handling and Home compatibility. Use isolated
broker authorities and synthetic commands disconnected from real printers.
Physical device evidence covers MQTT TLS rejection, reconnect, power-cycle
recovery and restoration under separately approved scope.

Rollback disables Agent features and restores the coordinated release/broker
configuration, preserving V11 and the latest authority/tombstone data. Never
restore stale authority data that revives revoked identities, remove migrations,
open anonymous secure access, erase NVS or flash implicitly. Home restoration
requires reviewing its saved destinations because it can resume legacy traffic.
Retire broker authorities while broker control is available before disabling
the MQTT component. A disabled feature alone does not close existing sockets;
an unfinished disconnection must remain explicitly pending.
