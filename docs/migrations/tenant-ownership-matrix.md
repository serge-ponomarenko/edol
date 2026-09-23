# Tenant Ownership Matrix

- **Status:** Approved
- **Approved:** 2026-09-22
- **Decision:** [ADR 0002](../adr/0002-secure-multi-tenant-architecture.md)

## Purpose

This document is the approved ownership model for EDOL multi-tenancy. It
defines where tenant identity is persisted, where it is derived, and how
Hibernate and PostgreSQL row-level security must enforce isolation.

The matrix is based on the current Hub and Core Flyway schemas, JPA entities,
repository access, native SQL, and runtime/background processing. It describes
the target schema; entries marked as new are not implemented yet.

## Ownership Rules

1. Do not add `tenant_id` to every table. A mandatory and immutable foreign-key
   chain is the preferred ownership source for children within one aggregate.
2. Store `tenant_id` on tenant roots, directly tenant-owned independent
   aggregates, and cross-aggregate link entities that must prove both sides
   belong to the same tenant.
3. Apply Hibernate `@TenantId` only where the entity stores the discriminator.
   Derived children rely on associations for ORM navigation and RLS for the
   database boundary.
4. Use tenant-aware composite foreign keys for cross-aggregate relationships.
   A referenced direct tenant table therefore needs a unique candidate key such
   as `(id, tenant_id)` even when `id` is already the primary key.
5. RLS policies use both `USING` and `WITH CHECK`. Missing tenant state denies
   access. Application roles are not owners, superusers, or `BYPASSRLS` roles,
   and tenant tables use `FORCE ROW LEVEL SECURITY`.
6. Set tenant identity with transaction-local
   `set_config('edol.tenant_id', ..., true)` on the connection executing the
   transaction. Never use a pooled session-level `SET`.
7. The Hub tenant is a domain aggregate. Core stores only an opaque tenant UUID
   assigned by a trusted Hub provisioning contract and never queries Hub to
   infer it.

## Persisted Entities

| Entity / table | Owning aggregate | Tenant source | Direct `tenant_id` target | Hibernate strategy | RLS strategy | Migration impact |
| --- | --- | --- | --- | --- | --- | --- |
| Hub `Tenant` / `tenants` | Tenant root | Its primary key | No | No `@TenantId`; access occurs after membership selection | `id` equals selected tenant; special bootstrap transaction is separately authorized | Retain a non-empty legacy tenant for first-owner claim; remove an empty historical seed after a complete ownership check; drop default marker after authentication bootstrap |
| Hub `User` / new `users` | Global identity | OIDC issuer and subject | No | No tenant discriminator | Self-only policy based on trusted OIDC identity context; provisioning path is explicit | Add immutable unique `(issuer, subject)` and profile/audit fields; no credentials |
| Hub `TenantMembership` / new `tenant_memberships` | Tenant and User | Stored membership tenant | Yes | No normal `@TenantId`, because membership discovery precedes tenant selection | Current authenticated user may list own memberships; selected-tenant operations additionally match tenant | Add tenant/user FKs, unique `(tenant_id, user_id)`, `OWNER` role, timestamps/status |
| Hub `Printer` / `printers` | Tenant | Existing printer tenant | Yes, existing | `@TenantId` | Direct tenant predicate | Validate backfill; add unique `(id, tenant_id)` candidate key for tenant-safe references |
| Hub `Vendor` / `vendors` | Tenant | Existing vendor tenant | Yes, existing | `@TenantId` | Direct tenant predicate | Add unique `(id, tenant_id)` and retain tenant-scoped name uniqueness |
| Hub `MaterialType` / `material_types` | Tenant | Existing material tenant | Yes, existing | `@TenantId` | Direct tenant predicate | Add unique `(id, tenant_id)` and retain tenant-scoped name uniqueness |
| Hub `Filament` / `filaments` | Tenant | Existing filament tenant | Yes, existing | `@TenantId` | Direct tenant predicate | Make vendor and material references tenant-safe composite FKs; add unique `(id, tenant_id)` |
| Hub `FilamentSpool` / `filament_spools` | Filament | `filament_id -> filaments.tenant_id` | No | Derived association | `EXISTS` through filament | Keep schema normalized; ensure mandatory filament FK and supporting index |
| Hub `PrintJob` / `print_jobs` | Printer | `printer_id -> printers.tenant_id` | No | Derived association | `EXISTS` through printer | Complete UUID printer backfill, make relationship mandatory, remove legacy integer column in a later contract migration |
| Hub `PrinterStats` / `printer_stats` | Printer | `printer_id -> printers.tenant_id` | No | Derived association | `EXISTS` through printer | Backfill and make printer FK non-null; retain one-stats-row-per-printer constraint |
| Hub `MaintenanceDefinition` / `maintenance_definition` | Printer | `printer_id -> printers.tenant_id` | No | Derived association | `EXISTS` through printer | Backfill and make printer FK non-null |
| Hub `MaintenanceExecution` / `maintenance_execution` | Maintenance definition | Definition to printer | No | Derived association | Nested `EXISTS` through definition and printer | No tenant duplication; retain mandatory definition FK |
| Hub `PrintAllocationPreview` / `print_allocation_preview` | Print job | Job to printer | No | Derived association | `EXISTS` through job and printer | No tenant duplication; retain one preview per job |
| Hub `PrintAllocationGroup` / `print_allocation_group` | Allocation preview plus optional filament | Backfilled from preview/job/printer | Yes, new | `@TenantId` | Direct predicate plus same-tenant checks | Add and backfill tenant; composite FK to optional filament and constraint trigger validating preview-derived ownership |
| Hub `PrintAllocationItem` / `print_allocation_item` | Allocation group plus optional spool | Backfilled from group | Yes, new | `@TenantId` | Direct predicate plus same-tenant checks | Add and backfill tenant; composite FK to group and constraint trigger validating spool-derived ownership |
| Hub `JobSpoolUsage` / `job_spool_usage` | Print job plus spool | Backfilled from job/printer and validated against spool/filament | Yes, new | `@TenantId` | Direct predicate plus same-tenant checks | Add tenant; reject historical mismatches; constraint triggers validate job- and spool-derived ownership |
| Core `Printer` / `core.printers` | Core printer, assigned to an EDOL tenant | Trusted Hub provisioning context | Yes, new | `@TenantId` for tenant-scoped application operations | Direct predicate | Add nullable column, migration-only Hub projection backfill, validate, then make non-null; no FK to Hub |
| Core `PrinterConnectionConfiguration` / `printer_connection_configurations` | Core printer | `printer_id -> core.printers.tenant_id` | No | Derived association | `EXISTS` through Core printer | No tenant duplication; retain one-to-one printer FK |
| Core `ActivePrintContext` / `active_print_context` | Core printer | `printer_id -> core.printers.tenant_id` | No | Derived association | `EXISTS` through Core printer | V7 adds the printer FK after a fail-fast orphan check; retain unique printer constraint |
| AMS `Terminal` / future `terminals` | Tenant-owned terminal | Trusted Hub management call at enrollment | Yes, new | AMS tenant strategy introduced with the feature | Direct predicate | Future AMS stage; terminal ID, credential digest/version, printer/API scope, revoke/rotation metadata |
| AMS `TerminalPairing` / future `terminal_pairings` | Tenant-owned enrollment | Trusted Hub management call | Yes, new | AMS tenant strategy introduced with the feature | Direct predicate | Future AMS stage; code digest, expiry, attempt limit, consumed state, creator audit |

## Cross-Aggregate Constraint Requirements

Adding a tenant discriminator to a link table is not sufficient by itself. The
database must prevent a valid row from connecting resources owned by different
tenants.

The implementation stages must create equivalent constraints for these paths:

```text
Filament(tenant_id, vendor_id)       -> Vendor(tenant_id, id)
Filament(tenant_id, material_type_id)-> MaterialType(tenant_id, id)
AllocationGroup(tenant_id, filament_id)-> Filament(tenant_id, id)
AllocationItem(tenant_id, group_id)  -> AllocationGroup(tenant_id, id)
```

Those links use composite foreign keys because both sides persist the tenant.
The following links target entities whose tenant is intentionally derived, so
a composite foreign key cannot express the invariant without duplicating
tenant state:

```text
AllocationGroup(tenant_id, preview_id) -> Preview -> PrintJob -> Printer
AllocationItem(tenant_id, spool_id)     -> Spool -> Filament
JobSpoolUsage(tenant_id, job_id)        -> PrintJob -> Printer
JobSpoolUsage(tenant_id, spool_id)      -> Spool -> Filament
```

Implement these checks with PostgreSQL `CONSTRAINT TRIGGER`s on insert and on
updates of the ownership columns. Make the triggers deferrable and initially
immediate so a transaction may explicitly defer them for a controlled bulk
migration without weakening ordinary writes. Trigger functions use invoker
privileges, fixed schema-qualified references, and no dynamic SQL. RLS
`WITH CHECK` repeats the same-tenant condition as the access-control boundary.
Ownership and parent identifiers are immutable in ordinary application
operations; a future transfer workflow must update the complete aggregate in a
separately audited transaction.

## RLS Policy Shapes

The following are policy shapes, not migration-ready SQL. Migration SQL must
use schema-qualified names, null-safe casts, both read and write policies, and
the project's actual database roles.

```sql
-- Direct ownership
USING (tenant_id = nullif(current_setting('edol.tenant_id', true), '')::uuid)
WITH CHECK (tenant_id = nullif(current_setting('edol.tenant_id', true), '')::uuid)

-- Derived ownership example
USING (exists (
    select 1
    from hub.printers p
    where p.id = print_jobs.printer_id
      and p.tenant_id = nullif(current_setting('edol.tenant_id', true), '')::uuid
))
```

Policies must be tested with Hibernate-generated SQL and direct native SQL.
Indexes on each ownership join path are part of the stage acceptance criteria.

`User` and `TenantMembership` are pre-tenant access cases. Authentication
establishes trusted OIDC identity context before those rows are queried. Set
validated `edol.oidc_issuer` and `edol.oidc_subject` with transaction-local
`set_config(..., true)`. The user policy matches the row's issuer and subject;
membership listing and tenant selection use an `EXISTS` path through that user.
The implementation must not solve pre-tenant discovery by giving the ordinary
Hub runtime unrestricted access to every user or membership.

## Runtime-Only State

The following state is not persisted tenant data and does not receive a tenant
column:

- Hub printer state and allocation-preview caches;
- Core printer runtime, telemetry, connection, and recovery objects;
- AMS scanned-spool staging state;
- Notify progress and notification state.

Every runtime entry must be keyed by printer identity and either carry or be
resolved under a trusted tenant context. An untrusted event or browser-provided
printer UUID may not create trusted runtime tenant state.

## Approval and Change Control

This matrix was reviewed and approved before ADR 0002 was finalized. Future
implementation findings may refine indexes, constraint mechanics, or table
names. A change to which aggregate owns an entity, whether tenant identity is
direct or derived, or which service owns the source of truth is an
architectural change and requires explicit escalation under the post-stage
audit procedure.
