# Secure Multi-Tenant Keycloak Phase 4.0 Deployment Readiness

## Scope and safety boundary

This material prepares two isolated server-side Keycloak instances for the
`secure-multi-tenant` deployment profile:

- `edol-secure-multi-tenant-dev` authenticates a Hub instance started locally
  from IntelliJ IDEA during development and test work.
- `edol-secure-multi-tenant-prod` is a separate production identity-provider
  instance.

Stage H must be formally accepted before either project is deployed or Phase 4
is accepted. Until then these are reviewed secure-profile deployment templates,
not an authorization to change a server. There is intentionally no local
Keycloak Compose service. Neither composition starts EDOL Hub, Core, MQTT, AMS,
Notify, or printer-facing services, and neither changes Hub/Core Flyway history,
schemas, roles, MQTT messages, or live devices.

Each instance has an independently named Compose project, PostgreSQL service,
persistent volume, Keycloak database, database role, hostname, private bridge
network, reverse-proxy DNS alias, and secret file. The identities are `edol_keycloak_dev` and
`edol_keycloak_prod`; they are distinct even though the two PostgreSQL services
are already physically isolated. Keycloak owns its own database migrations;
neither database is a Hub Flyway schema.

The two files are standalone secure-profile entry points and must never be
combined with `compose.yaml` or a future home Compose file through multiple
`-f` arguments. They have no `home` variant, no fallback value, and no shared
container, private network, volume, database, role, or secret with home. A short-lived
Compose guard requires the exact value
`EDOL_DEPLOYMENT_MODE=secure-multi-tenant` before it starts either Keycloak or
its PostgreSQL service. A missing value fails interpolation; `home` and every
other value fail the guard. Stage H owns the application-process mode binding
and the base home composition; this Phase 4.0 material does not implement it.
The Stage 3 `LegacyDefaultTenantCompatibilityScope` remains unchanged and is
not a home-mode mechanism.

## Dev server for local IntelliJ Hub work

`compose.keycloak-dev.yaml` runs on the server. Its realm client has the exact
development callback `http://localhost:8090/login/oauth2/code/edol-keycloak`
and origin `http://localhost:8090`, so the future Hub process started from
IntelliJ can use the dev issuer. No wildcard localhost URI is accepted.

This HTTP callback is solely a pre-BFF local development configuration. Before
Stage 4 BFF security acceptance, local Hub testing must move behind HTTPS so
its session cookie can be Secure, HttpOnly, and SameSite=Lax.

## Realm and client provisioning

`edol-realm.json` is mounted into Keycloak's supported startup-import
directory. For realm `edol`, Keycloak requires the `<realm>-realm.json`
convention, which produces `edol-realm.json`.
It is tracked, contains no real secret, and obtains deployment values only from
environment placeholders.

The import creates one `edol` realm and one confidential `edol-hub-web`
client. It enables only Authorization Code flow with PKCE `S256`; direct grant,
implicit flow, device flow, service accounts, and offline-token scope are
disabled. Keycloak 26.7.4 represents device authorization as the supported
client attribute `oauth2.device.authorization.grant.enabled=false`.
`standardTokenExchangeEnabled` is not a 26.7.4 `ClientRepresentation` field, so
the import does not contain it or any token-exchange-enabling attribute. The
server feature is available by default, but Keycloak requires an explicit
client switch before a client can use standard token exchange; its absence keeps
`edol-hub-web` unable to request it. The access-token lifetime is five minutes
and client session idle/max are 30 minutes/eight hours. Each target has one
exact redirect URI and web origin, supplied by its own secret-managed
environment.

Run `node docker/keycloak/realm/validate-realm-schema.mjs` before a realm
import. It checks the complete tracked realm/client key sets against the
reviewed Keycloak 26.7.4 representation subset and asserts the required client
security settings without resolving or printing secrets.

Keycloak skips an already imported realm, so an unchanged restart is
idempotent. Change review is performed through the tracked realm artifact; a
changed realm is applied only via a separately reviewed reconciliation procedure
or a fresh non-production volume. The admin console is never the source of
truth.

## Secrets and SMTP

Copy the appropriate template outside the repository as
`../../.env_edol_keycloak_dev` or `../../.env_edol_keycloak_prod`. Replace all
placeholders through the approved secret-injection mechanism; never commit
these files. Both instances require
`EDOL_DEPLOYMENT_MODE=secure-multi-tenant` and separate values for the
bootstrap-admin password, Keycloak database password, PostgreSQL bootstrap
password, and `edol-hub-web` client secret. The Keycloak secret files are not
used by home mode.

Resend is the current SMTP candidate for both server instances. Its API key,
verified sender, and any provider policy remain deployment secrets. Configure
`no-reply@edol.s-pon.dev` only after Resend verifies the selected domain or
subdomain. Do not substitute a local SMTP server or Mailpit for the server
deployments.

## Existing PostgreSQL bootstrap

For an existing PostgreSQL cluster, a DBA runs
`keycloak-postgres-bootstrap.sql` from a maintenance database with the Keycloak
database name, role name, existing EDOL database name, and Hub runtime role.
The script is idempotent, prompts for the database password, revokes Hub runtime
access to the new Keycloak database, and fails closed if the Keycloak role can
reach the existing EDOL database, `hub`, or `core` schema. If an existing EDOL
database grants `CONNECT` to `PUBLIC`, the DBA must approve a separate access
hardening change; this bootstrap never silently changes access for other EDOL
roles.

Before start, record these negative checks with the DBA:

```sql
SELECT has_database_privilege('<keycloak_database_role>', '<edol_database>', 'CONNECT') AS keycloak_can_connect_to_edol,
       has_schema_privilege('<keycloak_database_role>', 'hub', 'USAGE') AS keycloak_can_use_hub,
       has_schema_privilege('<keycloak_database_role>', 'core', 'USAGE') AS keycloak_can_use_core;

SELECT has_database_privilege('hub_runtime', '<keycloak_database>', 'CONNECT') AS hub_runtime_can_connect_to_keycloak;
```

All four results must be `false`. For dev use `edol_keycloak_dev`; for prod use
`edol_keycloak_prod`. Replace `<edol_database>` with the actual database name;
do not run these checks against production without its approved deployment
change.

## Deployment prerequisites and topology

Do not run either server Compose project until Stage H is accepted and all of
these inputs are approved:

1. Development and production Keycloak DNS hostnames.
2. Reverse-proxy/TLS ownership and certificate-issuance approach.
3. Resend SMTP policy, verified sender, and operator responsible for its secret.
4. Secret-injection mechanism and operator responsible for all values.
5. An external Docker bridge network named `edol-reverse-proxy` that is shared
   only by the Nginx transport path and the Keycloak services.
6. An administrative VPN or fixed source CIDR for Nginx access to `/admin/` and
   `/realms/master/`.

The approved identity hostnames and Docker DNS aliases are:

| Instance | Public hostname | Reverse-proxy DNS alias | Database role/database |
| --- | --- | --- | --- |
| Dev | `auth.dev.edol.s-pon.dev` | `edol-keycloak-dev:8080` | `edol_keycloak_dev` |
| Prod | `auth.edol.s-pon.dev` | `edol-keycloak-prod:8080` | `edol_keycloak_prod` |

Before starting Keycloak, the server administrator must:

1. Create Cloudflare DNS records for both hostnames to the Debian server.
2. Initially make each identity hostname DNS-only in Cloudflare. The Nginx
   template authorizes `/admin/` by source CIDR, which is meaningful only when
   Nginx receives the administrator's source address directly. If a hostname
   is later proxied through Cloudflare, do not enable the proxy until a
   separately reviewed Nginx `real_ip` configuration trusts only Cloudflare
   address ranges and an equivalent Cloudflare Access policy protects the
   administration paths.
3. Choose and execute a Let's Encrypt challenge procedure. HTTP-01 is
   acceptable when Nginx can serve the challenge path on port 80. DNS-01 is
   also acceptable with a Cloudflare token restricted to DNS edits for
   `s-pon.dev`. If Cloudflare proxying is later approved, configure its SSL/TLS
   mode as `Full (strict)`, never Flexible.
4. Create the external transport network once, without adopting the existing
   `nginx_default` project network:

   ```bash
   docker network inspect edol-reverse-proxy >/dev/null 2>&1 || \
     docker network create --driver bridge edol-reverse-proxy
   ```

5. Amend `/srv/nginx/docker-compose.yml` outside this repository so only the
   service that creates `nginx-home` joins `edol-reverse-proxy` as an external
   network. Do not attach PostgreSQL, Hub, Core, home, or another unrelated
   service. The required Compose fragment is:

   ```yaml
   services:
     <nginx-service>:
       networks:
         - default
         - edol-reverse-proxy

   networks:
     edol-reverse-proxy:
       external: true
       name: edol-reverse-proxy
   ```

6. Integrate `nginx-edol-keycloak.conf.template` into the bind-mounted
   `/srv/nginx/nginx.conf`, replace `<ADMIN_CIDR>` with the approved
   administrative network, and install the two certificate paths. Validate the
   running container with `docker exec nginx-home nginx -t`; then recreate only
   the Nginx service through the `/srv/nginx/docker-compose.yml` project in an
   approved maintenance window. Do not use `systemctl` and do not recreate
   unrelated services or networks.
7. Create the target external env file with owner-only mode, for example
   `install -m 600 /dev/null ../../.env_edol_keycloak_dev`, then populate it
   through the approved secret mechanism.

Once approved, `cd` to the checked-out release's `docker` directory and run
only one target secure-profile project. Do not append another Compose file:

```bash
# --quiet validates interpolation without writing secret-expanded values to the terminal.
docker compose --env-file ../../.env_edol_keycloak_dev -f compose.keycloak-dev.yaml config --quiet
docker compose --env-file ../../.env_edol_keycloak_dev -f compose.keycloak-dev.yaml up --build -d
```

Use the analogous `prod` file only in its separately approved production
change. Keycloak publishes no Debian-host port. It joins its instance-private
bridge network with its dedicated PostgreSQL service and the external
`edol-reverse-proxy` network with only its instance-specific DNS alias. The
Nginx container terminates TLS and forwards through Docker DNS. The proxy must
overwrite `X-Forwarded-*`, publish the approved HTTPS hostname, and deny public
access to administration paths and the master realm. `KC_HOSTNAME` is fixed to
the external HTTPS hostname, `KC_HOSTNAME_STRICT=true`, and
`KC_PROXY_HEADERS=xforwarded`. PostgreSQL joins only the private network and
has no host port. The private networks deliberately are not `internal: true`:
Keycloak needs outbound SMTP access to Resend. Isolation is enforced by network
attachment, not by removing all container egress.

## Email and account lifecycle verification

In dev, create a disposable Keycloak user, require email verification, and use
the account flow to request a reset-password email through configured Resend.
Disable that user and verify that a new authorization-code login is rejected.
Delete the disposable identity after recording evidence. Do not test these
flows with a production account.

## Required verification

1. `docker compose ... config` succeeds with the deployment-managed target env
   file.
2. A clean target volume starts, Keycloak becomes ready, and the realm/client
   import completes without secrets in logs.
3. Discovery and JWKS are reachable through the configured dev or prod HTTPS
   hostname.
4. An authorization request with an unapproved redirect URI is rejected.
5. A token request using `grant_type=password` is rejected because direct grant
   is disabled. A request for `scope=offline_access` is rejected because it is
   not a client scope or role mapping.
6. `docker network inspect edol-reverse-proxy` shows Nginx and the tested
   Keycloak service; it does not show PostgreSQL. A separately approved
   Keycloak instance may also share this transport network. The Keycloak and
   PostgreSQL inspect output proves that neither service has host port
   publications.
7. The PostgreSQL negative-access queries above pass for a shared PostgreSQL
   topology; the dedicated-container topology records the separate private
   network and absence of any Hub/Core connection path instead.
8. Dev email verification, reset password, and disabled-user behavior pass with
   a disposable identity.
9. Create a custom-format database backup, checksum it, restore it into a new
   isolated Keycloak database, and verify discovery after restore. Stop all
   Keycloak nodes before a realm export; realm exports are configuration/drift
   evidence, not a complete backup.

## Backup, upgrade, rollback, and rotation

- Back up each `edol_keycloak` database with `pg_dump -Fc --no-owner
  --no-privileges`, retain a SHA-256 checksum, and store the archive under the
  deployment backup policy.
- For realm export, stop the corresponding Keycloak instance first and use
  `kc.sh export --realm edol --dir <protected-directory> --users skip`; exports
  may contain sensitive configuration and never belong in Git.
- Upgrade only after release-note review, backup, isolated restore, and a
  non-production smoke check. Bump the exact Keycloak image tag in review.
- Rollback restores the preceding instance-specific Keycloak database backup
  and preceding image/configuration. Never attempt a Keycloak database schema
  downgrade.
- Rotate database password by changing the role password and secret injection
  in one maintenance window, then restart that Keycloak instance. Rotate the
  bootstrap admin password through Keycloak administration and secret injection.
  Rotate the Hub client secret in a controlled maintenance change that updates
  Keycloak and the future Hub secret together; do not enable Keycloak preview
  secret rotation merely for this stage.
