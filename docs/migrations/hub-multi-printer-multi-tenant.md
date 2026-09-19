# Hub Multi-Printer / Multi-Tenant Migration

## Scope and Current State

Core multi-printer runtime and provisioning are treated as an existing external contract and are not redesigned here. Hub is migrated from implicit singleton state to explicit printer identity and tenant ownership.

The implementation baseline follows the repository history:

1. `EDOL-043-11` introduced Core printer management API v2.
2. `EDOL-043-12` through `EDOL-043-19` separated and reconciled per-printer Core runtime lifecycle.
3. `EDOL-043-20` introduced UUID v7 persistence foundations in Hub.
4. `EDOL-043-21` and `EDOL-043-23` added Hub active-job and allocation recovery, still based on singleton assumptions.
5. The current Hub change carries printer UUID through events, runtime state, persistence, HTTP routes, dashboard selection, maintenance, statistics, commands, and allocation.

V3 is an already-applied foundation migration and must not be edited or repaired as part of this rollout. It creates the default tenant and nullable printer ownership roots. V4 is the expand migration described below.

## Implementation Status

- [x] Tenant-owned Core printer projections, bootstrap `TenantContext`, UUID-scoped Hub persistence, runtime state, MQTT handling, recovery, browser routes, and APIs are implemented.
- [x] V4 is implemented and has completed local upgraded-database verification without a hardcoded printer UUID; catalog synchronization and application-assisted backfill completed successfully.
- [x] Catalog startup synchronization runs through recovery before per-printer recovery; periodic and MQTT-triggered synchronization is serialized within one Hub process.
- [x] Dashboard camera snapshots use `/api/printers/{printerId}/camera/snapshot`; the unscoped Hub `/api/proxy/camera` endpoint has been removed.
- [ ] V5 contract migration, authentication-backed tenant resolution, tenant printer assignment, and retirement of legacy compatibility APIs remain pending.

## Ownership Model

| Data | Owner and scope |
| --- | --- |
| Printer identity and lifecycle | Core; UUID is authoritative. |
| Hub `printers` row | Tenant-owned projection of a Core printer using the same UUID. |
| Print job, maintenance, printer statistics | Printer-owned; tenant is derived through the printer. |
| Vendor, material type, filament | Directly tenant-owned. |
| Filament spool | Tenant is derived through filament. |
| Allocation and spool usage | Tenant/printer are derived through their job and inventory relationships. |

`TenantContext` currently resolves the single row marked `is_default`. This provides query isolation during the bootstrap release; it does not provide authentication or tenant membership.

## V4 Expand Migration

`V4__migrate_print_jobs_printer_id_to_uuid.sql` is deliberately metadata-only:

- marks the sole bootstrap tenant as default and enforces at most one default;
- adds Core projection fields to `printers`;
- adds nullable `print_jobs.printer_id_uuid` and its foreign key/index;
- makes the legacy integer `print_jobs.printer_id` nullable so new jobs need not invent an integer ID;
- adds per-printer session and statistics uniqueness indexes;
- does not insert a printer, generate a printer UUID, update legacy job ownership, or drop legacy data.

This behavior supports both an existing V3 database and a clean database with zero printers.

## Application-Assisted Backfill

`PrinterCatalogSyncService` retrieves `/api/printers` from Core and upserts projections under the default tenant. The UUID supplied by Core is used unchanged.

After synchronization, `LegacyPrinterBackfillService` performs the transitional mapping:

1. It verifies that unmapped legacy print jobs contain no integer IDs other than `1`.
2. It selects the projected Core printer with the lowest display ID as the legacy default.
3. It maps legacy print jobs, null maintenance definitions, and null printer statistics to that printer.
4. It runs updates transactionally and never invents an ID.

Backfill is blocked when legacy data exists but Core returns no printers, or when legacy IDs are ambiguous. A Core outage sets catalog status to degraded and is retried; already projected printers and historical data remain available.

## Runtime and HTTP Changes

- MQTT consumers require a UUID printer ID and synchronize its Core projection before processing the event.
- Active jobs and allocation-preview runtime caches are isolated by printer UUID.
- Recovery synchronizes the catalog first, then recovers each enabled and available printer independently by `(printer_id_uuid, session_id)`.
- Startup recovery is the single initial catalog trigger. Scheduled synchronization starts after the configured interval, and all catalog synchronization entry points are serialized within one Hub process.
- Dashboard, print jobs, controls, maintenance, statistics, media, commands, spool change, and allocation routes carry `{printerId}`.
- Dashboard camera snapshots use Hub `/api/printers/{printerId}/camera/snapshot`, which proxies the same printer-scoped Core endpoint. The legacy Hub `/api/proxy/camera` endpoint is removed.
- Tenant-owned CRUD queries use the current tenant ID; object identifiers alone are not accepted as an ownership check.
- Legacy `/api/printer/state`, `/stats`, and `/alerts` endpoints remain deprecated default-printer adapters. New code must use `/api/printers/{printerId}/...`.

## Rollout Procedure

1. Back up the Hub database and record the current Flyway history/checksums.
2. Deploy V4 with the legacy-capable Hub application. Do not deploy V5 in the same release.
3. Confirm Core `/api/printers` returns the expected printer UUIDs and stable display IDs.
4. Observe `/api/printers/catalog-status`; resolve `migrationBlocked` before proceeding.
5. Verify projections and backfill:

```sql
select id, display_id, tenant_id, enabled, available_in_core, last_synced_at
from hub.printers
order by display_id;

select printer_id, printer_id_uuid, count(*)
from hub.print_jobs
group by printer_id, printer_id_uuid
order by printer_id, printer_id_uuid;

select count(*) from hub.print_jobs where printer_id_uuid is null;
select count(*) from hub.maintenance_definition where printer_id is null;
select count(*) from hub.printer_stats where printer_id is null;
```

6. Exercise two printers concurrently: state, start/progress/finish or cancel, allocation preview, maintenance, statistics, media, and commands. Confirm no runtime or UI state crosses printer IDs.
7. Keep the legacy-capable application available for the rollback window. V4 is additive, so rollback does not require destructive SQL.

## V5 Contract Preconditions

Create and deploy V5 only after all checks below remain true for the agreed observation window:

- no `print_jobs.printer_id_uuid` is null;
- no maintenance or statistics printer relationship is null;
- no Hub write path writes only the legacy integer column;
- Core catalog synchronization is stable and no migration-blocked state occurs;
- all consumers use UUID printer identity;
- database backup and restore have been tested.

V5 should validate the preconditions in SQL, set the UUID relationships to `NOT NULL`, remove obsolete indexes/constraints, drop legacy `print_jobs.printer_id`, and rename `printer_id_uuid` to `printer_id`. It must fail rather than delete or guess when any precondition is violated.

## Verification Evidence

- `mvn -B -pl edol-hub -am test` covers Hub behavior, including per-printer runtime isolation.
- `HubMigrationTest` runs the real Flyway chain on PostgreSQL when Docker is available. It covers a clean database and V3 legacy data retained through V4 without a fabricated UUID.
- `mvn -B clean verify` is the final reactor check.

## Remaining Work

1. [ ] Add authentication and tenant membership, then resolve `TenantContext` from the authenticated principal.
2. [ ] Define printer assignment/reassignment policy for tenants; current projection assigns newly discovered printers to the bootstrap tenant.
3. [ ] Implement V5 only after production backfill evidence satisfies the contract preconditions.
4. [ ] After V5 is deployed and the rollback window is closed, audit every `@Deprecated` method and legacy compatibility endpoint in Core and Hub. Record each consumer and explicitly remove, migrate, or retain every adapter.
5. [ ] Add multi-tenant authorization integration tests and Core/Hub contract tests beyond Mockito boundaries.
