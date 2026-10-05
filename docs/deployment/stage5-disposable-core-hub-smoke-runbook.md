# Stage 5 Disposable Core and Hub Smoke Runbook

## Purpose and boundary

This is the only approved shape for a Stage 5 Core and Hub runtime
smoke. It uses a fresh disposable PostgreSQL cluster, local Core and Hub
processes, and the accepted remote-development Keycloak issuer. It never uses
the source development database, production, an MQTT broker, AMS, Notify, or a
printer or agent device.

The final fresh disposable acceptance completed on 2026-10-05 from commit
`7e5f66a295675d8b365a667ead3d5a997d71e2b0`. This runbook remains an execution
control, not standing authorization for a new runtime start, database creation,
role bootstrap, browser login, Keycloak change, or deletion of disposable
resources. Obtain a separate approval for each execution window.

## Preconditions

- Use a new disposable PostgreSQL cluster and a uniquely named database. It
  must not share a cluster with source development or production databases.
- Run [Stage 5 disposable PostgreSQL bootstrap](stage5-disposable-postgres-bootstrap.sql)
  exactly once before Flyway. The script must report the exact disposable
  database match and must not find pre-existing target roles. It provisions the
  `pgcrypto` database extension as the administrator before restricted Hub
  Flyway runs; do not grant database `CREATE` to `hub_flyway` or `hub_runtime`.
- Generate distinct passwords for `core_flyway`, `core_runtime`,
  `edol_core_catalog_runtime`, `hub_flyway`, and `hub_runtime` in the approved
  ephemeral secret mechanism. Never record their values in this repository,
  shell history, logs, or evidence.
- The remote-development Keycloak issuer and the existing `edol-hub-web` and
  `edol-hub-service` secrets must be injected from their approved secret files.
  Do not change Keycloak during this exercise.
- Confirm that the Core and Hub ports selected for the smoke are free before
  startup. Do not stop an occupied process.
- When Docker-dependent tests are needed on Windows, use
  `with-remote-docker.ps1` only for Testcontainers. It is not a launcher for
  persistent application services.

## Profile contract

Start both applications with:

```text
EDOL_DEPLOYMENT_MODE=secure-multi-tenant
SPRING_PROFILES_ACTIVE=secure-multi-tenant,stage5-smoke
```

The Core profile requires these disposable-only inputs:

```text
EDOL_CORE_STAGE5_SMOKE_DB_JDBC_URL
EDOL_CORE_STAGE5_SMOKE_RUNTIME_DB_USER
EDOL_CORE_STAGE5_SMOKE_RUNTIME_DB_PASSWORD
EDOL_CORE_STAGE5_SMOKE_FLYWAY_DB_USER
EDOL_CORE_STAGE5_SMOKE_FLYWAY_DB_PASSWORD
EDOL_CORE_STAGE5_SMOKE_CATALOG_DB_JDBC_URL
EDOL_CORE_STAGE5_SMOKE_CATALOG_DB_USER
EDOL_CORE_STAGE5_SMOKE_CATALOG_DB_PASSWORD
EDOL_CORE_STAGE5_SMOKE_PORT
```

The Hub profile requires these disposable-only inputs:

```text
EDOL_HUB_STAGE5_SMOKE_DB_JDBC_URL
EDOL_HUB_STAGE5_SMOKE_RUNTIME_DB_USER
EDOL_HUB_STAGE5_SMOKE_RUNTIME_DB_PASSWORD
EDOL_HUB_STAGE5_SMOKE_FLYWAY_DB_USER
EDOL_HUB_STAGE5_SMOKE_FLYWAY_DB_PASSWORD
EDOL_CORE_STAGE5_SMOKE_URL
```

The profile has no fallback JDBC URL. It disables Flyway clean, Core Paho MQTT
transport, Core printer-runtime bootstrap and scheduler paths, camera snapshot
storage, and Hub MQTT. Core and Hub MQTT endpoints are both loopback port 1;
there must be no broker in the disposable service set.

## Execution sequence after separate approval

1. Record the branch, commit, empty service set, disposable database name, and
   allocated local ports without recording credentials or identifiers.
2. Create the new PostgreSQL cluster/database, run the role bootstrap, inject
   the disposable role credentials, and verify every role is non-superuser and
   `NOBYPASSRLS`.
3. Start Core first. Require successful Core Flyway V9, the resource-server
   configuration, a live local listener, and absence of Paho MQTT adapter and
   printer-runtime startup evidence. `/actuator/health` is the only permitted
   unauthenticated read-only readiness request.
4. Start Hub second. Require successful Hub Flyway, the local Core URL, the
   service-client configuration, and absence of Hub MQTT, catalog, recovery,
   AMS, and Notify startup evidence.
5. Complete the normal browser BFF login only with an existing authorized
   remote-development identity. Enter credentials directly in the browser;
   never provide them to an assistant or script.
6. Create at most one synthetic printer through Hub with `enabled=false`,
   loopback-only connection endpoints, and non-production dummy values. Do not
   enter a real serial number, address, access code, agent ID, or any live
   device data. Verify Core persisted the Hub tenant and Hub projected the same
   printer UUID.
7. Capture redacted evidence that a missing token, incorrect audience/client,
   missing or malformed tenant header, and invalid scope are rejected. Verify
   a valid Hub request is accepted only with the service token and trusted
   tenant header. Do not replay or record tokens.
8. Verify RLS with disposable-only role sessions: cross-tenant reads/writes
   fail; the catalog role can read only `core.printers(id, tenant_id, enabled)`;
   it cannot perform DML, access non-catalog Core tables, or access `hub`.

## Stop conditions and cleanup

Stop immediately if a JDBC target is not the recorded disposable database; a
role is superuser or `BYPASSRLS`; Flyway clean is enabled; a Paho adapter,
MQTT broker, Core device runtime, AMS, Notify, or a printer/agent connection
appears; or any endpoint could reach a live device. Preserve redacted evidence
and do not repair the environment in place.

Stop only the smoke-owned Core and Hub processes through their bounded
supervisor. Retain the disposable database and redacted evidence for review.
Deleting the database, cluster, or evidence is a separate explicit approval.
