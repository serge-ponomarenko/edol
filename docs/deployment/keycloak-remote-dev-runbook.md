# Remote Dev Keycloak Runbook

This is the operator procedure for the Phase 4.0 **remote development**
instance only. It does not authorize a production deployment. It applies only
to `EDOL_DEPLOYMENT_MODE=secure-multi-tenant`; do not combine it with the base
or home Compose files.

The objective is a Keycloak endpoint at `https://auth.dev.edol.s-pon.dev` for
a future Hub process run from IntelliJ IDEA. It starts no EDOL application,
MQTT broker, printer integration, or live-device operation.

## Required operator inputs

Before starting, the server administrator records:

| Item | Required value or decision |
| --- | --- |
| Deployment mode | `secure-multi-tenant` |
| Public hostname | `auth.dev.edol.s-pon.dev` |
| Keycloak loopback port | `18443` |
| Administration network | a concrete VPN or fixed source CIDR for `<ADMIN_CIDR>` |
| TLS | Let's Encrypt certificate managed by Debian Nginx |
| DNS | initially Cloudflare DNS-only; see proxy restriction below |
| Mail | verified Resend sender and administrator-managed API key |
| Secrets | an external owner-only env file, injected with `--env-file` |

Do not create or copy a secret into the release checkout. The target env file
is outside Git and must be mode `0600`. Do not use a production password, a
production Keycloak database, or a production client secret for dev.

## Network and Nginx preflight

The supplied Nginx template allows `/admin/` and `/realms/master/` only from
loopback and `<ADMIN_CIDR>`. Set the Cloudflare DNS records to **DNS-only** for
the initial deployment so Nginx sees the actual client address. A proxied
Cloudflare hostname requires a separately reviewed trusted `real_ip`
configuration and Cloudflare Access policy before it may be enabled; do not
assume that an Nginx source-CIDR allowlist works behind an unconfigured proxy.

On the Debian server, copy the template into the Nginx configuration, replace
only `<ADMIN_CIDR>`, install the certificate paths, and validate before reload:

```bash
sudo nginx -t
sudo systemctl reload nginx
```

From an unapproved public network, `/admin/` and `/realms/master/` must return
an access denial. Do not place Keycloak port `18443`, PostgreSQL port `5432`, or
Keycloak management port `9000` in a public firewall rule.

## Secret file and Compose validation

From the checked-out release's `docker` directory, create the external dev env
file from `keycloak/.env.dev.example` using the approved secret-injection
process. The example contains placeholders only. Set file ownership to the
deployment operator and mode `0600`; do not print or commit its contents.

Required values include a dev-only bootstrap admin password, Keycloak database
password, separate PostgreSQL bootstrap password, future `edol-hub-web`
client secret, verified Resend configuration, and these non-secret values:

```text
EDOL_DEPLOYMENT_MODE=secure-multi-tenant
KEYCLOAK_DEV_HOSTNAME=auth.dev.edol.s-pon.dev
KEYCLOAK_DEV_HOST_PORT=18443
EDOL_HUB_WEB_REDIRECT_URI=http://localhost:8090/login/oauth2/code/edol-keycloak
EDOL_HUB_WEB_ORIGIN=http://localhost:8090
EDOL_KEYCLOAK_SSL_REQUIRED=all
```

Validate the composition without echoing resolved secrets:

```bash
docker compose --env-file ../../.env_edol_keycloak_dev \
  -f compose.keycloak-dev.yaml config --quiet
```

The command must exit zero. A missing mode or any value other than
`secure-multi-tenant` fails before PostgreSQL or Keycloak starts. Do not run
plain `docker compose config` with this env file because it can print expanded
secret values.

## Database bootstrap and start

The supplied dev composition is the clean-install path: its dedicated
PostgreSQL container creates only `edol_keycloak_dev` and the
`edol_keycloak_dev` role, with a separate named persistent volume. It is not a
Hub Flyway migration and must not join an EDOL database network.

If the approved topology instead uses an existing PostgreSQL cluster, the DBA
runs `docs/deployment/keycloak-postgres-bootstrap.sql` from a maintenance
database with the actual EDOL database and `hub_runtime` role names. The DBA
must retain the recorded negative-access results; do not replace that
controlled bootstrap with the container init script.

For a new dev Keycloak volume only, start the approved project:

```bash
docker compose --env-file ../../.env_edol_keycloak_dev \
  -f compose.keycloak-dev.yaml up --build -d
docker compose --env-file ../../.env_edol_keycloak_dev \
  -f compose.keycloak-dev.yaml ps
```

Do not use `down -v` on an existing Keycloak installation to simulate a clean
start. A clean-start acceptance test requires a new, disposable dev project or
volume specifically approved for that purpose.

## Acceptance evidence

Record command exit status and redacted results; never attach the env file or
secret-expanded Compose output to a ticket.

1. Confirm Keycloak readiness from the host through Docker health status. The
   Compose healthcheck reaches Keycloak's internal management port `9000`; that
   port is intentionally neither published nor proxied:

   ```bash
   docker compose --env-file ../../.env_edol_keycloak_dev \
     -f compose.keycloak-dev.yaml ps
   ```

   The `keycloak-dev` service must report `healthy`.

2. Confirm public OIDC discovery and JWKS through Nginx/TLS:

   ```bash
   curl --fail --silent --show-error \
     https://auth.dev.edol.s-pon.dev/realms/edol/.well-known/openid-configuration
   curl --fail --silent --show-error \
     https://auth.dev.edol.s-pon.dev/realms/edol/protocol/openid-connect/certs
   ```

3. Submit an authorization request with a deliberately unapproved redirect
   URI. Keycloak must reject it rather than redirecting:

   ```text
   https://auth.dev.edol.s-pon.dev/realms/edol/protocol/openid-connect/auth?client_id=edol-hub-web&response_type=code&scope=openid&redirect_uri=https%3A%2F%2Funapproved.example%2Fcallback
   ```

4. Using the approved secret-handling procedure, test `grant_type=password`
   and an authorization request for `scope=offline_access`; both must be
   rejected. Do not put the client secret on a command line or in shell
   history.

5. Prove database isolation. With the dedicated container topology, record
   that PostgreSQL has no published host port and that Hub is not attached to
   the Keycloak Compose network. With a shared PostgreSQL cluster, the DBA
   additionally records all four negative SQL checks from
   `keycloak-phase-4.0.md`: Keycloak role cannot connect to EDOL or use `hub`
   or `core`, and `hub_runtime` cannot connect to Keycloak.

6. Create one disposable dev identity, verify email, request a password reset,
   disable the identity, and prove the next authorization-code login fails.
   Delete the disposable identity after evidence is recorded.

7. Perform a backup-and-restore drill before acceptance: create a custom
   format `pg_dump -Fc --no-owner --no-privileges`, record its SHA-256, restore
   it to a new isolated Keycloak database, and repeat discovery there. Stop
   Keycloak before a realm export. Realm exports are drift evidence, not a
   substitute for database backup.

## Rollback and later production

If dev validation fails, stop only `edol-secure-multi-tenant-dev`; do not touch
the home, Hub, Core, MQTT, printer, or production Compose projects. Rollback
uses the preceding dev image/configuration and a verified backup restore; never
attempt a Keycloak schema downgrade.

Production is a separately approved change using
`compose.keycloak-prod.yaml`, `auth.edol.s-pon.dev`, port `19443`, its own
database, volume, env file, and secrets. No dev database, volume, backup, or
secret is reused for production.
