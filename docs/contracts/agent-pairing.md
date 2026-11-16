# Agent Pairing and HTTPS API

Pairing is opt-in in `secure-multi-tenant` mode via
`edol-core.agent.enabled` and `edol-hub.agent.enabled`, both off by default.
Home retains legacy trusted-network endpoints, uses `X-Agent-Id` only as
a routing hint, and initializes neither secure pairing nor its keys or tables.

## Ownership and flow

Core owns the durable agent identity, pairing-code digest, credential digest
and binding to an existing enabled AGENT-mode printer and tenant. One live
identity per printer is supported; one device currently serves one printer.
Hub checks current tenant OWNER membership and local printer access before
calling Core through its existing tenant-propagating service client. Core
independently checks the trusted service client, `tenant.context`, the dedicated
`core.agent.manage` scope and persisted printer ownership. Agent credentials
do not grant management, printer commands, service scopes or tenant delegation.

The owner opens **Manage printer agent** on the printer edit page and requests
a code. The code is shown only in that POST response, with `no-store`; it is
not placed in a redirect, flash/session attribute, status response or log.
The current status GET contains lifecycle metadata only. The local agent setup
accepts this code and obtains its own credential through the HTTPS flow below.

## Management API

Hub browser routes live at `/printers/{printerId}/agent`:

| Method and suffix | Behavior |
| --- | --- |
| GET, no suffix | Owner-authorized status page |
| POST `/pairings` | Issue a code; live identity/unexpired pairing is `409` |
| POST `/rotate` | Revoke prior live identity/pending code and issue a new code |
| POST `/revoke` | Revoke live identity/pending code; repeated revoke is safe |

The existing secure Hub session authentication and CSRF protection apply.
Backend routes have the same suffixes under
`/api/printers/{printerId}/agent`, including a GET for safe status (`404` when
there is no identity). Core creation requires an enabled AGENT-mode printer;
revocation remains available for an owned disabled printer or after changing
its connection mode. Foreign printers return `404`. Printer-row locking and
the unique live-identity index serialize concurrent management operations.

## Device API v1

All device routes are under `/api/agent/v1`, in a separate stateless security
chain. Core requires a secure servlet request, sets `no-store`, and rejects
device-supplied `X-Agent-Id` and `X-EDOL-Tenant-Id`. The deployment must either
terminate TLS in Core or correctly establish secure requests through a trusted
proxy. Only that proxy may reach a plaintext upstream; it must strip client
forwarding headers. The device-facing proxy must be verified in the deployment topology.

| Method and suffix | Input | Successful response |
| --- | --- | --- |
| POST `/enroll` | JSON object with `pairingCode` only; no credentials needed | JSON `agentId` and one-time `secret` |
| POST `/upload?fileName=...` | Raw `application/octet-stream` plus agent authentication | `200`, only after model validation and atomic promotion |
| POST `/camera/snapshot` | Raw `image/jpeg` plus agent authentication | `200`, only after validation and store update |

The upload authentication scheme is `EdolAgent`, with a credential formed from
the issued agent UUID, a dot, and its secret. There must be exactly one
authorization value. A JWT service token, a Basic credential or an `agentId`
header cannot substitute for it. Authentication resolves the persisted tenant
and printer before body consumption; the device cannot select either binding.
The tenant context and security context are cleared after the request, including
failure. Invalid/revoked credentials return generic `401`; no credentials are
returned by status or authentication failures. An already admitted request may
finish during concurrent revocation; later requests cannot authenticate.

Enrollment JSON is limited to 256 actual streamed bytes. Codes contain 12
case-insensitive Crockford Base32 characters (60 random bits), expire after
five minutes and are consumed by one atomic PostgreSQL update. They are not
retrievable. Invalid, expired and replayed codes receive the same `401`.
A bounded process-local admission limiter allows five attempts per remote
source in five minutes, including successful attempts, with at most 4096
active source entries. A full limiter fails closed with `429`. Reverse-proxy
rate limiting, trusted source-address handling and shared-NAT behavior must be
verified before deployment; restarts/replicas do not share this local budget.

The secret contains 32 random bytes encoded as unpadded Base64url. Only
domain-separated keyed HMAC-SHA256 digests are stored. Enrollment responses
are not retried to retrieve a secret: if the response is lost, the owner rotates
the identity and pairs again. Rotation invalidates the old identity immediately
for subsequent HTTPS requests. Broker-session retirement follows the [MQTT lifecycle](agent-mqtt.md#provisioning-retirement-and-recovery).

The upload guards below apply to both secure and Home Agent uploads. Rejected
upload-filter inputs preserve prior files/snapshots and emit no model event.
Semantic metadata/event-consumer errors after successful promotion remain
outside that filter guarantee. Error audit logs use fixed status fields;
accepted uploads log identity/byte counts, never codes, credentials, headers
or raw payloads.

## Keys, schema and roles

Secure Core binds `edol-core.agent.key-version` (default 1),
`edol-core.agent.pairing-keys` and `edol-core.agent.credential-keys`, both
version-to-Base64-key maps. Each configured key must decode to at least 32
random bytes. Only the current and optional immediately previous versions are
accepted. Use keys separate from AMS terminal keys. Supply them through the
deployment secret mechanism, not source control. Missing/invalid keys fail
startup when the secure feature is enabled; Home and disabled mode need none.
Retain an immediately previous credential key while its agents are re-paired;
removing it invalidates those credentials. Do not accumulate unbounded old keys.

Secure-only Flyway V10 creates `core.agents` with forced RLS, lifecycle/
digest constraints and one-live-agent-per-printer uniqueness. It does not
backfill or change existing agent IDs, printer settings or accepted migrations.
Printer deletion cascades to its new identity records. Runtime tenant policies
also validate the associated printer; pre-tenant authentication independently
joins current printer ownership, enabled state and AGENT connection mode.

Two fixed, PUBLIC-revoked `SECURITY DEFINER` functions permit code consumption
and active-credential lookup before a tenant is known. They use a fixed search
path and a dedicated `core_agent_authenticator` owner with NOLOGIN,
NOSUPERUSER, NOCREATEROLE, NOINHERIT and NOBYPASSRLS. Its role-specific policies
permit only the operations required by those functions. Runtime has no
membership in this role and cannot switch into it. It receives function
execution and ordinary tenant-RLS table access, not bypass privileges.

Before V10 under the normal restricted Flyway role, administrator bootstrap
must prepare that function-owner role and allow `core_schema_owner` to transfer
function ownership to it. Do not grant role creation or owner membership to
`core_runtime`. V10 rejects unsafe owner attributes/runtime membership, grants
function execution before transferring ownership, and revokes the owner's
temporary schema CREATE permission. Bootstrap/migration/rollback permissions
must be rehearsed on restored disposable copies before working DB cutover.

## Upload validation

Models use portable basename validation, no-link directory/target checks, and
same-directory staging with atomic replacement only after package validation.
Upload-filter rejection preserves the previous model and emits no
`ModelAvailableEvent`. JPEG snapshots are byte-bounded and decoded only after
dimension checks, before replacing the in-memory snapshot.

`edol-core.upload` defaults are 256 MiB per model, 8 MiB per snapshot, 16 MiB
per selected metadata entry, 64 MiB total inflated metadata including package
markers, 10,000 archive entries, and 40 million snapshot pixels. Limits are
configurable without enabling OAuth or tenancy. Package checks require ZIP
structure and the two package markers, safe distinct entry names, and bounded
CRC-checked reads of selected metadata; they do not validate the full 3MF/XML
schema or inflate unused model/G-code entries. Metadata extraction stages all
selected outputs before per-file atomic promotion. It is not a multi-file disk
transaction if an I/O failure occurs during promotion. Slice-info XML parsers
reject DTDs and external entities. Storage assumes application-owned directories
with permissions preventing concurrent local replacement by another user;
remote path/link defenses do not provide a privileged-local-process boundary.

## Firmware identity and transport

`lolin_d32` is the default Home build. `lolin_d32_secure` selects secure
firmware and requires `EDOL_AGENT_ROOT_CA_FILE`, a public CA bundle supplied
through a validated build-local header. Home retains its `edol_config` settings.

The secure setup page accepts the one-time code through the existing setup AP
and DNS portal. Save & Reboot uses Wi-Fi, synchronized time and verified TLS
to enroll and provision MQTT, then restarts into normal operation. A pending
code exists only in RAM for up to five minutes, subject to server expiry.
A pre-send transport failure can be retried. An uncertain response requires
owner recovery.
Enrollment accepts only `200`, disables redirects, bounds the streamed response
to 1024 bytes, validates the returned UUID/secret format and commits the identity
to the separate `edol_agent_sec` namespace with a final ready marker. Interrupted
writes fail closed rather than loading mixed identity fields. An uncertain
enrollment or failed storage write requires owner rotation/new pairing; no
automatic retry or plaintext fallback occurs.

Secure uploads use `WiFiClientSecure` with the configured CA, host-based
connection and a synchronized-clock prerequisite. The clock floor is build
time minus one day; ordinary SNTP supplies time, not an authenticated-time
guarantee. Model streaming has a fifteen-minute overall FTPS deadline and a
ten-second data-idle deadline. HTTP acknowledgement after the body has a
sixty-second budget; HTTPS connection/read timeouts are bounded separately.
Secure DNS/IPv4 hosts reject schemes, separators and control
characters. Existing stored ports remain explicit settings; the secure default
is 443. Agent identity comes only from the new namespace and is never taken
from legacy Home `agentId` settings or the snapshot caller argument.

HTTPS and MQTT require chain, signature, validity-date, usage and endpoint
verification. Canonical IPv4 endpoints require an exact binary iPAddress SAN.
DNS endpoints use DNS-name matching. Release verification must confirm that
the linked SDK enforces X.509 certificate dates, even with a synchronized
clock. See [device TLS verification](../operations.md#device-tls-and-restoration).

The setup AP remains open under the local onboarding trust model. Pairing
secrets are never prefilled or logged. Bambu MQTT, FTPS and camera connections
have a separate trusted-LAN boundary and currently use `setInsecure()`.
Ordinary Home and secure builds omit the optional lab USB console.

Reflash and rollback preserve NVS and identity unless an explicitly approved
factory reset is performed. The secure factory-reset path clears
`edol_agent_sec`. An app-only Home reflash leaves that namespace intact.
Use the [MQTT contract](agent-mqtt.md) for provisioning and retirement and
[operations](../operations.md) for coordinated rollout and rollback.
