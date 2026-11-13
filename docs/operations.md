# EDOL Operations

## Home profile

`docker/compose.home.yaml` is the only base home composition. It starts Core,
Hub, PostgreSQL, and Eclipse Mosquitto only. It does not include Keycloak, OIDC settings,
OAuth client secrets, Notify, AMS, or any Keycloak database, role, port, or
volume.

The standalone Compose entry point hard-codes and validates
`EDOL_DEPLOYMENT_MODE=home` before it starts PostgreSQL, MQTT, Hub, or Core.
The former general `compose.yaml` is not a profile entry point and its EDOL
processes fail without the required mode; do not use it for a new deployment.

Home is for one owner on an owner-controlled host and trusted local or private
network. It is not suitable for public exposure, untrusted users, or data
isolation between people. Do not combine this file with `compose.yaml` or a
Keycloak Compose file.

### Database and migration boundary

The home Hub starts standard immutable Flyway history plus the home-only
repeatable migration location. It disables inherited secure RLS only in the
home database, validates that there are zero or one tenants and no users or
memberships, and records exactly one installation tenant in
`hub.home_installations`. Hub derives its tenant context only from this row;
there is no tenant UUID environment variable, request header, or MQTT field.
The clean home template uses one home PostgreSQL user for Hub runtime and
Flyway and overrides the secure `hub_schema_owner` Flyway initialization SQL;
it does not create or reuse secure database roles.

Use new dedicated home persistent paths. Never point this composition at a
secure-multi-tenant database or its volumes. Moving an existing legacy database
to home requires a reviewed backup-first preflight. Changing
`EDOL_DEPLOYMENT_MODE` alone is not a conversion and a controlled migration must restore all secure prerequisites before starting secure mode.

### Operator setup

Copy `docker/.env.home.example` outside Git as `../../.env_edol_home`, replace
its placeholders, and restrict it to the operator. It contains ordinary home
database and Core configuration only; it must not contain Keycloak or OAuth
values. Select distinct, conflict-checked host ports and do not use live
printer configuration for a clean smoke check.

From the repository's `docker` directory, validate the standalone file:

```bash
docker compose --env-file ../../.env_edol_home -f compose.home.yaml config --quiet
```

Do not start the composition on a host with live devices until its operator
review confirms the Core printer configuration and MQTT network boundary.

## Secure deployment

Review the exact deployed revision, Compose file chain, service readiness,
mounted state and network boundaries before an upgrade or recovery. Preserve
volumes, identities, CA and firmware settings. Configuration validation uses
`docker compose ... config --quiet` to avoid printing expanded secrets.
Use the [Agent MQTT contract](contracts/agent-mqtt.md) for the required secure
broker override, persistent authority file and per-client trust.

The [Keycloak development](../docker/compose.keycloak-dev.yaml) and
[production](../docker/compose.keycloak-prod.yaml) compositions are independent
identity-provider projects. Use separate database, admin and Hub client
credentials from protected external configuration and separate instance data.
The shared reverse-proxy network carries only the proxy and Keycloak front-end
path. PostgreSQL and management health ports stay private. Terminate HTTPS at
the approved proxy, overwrite inbound forwarding headers and restrict the
plaintext upstream to the trusted proxy. Restrict administrative paths
including `/admin/` and `/realms/master/` to the approved management network.
Validate that source-based rules see the actual administrator address.

The [realm template](../docker/keycloak/realm/edol-realm.json) is the review
source. Run `node docker/keycloak/realm/validate-realm-schema.mjs` from the
repository root before a fresh import. Reconcile an existing realm in place
through a reviewed change. Keep the `edol-hub-web` redirect at
`https://<hub-host>/login/oauth2/code/edol-keycloak` and use the matching exact
HTTPS origin and post-logout URI. Require confidential Authorization Code
with PKCE S256, no wildcard redirect/origin, and no direct grants, implicit
flow, service account, device grant, token exchange or offline access for this
browser client. Validate discovery/issuer, login and logout, negative grants,
and the approved SMTP/account lifecycle with disposable identities.

### Branded login and self-registration

The Keycloak image bundles `docker/keycloak/themes/edol/login`. It inherits the
native Keycloak forms, changing only local CSS, the existing EDOL logo and English
messages. Login, registration, email verification, required actions and password
recovery share the theme. No external CDN or browser credential handler is added.
Run `node docker/keycloak/realm/validate-realm-schema.mjs` before building.

A fresh realm import selects `loginTheme=edol` and `registrationAllowed=true`.
**Startup import does not update an existing realm.** For an approved rollout:

1. Record the current realm login theme and registration settings, Hub self-service
   setting, image identity and exact service-specific Compose chain. Keep identities,
   volumes, client secrets and bootstrap state intact.
2. Build and deploy the reviewed Keycloak image with the theme using that instance's
   existing composition. In the EDOL realm Admin Console, select **Realm settings >
   Themes > Login theme > edol**. Under **Login**, enable **User registration**;
   retain email-as-username, unique email, email verification and brute-force protection.
   Do not change the master realm or open the legacy-owner claim window.
3. Verify that the realm's approved SMTP delivery works before inviting users.
   Keep email verification required; incomplete SMTP setup is not a reason to disable it.
4. Deploy the matching Hub configuration with
   `EDOL_HUB_SELF_SERVICE_REGISTRATION_ENABLED=true`. The secure profile defaults to
   true, but an existing explicit false overrides it. For invitation-only operation,
   disable Keycloak registration and set this Hub control to false together.
5. With an approved disposable identity, open Hub, follow **Create account**, submit
   the form, verify the email and complete login. Confirm a new personal tenant with
   OWNER membership, an empty printer list, and no access to existing tenant data.
   Sign out and in again: the same membership must be reused. Verify wrong-password,
   duplicate-email and invalid-form feedback, reset email delivery, desktop/mobile
   layout, keyboard focus and existing-user login. Check verification and recovery
   pages as well as the initial form. Do not record credentials or verification links.

If legacy data has not yet been claimed and bootstrap is closed, the existing JIT
safety gate still blocks provisioning; resolve that installation's approved bootstrap
procedure before enabling public registration. Do not reopen the claim window for
public users. Roll back by restoring the recorded realm settings, Hub control and
matching image; retain accounts and tenant data created during the rollout.

Keep a checksummed, restricted instance-specific PostgreSQL backup and test
an isolated restore before an upgrade. Roll back Keycloak by restoring the
matching database backup, image and configuration. Keycloak schema downgrades
are unsupported. Coordinate database password rotation with its injected
secret and rotate Hub's client secret in Keycloak and Hub together.

## Database changes and recovery

- Core, Hub and AMS keep immutable Flyway histories. Run Flyway validation
  and check the exact pending migrations against the deployment database.
  Resolve checksum or schema drift before rollout. Historical repair scripts
  are incident evidence and are never a routine upgrade step.
- Secure runtime roles have no ownership, superuser, role creation or
  RLS bypass. Use distinct migration and runtime identities. Verify schema
  ownership, grants, function owners and tenant isolation from the restricted
  runtime connection. The runtime role cannot write Flyway history. Required
  PostgreSQL extensions are administrator prerequisites.
- Take and checksum a backup, rehearse restore and rollback on an isolated
  copy, and validate data parity, tenant-safe references and forced RLS before
  advancing. Restoring with `--no-owner --no-privileges` requires a separate
  check of role and grant restoration. Stop on orphaned or ambiguous ownership.
  Keep the legacy first-owner claim closed except for a separately approved,
  serialized claim against a verified candidate tenant.
  Recover a failed claim through the reviewed backup path.
- Rehearse development first. A working production rollout requires accepted
  development evidence, a stable observation period, a rehearsal against a
  production backup copy and explicit approval. Preserve additive v2 until
  every consumer accepts the new contract. Remove remaining compatibility
  only after a measured zero-use window in the target environment.
- Full secure lockdown requires verified database TLS, restricted service
  exposure, approved secret storage and tested service credential/key rotation.
  Asymmetric client authentication is a later hardening requirement unless a
  deferral is explicitly recorded. Validate HTTP, MQTT, device and cross-tenant
  denial paths as well as normal service recovery.
- Roll back to the preceding coordinated authenticated release. Keep
  security and ownership data, applied migrations, revocation tombstones and
  compatible broker authority state. Restore a verified backup if the schema
  is incompatible. Never revive retired identities or open anonymous ingress.
  Use the [Agent pairing](contracts/agent-pairing.md) and [MQTT](contracts/agent-mqtt.md)
  contracts for credential transitions.

## Device TLS and restoration

Use a separately approved isolated fixture with the actual device transport
and production TLS library. Confirm the board, port, exact build, intended
transport, valid device clock, public test trust and a usable restoration
image before changing device state. Preserve enrollment, NVS, filesystems,
identity and original trust settings.

Exercise a known-trusted endpoint and independent unknown-CA, wrong-SAN and
expired-leaf cases on the transports affected by the change. Check that an
IP endpoint uses an exact binary iPAddress SAN and that the final linked
Agent SDK enforces certificate dates. Use credential-free fixture requests
and avoid logging headers or relaying fixture traffic into real services.
For the terminal's explicit HTTP/HTTPS selector, verify HTTPS never falls back
to plaintext. A timeout or Loading screen alone is insufficient. Require an
explicit verification failure or a corroborating TLS alert, zero completed
negative handshakes and zero plaintext fallback, then verify trusted recovery.

Restore the original firmware, configuration and trust without erasing NVS.
Stop only the test fixtures, remove temporary test trust and confirm original
authenticated operation where authorized. Record the exact firmware and source
revisions, bounded counters, operator observations and restoration in the
authorized Obsidian project.
