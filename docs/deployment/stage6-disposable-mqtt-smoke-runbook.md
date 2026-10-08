# Stage 6 Disposable MQTT Tenant Envelope Smoke Runbook

## Purpose and boundary

This runbook is a controlled verification harness for the Stage 6 source
implementation. It uses a new disposable PostgreSQL cluster, a local-only
disposable NanoMQ broker, and local Core and Hub processes. It may reuse the
accepted remote-development Keycloak issuer only for the existing Hub BFF
login and Hub-to-Core provisioning path.

It never targets the source development database, production database,
production Keycloak, a live broker, a printer, an agent, Notify, or AMS. The
broker exposes only a loopback port. The only allowed test printer is a new
synthetic disabled printer with loopback-only connection values and no real
device identifiers.

This runbook is an execution control, not standing authorization to create a
database, start Docker or an application, log in to Keycloak, change a broker,
or delete disposable resources. Obtain an execution-window approval before
starting it. A successful run is disposable smoke evidence only; it is neither
a development-database nor production rollout, and it does not by itself
accept Stage 6.

## Preconditions

- Start from the reviewed Stage 6 source revision and record its branch and
  commit. Do not mix unrelated changes into the run.
- Use a new PostgreSQL cluster and uniquely named database that share nothing
  with development or production. Before Flyway, run
  [the disposable role bootstrap](stage5-disposable-postgres-bootstrap.sql)
  once against that database with
  `-v stage5_smoke_database=<exact-disposable-database-name>`. The historical
  variable name is intentional; the script is also the approved restricted
  role bootstrap for this isolated Stage 6 run.
- Generate distinct ephemeral passwords for all seven PostgreSQL login roles
  and for the two broker identities. Put them only in the approved ephemeral
  secret mechanism. Do not put them in repository files, shell history, logs,
  screenshots, or evidence.
- Create an external NanoMQ password file that is readable only by the local
  operator and contains exactly the two identities `edolcore-events` and
  `edolhub-secure`, in the password-file format supported by the pinned
  NanoMQ image. Its path is supplied through `EDOL_STAGE6_NANOMQ_PASSWORD_FILE`.
  Do not add this file to the repository.
- Select unused loopback ports for PostgreSQL, MQTT, Core, and Hub. Confirm
  that no selected port maps to a non-loopback address and do not stop an
  occupied process.
- Reuse the already accepted remote-development Keycloak issuer and existing
  Hub web/service client secrets only through their approved secret files.
  Do not change any Keycloak user, client, role, realm setting, credential, or
  secret.
- Stage 6 smoke uses plain local MQTT only to prove the limited disposable
  ACL. It is not TLS evidence. Do not reinterpret this harness as deployment
  configuration or as the Stage 7 per-service TLS identity design.

## Disposable configuration contract

Start Core with:

```text
EDOL_DEPLOYMENT_MODE=secure-multi-tenant
SPRING_PROFILES_ACTIVE=secure-multi-tenant,stage6-smoke
```

Core requires these disposable-only values:

```text
EDOL_CORE_STAGE6_SMOKE_DB_JDBC_URL
EDOL_CORE_STAGE6_SMOKE_RUNTIME_DB_USER
EDOL_CORE_STAGE6_SMOKE_RUNTIME_DB_PASSWORD
EDOL_CORE_STAGE6_SMOKE_FLYWAY_DB_USER
EDOL_CORE_STAGE6_SMOKE_FLYWAY_DB_PASSWORD
EDOL_CORE_STAGE6_SMOKE_CATALOG_DB_JDBC_URL
EDOL_CORE_STAGE6_SMOKE_CATALOG_DB_USER
EDOL_CORE_STAGE6_SMOKE_CATALOG_DB_PASSWORD
EDOL_CORE_STAGE6_SMOKE_PORT
EDOL_CORE_STAGE6_MQTT_USERNAME
EDOL_CORE_STAGE6_MQTT_PASSWORD
EDOL_STAGE6_SMOKE_MQTT_URL
```

Set `EDOL_CORE_STAGE6_MQTT_USERNAME` to `edolcore-events`. The Core profile
keeps the generic agent MQTT transport and printer runtime disabled, while it
enables only the dedicated Core integration-event publisher.

Start Hub with the same deployment mode and profiles. It requires:

```text
EDOL_HUB_STAGE6_SMOKE_DB_JDBC_URL
EDOL_HUB_STAGE6_SMOKE_RUNTIME_DB_USER
EDOL_HUB_STAGE6_SMOKE_RUNTIME_DB_PASSWORD
EDOL_HUB_STAGE6_SMOKE_FLYWAY_DB_USER
EDOL_HUB_STAGE6_SMOKE_FLYWAY_DB_PASSWORD
EDOL_HUB_STAGE6_SMOKE_PORT
EDOL_HUB_STAGE6_MQTT_USERNAME
EDOL_HUB_STAGE6_MQTT_PASSWORD
EDOL_CORE_STAGE6_SMOKE_URL
EDOL_STAGE6_SMOKE_MQTT_URL
```

Set `EDOL_HUB_STAGE6_MQTT_USERNAME` to `edolhub-secure`. Do not set either
service's Stage 5 variables as a fallback.

Start the broker only through the isolated composition:

```text
EDOL_STAGE6_SMOKE_MQTT_PORT=<unused-loopback-port>
EDOL_STAGE6_NANOMQ_PASSWORD_FILE=<external-secret-file>
docker compose -f docker/compose.stage6-smoke.yaml up -d
```

The composition publishes `${EDOL_STAGE6_SMOKE_MQTT_PORT}` solely as
`127.0.0.1:<port>`, rejects anonymous access, permits only `edolcore-events`
to publish `edolcore/#`, and permits only `edolhub-secure` to subscribe to
that topic space. It must not be combined with the normal EDOL Compose files.

## Execution sequence

1. Record the empty service set, disposable database name, broker loopback
   port, Core and Hub ports, source revision, and the generated synthetic
   identifiers without recording passwords, connection strings containing
   credentials, tokens, or client secrets.
2. Create the fresh PostgreSQL cluster/database, run the restricted role
   bootstrap, inject role passwords, and verify all application roles are
   `NOSUPERUSER` and `NOBYPASSRLS`.
3. Start the isolated broker and require a healthy container and an explicit
   loopback-only port binding. Record redacted effective ACL evidence. Do not
   modify a pre-existing broker.
4. Start Core. Require Core Flyway through V9, a loopback listener, successful
   catalog datasource initialization, and the dedicated integration publisher
   connection. Require absence of generic agent MQTT transport, printer
   runtime, camera runtime, AMS, and Notify activity.
5. Start Hub. Require Hub Flyway through V9, a loopback listener, Core service
   client setup, and the `edolhub-secure` MQTT subscription. Require absence
   of catalog/recovery, AMS, and Notify startup paths.
6. Complete the existing BFF login using an authorized remote-development
   identity, entering credentials directly in the browser. Through Hub create
   one synthetic printer with `enabled=false`, loopback-only connection values,
   and dummy non-production values. Verify the same printer UUID is projected
   in Hub and that Core has the Hub-assigned tenant UUID.
7. With an approved local MQTT operator client and the **disposable Core
   identity**, publish one QoS 1 synthetic message to
   `edolcore/printer/online`. It must use a new UUID for `eventId`, the
   persisted synthetic printer and tenant UUIDs, an RFC 3339 UTC timestamp,
   and both additive and legacy-compatible fields:

   ```json
   {
     "schemaVersion": 2,
     "eventId": "<new-event-uuid>",
     "eventType": "printer.online",
     "tenantId": "<persisted-tenant-uuid>",
     "printerId": "<persisted-printer-uuid>",
     "timestamp": "<RFC-3339-UTC-timestamp>",
     "payload": {},
     "event": "printer.online"
   }
   ```

   This is an ACL/Hub-ingress fixture, not printer telemetry and not proof that
   a physical device emitted an event. It must not contain a real device value.
8. Verify that Hub accepts the valid fixture once, that exactly one matching
   tenant-RLS receipt exists, and that the receipt contains the synthetic
   event/printer/tenant identities. Re-publish the byte-equivalent message at
   QoS 1 and verify that no second receipt or duplicate mutation appears.
9. Publish two further synthetic no-op `printer.online` messages with different
   event IDs in reverse timestamp order. Verify both are received once and no
   cross-tenant or print-job mutation occurs. This tests QoS 1 reordering
   handling only; it is not physical print-lifecycle evidence.
10. Perform negative checks using only this disposable broker:

    - anonymous connection is rejected;
    - the Hub identity cannot publish to `edolcore/#`;
    - the Core identity cannot subscribe to `edolcore/#`;
    - a validly authenticated Core publish with an unknown printer or a tenant
      different from the persisted Hub projection is rejected before a receipt
      or domain mutation;
    - missing/malformed envelope identifiers, an invalid timestamp, and a
      mismatch between `eventType` and legacy `event` are rejected before a
      receipt or domain mutation.

    Never paste broker passwords into commands captured in evidence. Use the
    local operator client's secret injection mechanism.
11. Retain redacted Core/Hub/broker logs, Flyway versions, effective loopback
    bindings, ACL result summaries, and read-only receipt evidence. Also retain
    the Stage 6 component-test output that verifies a real Core publisher emits
    the additive envelope from persisted ownership. Do not claim the synthetic
    broker fixture proves physical-printer telemetry or full print projection.

## Evidence checklist

- Source branch and commit, clean pre-run repository status, and separated
  disposable resource identifiers.
- PostgreSQL role/`NOBYPASSRLS` checks, Core/Hub Flyway versions through V9,
  and read-only proof of the Core ownership and Hub projection tuple.
- Broker image digest or pinned tag, health, loopback-only binding, redacted
  ACL configuration, and each positive/negative authorization result.
- Core and Hub startup excerpts proving their Stage 6 profiles and proving the
  generic agent/runtime paths did not start.
- Redacted valid envelope, single receipt for the first publish, no additional
  receipt for the duplicate, reordered no-op results, and each rejected
  negative message with unchanged receipt/domain state.
- Test command/output and its environmental limitation. Do not include
  passwords, access codes, tokens, cookies, client secrets, full JDBC URLs with
  credentials, real addresses, serial numbers, or device identifiers.

## Stop conditions, rollback, and cleanup

Stop immediately if a target is not the recorded disposable resource; a port
is not loopback; a role is superuser or `BYPASSRLS`; a password is about to be
recorded; Flyway clean is enabled; a normal EDOL Compose stack or an existing
broker is selected; a live printer, agent, AMS, Notify, development database,
production resource, or Keycloak mutation is involved; or a test requires
TLS/deployment evidence that this harness does not provide. Preserve redacted
evidence and do not repair the environment in place.

Rollback is operational and additive: stop Hub's disposable smoke process
first, then Core and the smoke-owned broker. Do not delete `V9` receipt data,
drop database objects, remove roles, or destroy the cluster as part of rollback.
Keep the disposable cluster and evidence for review; deletion is a separately
approved action. Stage 7 migration of Notify/AMS identities and consumers,
Stage 8 terminal enrollment, and Stage 9 legacy-field removal/TLS lockdown are
explicitly outside this runbook.
