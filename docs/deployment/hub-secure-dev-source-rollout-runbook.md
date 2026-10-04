# Hub Secure-Dev Source Database Rollout Runbook

## Purpose and boundary

This runbook applies the already clone-accepted Hub V8 transition to the
existing development database `edol`. Phase A applies V8 and validates the
closed bootstrap state. Phase B, the one-time first-owner claim, is deliberately
outside Phase A and needs a new explicit approval after Phase A evidence is
reviewed.

This is a development-only, Hub-only procedure. It must not target production,
Core, MQTT, AMS, Notify, printer-facing services, or background work. It does
not modify Keycloak users, clients, roles, realm-wide settings, or secrets.
Never use the ordinary `dev` profile: it belongs to the separate local runtime
environment and has its own PostgreSQL, Core, and MQTT endpoints.

## Accepted prerequisites

Before Phase A, all of the following must be true:

- The source development database has a fresh `CANDIDATE_LEGACY_CLAIM`
  preflight result. The accepted baseline was V1 through V7, one non-empty
  default tenant, zero Hub users/memberships, no home marker, and clean
  ownership/integrity checks.
- The isolated clone smoke has passed for the same V1 through V7 data shape.
  It proved V8, the serialized first-owner claim, data parity, OIDC, CSRF,
  expiry, logout, and the runtime exclusions.
- The source release, including `application-dev-data-smoke.yaml`, its tests,
  and the relevant runbooks, has one recorded immutable local commit. Do not
  deploy from an uncommitted worktree and do not include generated logs.
- The remote-dev Keycloak client evidence remains valid for
  `https://dev.edol.s-pon.dev`; do not change the realm as part of this rollout.
- An approved operator can temporarily prevent browser traffic from reaching
  Hub while preserving the remote-dev Keycloak identity provider.

The only profile set for this procedure is:

```text
EDOL_DEPLOYMENT_MODE=secure-multi-tenant
SPRING_PROFILES_ACTIVE=secure-multi-tenant,dev-data-smoke
EDOL_HUB_DATA_SMOKE_DB_JDBC_URL=jdbc:postgresql://<private-dev-host>:5432/edol
EDOL_KEYCLOAK_ISSUER_URI=https://auth.dev.edol.s-pon.dev/realms/edol
EDOL_HUB_WEB_CLIENT_SECRET=<secret-injection-reference>
```

The JDBC value must name the source development database only in this source
rollout. It has no fallback. `dev`, `home`, and `dev-smoke` must not be active.
Use the approved deployment secret mechanism for all database credentials and
the OIDC client secret; never place them in a command line, evidence, log, or
repository.

## Phase A: V8 rollout with the bootstrap window closed

### 1. Establish the maintenance boundary

1. Record the immutable release commit and intended Hub artifact/image identity.
2. Temporarily block browser traffic to the Hub hostname at the trusted reverse
   proxy, or stop Hub before changing the database. Do not alter Keycloak.
3. Stop only Hub instances that use the source `edol` database. Do not start,
   stop, or connect to Core, MQTT, AMS, Notify, or printer-facing services.
4. Record the service set before the database step. Hub must be the only EDOL
   service eligible to start in this procedure.

### 2. Repeat the source preflight and create a recovery archive

Use [Hub Secure-Dev Data Clone Preflight](hub-secure-dev-data-clone-preflight.md)
against source `edol` through the approved one-time DBA exception. Stop if its
result is not again `CANDIDATE_LEGACY_CLAIM`.

Only after that fresh result, create a custom source backup through the private
DBA mechanism:

```text
pg_dump --format=custom --no-owner --no-privileges --file=<owner-only-archive> <source-edol-connection>
sha256sum <owner-only-archive>
pg_restore --list <owner-only-archive>
```

Retain the archive name, checksum, PostgreSQL major version, successful archive
list, preflight result, and timestamp outside Git. Do not use `pg_dumpall`, do
not export roles or secrets, and do not attempt a Flyway repair, clean,
baseline, or downgrade.

### 3. Start Hub once to apply V8

1. Start one Hub instance from the recorded artifact with only the required
   profile set above. Keep browser traffic blocked.
2. Hub Flyway must authenticate as `hub_flyway`; its existing initialization SQL
   changes only the effective migration role to `hub_schema_owner`.
3. Require a successful V8 entry and no other new Flyway history entry. Never
   run V8 SQL manually.
4. Do not open a browser session and do not open the bootstrap window.

If startup or migration fails, stop Hub and preserve the release identity,
redacted logs, Flyway history, preflight, and backup evidence. Do not modify
the source to repair the failure. Recovery by restoring the backup requires a
new explicit approval.

### 4. Verify the closed state and data parity

Run the read-only aggregate checks below through the approved DBA exception.
The result must match the source preflight except for the successful V8 history
entry and the new closed bootstrap state.

```sql
SELECT installed_rank, version, script, success
FROM hub.flyway_schema_history
ORDER BY installed_rank;

SELECT singleton,
       claim_open,
       claimed_by_user_id IS NOT NULL AS has_claimed_user,
       claimed_at IS NOT NULL AS has_claimed_at
FROM hub.legacy_tenant_bootstrap_state;

SELECT count(*) AS tenant_count,
       count(*) FILTER (WHERE is_default) AS default_tenant_count
FROM hub.tenants;

SELECT count(*) AS user_count
FROM hub.users;

SELECT count(*) AS membership_count
FROM hub.tenant_memberships;
```

Required Phase A result:

- V8 is the only new successful migration;
- exactly one bootstrap row exists, `claim_open=false`, with no claimed user or
  claimed timestamp;
- there is one default tenant, zero users, and zero memberships; and
- every row-count, direct-owner, orphan, and cross-tenant check from the fresh
  preflight matches its pre-V8 result.

Retain startup evidence proving there is no `MqttPahoClientFactory`,
`mqttInputChannel`, `edolcore/#` adapter, Core, MQTT, AMS, Notify, printer
service, recovery, catalog, or other secure-mode background start.

### 5. Stop at the Phase A gate

Stop Hub again or retain the trusted-proxy browser block. Do not expose a login
flow while the bootstrap window is closed and no membership exists. Provide the
Phase A evidence for review before taking any bootstrap action.

## Phase B: separately approved first-owner claim

Phase B is not authorized by Phase A. It needs a new explicit approval that
names the source development database, authorizes exactly one one-time
clone-accepted bootstrap-window operation, and identifies one existing
disposable OIDC identity without recording that identity in Git or evidence.

The reviewed Phase B procedure must:

1. re-check that the source database still has one unclaimed closed bootstrap
   row, one default tenant, and zero users/memberships;
2. open the window once through the reviewed source-dev DBA procedure;
3. start only Hub with the Phase A profile set and permit one normal browser
   Authorization Code plus PKCE login through the approved HTTPS hostname;
4. prove that the database function atomically created one Hub user and one
   active `OWNER` membership, cleared `tenants.is_default`, and closed the
   bootstrap window; and
5. repeat CSRF, 60-second expiry, logout, data-parity, and runtime-exclusion
   acceptance checks.

Do not manually insert or bind a Hub user, membership, tenant, or active tenant
and do not reopen the window after a claim attempt. A failed or ambiguous claim
is a stop condition; preserve evidence and request a new recovery decision.

## Recovery and cleanup

There is no Flyway rollback for V8 and no manual inverse of a first-owner claim.
If recovery is necessary, stop Hub and preserve evidence. Restoring the source
development backup is a separately approved destructive operation; it must not
be combined with a mode switch, data rewrite, or production action.

After Phase B acceptance, remove the temporary proxy block only after review.
Retain the backup and evidence under the approved retention policy. Deleting
the backup is a separate approval.
