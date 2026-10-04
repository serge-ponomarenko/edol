# Hub Secure-Dev Data Clone Smoke Runbook

## Purpose and boundary

This runbook describes the separately approved, Hub-only exercise for a
`CANDIDATE_LEGACY_CLAIM` development database. It creates an isolated,
disposable PostgreSQL clone, applies Hub V8 to that clone only, and validates
one serialized OIDC first-owner claim.

It must never run against the source development database or production. It
does not authorize a source migration, manual user or membership binding,
Keycloak realm changes, Core, MQTT, AMS, Notify, printer-facing services, or
secure background work. It is not a home-mode procedure and must not disable
RLS.

The source `edol` database is currently a candidate because its read-only
preflight recorded one non-empty default tenant, zero Hub users and
memberships, successful V1 through V7, no home marker, and no ownership or
integrity violations. Repeat its preflight if its state can have changed before
the backup begins.

## Required approvals and inputs

Obtain a separate approval for all state-changing steps before beginning:

- creation of a source backup and a named disposable clone database;
- clone-only database grants and role verification;
- the `dev-data-smoke` source configuration described below;
- Hub-only startup against the clone;
- the one-time bootstrap-window opening and one approved OIDC login; and
- later deletion of the clone and backup under the retention policy.

Use an approved clone database name and an owner-only backup directory outside
Git. Keep database connection strings, credentials, Keycloak client secrets,
cookies, tokens, authorization codes, user identities, and tenant identifiers
out of commands, logs, evidence, and this repository.

## 1. Freeze the source scope and create the archive

Do not make a backup while an unreviewed Hub write workload is active. Either
stop Hub for the source database or establish an approved write barrier for the
backup interval. Do not start, stop, or contact Core, MQTT, AMS, Notify, or any
printer-facing service for this exercise.

The DBA uses the existing private administrative PostgreSQL route to create a
custom archive of the source `edol` database. The archive must contain data and
schema only; it must not recreate database roles or privileges.

```text
pg_dump --format=custom --no-owner --no-privileges --file=<owner-only-archive> <source-edol-connection>
sha256sum <owner-only-archive>
pg_restore --list <owner-only-archive>
```

`<source-edol-connection>` is supplied only through the approved server-side
mechanism. Retain the archive name, checksum, PostgreSQL major version, and a
successful archive-list result as redacted evidence. Do not use `pg_dumpall`,
do not export roles, and do not place the archive in the repository.

## 2. Create and restore the isolated clone

The clone must be a new private PostgreSQL database. It must not publish a host
port, reuse the source database name, or be included in an application’s normal
development profile.

The existing global roles `hub_schema_owner`, `hub_flyway`, and `hub_runtime`
are reused only within the clone database. Do not create replacement roles and
do not change the source database grants. The DBA creates the clone with
`hub_schema_owner` as database owner and restores schema/data as that owner:

```text
createdb --owner=hub_schema_owner --template=template0 --encoding=UTF8 <clone-database>
pg_restore --exit-on-error --no-owner --no-privileges --role=hub_schema_owner \
  --dbname=<clone-database> <owner-only-archive>
```

Before every clone-only SQL block, the DBA records `current_database()` and
confirms it equals `<clone-database>`. A mismatch is a stop condition. The
source `edol` database must not receive clone grants, restores, V8, or bootstrap
state changes.

## 3. Reconcile clone-only secure role grants

Because the archive intentionally excludes privileges, the DBA applies the
following only while connected to `<clone-database>`. It restores the accepted
Stage 3 separation without giving `hub_runtime` Flyway-history access. The
existing global membership that allows `hub_flyway` to `SET ROLE
hub_schema_owner` is not changed.

```sql
REVOKE ALL PRIVILEGES ON DATABASE <clone-database> FROM PUBLIC;
GRANT CONNECT ON DATABASE <clone-database> TO hub_flyway, hub_runtime;

REVOKE ALL ON SCHEMA hub FROM PUBLIC;
GRANT USAGE ON SCHEMA hub TO hub_flyway, hub_runtime;

GRANT SELECT, INSERT, UPDATE, DELETE
ON TABLE hub.flyway_schema_history TO hub_flyway;

GRANT SELECT, INSERT, UPDATE, DELETE
ON ALL TABLES IN SCHEMA hub TO hub_runtime;
REVOKE ALL PRIVILEGES ON TABLE hub.flyway_schema_history FROM hub_runtime;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA hub TO hub_runtime;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA hub TO hub_runtime;

ALTER DEFAULT PRIVILEGES FOR ROLE hub_schema_owner IN SCHEMA hub
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO hub_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE hub_schema_owner IN SCHEMA hub
    GRANT USAGE, SELECT ON SEQUENCES TO hub_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE hub_schema_owner IN SCHEMA hub
    REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
ALTER DEFAULT PRIVILEGES FOR ROLE hub_schema_owner IN SCHEMA hub
    GRANT EXECUTE ON FUNCTIONS TO hub_runtime;
```

Replace `<clone-database>` only through DBA-controlled identifier quoting. Do
not paste an unquoted database name into this block. Capture redacted evidence
for the following results:

```sql
SELECT rolname, rolsuper, rolbypassrls, rolcanlogin
FROM pg_roles
WHERE rolname IN ('hub_schema_owner', 'hub_flyway', 'hub_runtime')
ORDER BY rolname;

SELECT has_database_privilege('hub_flyway', current_database(), 'CONNECT')
           AS flyway_can_connect,
       has_database_privilege('hub_runtime', current_database(), 'CONNECT')
           AS runtime_can_connect;

WITH requested(privilege) AS (
    VALUES ('SELECT'), ('INSERT'), ('UPDATE'), ('DELETE')
)
SELECT role_name,
       bool_or(has_table_privilege(
           role_name, 'hub.flyway_schema_history', requested.privilege
       )) AS has_any_history_access
FROM (VALUES ('hub_runtime'), ('hub_flyway')) AS roles(role_name)
CROSS JOIN requested
GROUP BY role_name
ORDER BY role_name;
```

Required results: all three roles are non-superuser and `NOBYPASSRLS`;
`hub_schema_owner` is `NOLOGIN`; both login roles can connect; the history
result is `false` for `hub_runtime` and `true` for `hub_flyway`.

## 4. Re-run the data preflight on the clone

Run [Hub Secure-Dev Data Clone Preflight](hub-secure-dev-data-clone-preflight.md)
against the clone through its approved one-time DBA exception. It must reproduce
the source classification and all redacted counts before V8:

- V1 through V7 are successful and V8 is absent;
- one default tenant and zero users/memberships exist;
- all direct-owner, orphan, and cross-tenant checks remain zero; and
- all 14 policy tables retain enabled and forced RLS.

Any difference blocks the exercise. Preserve the clone and evidence for
diagnosis; do not repair or recreate it without a new approval.

## 5. Prepare the dedicated data-smoke profile

Do not use `dev` or the empty-database `dev-smoke` profile. The `dev` profile
belongs to the separate local development runtime and intentionally selects its
own PostgreSQL, Core, and MQTT endpoints. It must not be active for this
Hub-only secure exercise.
`application-dev-data-smoke.yaml` is the dedicated source profile and has this
exact effective configuration contract:

```yaml
spring:
  datasource:
    url: ${EDOL_HUB_DATA_SMOKE_DB_JDBC_URL}
  flyway:
    clean-disabled: true

edol-core:
  url: http://127.0.0.1:1

mqttServer:
  url: tcp://127.0.0.1:1

edol-hub:
  registration:
    self-service-enabled: false
  session:
    maximum-age: PT60S
```

`self-service-enabled: false` prevents personal-tenant creation for an
unrecognized or membership-less identity. It does not prevent the guarded
legacy claim when the serialized bootstrap window is open. The datasource
variable has no fallback and must name the isolated clone database only.

## 6. Apply V8 only through the Hub Flyway path

After runtime start receives approval, start Hub only with the following
effective inputs supplied by the approved secret and deployment mechanism:

```text
EDOL_DEPLOYMENT_MODE=secure-multi-tenant
SPRING_PROFILES_ACTIVE=secure-multi-tenant,dev-data-smoke
EDOL_HUB_DATA_SMOKE_DB_JDBC_URL=jdbc:postgresql://<private-host>:5432/<clone-database>
EDOL_KEYCLOAK_ISSUER_URI=https://auth.dev.edol.s-pon.dev/realms/edol
EDOL_HUB_WEB_CLIENT_SECRET=<secret-injection-reference>
```

Hub’s Flyway login remains `hub_flyway`; its configured initialization changes
only the effective migration role to `hub_schema_owner`. V8 must be the only
new successful history entry. Do not use Flyway repair, clean, baseline, or a
manual V8 SQL execution.

Before opening any browser session, retain redacted evidence that V8 succeeded,
`legacy_tenant_bootstrap_state` has one row with `claim_open=false`, exactly one
default tenant remains, and users/memberships remain zero. Confirm the Hub
startup evidence contains no Core, MQTT, AMS, Notify, or secure background-work
startup.

## 7. Separately controlled first-owner claim

The bootstrap-window state change is intentionally not included here. It is a
single clone-only DML operation and requires a final explicit approval after V8
evidence is reviewed. Do not log in before that approval, and do not manually
insert a Hub user, tenant membership, or active tenant.

The approved operation must prove the window was previously closed and
unclaimed, open it once, permit exactly one approved disposable Keycloak OIDC
identity to complete the normal Hub Authorization Code plus PKCE browser flow,
then prove that the function closed the window. No Keycloak user, client, role,
or realm change is part of this operation.

## 8. Acceptance evidence after the claim

Using the same Hub-only browser path, record redacted evidence of:

1. successful OIDC callback and automatic selection of the single membership;
2. exactly one Hub user and one active `OWNER` membership;
3. the same one legacy tenant with `is_default=false`;
4. one bootstrap-state row with `claim_open=false`, a claimed user, and a claim
   timestamp;
5. unchanged tenant-owned and derived data counts from the clone preflight;
6. tenant-selection rendering, a valid CSRF POST, and a missing-CSRF `403`;
7. a fresh OIDC chain after the 60-second maximum session age; and
8. logout reaching the approved post-logout URI, followed by a protected
   request that requires fresh authentication.

The operator must also record that Core, MQTT, AMS, Notify, printer-facing
services, and secure-mode background work never started. Redact all identity,
cookie, token, authorization-code, and tenant-identifier values.

## Cleanup

Stop Hub and retain the clone, archive, checksums, and redacted evidence until
the result is reviewed. Deleting the disposable clone or archive is a separate
approval. Never treat deletion as a rollback of the source development database
or as permission to alter production.

After this clone exercise passes and the required source rollout approval is
recorded, use the
[Hub Secure-Dev Source Database Rollout Runbook](hub-secure-dev-source-rollout-runbook.md).
