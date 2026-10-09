# EDOL Architecture

## System Map

EDOL is a Java 21, Spring Boot 4 Maven reactor deployed as four services with PostgreSQL and NanoMQ. The root `pom.xml` is the build entry point; Docker Compose is the local/self-hosted runtime composition.

| Module | Responsibility | Main boundaries |
| --- | --- | --- |
| `edol-core-api` | Shared printer-state and AMS DTOs. | Consumed by all services; changes are cross-service API changes. |
| `edol-core` | Printer provisioning, direct/agent telemetry, commands, model metadata, camera, runtime and recovery state. | Bambu printer protocols, agents, MQTT, `core` PostgreSQL schema, HTTP state API. |
| `edol-hub` | Persistent operational domain and dashboard: inventory, spools, print jobs, allocation, maintenance, labels. | `hub` PostgreSQL schema, Core HTTP API, Core MQTT events, browser clients. |
| `edol-notify` | Printer-scoped Telegram commands and printer/print notifications. | Core HTTP API, Core MQTT events, Telegram. |
| `edol-ams` | Printer-scoped AMS status and spool-change workflow. | Core and Hub HTTP APIs, Core MQTT events. |

## Runtime and Data Flows

1. A printer reports telemetry directly or through an agent. Core resolves the printer, updates its in-memory runtime state, and derives application events.
2. Core handles those events for print lifecycle, metadata, camera, recovery, and commands, then publishes integration events under `edolcore/#`.
3. Hub consumes the Core integration-event contract for persistent print and inventory effects. In `secure-multi-tenant` mode, Notify consumes only its declared printer and print topics, and AMS consumes only `edolcore/ams` and `edolcore/print/ams`; both validate the additive v2 envelope before using its tenant context. Home retains the legacy trusted-network subscriptions.
4. Hub, Notify, and AMS also call Core over HTTP for current printer state or commands. Hub owns its persistence; Core owns printer connectivity and runtime state.

Core runtime is per printer. New printer, connection and session identifiers use UUID v7; historical UUID v4 values are retained. Its HTTP API exposes a printer catalog and explicit `{printerId}` state, command, and media endpoints; default-printer HTTP endpoints are not supported. Hub, AMS and Notify use only explicit printer endpoints. Any change that carries printer identity through MQTT or HTTP must be coordinated across every producer and consumer.

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
Core accepts the tenant header only from allowlisted service clients, and Hub
accepts AMS service JWT ingress only on the exact spool routes. Keycloak
clients, broker identities/ACLs, and mappings were provisioned only in an
isolated disposable Stage 7 smoke on 2026-10-09. That run verified dedicated
connections, ACL denials, selected v2 processing, and selected cross-tenant
rejections, but is **PARTIAL ACCEPTANCE — NOT FULL PASS**: positive AMS spool
lookup and unknown-printer handling returned HTTP 500. The subsequent source
remediation uses a narrow AMS Hub-spool read DTO and maps an unmapped HTTP
printer to `404` before a downstream call; its local regression tests pass, but
a fresh disposable rerun is still required. Complete HTTP authorization, Notify
delivery, mapping-validation, metrics, and Maven evidence also remain open. No
deployment, TLS, development-database, live-device, home-mode, or production
acceptance was performed. The temporary legacy paths remain available until the
later observation and Stage 9 removal gates. Stage 8 will own terminal
enrollment and per-device credentials.

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
