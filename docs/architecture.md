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

Hub projects Core printers with the same UUID and assigns the projection to a Hub tenant. Hub runtime state, print recovery, jobs, allocation, maintenance, statistics, commands, dashboard routes and printer-management UI are printer-scoped. During the tenant bootstrap phase, `TenantContext` resolves one database-marked default tenant; authentication, user membership and printer assignment remain future work. AMS staging and Notify progress state are keyed by printer UUID. See `docs/adr/0001-hub-printer-projection-and-tenant-bootstrap.md` and `docs/migrations/multi-printer-multi-tenant.md`.

## Accepted Multi-Tenant Direction

The secure multi-tenant target is accepted but not yet implemented. Hub will
own EDOL users, tenants, memberships and roles while Keycloak provides OIDC
identity and credential lifecycle. Hub will operate as a BFF, and services will
use separate OAuth client identities. Authentication identity and trusted
tenant context remain separate.

Tenant persistence will use Hibernate discriminator tenancy together with
forced PostgreSQL row-level security. Normal tenant resolution will be
fail-closed. The current default-tenant behavior may survive only as a named,
measured migration compatibility scope before user authentication and will be
removed in that authentication stage.

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

## Lifecycle and Persistence

- On `ApplicationReadyEvent`, Core creates and starts runtime for enabled printers. Hub synchronizes the Core printer catalog and validates its UUID projection and printer-owned Hub data. Hub V5 rechecks Hub ownership paths, makes the Hub job, maintenance, and statistics printer relationships mandatory, and removes the legacy integer job printer ID without inferring a mapping. Missing, orphaned, or duplicate Hub ownership blocks the migration; a Core catalog mismatch blocks runtime validation, while catalog unavailability skips that validation and reports the catalog unavailable. Hub then attempts recovery independently for every enabled projected printer.
- Core and Hub each use Flyway with PostgreSQL and `hibernate.ddl-auto=validate`; their schemas are `core` and `hub` respectively. Flyway migrations are the database contract. Core V7 adds the foreign key from `active_print_context.printer_id` to `printers.id` after failing on existing orphaned contexts; Hub V5 contracts the validated printer ownership columns.
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
