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
3. Hub, Notify, and AMS subscribe to `edolcore/#`. Hub persists print and inventory effects; Notify sends Telegram messages; AMS changes spool state.
4. Hub, Notify, and AMS also call Core over HTTP for current printer state or commands. Hub owns its persistence; Core owns printer connectivity and runtime state.

Core runtime is per printer. New printer, connection and session identifiers use UUID v7; historical UUID v4 values are retained. Its HTTP API exposes a printer catalog and explicit `{printerId}` state, command, and media endpoints; default-printer HTTP endpoints are not supported. Hub, AMS and Notify use only explicit printer endpoints. Any change that carries printer identity through MQTT or HTTP must be coordinated across every producer and consumer.

Hub projects Core printers with the same UUID and assigns the projection to a Hub tenant. Hub runtime state, print recovery, jobs, allocation, maintenance, statistics, commands, dashboard routes and printer-management UI are printer-scoped. `TenantContext` is fail-closed; the database-marked default tenant is available only through the named legacy compatibility scope at explicitly allowlisted pre-auth ingress. Authentication, user membership and printer assignment remain future work. AMS staging and Notify progress state are keyed by printer UUID. See `docs/adr/0001-hub-printer-projection-and-tenant-bootstrap.md` and `docs/migrations/multi-printer-multi-tenant.md`.

## Accepted Multi-Tenant Direction

The secure multi-tenant target is accepted but not yet implemented. Hub will
own EDOL users, tenants, memberships and roles while Keycloak provides OIDC
identity and credential lifecycle. Hub will operate as a BFF, and services will
use separate OAuth client identities. Authentication identity and trusted
tenant context remain separate.

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
Existing pre-auth Hub ingress uses only the named, metered legacy compatibility
scope; ordinary Hub persistence has no default tenant fallback.
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
bypass. MQTT events will gain an additive tenant-aware envelope before legacy
fields are retired. AMS will own future terminal enrollment and per-device
credentials.

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
is not deployed. Until the BFF and service-authentication stages exist,
the selected `secure-multi-tenant` mode fails startup rather than exposing the
current pre-auth ingress.

## Lifecycle and Persistence

- On `ApplicationReadyEvent`, Core creates and starts runtime for enabled printers. Hub synchronizes the Core printer catalog and validates its UUID projection and printer-owned Hub data. Hub V5 rechecks Hub ownership paths, makes the Hub job, maintenance, and statistics printer relationships mandatory, and removes the legacy integer job printer ID without inferring a mapping. Missing, orphaned, or duplicate Hub ownership blocks the migration; a Core catalog mismatch blocks runtime validation, while catalog unavailability skips that validation and reports the catalog unavailable. Hub then attempts recovery independently for every enabled projected printer.
- Core and Hub each use Flyway with PostgreSQL and `hibernate.ddl-auto=validate`; their schemas are `core` and `hub` respectively. Flyway migrations are the database contract. Core V7 adds the foreign key from `active_print_context.printer_id` to `printers.id` after failing on existing orphaned contexts; Hub V5 contracts the validated printer ownership columns. Stage 2 Core V8 adds nullable opaque printer ownership without requiring a Hub schema when Core has no printers, and uses the Hub projection only to backfill a non-empty Core catalog. Local migration, mapping, and runtime checks passed. The controlled production Hub V2 history repair completed on 2026-09-24 from the exact corrected release, after which Hub V5 and V6 applied successfully; the evidence is recorded in the Stage 2 migration audit.
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
