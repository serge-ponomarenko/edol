# EDOL Architecture

## System Map

EDOL is a Java 21, Spring Boot 4 Maven reactor with four services, PostgreSQL and MQTT. Source composition now selects Mosquitto; replacing an existing NanoMQ installation remains a separately approved rollout. The root `pom.xml` is the build entry point; Docker Compose is the local/self-hosted runtime composition.

| Module | Responsibility | Main boundaries |
| --- | --- | --- |
| `edol-core-api` | Shared printer-state and AMS DTOs. | Consumed by all services; changes are cross-service API changes. |
| `edol-core` | Printer provisioning, direct/agent telemetry, commands, model metadata, camera, runtime and recovery state. | Bambu printer protocols, agents, MQTT, `core` PostgreSQL schema, HTTP state API. |
| `edol-hub` | Persistent operational domain and dashboard: inventory, spools, print jobs, allocation, maintenance, labels. | `hub` PostgreSQL schema, Core HTTP API, Core MQTT events, browser clients. |
| `edol-notify` | Printer-scoped Telegram commands and printer/print notifications. | Core HTTP API, Core MQTT events, Telegram. |
| `edol-ams` | Printer-scoped AMS workflow and secure terminal enrollment. | Core and Hub HTTP APIs, Core MQTT events, `ams` PostgreSQL schema. |

## Runtime and Data Flows

1. A printer reports telemetry directly or through an agent. Core resolves the printer, updates its in-memory runtime state, and derives application events.
2. Core handles those events for print lifecycle, metadata, camera, recovery, and commands, then publishes integration events under `edolcore/#`.
3. Hub consumes the Core integration-event contract for persistent print and inventory effects. In `secure-multi-tenant` mode, Notify consumes only its declared printer and print topics, and AMS consumes only `edolcore/ams` and `edolcore/print/ams`; both validate the additive v2 envelope before using its tenant context. Home retains the legacy trusted-network subscriptions.
4. Hub, Notify, and AMS also call Core over HTTP for current printer state or commands. Hub owns its persistence; Core owns printer connectivity and runtime state.

Core runtime is per printer. New printer, connection and session identifiers use UUID v7; historical UUID v4 values are retained. Its HTTP API exposes a printer catalog and explicit `{printerId}` state, command, and media endpoints; default-printer HTTP endpoints are not supported. Hub, AMS and Notify use only explicit printer endpoints. Any change that carries printer identity through MQTT or HTTP must be coordinated across every producer and consumer.

DIRECT Paho report/disconnect callbacks restore the tenant from the trusted
runtime registry on the callback thread. Asynchronous startup recovery and scheduled connectivity checks
execute within that same trusted runtime tenant boundary. Recovery and reconnect
do not inherit a scheduler/caller tenant or resolve one from printer payloads.
Direct connection and camera
configuration reads use the transactional printer-management service so the
secure RLS tenant setting is established; network I/O remains outside that
configuration transaction. Transport connectivity alone is not proof of
processed telemetry: overview readiness waits for usable printer state.

Hub projects Core printers with the same UUID and assigns the projection to a Hub tenant. Hub runtime state, print recovery, jobs, allocation, maintenance, statistics, commands, dashboard routes and printer-management UI are printer-scoped. In `secure-multi-tenant` mode, Hub is an OIDC BFF: a validated OIDC identity discovers active memberships before a request establishes the fail-closed `TenantContext`. The historical default-tenant request bridge is removed. Stage 5 establishes authenticated Hub-to-Core tenant propagation. The `stage5-smoke` profile deliberately keeps Core printer runtime, Hub MQTT, catalog, recovery, AMS, and Notify disabled; later MQTT and consumer stages must not infer a tenant. AMS retains only its separately trusted three-route internal compatibility ingress. Home uses its independent installation-owned tenant scope. AMS staging and Notify progress state are keyed by printer UUID. See `docs/adr/0001-hub-printer-projection-and-tenant-bootstrap.md` and `docs/migrations/multi-printer-multi-tenant.md`.

## Accepted Multi-Tenant Direction

The secure multi-tenant target is accepted. The Stage 4 Hub BFF and Stage 5
authenticated Hub-to-Core foundation are implemented and accepted for their
source and disposable remote-development scopes; production acceptance and the
later MQTT and consumer-migration stages remain outstanding. Hub owns EDOL users,
tenants, memberships and roles while Keycloak provides OIDC identity and
credential lifecycle. Hub operates as a BFF, and services will use separate
OAuth client identities. Authentication identity and trusted tenant context
remain separate.

Stage 2 adds the Hub user and membership domain, direct tenant ownership for
allocation and usage links, tenant-safe cross-aggregate constraints, and
nullable opaque tenant ownership on Core printers with a guarded one-time
Hub-projection backfill. Source, local-development, and production acceptance
are complete. On 2026-09-24, the controlled production Hub V2 history repair
updated only the historical checksum, then the matching release applied Hub V5
and V6 and served the read-only root request. Stage 2 does not enable Hibernate
tenancy, RLS, authentication, service transport, or MQTT tenant propagation.

Tenant persistence will use Hibernate discriminator tenancy together with
forced PostgreSQL row-level security. Normal tenant resolution will be
fail-closed. The current default-tenant behavior may survive only as a named,
measured migration compatibility scope before user authentication and will be
removed in that authentication stage.

The Stage 3 source implementation adds this Hub boundary: approved direct
tenant entities use Hibernate discriminators, tenant state is transaction-local
on the runtime connection, and PostgreSQL policies provide the native-SQL
boundary. The controlled production rollout provisioned the separate Hub schema
owner, Flyway executor, and runtime roles and applied Hub V7 successfully on
2026-09-28. A follow-up Stage 3 release prepares an eager serialization graph
for the unchanged legacy spool payload, logs compatibility-counter values, and
contains a controlled runtime-grant correction that removes access to Flyway
history. The follow-up was deployed successfully: runtime Flyway-history
privileges were removed, Flyway privileges were preserved, and compatibility
logs now expose incrementing metric values. A production request matching an
active spool returned `HTTP 200` with the complete unchanged JSON graph and no
lazy-proxy serialization error. Stage 3 was formally accepted on 2026-09-28.
Stage 4 source replaces the pre-auth Hub ingress with Keycloak Authorization
Code + PKCE BFF login, server-side servlet sessions and authorized-client
storage, active-membership tenant selection, and idempotent JIT provisioning.
V8 adds transaction-local OIDC identity RLS policies and a closed-by-default,
serialized legacy first-owner claim. It deliberately does not drop
`tenants.is_default`: that cleanup is authored only after a recorded claim.
Remote-dev and source-development acceptance completed on 2026-10-04 from the
immutable rollout commit `b849831fbf17d39db33c735883af63ca1b5ab6b2`. Empty and
data-bearing clone smoke tests passed, followed by a backup-first V8 rollout
and one guarded persistent-owner OIDC claim on the source development
database. The source result retained legacy data counts and integrity, created
one active `OWNER` membership, and cleared the claimed tenant's
`is_default` marker. Browser OIDC, CSRF, session-expiry, and logout behavior
passed using the same Hub-only secure profile during the acceptance smokes.
Core, MQTT, AMS, Notify, printer-facing services, and secure-mode background
work did not start. This is development evidence only: production Keycloak,
production Hub deployment, and trusted production AMS ingress remain
unaccepted.
Stage 5 source and final fresh disposable acceptance completed on 2026-10-05
from immutable commit `7e5f66a295675d8b365a667ead3d5a997d71e2b0`. A fresh
restricted-role PostgreSQL cluster applied Core V1-V9 and Hub V1-V8; Core and
Hub started under `secure-multi-tenant,stage5-smoke`; BFF login, authenticated
Hub provisioning, Core/Hub projection, RLS, and catalog-role isolation passed.
The acceptance also confirmed fail-closed tenant-header and service-token
responses and the scalar Hub printer-stats response. It used the accepted
remote-development Keycloak issuer only, with no MQTT broker, live printer or
agent, AMS, Notify, development database, or production resource. It is not a
development-database or production rollout.
Spring Data repositories initialize lazily so framework bootstrap has no tenant
context; their first actual use still requires the normal fail-closed resolver.
A clean installation may have no migration tenant because Stage 2 removes an
empty historical seed. In that state, compatibility background work is logged
and metered as unavailable and does not run; HTTP and MQTT persistence remain
fail-closed until an explicitly configured, verified migration tenant exists.

Core will remain passive. Its printer records will store an opaque tenant UUID
assigned by a one-time migration backfill or an authenticated Hub provisioning
request; Core will never call Hub to infer ownership. Core background runtime
discovery will use a narrow read-only catalog identity, not a universal RLS
bypass. Stage 6 source publishes each tenant-owned Core integration event as
one additive v2 message: its trusted envelope has a schema version, event ID,
event type, tenant and printer IDs, RFC 3339 timestamp, and nested payload,
while the legacy `event` and top-level fields remain in that same message. Core
derives the tenant only from persisted printer ownership. Secure Hub MQTT
ingress is disabled by default and uses tenant/printer validation plus a
tenant-RLS receipt ledger before it mutates its projection. The
`stage6-smoke` profiles and `docker/compose.stage6-smoke.yaml` provide a
local-loopback, disposable ACL harness only: generic agent MQTT and printer
runtime remain disabled, while the dedicated Core publisher and Hub subscriber
are enabled. On 2026-10-09, the Stage 6 disposable MQTT integration/security
acceptance passed with a fresh PostgreSQL cluster and NanoMQ 0.25.6 harness.
It verified authenticated Core publish and Hub subscribe ACLs, additive v2
delivery, tenant-RLS receipt persistence, QoS 1 duplicate idempotency,
reverse-timestamp receipt persistence, and malformed, unknown-printer, and
cross-tenant rejection. A temporarily approved Hub browser ingress deviation
was reverted during cleanup. This is not deployment configuration, TLS,
production broker-identity, retention-operation, live-device, development
database, or production evidence. Stage 7 source now gives Notify and AMS
independent client-credentials contracts and restricts their secure MQTT topic
sets. Notify establishes its tenant from a deployment-managed tenant-to-chat
mapping; AMS establishes it from a deployment-managed printer-to-tenant mapping
or the validated v2 envelope, never from an untrusted terminal parameter.
AMS can start with an empty printer mapping; unknown printers/envelopes are
still rejected before downstream work. Notify can start with Telegram disabled
and no recipients; an empty mapping authorizes no tenant or chat. Enabling
Telegram requires a bot token and, in secure mode, nonempty validated recipient
mappings. Disabling Telegram never starts polling.
Core accepts the tenant header only from allowlisted service clients, and Hub
accepts AMS service JWT ingress only on the exact spool routes. Keycloak
clients, broker identities/ACLs, and mappings were provisioned only in an
isolated disposable Stage 7 smoke. The initial 2026-10-09 run was partial; its
AMS lookup and unmapped-printer defects were remediated with a narrow AMS
Hub-spool read DTO and a pre-downstream `404`. Final disposable secure
multi-tenant acceptance passed on 2026-10-10 at
`88145ac1af2b8b4936e7595f674af062dd3622ee`: four safe AMS HTTP cases,
Tenant B Notify v2 ingestion, recipient/mapping/counter regression coverage,
and the previously recorded RLS, Flyway, ACL, and service-token evidence were
reconciled. Core legacy compatibility ingress was explicitly disabled in that
disposable configuration; Hub's retained compatibility ingress initialized its
counter at zero and accepted no legacy requests during the recorded observation
window. This is source/disposable acceptance only. It does not accept
deployment, TLS, development-database, live-device, home-mode, terminal
identity, or production behavior. Compatibility source paths remain subject to
the Stage 9 removal gate. Stage 8 owns terminal enrollment and per-device
credentials.

Stage 8 uses an AMS-owned `ams` schema with tenant-scoped terminal and pairing
records, forced RLS, and narrow security-definer functions for pre-tenant
pairing consumption and terminal credential lookup. AMS runtime remains a
non-owner, `NOSUPERUSER`, `NOBYPASSRLS` role. A dedicated `NOLOGIN`,
`NOINHERIT` terminal-function owner has only function-required privileges and
`BYPASSRLS`; it is not an application datasource. Hub exposes owner-authorized
terminal management on the printer edit page and calls AMS using Hub's service
identity with a server-established tenant header. The UI shows a newly issued
pairing code once with `no-store`, lifecycle metadata, and recoverable pairing
conflicts.

A terminal sends only the pairing code during enrollment. AMS locates the
pending pairing by HMAC digest and derives the bound tenant and printer from
that record; the terminal never supplies tenant or printer identity. A
successful enrollment returns one random `EDOL-Terminal terminalId.secret`
credential with `Cache-Control: no-store`, and all operational terminal calls
authenticate with that credential. Codes are eleven uppercase Latin letters, expire in five minutes, and are single-use. Eleven letters preserve at least the former 50-bit code entropy while avoiding numeric input on the terminal touchscreen. Invalid attempts are rate-limited
by source without disclosing pairing state. Pairing-code HMAC verification
accepts only current and immediately previous configured key versions.
Plaintext codes and credentials are neither persisted nor logged.

The standard ESP32 image stores the terminal credential in Preferences (NVS)
and does not require Secure Boot, Flash Encryption, encrypted NVS, eFuses, or
hardware-backed storage. Its transport is explicit: HTTP is allowed only for a
trusted LAN and exposes credentials to a network interceptor; HTTPS continues
to validate its configured CA and never falls back to HTTP after a TLS failure.
Home retains its existing trusted-network routes. Disposable backend checks,
firmware builds and the physical trusted-LAN functional smoke have passed.
Secure/disposable acceptance completed on 2026-10-21 with the real ESP32
SEC-06 TLS rejection/no-downgrade regression. The optional CA build hook now
uses a build-local header to preserve PEM newlines on Windows; see the
[Stage 8 acceptance record](deployment/stage8-terminal-acceptance-record.md).
Home compatibility and production rollout remain separate gates.

Stage 9.1 adds a secure-mode HTTP ownership interceptor for mapped
`/api/printers/**` handlers with a `printerId` path variable. It resolves the
persisted printer under the current tenant before runtime, camera, media, or
command handlers run. The aggregate state endpoint returns only printers visible
to the current tenant. Background runtime enumeration remains independent of
this HTTP boundary. Home does not install the interceptor and retains its
trusted-network state and command access without OAuth or tenant headers.

Connection response DTOs contain no printer access code in either deployment
mode. Creation and update requests still accept a code. Core preserves the
existing code when an update omits it or supplies a blank value; a nonblank value
replaces it. Hub no longer reads the old code before editing a connection.
Deploy this Core contract before the corresponding Hub change; an older Core
can overwrite a code with the new Hub's blank-input request. This source change
does not accept a deployment, change stored credential protection, or remove
HTTP/MQTT compatibility.

Stage 9.2 prepares secure Hub, Notify, and AMS readers for additive v2 and
envelope-only v3. V2 still requires a matching legacy `event` marker; v3 does
not require it, but rejects a contradictory marker if present. Both versions
retain mandatory envelope identity, timestamp, payload, and the existing
tenant/ownership checks. Core still publishes additive v2; topics, QoS, Hub
receipt idempotency, and Home legacy consumers are unchanged.

Each secure reader logs process-local ingress snapshots with a restart-specific
observation ID and fixed counters for validated v2/v3 deliveries, presence of
legacy `event`, rejected envelopes, and processing failures. These are log-based
observations, not a new metrics exporter or proof of a zero-use deployment
window. Validated delivery counts include duplicates and messages later denied
by tenant/processing checks. No payload, credential, tenant, printer, or topic
value is used as a counter dimension. Home does not initialize or record these
secure observations.

Stage 9.3a bounds agent model and snapshot uploads in both deployment modes.
Core resolves the configured agent before reading the body; Home still uses
`X-Agent-Id` as trusted-network routing, not authentication. Secure
`/api/agent/**` remains denied pending a separately accepted machine identity.
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

Stage 9.3b.1 adds an opt-in secure agent pairing/HTTPS foundation. Core owns
`core.agents`; Hub checks OWNER membership/printer access and calls its
printer-scoped management API with the dedicated `core.agent.manage` scope.
One-time five-minute codes are atomically exchanged for an agent-specific
credential; only keyed digests persist. A separate stateless `/api/agent/v1/**`
chain derives tenant/printer from the active credential before uploads and
rejects device tenant/agent headers and plaintext requests. Forced RLS and two
narrow pre-tenant functions protect the new secure-only V10 table; their
NOLOGIN owner has NOBYPASSRLS and runtime cannot assume it. Home initializes
none of this. Both service feature flags default off. Firmware pairing,
broker identity/ACLs and MQTT session revocation are not implemented by this
foundation; generic agent MQTT remains a separate outstanding boundary, not
accepted secure ingress. See the
[agent pairing contract](deployment/stage9-agent-pairing-contract.md).

Stage 9.3b.2 firmware source adds an explicit secure PlatformIO profile for
one-time HTTPS pairing and credential-bound streamed uploads, with a public CA
provided through a build-local header and a separate device identity namespace.
The Home profile remains the default and retains legacy settings/contracts.
The owner explicitly deferred setup AP hardening: its existing open Wi-Fi and
portal remain unchanged as a local-trust limitation. Secure agent MQTT is
implemented by the following coordinated increment; this is not complete
secure-agent or physical TLS acceptance.

The opt-in `lolin_d32_secure_smoke` lab environment adds bounded USB entry points
into the same production camera/FTPS-to-HTTPS services and an explicit return
to the existing setup portal without clearing identity/settings. Normal Home
and secure builds contain no lab console. See the Stage 9 disposable smoke
workbook; direct USB-triggered uploads do not establish secure MQTT operation.

Stage 9.3b secure MQTT source uses one Mosquitto/Dynamic Security broker for
service events and Agent traffic, with independent identities/ACLs. The approved
transport is server-authenticated TLS plus distinct client credentials, not
mandatory Agent mTLS. Secure Paho clients require TLS/credentials; Home remains
trusted-network and uses its legacy contracts. Authenticated HTTPS provisioning
issues a separate MQTT secret once. Secure-only V11 stores authority lifecycle
metadata and durable revocation tombstones with forced RLS. Core resolves
`edol/agent/v1/{agentId}/...` through persisted active Agent/printer/tenant
bindings for ingress and commands. Retirement, printer disable/mode changes and
deletion invalidate routing and enqueue broker disconnection; failed operations
remain pending and are retried. Replace waits for confirmed broker retirement.
Pending retirement also blocks successful revoke and new codes when the MQTT
feature is disabled after authority issuance; feature disable is not revocation.
Firmware keeps separate MQTT readiness/attempt markers, verifies TLS and
restores subscriptions on reconnect. See the
[MQTT contract](deployment/stage9-agent-mqtt-contract.md) for configuration,
failure windows, compatibility and rollback. This is source/disposable work,
not working-broker replacement, database rollout or physical acceptance.

See `docs/adr/0002-secure-multi-tenant-architecture.md`,
`docs/migrations/tenant-ownership-matrix.md`, and
`docs/migrations/secure-multi-tenant-migration-plan.md`. These documents define
the target and rollout; the system map and flows above remain the implemented
current state until individual stages are completed and audited.

## Planned Deployment Profiles

The accepted target includes two explicitly selected, mutually exclusive
profiles. `home` will be a single-owner, trusted-network installation with
Core, Hub, PostgreSQL, and MQTT only; it has one internally controlled tenant
and does not start Keycloak, OIDC, user memberships, service OAuth, or
multi-tenant RLS. Notify and AMS will be opt-in home add-ons. It is not
appropriate for untrusted users, public exposure, or data isolation between
people.

`secure-multi-tenant` remains the ADR 0002 target: Keycloak, authenticated
memberships, separate service identities, trusted tenant propagation, and
forced RLS are required. The required `EDOL_DEPLOYMENT_MODE` /
`edol.deployment.mode` selection will fail on an absent or unknown value, and
mode changes will not convert an existing database; a later controlled,
backup-first home-to-secure migration is required. Stage H is accepted and
provides the explicit home Compose template and fail-closed mode selection, but
is not deployed. Secure Hub now requires its BFF OIDC configuration and starts
only with those prerequisites; it does not expose the former pre-auth ingress.

## Lifecycle and Persistence

- On `ApplicationReadyEvent`, Core creates and starts runtime for enabled printers. Hub synchronizes the Core printer catalog and validates its UUID projection and printer-owned Hub data. Hub V5 rechecks Hub ownership paths, makes the Hub job, maintenance, and statistics printer relationships mandatory, and removes the legacy integer job printer ID without inferring a mapping. Missing, orphaned, or duplicate Hub ownership blocks the migration; a Core catalog mismatch blocks runtime validation, while catalog unavailability skips that validation and reports the catalog unavailable. Hub then attempts recovery independently for every enabled projected printer.
- Core and Hub each use Flyway with PostgreSQL and `hibernate.ddl-auto=validate`; their schemas are `core` and `hub` respectively. Flyway migrations are the database contract. Core V7 adds the foreign key from `active_print_context.printer_id` to `printers.id` after failing on existing orphaned contexts; Hub V5 contracts the validated printer ownership columns. Stage 2 Core V8 adds nullable opaque printer ownership without requiring a Hub schema when Core has no printers, and uses the Hub projection only to backfill a non-empty Core catalog. Local migration, mapping, and runtime checks passed. The controlled production Hub V2 history repair completed on 2026-09-24 from the exact corrected release, after which Hub V5 and V6 applied successfully; the evidence is recorded in the Stage 2 migration audit. PostgreSQL extensions required by immutable historical migrations are database-administrator bootstrap prerequisites; restricted Flyway and runtime roles do not receive database `CREATE` for extension provisioning.
- Core stores models and camera snapshots on mounted volumes. Docker Compose also mounts service logs.

## Operational Contracts

- MQTT event type, payload fields (including printer and session identity), topic, and ordering/recovery expectations are cross-service contracts.
- Core HTTP state and command endpoints are cross-service contracts. Printer commands may affect physical devices.
- Docker Compose resolves its environment files and persistent volumes relative to the parent of the repository. Do not assume versioned root `.env_edol_*` files are the runtime configuration used by Compose.

## Testing and Checks

- The reactor check used by CI is `mvn -B clean verify`, followed by SonarQube analysis.
- Hub has Mockito-based unit tests and optional Testcontainers coverage for the Flyway PostgreSQL chain. There is no automated integration coverage for Core/Hub HTTP, MQTT, or physical printers.
- Validate changes at the narrowest affected module first, then broaden checks when a shared contract, migration, or runtime boundary changes.

## Decision Records

Use `docs/adr/README.md` for decisions that are explicitly established or newly accepted. Do not create ADRs from inferred or unfinished work.

Accepted target decisions do not imply that their implementation stages are
complete. Update this document after each stage only with behavior verified in
the repository and deployment contract.
