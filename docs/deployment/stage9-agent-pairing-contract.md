# Stage 9 Agent Pairing Contract

## Scope and acceptance boundary

Current continuation (2026-10-22): the approved single-Mosquitto secure MQTT
increment is described in [the MQTT contract](stage9-agent-mqtt-contract.md).
It supersedes the MQTT-disabled implementation boundary recorded below for
9.3b.1/9.3b.2. Working rollout and physical acceptance remain open.

Stage 9.3b.1 implements the server-side pairing and HTTPS upload foundation.
It is not complete secure-agent acceptance or a deployment recipe. Agent
firmware, broker authentication/topic ACLs, active-session revocation, secure
Core agent MQTT routing and physical transport verification remain required.
Do not enable this feature in a working installation until those gates pass.
The EDOL-AGENT cleanup/publication handoff is complete. The sanitized baseline
is private GitHub `serge-ponomarenko/EDOL-AGENT`, `main` at
`14da63f360d4a288c7207340dc60cd9e78ce7907`. Its local publication checkout is
`C:\Users\Ponomarenko\Documents\PlatformIO\Projects\EDOL-AGENT-publication-20261021`;
that branch/SHA and clean working tree were checked locally. Remote verification
is supplied by the publishing session, not repeated here. The original
`EDOL-AGENT` checkout and its secret-bearing history remain local and must not
be pushed. Stage 9 firmware work starts from the sanitized baseline in a
separate worktree/branch, preserving the published checkout and local settings.

The cleanup preserves existing topics, endpoints and Preferences while fixing
snapshot HTTP-status handling and camera-header timeout. Those fixes are part
of the baseline and must remain covered. Plaintext EDOL transports, unchecked
printer certificates and the open, credential-prefilling setup portal are known
remaining boundaries, not secure acceptance. Previously exposed credentials
require a separate operator-led rotation; this source work does not rotate them.

Baseline verification was repeated in the isolated worktree: both offline C++
executables passed using `py -3 scripts/run_host_tests.py`, and PlatformIO
`lolin_d32` built successfully (RAM 14.5%, flash 75.5%). No device, serial
monitor, MQTT, live backend or NVS operation was performed. These checks
establish the sanitized Home baseline only, not pairing or TLS acceptance.

Home keeps the existing trusted-network setup and legacy agent endpoints.
It initializes no pairing beans or keys and does not apply secure V10.
Pairing is optional infrastructure for secure mode only, disabled by default:
`edol-core.agent.enabled` in Core and `edol-hub.agent.enabled` in Hub.
Enabling either flag in Home does not enable pairing.

## Ownership and flow

Core owns the durable agent identity, pairing-code digest, credential digest
and binding to an existing enabled AGENT-mode printer and tenant. One live
identity per printer is supported; one device currently serves one printer.
Hub checks current tenant OWNER membership and local printer access before
calling Core through its existing tenant-propagating service client. Core
independently checks the trusted service client, `tenant.context`, the dedicated
`core.agent.manage` scope and persisted printer ownership. Agent credentials
do not grant management, printer commands, service scopes or tenant delegation.
No existing service scope is silently expanded and no Keycloak client is
modified by this source change.

The owner opens **Manage printer agent** on the printer edit page and requests
a code. The code is shown only in that POST response, with `no-store`; it is
not placed in a redirect, flash/session attribute, status response or log.
The current status GET contains lifecycle metadata only. The local agent setup
accepts this code and obtains its own credential through the separate firmware
HTTPS increment documented below; firmware is not part of the server increment.

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
forwarding headers. Source tests do not establish actual TLS/proxy behavior.

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
for subsequent HTTPS requests. This does not yet disconnect MQTT sessions.

Uploads reuse all Stage 9.3a byte/type/path/CRC/atomic-write guards. Rejected
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

New secure-only Flyway V10 creates `core.agents` with forced RLS, lifecycle/
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
The Stage 5 disposable test models this separate bootstrap explicitly.

## Compatibility, rollout and rollback

The legacy Home endpoints/topics remain unchanged. Secure legacy agent upload
routes stay denied; this increment does not enable or secure the generic MQTT
bridge, change MQTT topics, synchronize the legacy configured agent ID, or
provision broker users. The new enrollment secret is an HTTPS credential;
firmware must not assume that it is already a provisioned MQTT password.
No live traffic/zero-use observation is claimed.

Deploy source only after coordinated firmware and broker acceptance. Keep both
feature flags off until then. Prepare V10 roles/keys/scopes first, then Core,
then the opt-in Hub page, then compatible firmware. Working dev/prod migrations
still wait until Stage 9 completion and separately authorized cutover.

Rollback disables both feature flags and restores the preceding coordinated
release while leaving V10 data intact. Do not delete identity data, reverse
accepted migrations, enable legacy secure trust, or flash devices implicitly.
Key rollback and compatible firmware recovery require a separate operator plan;
never make revoked credentials valid again as a recovery shortcut.

## Remaining secure-agent gates

- Stage 9.3b.2 now implements firmware HTTPS enrollment/uploads in the isolated
  firmware branch. Finish secure MQTT transport and its coordinated identity
  contract; preserve explicit Home/secure selection and NVS.
- Operator decision on 2026-10-21: retain the existing open setup Wi-Fi AP and
  connection procedure unchanged. No AP password, label/card or new local
  authentication is required in this increment. The setup portal remains a
  local-trust boundary, including existing credential prefilling; secure
  internet transport does not establish protected local onboarding. Do not
  make a later AP-hardening decision an implicit prerequisite for this stage.
  New pairing secrets are never prefilled or printed, and the pairing code is
  not persisted. Keep local printer certificate
  limitations separate: current Bambu MQTT, FTPS and camera clients use
  `setInsecure()`, which is not server-authenticated internet transport.
- Verify a broker-specific per-agent identity/ACL and lifecycle mechanism,
  including active-session revocation. Keep per-client certificates and
  TLS-plus-distinct-agent-credentials as explicit alternatives until disposable
  evidence supports the chosen approach. Pairing does not itself settle this
  transport decision or waive the Stage 9 certificate requirement.
- Bind secure Core MQTT ingress and command egress to the persisted identity;
  reject foreign/wildcard topics and prevent legacy configured IDs from acting
  as authority. Preserve Home behavior.
- Verify unknown CA, hostname mismatch, expiry, reconnect, power-cycle recovery,
  revoke/replace and lost enrollment response on an explicitly authorized
  device after source/disposable checks. No physical acceptance is claimed here.

## Stage 9.3b.2 firmware HTTPS increment

The sanitized baseline remains `14da63f`; new firmware source lives in the
isolated `stage9-agent-pairing` branch/worktree, without a commit or push implied
by this record. `lolin_d32` is still the default Home environment, with no CA
prerequisite. Explicit `lolin_d32_secure` sets `EDOL_AGENT_SECURE_BUILD=1` and
requires `EDOL_AGENT_ROOT_CA_FILE`. The pre-build hook validates a certificate-
only public trust bundle and generates a build-local header preserving PEM
newlines. No certificate/key data is added to source control. The existing
Home `edol_config` Preferences namespace and keys retain their meaning.

The secure setup page replaces manual agent ID/password fields with the
one-time code. It retains the same AP, DNS portal and save route. Saving joins
the configured Wi-Fi station while retaining the AP, synchronizes time and
performs one HTTPS enrollment request. The code exists only during that request.
Enrollment accepts only `200`, disables redirects, bounds the streamed response
to 1024 bytes, validates the returned UUID/secret format and commits the identity
to the separate `edol_agent_sec` namespace with a final ready marker. Interrupted
writes fail closed rather than loading mixed identity fields. An uncertain
enrollment or failed storage write requires owner rotation/new pairing; no
automatic retry or plaintext fallback occurs.

Secure uploads use `WiFiClientSecure` with the configured CA, host-based
connection and a synchronized-clock prerequisite. The clock floor is build
time minus one day; ordinary SNTP supplies time, not an authenticated-time
guarantee. Connection/read timeouts are bounded per operation, not an overall
upload deadline. Secure DNS/IPv4 hosts reject schemes, separators and control
characters. Existing stored ports remain explicit settings; the secure default
is 443. Agent identity comes only from the new namespace and is never taken
from legacy Home `agentId` settings or the snapshot caller argument.

Both secure upload routes use the fixed v1 paths and the issued HTTPS
credential. Existing streamed writes, incomplete-response rejection and the
cleanup session's HTTP-aware snapshot health remain covered. Secure
`EdolMqttClient::connect()` currently fails closed: the HTTPS secret is not
silently reused as a plaintext MQTT password. This firmware profile is
development-only and not a usable complete secure MQTT agent yet. Keep working
deployments unchanged until broker and physical acceptance pass.

The existing reset detector and explicit factory-reset behavior are not invoked
by source checks. In secure firmware, that reset path also clears its new
identity namespace. Reflashing Home does not itself erase that namespace or
settings; rollback/recovery must be deliberate and separately authorized.

Verification: five native C++ executables and three Python trust-hook cases
passed. Both ESP32 profiles built (Home RAM 14.5%, flash 75.6%; secure RAM 14.9%,
flash 78.1%). Secure-without-CA failed closed; the successful secure build used
a temporary public CA fixture. The final Python delta passed after explicit
TLS-1.2 minimum configuration in the CA-parsing context. All three supported
Python files passed read-only Sonar snippet analysis; C++ is not supported by
that tool and no C++ Sonar or physical TLS result is asserted.

Final revalidation on 2026-10-21: full remote-Docker reactor passed **287 tests,
zero failures/errors/skips**, including PostgreSQL migration checks. Firmware
native five and Python three checks passed again. Home, secure and the separate
`lolin_d32_secure_smoke` ESP32 builds passed; both secure environments reject a
missing CA. Lab flash usage is 78.2%; ordinary secure remains 78.1%.

The [disposable physical smoke workbook](stage9-agent-disposable-smoke-workbook.md)
is prepared under the operator's explicit real Agent/printer test authorization.
Its lab-only USB console invokes production snapshot/model read-and-upload
services, reports safe numeric HTTP/TLS diagnostics, and can reopen the existing
setup portal without clearing settings for deliberate replacement pairing.
Home and ordinary secure firmware contain no console. These are direct USB
triggers, not secure MQTT acceptance. Physical execution/restoration, broker
ACLs and active-session revocation remain untested; no push or rollout occurred.
