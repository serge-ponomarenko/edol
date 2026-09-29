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
| Reverse-proxy transport | external Docker bridge `edol-reverse-proxy` |
| Keycloak Docker DNS alias | `edol-keycloak-dev:8080` |
| Administration network | a concrete VPN or fixed source CIDR for `<ADMIN_CIDR>` |
| TLS | Let's Encrypt certificate managed by containerized Nginx |
| DNS | Cloudflare DNS-only; see LAN split-DNS and proxy restriction below |
| Mail | verified Resend sender and administrator-managed API key |
| Secrets | an external owner-only env file, injected with `--env-file` |

Do not create or copy a secret into the release checkout. The target env file
is outside Git and must be mode `0600`. Do not use a production password, a
production Keycloak database, or a production client secret for dev.

## Network and Nginx preflight

The supplied Nginx template allows `/admin/` and `/realms/master/` only from
container loopback and `<ADMIN_CIDR>`. Keep the auth hostname **DNS-only** in
Cloudflare so the Nginx container sees the actual client address. Cloudflare
proxying is not part of the approved topology and must not be enabled for this
hostname; do not assume that a source-CIDR allowlist works through a proxy.

For the accepted dev topology, the public DNS record resolves
`auth.dev.edol.s-pon.dev` to the public server address, while the LAN resolver
should use split DNS to resolve that same hostname to `192.168.0.200`. This
preserves TLS hostname validation and avoids unreliable router hairpin NAT.
Do not change public DNS merely to solve LAN access; a LAN browser error such as
`ERR_SSL_VERSION_OR_CIPHER_MISMATCH` during hairpin access is a network-path
problem, not by itself evidence of a Keycloak or certificate fault.

Create the transport network once. It is independent of the existing
`nginx_default` Compose-owned network and remains outside the lifecycle of both
EDOL and Nginx Compose projects:

```bash
docker network inspect edol-reverse-proxy >/dev/null 2>&1 || \
  docker network create --driver bridge edol-reverse-proxy
```

In `/srv/nginx/docker-compose.yml`, add the following external network to the
service that creates the `nginx-home` container. Preserve its existing default
network; do not attach another Nginx service, PostgreSQL, Hub, Core, or home
container.

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

Integrate `nginx-edol-keycloak.conf.template` into the bind-mounted
`/srv/nginx/nginx.conf`, replace only `<ADMIN_CIDR>`, and install the certificate
paths. Validate the container configuration before changing its service:

```bash
docker exec nginx-home nginx -t
```

In an approved maintenance window, recreate only `<nginx-service>` through the
`/srv/nginx/docker-compose.yml` project so its external network attachment takes
effect. Do not use `systemctl`, manually connect containers, or recreate
unrelated services or networks.

```bash
cd /srv/nginx
docker compose -p nginx -f docker-compose.yml up -d --no-deps --force-recreate <nginx-service>
```

From an unapproved public network, `/admin/` and `/realms/master/` must return
an access denial. Do not place Keycloak `8080`, PostgreSQL `5432`, or Keycloak
management port `9000` in a public firewall rule.

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
EDOL_HUB_WEB_REDIRECT_URI=http://localhost:8090/login/oauth2/code/edol-keycloak
EDOL_HUB_WEB_ORIGIN=http://localhost:8090
EDOL_KEYCLOAK_SSL_REQUIRED=all
```

For realm `edol`, the tracked startup-import file must be named
`edol-realm.json`: Keycloak applies the `<realm>-realm.json` filename rule.
The JSON is limited to the reviewed Keycloak 26.7.4 realm/client
representation subset. In particular, do not add the unsupported
`standardTokenExchangeEnabled` field; standard token exchange remains
unconfigured for this client. Validate the composition without echoing resolved
secrets:

```bash
node keycloak/realm/validate-realm-schema.mjs
docker compose --env-file ../../.env_edol_keycloak_dev \
  -f compose.keycloak-dev.yaml config --quiet
```

Both commands must exit zero. A missing mode or any value other than
`secure-multi-tenant` fails before PostgreSQL or Keycloak starts. Do not run
plain `docker compose config` with this env file because it can print expanded
secret values. The Compose guard deliberately uses one scalar command argument,
`'test "$$EDOL_DEPLOYMENT_MODE" = "secure-multi-tenant"'`, with
`entrypoint: ["/bin/bash", "-ec"]`. Do not split the test expression into YAML
list items: Bash accepts only the first item after `-c` as its command string.

If Node is not installed on the server, run the same read-only validator in a
disposable container; it reads only the tracked realm directory and uses no
network:

```bash
docker run --rm --read-only --network none \
  -v "$PWD/keycloak/realm:/work:ro" -w /work node:22-alpine \
  node validate-realm-schema.mjs
```

## Database bootstrap and start

The supplied dev composition is the clean-install path: its dedicated
PostgreSQL container creates only `edol_keycloak_dev` and the
`edol_keycloak_dev` role, with a separate named persistent volume. It is not a
Hub Flyway migration and joins only `edol-keycloak-dev-private`. `keycloak-dev`
also joins `edol-reverse-proxy` under the stable alias
`edol-keycloak-dev`; it has no `ports:` publication. PostgreSQL never joins the
shared reverse-proxy network. The private bridge is intentionally not
`internal: true`, because Keycloak needs outbound SMTP access to Resend.

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

1. Record the new network topology without secrets:

   ```bash
   docker network inspect edol-reverse-proxy
   docker inspect nginx-home --format '{{json .NetworkSettings.Networks}}'
   docker compose --env-file ../../.env_edol_keycloak_dev \
     -f compose.keycloak-dev.yaml ps
   docker inspect "$(docker compose --env-file ../../.env_edol_keycloak_dev \
     -f compose.keycloak-dev.yaml ps -q keycloak-dev)" \
     --format '{{json .NetworkSettings.Ports}}'
   docker inspect "$(docker compose --env-file ../../.env_edol_keycloak_dev \
     -f compose.keycloak-dev.yaml ps -q keycloak-dev-postgres)" \
     --format '{{json .NetworkSettings.Ports}}'
   ```

   The shared network must contain `nginx-home` and the dev Keycloak container;
   it must not contain `keycloak-dev-postgres`. The dev Keycloak service must
   show no published `8080`, `18443`, or `19443` host listener, and PostgreSQL
   must show no published `5432` listener. An exposed-but-unpublished container
   port appears as `null` in the Docker inspect port mapping.
   If an independently approved production Keycloak also exists, its Keycloak
   container may be present on this transport network, but its PostgreSQL
   container must not be.

2. Prove Docker-DNS upstream reachability from the Nginx container without
   using a host address or container IP:

   ```bash
   docker exec nginx-home wget -q -O /dev/null \
     http://edol-keycloak-dev:8080/realms/edol/.well-known/openid-configuration
   ```

3. Confirm Keycloak readiness from the host through Docker health status. The
   Compose healthcheck reaches Keycloak's internal management port `9000`; that
   port is intentionally neither published nor proxied:

   ```bash
   docker compose --env-file ../../.env_edol_keycloak_dev \
     -f compose.keycloak-dev.yaml ps
   ```

   The `keycloak-dev` service must report `healthy`.

4. Confirm public OIDC discovery and JWKS through Nginx/TLS:

   ```bash
   curl --fail --silent --show-error \
     https://auth.dev.edol.s-pon.dev/realms/edol/.well-known/openid-configuration
   curl --fail --silent --show-error \
     https://auth.dev.edol.s-pon.dev/realms/edol/protocol/openid-connect/certs
   ```

5. Submit an authorization request with a deliberately unapproved redirect
   URI. Keycloak must reject it rather than redirecting:

   ```text
   https://auth.dev.edol.s-pon.dev/realms/edol/protocol/openid-connect/auth?client_id=edol-hub-web&response_type=code&scope=openid&redirect_uri=https%3A%2F%2Funapproved.example%2Fcallback
   ```

6. Using the approved secret-handling procedure, test `grant_type=password`
   and an authorization request for `scope=offline_access`; both must be
   rejected. Do not put the client secret on a command line or in shell
   history.

7. Prove database isolation. With the dedicated container topology, record
   that PostgreSQL has no published host port, is absent from
   `edol-reverse-proxy`, and that Hub/Core are not attached to the private
   Keycloak network. With a shared PostgreSQL cluster, the DBA additionally
   records all four negative SQL checks from
   `keycloak-phase-4.0.md`: Keycloak role cannot connect to EDOL or use `hub`
   or `core`, and `hub_runtime` cannot connect to Keycloak.

8. After confirming that the Resend sender domain is verified, perform the
   disposable account lifecycle in this order: create the identity; complete
   email verification; send and complete password update/reset; prove an
   enabled-user Authorization Code + PKCE login; disable the identity; prove a
   fresh private-session login is rejected; then delete the identity. Never use
   a production identity and never retain action-token URLs or authorization
   codes in the evidence.

9. Perform the backup-and-restore drill below before acceptance. Stop Keycloak
   before a realm export. Realm exports are drift evidence, not a substitute
   for database backup.

## Backup and isolated restore drill

Create the instance-specific backup with `pg_dump -Fc --no-owner
--no-privileges`, keep it outside Git under the approved backup policy, record
its SHA-256, and set owner-only permissions:

```bash
chmod 600 <keycloak-backup-file>
sha256sum <keycloak-backup-file>
```

If the host has no compatible `pg_restore`, inspect the custom archive with a
read-only PostgreSQL 17 container instead of installing tools or exposing the
database:

```bash
docker run --rm --read-only \
  -v <backup-directory>:/backup:ro postgres:17 \
  pg_restore --list /backup/<keycloak-backup-file> >/dev/null
```

For a recovery drill, use a uniquely named disposable PostgreSQL 17 container,
private Docker network, and volume. Inject a temporary restore-only database
credential through an external protected env file; do not put it in the
command line or the repository. Do not attach the restore database or
Keycloak to `edol-reverse-proxy`, and do not publish any host port. Restore
with `pg_restore --no-owner --no-privileges --exit-on-error`, then confirm the
expected `master`, `edol`, and `edol-hub-web` records.

Start a disposable Keycloak container on that same private restore network
against the restored database with an isolated test hostname. Do **not** pass
`--import-realm`; this validates the restored database rather than a fresh JSON
import. Query discovery from a disposable curl container on the restore
network and require HTTP `200` and the isolated issuer. After recording only
redacted evidence, explicitly inspect and remove the named temporary
containers, network, and volume. Do not use broad Docker prune commands or
remove any live dev resources.

## Rollback and later production

If dev validation fails, stop only `edol-secure-multi-tenant-dev`; do not touch
the home, Hub, Core, MQTT, printer, or production Compose projects. Rollback
uses the preceding dev image/configuration and a verified backup restore; never
attempt a Keycloak schema downgrade.

Production is a separately approved change using
`compose.keycloak-prod.yaml`, `auth.edol.s-pon.dev`, the distinct
`edol-keycloak-prod` Docker DNS alias, its own private network, database,
volume, env file, and secrets. No dev database, volume, backup, or secret is
reused for production.
