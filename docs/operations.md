# EDOL Operations

## Deployment behavior

The manual `dev-secure-deploy` and `dev-home-deploy` workflows check out the
selected branch's exact commit and run **Docker Compose** on the operator
host. The shared model generates protected `state/compose.json`; JSON uses
the same Compose service/network/volume model as YAML. A temporary Flyway
container applies immutable migrations before the applications start.

The first deployment creates empty databases. It creates no EDOL user, OWNER,
printer, filament, spool or PrintJob. Register and populate through the normal
application flows. Home creates only its required installation-tenant record
through the existing Home migration and has no OIDC registration.

Later deployments retain all manually entered data, Keycloak accounts, device
bindings, broker authority, keys and media. There is **no automatic reset,
dataset bootstrap, SQL restore or dependency on previous test environments**.
Only `edol-dev-secure` and `edol-dev-home` are stopped when switching modes;
their volumes and state are separate. The old `/srv/edol` and unrelated
services are outside this workflow. No old containers or home-directory files
are required. A future reusable SQL snapshot is a separate requested task.

Supported assets: application Dockerfiles, Keycloak image/theme/browser realm,
Mosquitto configuration/authority initializer, [deployment code](../docker/deploy/dev_deploy.py),
its tests/configuration helper and [nginx template](../docker/nginx/dev.conf.template).
Legacy Compose/NanoMQ smoke assets and automatic push-to-main deployment are
retired. README remains frozen; use this procedure for deployment.

## First secure dev launch on edol-server

These are operator instructions; repository preparation does not execute them.
Run server commands in Bash as the Docker-enabled deployment operator. Start
with secure mode to test registration. Home is a separate trusted-LAN mode.

### 1. Push and prepare the directories

Push the reviewed commit to `EDOL-048`. Obtain a separate checkout of that
revision on the server, e.g. `/home/serge/edol-dev-setup`; do not reset a shared
application checkout. Run the following from that repository root:

```bash
python3 --version
docker compose version
command -v openssl
sudo install -d -m 0700 -o "$(id -un)" -g "$(id -gn)" /srv/edol-dev
install -d -m 0700 /srv/edol-dev/releases /srv/edol-dev/secure /srv/edol-dev/home
```

Reserve unused private /24 networks and host ports. Examples use
`10.253.92.0/24` (secure), `10.253.93.0/24` (Home), ports 18090/18091/18099/18884,
and the private host address `192.168.0.200`. Check conflicts first. PostgreSQL
has no published host port. The Actions job image needs Bash, Git, tar and
OpenSSH/scp; it deploys through SSH and needs no host Docker socket access.

### 2. Generate protected configuration and secrets

```bash
python3 docker/deploy/init_config.py --mode secure --output /srv/edol-dev/secure/instance.json
python3 docker/deploy/init_config.py --mode home --output /srv/edol-dev/home/instance.json
```

The helper creates independent random passwords and HMAC/encryption keys in
0600 files outside Git. It refuses to overwrite a file. Keep these values
across deploys; rerunning the helper is not a credential rotation procedure.
Edit the secure file locally with a private editor:

| Field | Required value |
| --- | --- |
| `bind_address`, `subnet`, `*_port` | Actual private address, unused subnet and ports. |
| `hub_origin` | `https://dev.edol.s-pon.dev`, no trailing slash. |
| `auth_origin` | `https://auth.dev.edol.s-pon.dev`, no trailing slash. |
| `proxy_address` | Actual nginx IPv4 on `edol-reverse-proxy`, without CIDR suffix. |
| `smtp` | Working provider host/port, sender, user/password, auth and STARTTLS. |
| `keycloak_admin_user` | Technical Keycloak admin, initially `edol-admin`. |
| `secrets` | Keep generated values; no application owner/printer credentials are needed. |

The technical admin password is `secrets.keycloak_admin`; inspect it only in
the protected editor. This admin is not an EDOL application account. Configure
SMTP before registration: verification/reset emails need real delivery. Keep
email verification enabled. Do not paste JSON, private keys, tokens or full
container environments into chat/logs.

Generated Compose/identity files are 0600 under `state/` (0700). The realm import
is 0644 inside that non-traversable host directory so Keycloak's image UID can
read its file bind mount; retain the parent permissions. Keep CA private keys
outside any broker-mounted directory.

### 3. Create fresh MQTT TLS trust

Run once while the destinations below do not exist. Use OpenSSL on the host
and the public JVM roots from the Java runtime image:

```bash
set -e
umask 077
install -d -m 0700 /srv/edol-dev/secure/ca /srv/edol-dev/secure/certs/mqtt
ca=/srv/edol-dev/secure/ca
certs=/srv/edol-dev/secure/certs
test ! -e "$ca/ca-key.pem" && test ! -e "$certs/mqtt/server-key.pem"
openssl req -x509 -newkey rsa:3072 -nodes -days 3650 -sha256 \
  -keyout "$ca/ca-key.pem" -out "$ca/ca.pem" -subj '/CN=EDOL dev MQTT CA'
openssl req -new -newkey rsa:2048 -nodes -keyout "$certs/mqtt/server-key.pem" \
  -out "$ca/server.csr" -subj '/CN=mqtt'
printf '%s\n' 'subjectAltName=DNS:mqtt,IP:192.168.0.200' 'extendedKeyUsage=serverAuth' > "$ca/server.ext"
openssl x509 -req -in "$ca/server.csr" -CA "$ca/ca.pem" -CAkey "$ca/ca-key.pem" \
  -CAcreateserial -out "$certs/mqtt/server.pem" -days 365 -sha256 -extfile "$ca/server.ext"
install -m 0600 "$ca/ca.pem" "$certs/mqtt/ca.pem"
export EDOL_STORE_PASSWORD="$(python3 -c 'import json; print(json.load(open("/srv/edol-dev/secure/instance.json"))["secrets"]["truststore"])')"
export EDOL_STORE_DEFAULT=changeit
docker run --rm --user "$(id -u):$(id -g)" -e EDOL_STORE_PASSWORD -e EDOL_STORE_DEFAULT \
  -v "$certs:/export" --entrypoint sh eclipse-temurin:21-jre -ec '
  test ! -e /export/combined.p12
  keytool -importkeystore -srckeystore "$JAVA_HOME/lib/security/cacerts" \
    -srcstorepass:env EDOL_STORE_DEFAULT -destkeystore /export/combined.p12 \
    -deststoretype PKCS12 -deststorepass:env EDOL_STORE_PASSWORD -noprompt
  keytool -importcert -alias edol-dev-mqtt -file /export/mqtt/ca.pem \
    -keystore /export/combined.p12 -storepass:env EDOL_STORE_PASSWORD -noprompt'
unset EDOL_STORE_PASSWORD EDOL_STORE_DEFAULT
```

Adjust the SAN before issuance if the device endpoint differs. Do not regenerate
trust after devices use it. The broker mounts only `certs/mqtt`, never the CA
private key. The combined JVM store retains public roots for Keycloak/Telegram
and adds the public MQTT CA. No printer or device is needed to start the empty
web environment. Configure device endpoints, trust and pairing later through
the supported setup flow; deployment changes no firmware, NVS or device trust.

### 4. Configure nginx and DNS

Ensure both dev DNS names reach nginx and their HTTPS certificates are valid.
Read its actual Docker network address:

```bash
docker network inspect edol-reverse-proxy --format '{{range .Containers}}{{println .Name .IPv4Address}}{{end}}'
```

If missing, provision that external bridge once through nginx's deployment
setup and attach `nginx-home`. Put its actual IPv4 in `proxy_address`.
Back up `/srv/nginx/nginx.conf` privately. Merge
[dev.conf.template](../docker/nginx/dev.conf.template) into the existing HTTPS
`dev.edol.s-pon.dev` block, replacing the old `location /` rather than adding a
second one. Keep existing certificate/ACME/TLS settings. New aliases are
`edol-hub-dev:8090`, `edol-core-dev:8080` and `edol-ams-dev:8099`; Agent/terminal
paths are routed explicitly. Core trusts forwarding only from the configured
nginx address. Keep `auth.dev.edol.s-pon.dev` routing to `edol-keycloak-dev:8080`
with Docker DNS resolution and management-network restrictions for `/admin/`
and `/realms/master/`.

```bash
docker exec nginx-home nginx -t
# After successful validation only:
docker exec nginx-home nginx -s reload
```

Before deployment the new upstreams may return 502. Home gets no public proxy
alias; use its private LAN URL instead.

### 5. Add Gitea Actions secrets

Generate a dedicated unattended SSH key on a trusted workstation and append
only its public key to the operator's `~/.ssh/authorized_keys`. Verify the
server host-key fingerprint using the trusted existing connection. A local
SSH alias is not automatically available inside the Actions job.
In repository **Settings -> Actions -> Secrets**, add:

| Secret | Value |
| --- | --- |
| `EDOL_DEV_SSH_HOST` | Reachable hostname/IP from the runner, e.g. `192.168.0.200`. |
| `EDOL_DEV_SSH_USER` | Deployment operator, e.g. `serge`. |
| `EDOL_DEV_SSH_PORT` | Actual decimal SSH port. |
| `EDOL_DEV_SSH_KEY` | Whole private key including BEGIN/END; usable without a passphrase prompt. |
| `EDOL_DEV_SSH_KNOWN_HOSTS` | Verified known_hosts line for that host/port; `[host]:port` for non-default ports. |

[Actions secrets](https://docs.gitea.com/usage/actions/secrets/) supply SSH only;
application secrets stay on the server. `ssh-keyscan` output is only a candidate:
verify the fingerprint before saving it. `StrictHostKeyChecking=yes` stays
mandatory. Dispatch only trusted reviewed branches because their code executes
with the deployment account's authority.

### 6. Validate and run the workflow

From the separate reviewed setup checkout:

```bash
revision="$(git rev-parse HEAD)"
python3 docker/deploy/dev_deploy.py render --mode secure \
  --config /srv/edol-dev/secure/instance.json --revision "$revision"
docker compose -f /srv/edol-dev/secure/state/compose.json config --quiet
```

In Gitea Actions select `dev-secure-deploy`, choose `EDOL-048`, run manually and
check the source SHA. If not listed, check workflow discovery/branch/Actions
settings; do not run a legacy deployment job. A push starts no deployment.

Deployment builds/pulls before stopping either new dev project, provisions
missing databases/roles, migrates and starts dependencies/apps with Compose
`up --wait`. It never drops a database or restores SQL. A host lock serializes
both modes. Failure stops the target dev project while retaining its state.
Existing realms are never overwritten by startup import.

### 7. Register and populate manually

Open `https://dev.edol.s-pon.dev`, register, verify the email and log in. The
new personal tenant/OWNER should have empty printers, inventory and history.
Create printers, pair devices, add materials/spools and perform your test
prints manually. Check repeat login and a later deployment keep this data.
Only after accepting that dataset, request a coordinated SQL dump for future
initialization. No dump/restore or dataset bootstrap is implemented now; the
future media/identity/secret scope must be selected then.

## Home mode and healthchecks

`dev-home-deploy` uses `/srv/edol-dev/home/instance.json`, an independent volume
and subnet. It starts all four EDOL services, PostgreSQL and trusted-LAN MQTT.
It has no Keycloak/OAuth, secure roles, browser registration or public-proxy
network; Telegram is disabled. Populate through the LAN UI. Its Home migration
creates the installation tenant and disables secure Hub RLS only in that Home
database. Switching mode is not a database conversion.

| Container | Native Docker healthcheck |
| --- | --- |
| PostgreSQL | `pg_isready`. |
| Keycloak | HTTP 200 from `/health/ready` on management port. |
| Hub | HTTP 200/302 from login/home. |
| Core | Mapped printer API: secure unauthenticated 401, Home 200. |
| AMS | Secure: `EDOL-Terminal readiness-probe` is deliberately malformed and must return 401 before DB access; Home parameter-validation 400 is unchanged. |
| MQTT | Successful subscribe acknowledgement; secure TLS/auth, no publish. |
| Notify | Java PID liveness only, labelled `process-liveness-only`, not Telegram readiness. |

Use `docker compose -f /srv/edol-dev/secure/state/compose.json ps` and
`docker inspect --format '{{.State.Health.Status}}' <exact-container>` for
status. Do not export full container config/environments into public logs.
Healthchecks do not prove SMTP, registration, Telegram delivery or real printing.

Secure AMS requires an Authorization header at its mapped terminal route. A
missing header produces HTTP 400, not the terminal-authentication rejection.
Both its native healthcheck and the host HTTP probe send the same non-secret,
invalid terminal credential; arbitrary 400/403/404/5xx and an unexpected 200
remain failures. No real terminal credential, pairing, DB lookup or printer
operation is needed for this check.

Failure summaries expose only fixed stage/service identifiers and, for HTTP
timeouts, the last numeric status. If Compose startup fails, the deployer
queries only container state/health metadata to identify the failed service;
if that diagnostic query fails, it retains the original service group. Original
exception text, response bodies, authorization headers, SQL parameters and
expanded command output remain hidden. Cleanup is still attempted on failure;
a cleanup failure is reported safely without replacing the original failure. For example:
`Deployment stopped: stage=http-readiness services=edolams last_http_status=400`.
An old generic `RuntimeError` message alone cannot establish which command or
probe originally failed. SMTP failures remain a separate diagnostic track.

Offline checks:

```bash
python3 -B -m unittest discover -s docker/deploy/tests -v
bash -n docker/deploy/dispatch.sh
node docker/keycloak/realm/validate-realm-schema.mjs
python3 -B docker/deploy/tests/postgres_smoke.py --pg-bin /path/to/postgresql/bin
```

The SQL smoke check owns temporary localhost clusters on random ports. It
executes migration SQL directly, checks empty domain tables/RLS and that
reprovisioning retains operator data. It does not run the Flyway CLI or create
real migration history. Image builds, native healthchecks, Linux permissions,
Actions/SSH, public routing, SMTP/login and devices need runtime evidence.

## Production rendering

The same model supports `environment=prod`, `project=edol-prod-home` or
`edol-prod-secure` with separate state/networks/ports/secrets. No production
Actions workflow or dataset/reset path exists. After the approved backup and
preflight, `provision-db` creates missing roles/databases and applies migrations
without resetting data. Start the reviewed generated composition in dependency
order with health checks. `deploy-dev` rejects production.

```bash
python3 docker/deploy/dev_deploy.py render --mode secure --config /srv/edol-prod/secure/instance.json --revision <full-commit-sha>
docker compose -f /srv/edol-prod/secure/state/compose.json config --quiet
```

Secure runtime and migration roles are distinct; runtime users have no object
ownership, superuser or RLS bypass and cannot write Flyway history. Follow the
[Agent MQTT](contracts/agent-mqtt.md) and [pairing](contracts/agent-pairing.md)
contracts for trust/authority/lifecycle. Service clients get narrow scopes and
explicit API audiences; the browser retains confidential Code + PKCE S256,
exact redirect/origin/logout URIs and verified email. Reconcile an existing
realm through an approved change rather than relying on startup import.

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
