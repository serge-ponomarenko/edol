# ADR 0001: Hub Printer Projection and Tenant Bootstrap

**Status:** Accepted

## Context

EDOL Core already owns multi-printer provisioning and runtime state. Hub historically assumed one printer and one tenant, while existing Hub databases contain print jobs with the legacy integer `printer_id = 1` and maintenance/statistics rows without printer ownership. The migration must preserve those rows, support an empty installation, and must not invent or hardcode a Core printer UUID.

Authentication and user-to-tenant membership are not part of the current release. A deterministic tenant context is still required so that application queries stop crossing tenant boundaries before authentication is introduced.

## Decision

- Core remains the system of record for printer identity and generates printer UUIDs.
- Hub stores a tenant-owned projection of the Core printer catalog using the same UUID.
- Hub synchronizes that projection from Core at startup, periodically, and when an event references an unknown printer.
- Hub uses one database-marked default tenant as the temporary system tenant. All browser and inventory access is tenant-scoped even though tenant authentication is deferred.
- Runtime print state and allocation-preview caches are keyed by printer UUID.
- Printer-specific browser and API routes carry `{printerId}` explicitly. Deprecated single-printer state endpoints delegate to the current tenant's default printer only as a compatibility boundary.
- Database rollout uses expand/backfill/contract phases. V4 only adds the UUID relationship and projection metadata. Application code backfills legacy data after it has synchronized a real Core printer UUID. A later V5 will enforce `NOT NULL` and remove the legacy integer column only after operational verification.
- If Core is unavailable, Hub remains available in degraded mode and retries synchronization. If legacy IDs are ambiguous, migration is marked blocked and no guessed mapping is applied.

## Rationale

Reusing the Core UUID prevents a second identity mapping domain. Application-assisted backfill is necessary because SQL migrations cannot discover Core state reliably and clean databases have no printer to reference. The expand/contract sequence permits rollback to the legacy-capable application while preserving all existing rows.

## Alternatives

- Generate a Hub printer UUID in V4: rejected because it would diverge from Core identity and require a permanent mapping table.
- Hardcode the UUID of the currently installed printer: rejected because it breaks clean installations and every other deployment.
- Make Core tenant-aware immediately: deferred because it expands the current migration across provisioning, authorization, and every Core consumer.
- Drop legacy data on migration: rejected because print history, maintenance, and statistics must be retained.

## Consequences

- Hub startup depends on Core catalog synchronization for legacy backfill, but not for serving already projected data.
- The temporary default tenant is an explicit bootstrap mechanism, not an authorization model.
- Core printer deletion is represented as `available_in_core = false`; Hub history remains intact.
- V5 cannot be deployed until all legacy rows are mapped, every required printer relationship is non-null, and the legacy-capable rollback window is closed.
- Future authentication must replace `TenantContext` default resolution with authenticated membership without relaxing repository scoping.
