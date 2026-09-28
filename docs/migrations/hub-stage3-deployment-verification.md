# Hub Stage 3 Deployment and Verification

This is the single deployment runbook for Stage 3. It never runs Flyway repair
and never changes historical migration files. Keep role passwords, connection
strings, backups, and command output containing secrets outside the repository.

## What is automated and what is DBA work

- Flyway is started by Hub and owns versioned schema work: tables, constraints,
  data conversions, and V7 RLS policies. It must not create the runtime role,
  because Flyway itself needs a separate, elevated migration identity.
- The DBA bootstrap below is a one-time database-instance operation: create
  login roles, establish object ownership, and grant access. It is not a
  Flyway migration and must not run from the Hub application.

## Choose the target state

| Target state | DBA bootstrap | First Hub start |
| --- | --- | --- |
| Clean database | Run it before Hub starts. It creates the empty `hub` schema. | Flyway applies V1 through V7, then the DBA applies the runtime-grant correction before normal traffic. No legacy tenant exists, so background compatibility work skips and tenant endpoints fail closed until a later onboarding stage creates one. |
| Existing database at V1-V6 | Back up first, stop Hub, then run bootstrap. It transfers existing Hub object ownership itself. | Flyway applies only V7, then the DBA applies the runtime-grant correction before normal traffic. |
| Existing database already at V7 | Back up first, stop Hub, then run bootstrap. It transfers existing Hub object ownership itself. | Flyway validates history only. The DBA still applies the runtime-grant correction; never rerun or repair V7. |

## One-time DBA bootstrap

1. For an existing database, take and record a restorable backup. Record the
   release identity, backup checksum, `hub.flyway_schema_history`, object
   owners, role flags, and current Hub environment. Stop every Hub instance;
   do not stop Core, MQTT, printers, or unrelated services.
2. Connect as the database deployment administrator and run
   [`hub-stage3-database-roles.sql`](hub-stage3-database-roles.sql) with
   `psql -v ON_ERROR_STOP=1 -f docs/migrations/hub-stage3-database-roles.sql`.
   The script is one transaction: it creates the three roles, transfers only
   Hub relations and non-extension functions to `hub_schema_owner`, and grants
   least privilege to `hub_runtime`. It never changes table data or Core. It
   fails without making a change if any Stage 3 role already exists.
3. Set passwords for `hub_flyway` and `hub_runtime` through the approved secret
   mechanism. Do not put passwords in the SQL script or repository. Set the
   deployment-secret values for `HUB_RUNTIME_DB_USER`,
   `HUB_RUNTIME_DB_PASSWORD`, `HUB_FLYWAY_DB_USER`, and
   `HUB_FLYWAY_DB_PASSWORD`; the user values must name `hub_runtime` and
   `hub_flyway`, not `POSTGRES_USER`. For an existing migration tenant, set
   `edol-hub.legacy-default-tenant-compatibility.tenant-id` to that UUID. A
   clean database deliberately has no such tenant and leaves this value unset.
4. The role script grants the runtime role access to Hub application tables,
   sequences, and functions, explicitly excluding `hub.flyway_schema_history`.
   It establishes matching defaults for future `hub_schema_owner` migrations.
   It also grants `hub_flyway` only schema usage and CRUD access to Flyway
   history, because Flyway reads that history before its init SQL assumes the
   owner role. Do not rerun V7 to obtain these grants.
5. Confirm the effective identities before starting Hub:

   ```sql
   SELECT rolname, rolsuper, rolbypassrls
   FROM pg_roles
   WHERE rolname IN ('hub_schema_owner', 'hub_flyway', 'hub_runtime');

   SELECT nspname, pg_get_userbyid(nspowner) AS owner
   FROM pg_namespace
   WHERE nspname = 'hub';

   SELECT c.relname, pg_get_userbyid(c.relowner) AS owner
   FROM pg_class c
   JOIN pg_namespace n ON n.oid = c.relnamespace
   WHERE n.nspname = 'hub' AND c.relkind IN ('r', 'S')
   ORDER BY c.relname;
   ```

   `hub_runtime` must be neither owner, superuser, nor `BYPASSRLS`.

## Start Hub and verify

1. If V7 is not installed, start one Hub instance with the Flyway connection
   as `hub_flyway`. Its init SQL changes only the effective migration role to
   `hub_schema_owner`; retain `session_user` evidence showing `hub_flyway`.
   Keep normal traffic disabled for that first run. If V7 is already
   successful, leave Hub stopped for the correction below; do not invoke
   Flyway repair or rerun V7.
2. Keep normal Hub traffic disabled, then execute
   [`hub-stage3-runtime-grant-correction.sql`](hub-stage3-runtime-grant-correction.sql)
   exactly once as the database deployment administrator:

   ```text
   psql -v ON_ERROR_STOP=1 -f docs/migrations/hub-stage3-runtime-grant-correction.sql
   ```

   The correction is required after the first Flyway run even on a clean
   database: V7 historically grants runtime CRUD to every Hub table, including
   the Flyway history table. On a database already at V7, apply it while Hub is
   stopped before deploying the corrected artifact. It only revokes runtime
   access to that history; it does not edit history, schema, or application
   data. Start or restart Hub after a successful correction before returning it
   to normal traffic.
3. Confirm that the runtime role has no Flyway-history privilege and that the
   Flyway role retains its required privilege:

   ```sql
   WITH requested(privilege) AS (
       VALUES ('SELECT'), ('INSERT'), ('UPDATE'), ('DELETE')
   )
   SELECT role_name,
          bool_or(has_table_privilege(role_name, 'hub.flyway_schema_history', requested.privilege))
              AS has_any_history_access,
          bool_and(has_table_privilege(role_name, 'hub.flyway_schema_history', requested.privilege))
              AS has_all_history_access
   FROM (VALUES ('hub_runtime'), ('hub_flyway')) AS roles(role_name)
   CROSS JOIN requested
   GROUP BY role_name
   ORDER BY role_name;
   ```

   The required result is `false` for `hub_runtime.has_any_history_access` and
   `true` for `hub_flyway.has_all_history_access`.
4. Confirm Flyway history has a new successful V7 entry and that V1–V6,
   including repaired V2 checksum `818545878`, are unchanged.
5. Confirm every policy table has RLS enabled and forced:

   ```sql
   SELECT c.relname, c.relrowsecurity, c.relforcerowsecurity,
          p.polname, pg_get_expr(p.polqual, p.polrelid) AS using_expression,
          pg_get_expr(p.polwithcheck, p.polrelid) AS with_check_expression
   FROM pg_class c
   JOIN pg_namespace n ON n.oid = c.relnamespace
   LEFT JOIN pg_policy p ON p.polrelid = c.oid
   WHERE n.nspname = 'hub'
     AND c.relname IN (
       'tenants', 'printers', 'vendors', 'material_types', 'filaments',
       'filament_spools', 'print_jobs', 'printer_stats',
       'maintenance_definition', 'maintenance_execution',
       'print_allocation_preview', 'print_allocation_group',
       'print_allocation_item', 'job_spool_usage'
     )
   ORDER BY c.relname, p.polname;
   ```

6. In two separate `hub_runtime` transactions, set a different tenant with
   parameterized `set_config('edol.tenant_id', ..., true)` and prove each can
   read only its own direct and derived rows. Repeat an attempted cross-tenant
   insert/update/delete and record the RLS denial.
7. Commit and roll back separate transactions, then on a reused runtime
   connection run `select current_setting('edol.tenant_id', true)`. It must be
   empty or null and a tenant-table read without setting it must return no rows.
8. Exercise each explicitly allowed legacy HTTP route, one scheduled sync,
   startup recovery, and an MQTT event in a non-production environment. Verify
   one structured compatibility log with `metricName` and incrementing
   `metricValue` per ingress.
   Exercise a non-allowlisted path and a direct persistence call without scope;
   both must fail closed.

## Controlled rollback

1. Stop Hub and retain the failed-release logs, Flyway history, role/grant
   snapshot, and test evidence.
2. Deploy the accepted Stage 2 artifact with its original, compatible database
   credentials only through the approved rollback change. Do not edit resolver
   code, add a default fallback, grant `BYPASSRLS`, or run Flyway repair.
3. A database administrator must apply the reviewed rollback grants/RLS plan
   from the change record before starting Stage 2. The decision to disable RLS
   is a controlled security rollback and must be separately recorded.
4. Re-run the read-only Hub health check and record the exact role and RLS
   state. A later forward rollout must repeat the complete Stage 3 procedure.
