# Hub Secure-Dev Data Clone Preflight

## Purpose and boundary

This is a read-only classification procedure for a development PostgreSQL
database that already contains Hub data. Its only outcome is evidence for a
later decision whether a separately restored disposable clone can exercise the
secure multi-tenant legacy first-owner claim.

It is not authorization to create a backup or clone, apply Flyway V8, start
Hub, create or bind a user, change Keycloak, open the legacy claim window,
repair data, or access production. Do not run the empty-database `dev-smoke`
profile against this database.

## Operator and evidence rules

Use an approved development database route with read-only access and complete
schema visibility. Do not use `hub_runtime`: RLS can intentionally return a
partial view without a tenant context. Do not use `hub_flyway` as a preflight
route, even when the operator opens a read-only transaction.

Keep the connection details, identity values, tenant names, and identifiers
out of the evidence. The evidence may redact identifiers while retaining query
names, row counts, booleans, migration versions, and failure messages. If any
query cannot run read-only or cannot see the complete expected schema, stop and
record that limitation. The only exception is the narrowly approved DBA
procedure below; do not create a reader role or change a policy ad hoc.

Run all SQL below in one transaction, then roll it back even though the
transaction is read-only:

```sql
BEGIN TRANSACTION READ ONLY;
SET LOCAL search_path = hub, pg_catalog;
```

Finish with:

```sql
ROLLBACK;
```

## Approved one-time DBA exception

Forced RLS makes a direct, full-schema query by a new `NOBYPASSRLS` reader
impossible without adding a purpose-built RLS policy or a privileged execution
mechanism. If no existing approved reader has complete visibility, the DBA may
use the existing administrative route for this one source-development-database
preflight only.

This exception does not permit a reusable privileged login, a grant, a policy
change, `SET ROLE`, `SET row_security`, DDL, DML, Flyway, a backup, a clone, or
a Hub start. It is not permitted for production.

The DBA must:

1. Connect directly through the existing administrative mechanism without
   copying its connection string, password, or environment values into this
   document, a shell history, or evidence.
2. In the interactive SQL client, enable stop-on-error and disable paging, then
   execute only the preflight statements in this document inside one
   `BEGIN TRANSACTION READ ONLY` transaction.
3. First record this non-sensitive proof that the transaction is read-only:

   ```sql
   SELECT current_database() AS database_name,
          current_user AS execution_role,
          current_setting('transaction_read_only') AS transaction_read_only;
   ```

4. Execute sections 1 through 4 unchanged, retaining only their aggregate,
   boolean, migration-history, RLS, and role-posture output. Do not query raw
   user, tenant, membership, printer, or inventory rows.
5. Execute `ROLLBACK` and record successful completion. The evidence must state
   that no grants, role changes, database settings, migrations, or data changed.

For `psql`, the client commands below are sufficient before the SQL transaction;
they neither expose credentials nor modify the database:

```text
\set ON_ERROR_STOP on
\pset pager off
```

## 1. Establish schema and migration facts

Record the complete ordered result. Do not apply a missing migration to the
source development database.

```sql
SELECT installed_rank,
       version,
       description,
       type,
       script,
       checksum,
       success
FROM hub.flyway_schema_history
ORDER BY installed_rank;
```

Record whether the two mutually exclusive deployment markers exist:

```sql
SELECT to_regclass('hub.home_installations') AS home_installations_relation,
       to_regclass('hub.legacy_tenant_bootstrap_state')
           AS bootstrap_state_relation;
```

If `home_installations_relation` is not null, record only the non-sensitive
state below. A home installation is not a candidate for secure-mode conversion
in this procedure.

```sql
SELECT singleton,
       tenant_id IS NOT NULL AS has_installation_tenant
FROM hub.home_installations;
```

If `bootstrap_state_relation` is not null, record only its lifecycle state.
Do not update `claim_open`.

```sql
SELECT singleton,
       claim_open,
       claimed_by_user_id IS NOT NULL AS has_claimed_user,
       claimed_at IS NOT NULL AS has_claimed_at
FROM hub.legacy_tenant_bootstrap_state;
```

## 2. Record identity and direct-ownership shape

```sql
SELECT count(*) AS tenant_count,
       count(*) FILTER (WHERE is_default) AS default_tenant_count
FROM hub.tenants;

SELECT count(*) AS user_count
FROM hub.users;

SELECT status, role, count(*) AS membership_count
FROM hub.tenant_memberships
GROUP BY status, role
ORDER BY status, role;

SELECT count(*) AS membership_count
FROM hub.tenant_memberships;
```

Record row distribution without exposing tenant identifiers:

```sql
SELECT 'printers' AS relation,
       count(*) AS row_count,
       count(DISTINCT tenant_id) AS tenant_count
FROM hub.printers
UNION ALL
SELECT 'vendors', count(*), count(DISTINCT tenant_id) FROM hub.vendors
UNION ALL
SELECT 'material_types', count(*), count(DISTINCT tenant_id) FROM hub.material_types
UNION ALL
SELECT 'filaments', count(*), count(DISTINCT tenant_id) FROM hub.filaments
UNION ALL
SELECT 'print_allocation_group', count(*), count(DISTINCT tenant_id) FROM hub.print_allocation_group
UNION ALL
SELECT 'print_allocation_item', count(*), count(DISTINCT tenant_id) FROM hub.print_allocation_item
UNION ALL
SELECT 'job_spool_usage', count(*), count(DISTINCT tenant_id) FROM hub.job_spool_usage
ORDER BY relation;

SELECT 'printers' AS relation, count(*) AS missing_tenant_count
FROM hub.printers WHERE tenant_id IS NULL
UNION ALL
SELECT 'vendors', count(*) FROM hub.vendors WHERE tenant_id IS NULL
UNION ALL
SELECT 'material_types', count(*) FROM hub.material_types WHERE tenant_id IS NULL
UNION ALL
SELECT 'filaments', count(*) FROM hub.filaments WHERE tenant_id IS NULL
UNION ALL
SELECT 'print_allocation_group', count(*)
FROM hub.print_allocation_group
WHERE tenant_id IS NULL
UNION ALL
SELECT 'print_allocation_item', count(*)
FROM hub.print_allocation_item
WHERE tenant_id IS NULL
UNION ALL
SELECT 'job_spool_usage', count(*) FROM hub.job_spool_usage WHERE tenant_id IS NULL
ORDER BY relation;
```

Any non-zero `missing_tenant_count` blocks the clone exercise.

## 3. Check derived ownership and cross-tenant integrity

All counts below must be zero. A non-zero count means the data must be handled
by a separately designed migration; do not guess, delete, or manually reassign
ownership.

```sql
SELECT 'filament_spools_without_filament' AS check_name, count(*) AS violation_count
FROM hub.filament_spools spool
LEFT JOIN hub.filaments filament ON filament.id = spool.filament_id
WHERE filament.id IS NULL
UNION ALL
SELECT 'print_jobs_without_printer', count(*)
FROM hub.print_jobs job
LEFT JOIN hub.printers printer ON printer.id = job.printer_id
WHERE printer.id IS NULL
UNION ALL
SELECT 'printer_stats_without_printer', count(*)
FROM hub.printer_stats statistics
LEFT JOIN hub.printers printer ON printer.id = statistics.printer_id
WHERE printer.id IS NULL
UNION ALL
SELECT 'maintenance_definition_without_printer', count(*)
FROM hub.maintenance_definition definition
LEFT JOIN hub.printers printer ON printer.id = definition.printer_id
WHERE printer.id IS NULL
UNION ALL
SELECT 'maintenance_execution_without_definition', count(*)
FROM hub.maintenance_execution execution
LEFT JOIN hub.maintenance_definition definition ON definition.id = execution.maintenance_id
WHERE definition.id IS NULL
UNION ALL
SELECT 'allocation_preview_without_job', count(*)
FROM hub.print_allocation_preview preview
LEFT JOIN hub.print_jobs job ON job.id = preview.print_job_id
WHERE job.id IS NULL
UNION ALL
SELECT 'allocation_group_without_preview', count(*)
FROM hub.print_allocation_group allocation_group
LEFT JOIN hub.print_allocation_preview preview ON preview.id = allocation_group.preview_id
WHERE preview.id IS NULL
UNION ALL
SELECT 'allocation_item_without_group', count(*)
FROM hub.print_allocation_item allocation_item
LEFT JOIN hub.print_allocation_group allocation_group ON allocation_group.id = allocation_item.group_id
WHERE allocation_group.id IS NULL;

SELECT 'filament_vendor_tenant_mismatch' AS check_name, count(*) AS violation_count
FROM hub.filaments filament
JOIN hub.vendors vendor ON vendor.id = filament.vendor_id
WHERE filament.tenant_id IS DISTINCT FROM vendor.tenant_id
UNION ALL
SELECT 'filament_material_type_tenant_mismatch', count(*)
FROM hub.filaments filament
JOIN hub.material_types material_type ON material_type.id = filament.material_type_id
WHERE filament.tenant_id IS DISTINCT FROM material_type.tenant_id
UNION ALL
SELECT 'allocation_group_filament_tenant_mismatch', count(*)
FROM hub.print_allocation_group allocation_group
JOIN hub.filaments filament ON filament.id = allocation_group.filament_id
WHERE allocation_group.tenant_id IS DISTINCT FROM filament.tenant_id
UNION ALL
SELECT 'allocation_group_preview_tenant_mismatch', count(*)
FROM hub.print_allocation_group allocation_group
JOIN hub.print_allocation_preview preview ON preview.id = allocation_group.preview_id
JOIN hub.print_jobs job ON job.id = preview.print_job_id
JOIN hub.printers printer ON printer.id = job.printer_id
WHERE allocation_group.tenant_id IS DISTINCT FROM printer.tenant_id
UNION ALL
SELECT 'allocation_item_group_tenant_mismatch', count(*)
FROM hub.print_allocation_item allocation_item
JOIN hub.print_allocation_group allocation_group ON allocation_group.id = allocation_item.group_id
WHERE allocation_item.tenant_id IS DISTINCT FROM allocation_group.tenant_id
UNION ALL
SELECT 'allocation_item_spool_tenant_mismatch', count(*)
FROM hub.print_allocation_item allocation_item
JOIN hub.filament_spools spool ON spool.id = allocation_item.filament_spool_id
JOIN hub.filaments filament ON filament.id = spool.filament_id
WHERE allocation_item.tenant_id IS DISTINCT FROM filament.tenant_id
UNION ALL
SELECT 'job_spool_usage_job_tenant_mismatch', count(*)
FROM hub.job_spool_usage usage
JOIN hub.print_jobs job ON job.id = usage.print_job_id
JOIN hub.printers printer ON printer.id = job.printer_id
WHERE usage.tenant_id IS DISTINCT FROM printer.tenant_id
UNION ALL
SELECT 'job_spool_usage_spool_tenant_mismatch', count(*)
FROM hub.job_spool_usage usage
JOIN hub.filament_spools spool ON spool.id = usage.filament_spool_id
JOIN hub.filaments filament ON filament.id = spool.filament_id
WHERE usage.tenant_id IS DISTINCT FROM filament.tenant_id;
```

## 4. Record RLS and role posture

These results are environmental evidence only. Do not alter them during this
preflight.

```sql
SELECT rolname, rolsuper, rolbypassrls, rolcanlogin
FROM pg_roles
WHERE rolname IN ('hub_runtime', 'hub_flyway', 'hub_schema_owner')
ORDER BY rolname;

SELECT relation.relname,
       relation.relrowsecurity AS rls_enabled,
       relation.relforcerowsecurity AS rls_forced
FROM pg_class relation
JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
WHERE namespace.nspname = 'hub'
  AND relation.relname IN (
      'tenants', 'printers', 'vendors', 'material_types', 'filaments',
      'print_allocation_group', 'print_allocation_item', 'job_spool_usage',
      'filament_spools', 'print_jobs', 'printer_stats',
      'maintenance_definition', 'maintenance_execution',
      'print_allocation_preview'
  )
ORDER BY relation.relname;
```

## Classification and stop conditions

- `BLOCKED_HOME`: `home_installations` exists, or evidence identifies the
  database as home mode. Do not convert or roll it back. Home-to-secure remains
  a separately approved, backup-first migration.
- `CANDIDATE_LEGACY_CLAIM`: no home marker; exactly one default tenant; zero
  Hub users and memberships; all ownership queries pass; source migration state
  is recorded. Only after separate approval, restore an isolated clone and
  rerun this preflight there.
- `CANDIDATE_CLEAN_JIT`: no tenants, users, memberships, or tenant-owned rows.
  The empty-database smoke already covers this case; no data-clone exercise is
  needed.
- `BLOCKED_EXISTING_IDENTITIES`: any Hub user or membership exists. Do not
  manually create, bind, or rewrite users and memberships. Design a separate
  identity reconciliation first.
- `BLOCKED_AMBIGUOUS_OWNERSHIP`: more than one or no default tenant with data,
  a missing direct owner, an integrity violation, incomplete visibility, or an
  unexpected query failure. Stop. Design a separate data migration; never
  infer ownership from names or current operator identity.

V8 creates the serialized bootstrap state only when a legacy default tenant is
present, and the state starts closed. A candidate is not permission to open it
on the source database or to let ordinary self-service JIT claim existing
data.

## Later clone-only exercise, requiring separate approval

If the result is `CANDIDATE_LEGACY_CLAIM`, the next proposal must be reviewed
separately. Its expected sequence is:

1. Create an approved backup and restore it into a new isolated disposable
   clone; never use the original development database.
2. Rerun this entire preflight against the clone and compare the redacted
   counts and classifications.
3. Apply V8 to the clone through the ordinary Flyway identity, then use a
   dedicated Hub-only data-smoke profile with self-service JIT disabled.
4. Open the serialized legacy bootstrap window only through a separately
   reviewed one-time operator procedure; one approved OIDC identity performs
   the claim.
5. Verify one active `OWNER` membership, the claimed bootstrap state closed,
   `tenants.is_default` cleared by the actual claim, tenant selection, CSRF,
   session expiry, and logout. Verify again that Core, MQTT, AMS, Notify, and
   secure background work never started.

This document deliberately contains no backup, restore, migration, bootstrap,
or runtime commands because each changes state and requires an explicit
approval.

After the candidate result and separate approval are recorded, use the
[Hub Secure-Dev Data Clone Smoke Runbook](hub-secure-dev-data-clone-smoke-runbook.md).
