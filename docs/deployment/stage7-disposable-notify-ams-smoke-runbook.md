# Stage 7 Disposable Notify and AMS Smoke Runbook

## Purpose and boundary

This runbook verifies the Stage 7 source contract with a fresh disposable
PostgreSQL cluster, a local-only disposable MQTT broker, local Core, Hub,
Notify, and AMS processes, and an explicitly approved non-production Keycloak
realm or disposable clients. It builds on the accepted Stage 6 harness; it is
not a deployment procedure.

It never targets a development or production database, a production Keycloak
realm, a pre-existing broker, a printer, agent, AMS Terminal, or Notify bot.
The only printer is a new disabled synthetic printer with loopback-only dummy
connection values. All local listeners and broker ports must be loopback-only.
Use a separate disposable Telegram bot and chat only if the Notify delivery and
command checks are in scope for the execution window.

This document is an execution control, not standing authorization to create a
database, broker user, Keycloak client, secret, Telegram bot, or process.
Obtain a separate execution-window approval. A PASS is source/disposable
acceptance only. It is not a deployment, TLS, development-database, production,
live-device, terminal-enrollment, or Stage 8/9 acceptance.

## Executed disposable evidence (2026-10-09)

The execution against `4e88bc348177d91eb96dad39c47787c13892ef8b` is **PARTIAL
ACCEPTANCE — NOT FULL PASS**. It used isolated disposable PostgreSQL, NanoMQ,
and Keycloak resources with two synthetic tenants, printers, and spools. It
passed the restricted-role/RLS and Core/Hub V1–V9 checks, dedicated MQTT
connections and ACL denials, valid v2 Notify and AMS event handling, and the
exercised cross-tenant rejection paths.

It did not complete Stage 7 acceptance. `GET /ams/find` for a tenant-owned
spool and `/ams/state` for an unknown printer returned HTTP 500; both need
fresh disposable rerun evidence after source remediation. The complete
service-token negative matrix, recipient-delivery separation, invalid-mapping
startup evidence, legacy metric counters, and Maven test output were not
collected.
`/ams/set-spool` was deliberately not invoked because its downstream path can
issue a Core printer command; see the execution sequence below. The test did
not cover TLS, deployment configuration, home mode, terminal identity, or a
live device. Preserve the redacted evidence and disposable resources until a
separate cleanup approval.

## Stage 7 security boundary

Stage 7 gives the backend services independent identities. `edol-notify-service`
may call Core; `edol-ams-service` may call Core and the three AMS Hub routes.
Both service clients establish `X-EDOL-Tenant-Id` only after local trusted
mapping or a validated Core v2 MQTT envelope has selected the tenant.

AMS Terminal ingress is intentionally **not** authenticated in this stage. The
existing `/ams/*` request shape still supplies `printerId`; AMS resolves that
identifier through its deployment-managed printer-to-tenant mapping before it
calls Core or Hub. Consequently, run AMS only on a trusted disposable network
and do not claim that this proves terminal identity or prevents a network peer
from choosing another configured printer ID. Stage 8 owns one-time pairing,
per-terminal credentials, credential storage, revocation, and terminal firmware
changes.

`home` is not part of this secure acceptance. It has no Keycloak service
clients, secure mappings, or broker identities. A later explicit home-mode smoke
must verify its existing trusted-network `printerId` contract separately.

## Preconditions

- Record the exact branch and immutable local commit containing the approved
  Stage 7 source. Do not mix unrelated work into the run.
- Use a new disposable PostgreSQL cluster/database and run
  [the restricted role bootstrap](stage5-disposable-postgres-bootstrap.sql)
  exactly once before Flyway. Confirm all application roles are `NOSUPERUSER`
  and `NOBYPASSRLS`.
- Start from an isolated Stage 6-style NanoMQ broker with a new external,
  operator-readable-only password include. Do not alter an existing broker.
  Give every identity a separate generated password and never put passwords,
  tokens, client secrets, cookies, or full credential-bearing URLs in Git,
  terminal history, logs, screenshots, or evidence.
- Use an approved isolated Keycloak realm or disposable clients. Do not change
  the existing development or production realm. Configure five-minute access
  tokens, issuer validation, and audiences exactly as listed below. Retain only
  redacted client metadata and token claim summaries as evidence.
- Allocate fresh loopback ports for PostgreSQL, MQTT, Core, Hub, Notify, and
  AMS. Check each is free before starting; never stop an occupied process.
- Use two synthetic tenants, two synthetic disabled printers, two tenant-specific
  Telegram test chats, and at least one Hub spool per tenant. Do not use a real
  Telegram chat or a real printer/terminal identifier.

## Disposable identity and authorization contract

Create the following non-production service clients. Their secrets remain in
the approved ephemeral secret mechanism only.

| Client | Audience | Granted scopes | Required consumer access |
| --- | --- | --- | --- |
| `edol-hub-service` | `edol-core-api` | Existing Hub scopes plus `tenant.context` | Existing Hub-to-Core path |
| `edol-notify-service` | `edol-core-api` | `tenant.context`, `core.printer.read`, `core.state.read`, `core.command.execute`, `core.media.read` | Core only |
| `edol-ams-service` | `edol-core-api`, `edol-hub-api` | `tenant.context`, `core.state.read`, `hub.spool.read`, `hub.spool.change` | Core state and exact Hub spool routes |

The Core resource server must accept audience `edol-core-api`; Hub must accept
audience `edol-hub-api` for the AMS route set. The access token must carry
`azp` equal to the named service client and a `tenant.context` authority. A
Notify token must not be accepted by the Hub AMS routes; an AMS token must not
gain Notify-only Core access. Do not use a human browser token as a substitute.

Set the Core service-client allowlist to exactly the clients required for the
run, including `edol-hub-service`, `edol-notify-service`, and
`edol-ams-service`. This is an external disposable environment value, not a
tracked configuration change.

## Disposable MQTT ACL contract

Create separate broker identities with no anonymous access and no wildcard
privilege beyond the explicit Hub compatibility subscription:

| Identity | Publish | Subscribe |
| --- | --- | --- |
| `edolcore-events` | `edolcore/#` | None |
| `edolhub-secure` | None | `edolcore/#` |
| `edol-notify-service` | None | `edolcore/printer/online`, `edolcore/printer/offline`, `edolcore/print/{started,running,paused,finished,failed,error,progress,metadata,timelapse}` |
| `edol-ams-service` | None | `edolcore/ams`, `edolcore/print/ams` |

The NanoMQ password include is external and must contain the four quoted
identity/password mappings required by the chosen NanoMQ version. Record a
redacted effective ACL summary, not the password file.

## Secure profile and mapping contract

Start Core and Hub with:

```text
EDOL_DEPLOYMENT_MODE=secure-multi-tenant
SPRING_PROFILES_ACTIVE=secure-multi-tenant,stage6-smoke
```

Use the Stage 6 disposable Core/Hub inputs, substituting the fresh database,
loopback ports, broker URL, and the four-identity ACL arrangement. In addition,
inject the three-client Core allowlist and the external Keycloak issuer/secrets.
The Stage 6 smoke profile remains necessary to keep generic printer and agent
runtime disabled while permitting Core integration events and Hub MQTT receipt
handling.

Start Notify with `EDOL_DEPLOYMENT_MODE=secure-multi-tenant`, profile
`secure-multi-tenant`, its client credentials, the loopback Core/broker URLs,
and its dedicated MQTT identity. Supply the following only outside the
repository, replacing placeholders with synthetic identifiers:

```yaml
edol-notify:
  recipients:
    mappings:
      - tenant-id: <tenant-a-uuid>
        chat-ids: [<test-chat-a-id>]
      - tenant-id: <tenant-b-uuid>
        chat-ids: [<test-chat-b-id>]
```

Start AMS with `EDOL_DEPLOYMENT_MODE=secure-multi-tenant`, profile
`secure-multi-tenant`, its client credentials, loopback Core/Hub/broker URLs,
and its dedicated MQTT identity. Supply the following only outside the
repository:

```yaml
edol-ams:
  printer-tenants:
    mappings:
      - printer-id: <printer-a-uuid>
        tenant-id: <tenant-a-uuid>
      - printer-id: <printer-b-uuid>
        tenant-id: <tenant-b-uuid>
```

The mappings must be complete and unique. A missing, duplicated, or malformed
entry is a required startup failure. Do not add a tenant header to an AMS
Terminal request; AMS owns the mapping lookup in this stage.

## Execution sequence

1. Record the source revision, clean pre-run status, disposable identifiers,
   loopback ports, and empty service set. Exclude secrets and real identifiers.
2. Create the restricted disposable database and broker. Verify Flyway `clean`
   is disabled, all ports are loopback-only, broker anonymous access is denied,
   and the effective ACL matches the table above.
3. Start Core, then Hub. Require Core and Hub Flyway through V9, the existing
   secure Hub BFF login, Core persisted ownership, and Hub projection for two
   synthetic disabled printers in distinct tenants. Retain the Stage 6 proof
   that v2 Core events are accepted by Hub.
4. Start Notify and AMS. Require both to obtain their own client-credentials
   token and connect using only their own MQTT identity. Require the Notify and
   AMS mapping validations to complete. A startup failure caused by omitted
   mappings is a positive fail-closed check; restore the valid external mapping
   before continuing.
5. Verify broker authorization using only disposable identities: anonymous
   connection is rejected; neither Notify nor AMS can publish; Notify cannot
   subscribe to `edolcore/ams`; AMS cannot subscribe to Notify print topics;
   Core cannot subscribe; and Hub cannot publish. Record outcomes without
   recording passwords.
6. Publish a valid synthetic QoS 1 v2 `printer.online` event as
   `edolcore-events` for printer A. Include matching legacy `event` and
   `printerId`, a new event UUID, the persisted tenant/printer identities, UTC
   timestamp, and a payload object. Verify Notify accepts it, uses only tenant
   A's recipient scope for Core calls, and sends only to test chat A if the
   optional Telegram test is approved. Repeat for printer B and verify no chat
   A delivery or tenant-A Core call occurs for it.
7. Publish a valid synthetic QoS 1 v2 `ams.slot.loaded` or `ams.slot.changed`
   event on the corresponding Core AMS topic for printer A. Verify AMS accepts
   it only when its envelope tenant equals the configured printer-A tenant.
   Publish an otherwise valid event with printer A and tenant B; require AMS to
   reject it before a Hub spool mutation.
8. Exercise AMS HTTP with only synthetic data:

   - `GET /ams/state?printerId=<printer-a>` must use the configured tenant-A
     mapping and succeed only through AMS's Core service token.
   - `GET /ams/find?id=<tenant-a-spool>&printerId=<printer-a>` must use AMS's
     Hub read scope and return only the tenant-A spool.
   - Do **not** invoke
     `GET /ams/set-spool?id=<tenant-a-spool>&slot=<slot>&printerId=<printer-a>`
     in this runtime smoke. The AMS endpoint calls Hub's change route, which
     dispatches a Core `spool-change` command and can reach a printer command
     publisher. The Stage 6 smoke profile disables normal printer runtime but
     does not provide a safe command sink. Verify its authorization and
     propagation with an automated test that mocks or stubs the Core command
     boundary; a successful runtime invocation belongs only to a separately
     approved safe command-harness execution.
   - Repeat tenant-A spool access through printer B and require Hub's tenant
     isolation to reject it. An unknown printer ID must fail before Core or Hub
     is called.

9. Verify HTTP authorization with short-lived disposable client tokens by using
   a secret-safe operator method: wrong audience is `401`; wrong `azp`, missing
   `tenant.context`, or missing required scope is `403`; missing, duplicate, or
   malformed `X-EDOL-Tenant-Id` is `400` for a trusted service request. Verify
   an AMS token is accepted only at the three Hub routes and a Notify token is
   rejected there. Do not save or paste raw tokens into evidence.
10. Confirm legacy compatibility remains observable but is not used by the
    migrated service paths: Core legacy allowlist and Hub AMS compatibility
    ingress show zero requests during the secure client checks. Do not disable
    either path in this run.

## Required evidence

- Exact source revision, clean pre-run status, redacted disposable topology,
  loopback bindings, role `NOBYPASSRLS` proof, and Core/Hub Flyway V9 proof.
- Redacted Keycloak client metadata and claim summaries proving separate `azp`,
  audiences, scopes, and five-minute maximum token lifetime; never retain
  client secrets or raw access tokens.
- Redacted broker health, image/tag, four-identity ACL summary, and every
  successful/denied publish or subscription result.
- Notify and AMS startup excerpts proving secure profiles, independent client
  registrations, independent MQTT identities, and accepted mappings; retain
  the expected invalid-mapping startup rejection separately.
- Valid and invalid v2 fixtures; logs/metrics proving mapping selection,
  rejected envelope mismatch, recipient separation, zero legacy-path use, and
  unchanged Hub state after each rejected fixture.
- Core/Hub HTTP response status summaries for valid and invalid service clients,
  tenant headers, audiences, and scopes; AMS state/read results, cross-tenant
  rejection, and an automated mocked-Core result for the change route. Record
  that no runtime `/ams/set-spool` request was made unless a separate safe
  command-harness approval exists.
- Maven test outputs and explicit limitations. The expected limitation is that
  this harness does not prove TLS, production configuration, terminal identity,
  firmware storage, a live printer, or home-mode deployment.

## Stop conditions, rollback, and cleanup

Stop immediately if a target is not the recorded disposable resource; any port
is not loopback-only; a role has `SUPERUSER` or `BYPASSRLS`; Flyway clean is
enabled; a secret/token is about to be captured; a real Telegram chat, printer,
terminal, agent, database, broker, or Keycloak realm is selected; or terminal
identity is claimed as evidence; or `/ams/set-spool` is about to be invoked
without a separately approved safe command harness. Preserve redacted evidence
and do not repair the environment in place.

Rollback is additive and per service: stop Notify or AMS first, then Hub,
Core, and the smoke-owned broker. Keep the Core legacy allowlist and Hub AMS
compatibility ingress configured but unused; do not reintroduce a shared
credential or remove the other service's identity. Retain the disposable
cluster, external secret material, and redacted evidence for review. Deleting
resources, secrets, or evidence is a separate approval.
