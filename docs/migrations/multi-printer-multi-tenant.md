# Multi-Printer / Multi-Tenant Migration

## Current State

Core owns the authoritative printer catalog, lifecycle, connectivity, runtime
state, commands, and media. Hub owns a tenant-scoped projection of that catalog
and all operational data. AMS and Notify consume Core MQTT events and Core HTTP
APIs. Printer UUID is the cross-service identity.

| Area | Status |
| --- | --- |
| Core per-printer runtime and management API | Complete |
| Hub V3/V4 foundation, catalog projection, validated legacy backfill | Complete and locally verified |
| Hub printer-scoped dashboard, jobs, maintenance, allocation, camera and commands | Complete |
| Hub printer management UI and scope-grouped navigation | Complete |
| New Core persisted and session UUID generation | Complete; UUID v7 |
| AMS and Notify default-printer contract removal | Complete |
| Authentication, tenant membership and printer assignment | Pending |
| Hub V5 legacy-column contraction and compatibility retirement | Pending |

V3 is deployed and immutable. V4 is additive: it introduces a nullable UUID
relationship for legacy Hub print jobs without inserting or hardcoding a Core
printer UUID. `PrinterCatalogSyncService` uses the Core catalog and validated
application-assisted backfill to map legacy singleton data. It supports an
empty database and blocks ambiguous historical mappings.

## Implemented Scope

### Identity and Hub UI

- Hub uses UUID v7 for locally generated persistence IDs; Core now uses the
  same UUID v7 generator for new printer IDs, connection IDs and print session
  IDs. Existing persisted UUID v4 values remain unchanged to preserve all
  references.
- `GET /` redirects to the tenant-scoped printer catalog. The previous dashboard
  remains `/printers/{printerId}` and is labelled **Printer Status**.
- The catalog supports create, edit, enable/disable, connection settings and
  decommissioning through the existing Core Printer Management API. Hub never
  creates a competing printer record; it synchronizes the Core result into the
  current tenant projection. Update and delete first verify tenant ownership.
- The sidebar separates tenant inventory (`Printers`, spools, filaments,
  vendors and materials) from printer scope (`Printer Status`, print jobs,
  maintenance and maintenance definitions).

### AMS

- `GET /ams/state`, `GET /ams/find` and `GET /ams/set-spool` require a
  `printerId` request parameter.
- AMS requests Core state through `/api/printers/{printerId}/state` and submits
  spool changes through Hub `/s/{printerId}/{spoolId}/{trayId}`.
- A scanned spool and its timeout are held per printer UUID, so a scan on one
  printer cannot be applied by an AMS event from another printer.
- AMS MQTT handling requires `printerId` in each Core payload.

### Notify

- Notify calls only explicit Core state, camera and command endpoints.
- MQTT notifications, progress milestones and timelapse handling are keyed by
  the event printer UUID.
- Telegram starts with a printer selector. Status, controls, confirmation and
  metadata/push-all callbacks carry `action_{printerId}`; the payload stays
  within Telegram's callback-data limit. Messages identify the selected printer.

## Remaining Migration

1. Add authentication and tenant membership, then resolve Hub `TenantContext`
   from the authenticated principal.
2. Define printer assignment and reassignment rules. The bootstrap release
   assigns newly synchronized Core printers to the default tenant only.
3. Bind AMS service credentials and Telegram chat recipients to a tenant before
   enabling multiple tenants outside the bootstrap environment. Notify must use
   that mapping to select recipients; AMS must be authorized for its printers.
4. Add end-to-end Core/Hub/AMS/Notify contract tests with two printers and two
   tenants. Cover MQTT events, status/media, commands, inventory mutation and
   recipient isolation.
5. Deploy V5 only after every `print_jobs.printer_id_uuid` is non-null, all
   maintenance/statistics references are non-null, Core catalog synchronization
   is stable and all consumers use UUID identity. V5 must validate those
   preconditions, make UUID ownership mandatory, drop the obsolete integer
   column and rename `printer_id_uuid` to `printer_id`. It must fail rather than
   guess or delete data.
6. After the V5 rollback window, audit every deprecated Core and Hub
   default-printer adapter. Remove an adapter only after its consumers are
   migrated and verified.

## Verification and Rollout

1. Back up Hub and record Flyway history before deployment. Do not pair V4/V5
   changes in one release.
2. Confirm `/api/printers/catalog-status` is healthy and that `hub.printers`
   contains the expected Core UUIDs before exercising the UI.
3. Exercise two printers concurrently: lifecycle events, dashboard state and
   camera, commands, allocation, maintenance, AMS spool staging and Telegram
   callbacks. Confirm no state or notification crosses printer UUIDs.
4. Run affected module tests and `mvn -B clean verify`. Hardware, broker and
   Telegram delivery still require a controlled local verification run.
