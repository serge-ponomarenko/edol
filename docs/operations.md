# EDOL Operations

## Configuration ownership

Root environment files and the standalone local dev smoke wrapper are retired.
Deployments read the protected operator-owned `instance.json` and generate
`state/compose.json`, including application properties in
`SPRING_APPLICATION_JSON`. Root environment files are excluded from deployment
archives. Tenant-managed settings remain in the database; infrastructure and
service credentials remain in protected deployment configuration.
Use the supported deployment workflows below for runtime verification.
Local unit tests remain available.

The modules retain their base application YAML and deployment profiles
(`home` and `secure-multi-tenant`). Local `application-dev.yaml` files
are retired, as are the historical smoke and terminal simulator profiles and
their dependent tests. The `dev` and `prod` deployment environments select
`home` or `secure-multi-tenant`; they do not require separate application
profiles named `dev` or `prod`.

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
Legacy Compose/NanoMQ smoke assets are retired. Production deployment runs
after successful main CI; see [production cutover](#production-installation-and-cutover).

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
| `proxy_container` | Optional nginx container name, default `nginx-home`; used to verify the exact endpoint before secure deployment. |
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
printf '%s\n' 'subjectAltName=DNS:mqtt,DNS:dev.edol.s-pon.dev,IP:192.168.0.200' 'extendedKeyUsage=serverAuth' > "$ca/server.ext"
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

For an existing Secure Dev certificate missing the public DNS SAN, prepare a
replacement with the existing CA and server key from the reviewed checkout:

```bash
python3 docker/deploy/mqtt_certificate.py --config /srv/edol-dev/secure/instance.json
```

This stages only `state/mqtt-server.pending.pem` under the shared deployment
lock; it does not replace the active certificate or restart a service. The
public hostname is derived from `hub_origin`, so both device transports use
`dev.edol.s-pon.dev`. The current CA, server key, internal `mqtt`/LAN identities
and existing DNS/IP SANs are preserved. The CA signing key stays in the
instance's `ca/ca-key.pem`; it is never mounted into the broker or exported.

On the next `dev-secure-deploy`, certificate staging/validation happens before
stopping Dev. After stopping, the deployer validates the candidate against the
same CA/server key and required DNS/IP SANs, keeps a fingerprint-named backup
of the prior server certificate, and atomically installs only `server.pem`
before starting services. If the active certificate already validates, it is
left unchanged and no online CA signing key is required. Invalid/mismatched
candidates fail closed with `stage=mqtt-certificate services=mqtt`. A stale
candidate is never silently overwritten; inspect it locally before replacing
that public certificate file. Home and production do not use this Dev signing
procedure. Broker authority, PostgreSQL, device identities, CA, private keys,
JVM truststore and ESP32 NVS are unchanged.

### External MQTT access

Secure Dev uses raw MQTT over TLS at `dev.edol.s-pon.dev:18884`. The DNS record
must be DNS-only and resolve to the public ingress address for external
clients. Publish/forward **TCP 18884** at the router/firewall to
`192.168.0.200:18884` (adjust the destination to the protected instance's actual
`bind_address`/`mqtt_port`). Existing Compose maps that listener to broker TLS
port 8883. Keep anonymous access disabled and all existing Agent ACLs intact.
Certificate preparation does not configure the router/firewall or prove WAN
reachability; verify these separately from outside the LAN. Split DNS may map
the same hostname to the LAN address while preserving hostname verification.

HTTPS remains on 443. The current Agent uses raw MQTT, not WebSockets. Sharing
the same hostname/IP/443 with HTTPS requires an explicitly designed transport
proxy or a firmware transport change; an HTTP nginx location alone cannot
route the current MQTT stream. No 443 sharing or nginx change is implemented.

### 4. Configure nginx and DNS

Ensure both dev DNS names reach nginx and their HTTPS certificates are valid.
Read its actual Docker network address:

```bash
docker network inspect edol-reverse-proxy --format '{{range .Containers}}{{println .Name .IPv4Address}}{{end}}'
```

If missing, provision that external bridge once through nginx's deployment
setup and attach `nginx-home`. Put its actual IPv4 in `proxy_address`.
Reserve that address as a static address in nginx's owning Compose definition
on the external bridge. Both protected Dev and production instance files must
refer to the same proxy address; never pin nginx by disconnecting a live shared
proxy without an approved maintenance window. Application addresses are not
proxy identities. Reinspect the endpoint after recreating nginx or the bridge.
Secure deployments check the named, running proxy endpoint before building,
again before stopping services and before recording readiness. A mismatch fails
with `stage=proxy-trust`, without widening Core's exact-address trust rule.
After an approved proxy address change, update both protected instance files
and redeploy each affected installation. Changing JSON alone does not update
the running Core or Keycloak configuration.

The Agent upload locations in both nginx templates use a 960-second upstream
response budget, covering a fifteen-minute streamed model transfer plus final
validation. The 60-second client-body/send timers limit inactivity between I/O
operations, not total transfer duration. Apply these locations to the existing
nginx configuration through its owner; deployment does not rewrite shared nginx.
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

After Keycloak readiness, every Secure Dev deployment authenticates with the
protected technical admin on the instance's private Docker network and sends
`PUT /admin/realms/edol` containing only `smtpServer`. This applies the current
`instance.json` SMTP map to both newly imported and existing realms before apps
start. A changed SMTP API key is applied on the next deployment; it is not part
of the persistent device-identity fingerprint. No realm export/import override,
database reset, user/client/role replacement or read-back of a masked password
is used. Other realm settings remain operator-owned.

SMTP `auth` and `starttls` accept JSON booleans or true/false strings and are
rendered as lowercase strings. Keycloak 26.7.4 compares these values to the exact
string `"true"`; Python-style `"True"` disables the corresponding feature. The
previous boolean conversion could therefore skip SMTP AUTH and cause provider
`530 authentication Required` despite a valid API key. Configuration failures
stop deployment with `stage=smtp-config services=keycloak`; admin/SMTP credentials,
tokens and HTTP error bodies are never logged. Applying settings does not send
an email or prove external provider availability.

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

The optional `docker/deploy/tests/keycloak_smtp_smoke.py` uses disposable
Keycloak 26.7.4, PostgreSQL 17 and a local SMTP sink with synthetic credentials.
It reproduces the AUTH casing failure, checks delivery after configuration,
restarts Keycloak with the same PostgreSQL state, rotates the SMTP password,
applies the update twice and verifies retained realm settings/users/clients/roles.
It does not deploy EDOL or contact Resend. The fixture tests AUTH without TLS;
STARTTLS normalization is covered by unit tests. Run through the remote-Docker
Skill wrapper when local Docker is unavailable; it removes only its own test
containers, anonymous volume and network.

The SQL smoke check owns temporary localhost clusters on random ports. It
executes migration SQL directly, checks empty domain tables/RLS and that
reprovisioning retains operator data. It does not run the Flyway CLI or create
real migration history. Image builds, native healthchecks, Linux permissions,
Actions/SSH, public routing, SMTP/login and devices need runtime evidence.

## Production rendering

The same model supports `environment=prod`, `project=edol-prod-home` or
`edol-prod-secure` with separate state/networks/ports/secrets. The `prod-deploy`
job follows successful main CI; `deploy-prod` owns the scoped backup, migration
and readiness sequence. Neither a dataset/reset path nor a production
`provision-db` bypass is supported. `deploy-dev` rejects production. Complete
the [production runbook](#production-installation-and-cutover) before launch.

```bash
python3 docker/deploy/dev_deploy.py render --mode secure --config /srv/edol/secure/instance.json --revision <full-commit-sha>
docker compose -f /srv/edol/secure/state/compose.json config --quiet
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

### Secure Dev Agent setup

Save an enabled printer with connection mode `AGENT`, then open **Manage
printer agent > Pair agent** as its tenant OWNER. Enter the new 12-character
code in the secure Agent setup portal within five minutes. Codes are shown
once; the Agent obtains its own identity and MQTT credentials. An identity
from a retired environment does not create a binding in a fresh Core database.
Use the owner pairing/replacement flow rather than erasing device storage.

For the example Dev instance, use these device-side settings; confirm any
operator overrides in the protected `instance.json` first:

| Agent setup field | Value |
| --- | --- |
| Core Host / Port | `dev.edol.s-pon.dev` / `443`, no scheme in Host |
| EDOL MQTT Host / Port | `dev.edol.s-pon.dev` / `18884` |
| Printer Host / Serial / Access Code | Actual printer LAN settings, entered locally |
| WiFi | Actual local network settings, entered locally |

The public Agent HTTPS routes must reach Core through the trusted nginx
location in `docker/nginx/dev.conf.template`. The MQTT endpoint uses the
externally routed TLS listener with a matching DNS SAN, not the Cloudflare HTTP
proxy or a Docker-only service name.

The Agent's `EDOL_AGENT_ROOT_CA_FILE` must contain public trust anchors for
**both** endpoints. Keep the instance's MQTT CA from
`/srv/edol-dev/secure/certs/mqtt/ca.pem`; add the HTTPS issuer's trusted root
certificates (ISRG Root X1/X2 for the current Let's Encrypt chain). Preserve
existing anchors still needed by the device. Never substitute the HTTPS leaf
certificate for a CA or include private keys in the firmware bundle. Updating
the JVM truststore alone does not update the ESP32 trust bundle.

Use the Agent repository's current isolated build procedure and corrected
date-enforcing SDK; its `docs/dynamic-tls-experiment.md` describes the required
guards. Keep generated CA files, SDKs and firmware outside Git. Before a manual
app-only update, verify the artifact hash, embedded bundle hash and unchanged
partition table; retain NVS, device identity and prior recovery artifacts.
Host TLS verification and a successful build do not establish on-device TLS,
enrollment, MQTT or printing acceptance. Record these results separately.

### Secure Dev AMS terminal

The terminal uses HTTPS through the existing public nginx listener; no separate
WAN port to AMS is required. Configure secure mode with:

| Terminal setting | Value |
| --- | --- |
| AMS Host | `dev.edol.s-pon.dev`, without scheme or path |
| AMS Port / HTTPS | `443` / enabled |
| Enrollment | Fresh **Pair AMS Terminal** code from the owned printer edit page |
| Code format / expiry | 11 Latin letters / five minutes, single use |

nginx routes `/api/terminal/v1/` to `edol-ams-dev:8099`. The host's private
AMS mapping (example `192.168.0.200:18099`) is not a public terminal endpoint;
do not forward 18099 or 8099 from WAN. The terminal does not need MQTT access.
Secure API operations authenticate with the terminal's own credential and use
its server-bound tenant/printer; Hub management requires OWNER access and a
separate trusted service identity. See [AMS identity](architecture.md#ams-terminal-identity-and-operations).

The terminal has its own compiled CA configuration via
`EDOL_AMS_ROOT_CA_FILE`. It must trust the public HTTPS issuer and have a valid
clock; rebuilding Agent firmware does not update terminal trust. Keep HTTPS
enabled for external use, without plaintext fallback or device/NVS erasure.

Keep the plaintext AMS upstream restricted to the trusted proxy/private
network. Review ingress request/body/connection limits before external device
enrollment; application authentication and the per-source enrollment limiter
do not establish DoS protection or full Internet-facing acceptance. Verify
malformed/missing credentials separately from a real enrollment; never test
spool assignment or lifecycle operations against a live printer as a probe.

### Bounded TLS verification

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

## Production installation and cutover

This procedure prepares a fresh Secure installation or replaces a legacy
single-tenant installation. Production is `/srv/edol`, project
`edol-prod-secure`; Dev stays in `/srv/edol-dev`. For a third-party fresh
installation, skip the legacy audit/archive steps and substitute your own
DNS names, private addresses, networks and repository URL. Home uses
`--environment prod --mode home`, no Keycloak/TLS proxy, and must stay private.

Preparation in Git does not execute server commands. Confirm the maintenance
window and the reviewed audit/backup results before stopping the old runtime,
renaming directories, changing nginx or merging the deployment PR. Stop at
any failed command or unexplained schema/checksum/ownership difference.

### 1. Audit the legacy runtime and freeze the migration baseline

Use the existing trusted server connection. Do not run `docker inspect` without
selecting fields: environments contain credentials. Record privately:

```bash
set -euo pipefail
umask 077
docker ps -a --format '{{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Label "com.docker.compose.project"}}'
docker volume ls
docker network ls
sudo ss -lntp
```

Identify the old Compose project, exact source commit/images, all application
writers, PostgreSQL container(s), database names, credentials' protected file
locations, media directories and every bind/volume. For each selected container:

```bash
legacy_container='<reviewed-container-name>'
docker inspect --format '{{json .Mounts}}' "$legacy_container"
docker inspect --format '{{json .NetworkSettings.Networks}}' "$legacy_container"
```

Never infer storage from `/srv/edol` alone. Named volumes and media/configuration
outside that directory need separate backup coverage. Identify external
Notify/AMS/Agent connections that could still write. Do not publish printer
commands as a probe. Confirm no active print/recovery work before downtime.

For each reviewed database, inspect in a read-only transaction using its actual
admin user (examples assume `postgres`). Save results under a private backup
root outside `/srv/edol`; do not export credentials or data to CI logs:

```bash
backup_root="$HOME/edol-production-backups/$(date -u +%Y%m%dT%H%M%SZ)"
install -d -m 0700 "$backup_root"
legacy_pg='<reviewed-postgres-container>'
legacy_db='<reviewed-database-name>'
cluster_backup="$backup_root/$legacy_pg"
install -d -m 0700 "$cluster_backup"
docker exec -i "$legacy_pg" psql -X -v ON_ERROR_STOP=1 -U postgres -d "$legacy_db" \
  > "$cluster_backup/$legacy_db-audit.txt" <<'SQL'
BEGIN READ ONLY;
SET LOCAL statement_timeout = '30s';
SELECT version();
SELECT schemaname, tablename FROM pg_tables
 WHERE schemaname NOT IN ('pg_catalog','information_schema') ORDER BY 1,2;
SELECT table_schema, table_name, column_name, data_type, is_nullable
 FROM information_schema.columns WHERE table_schema IN ('public','core','hub','ams')
 ORDER BY table_schema, table_name, ordinal_position;
SELECT format('SELECT %L AS history, installed_rank, version, description, type, script, checksum, success FROM %I.%I ORDER BY installed_rank',
              schemaname || '.' || tablename, schemaname, tablename)
 FROM pg_tables WHERE tablename = 'flyway_schema_history'
\gexec
SELECT format('SELECT %L AS table_name, count(*) AS rows FROM %I.%I',
              schemaname || '.' || tablename, schemaname, tablename)
 FROM pg_tables WHERE schemaname IN ('public','core','hub','ams')
\gexec
COMMIT;
SQL
docker exec "$legacy_pg" pg_dump -U postgres --schema-only -d "$legacy_db" \
  > "$cluster_backup/$legacy_db-schema.sql"
```

Repeat for every application/identity database. Compare applied Flyway
versions, script checksums, schemas, PK/FK types, sequences, RLS and row counts
with the chosen source revision. List legacy printer IDs and each printer's
history/inventory/maintenance ownership privately. Do not repair/baseline
Flyway or edit an applied migration to force an upgrade.

Known code constraints: Hub V2 converts job IDs to UUID and preserves numeric
IDs as `public_id`; V3 assigns legacy catalog data to a reserved default tenant;
V4 expands printer UUID mappings; V5 refuses null/orphaned job, maintenance and
statistics mappings. Core V8 requires a matching Hub printer projection for
existing Core printers. A direct full-chain upgrade of populated legacy data
is therefore not an automatic tenant conversion. Current deploy creates
separate empty `edol_devices`, `edol_hub` and `edol_keycloak` databases.

### 2. Back up, rehearse restore, then retire the old runtime

Keep the original deployment operational until an initial backup has been
restored successfully into a separate, network-isolated PostgreSQL cluster of
a compatible major version. Restore no application/broker containers there;
prevent outbound printer, MQTT, Telegram and SMTP access. Verify schema,
constraints, exact counts and representative history/media references. A
checksum or `pg_restore --list` alone is not a restore rehearsal.

For the final consistent backup, enter the agreed downtime: stop the exact
reviewed application/notification/AMS writers first; keep PostgreSQL running
for logical dumps. Use explicit names from the audit, never a host-wide stop.
Run this for each selected PostgreSQL container and each reviewed database:

```bash
# Run only after the reviewed writers have stopped.
docker exec "$legacy_pg" pg_dumpall -U postgres --globals-only > "$cluster_backup/globals.sql"
docker exec "$legacy_pg" pg_dump -U postgres -Fc -d "$legacy_db" > "$cluster_backup/$legacy_db.dump"
docker exec -i "$legacy_pg" pg_restore --list < "$cluster_backup/$legacy_db.dump" \
  > "$cluster_backup/$legacy_db-toc.txt"
```

Archive the protected old configuration, source/Compose assets and every
identified media directory while writers remain stopped. Stop the old
PostgreSQL container before making any physical volume copy. Retain its
original named volume as an additional recovery source; never `down -v`,
`volume prune` or copy a running PostgreSQL data directory as a valid backup.
Store SHA-256 manifests and a protected copy off this host. Record restore
results and the final cutoff time. If any dump/media path is missing, stop.

Only after successful audit, final backups and restore rehearsal: remove the
reviewed legacy containers using their original Compose definition with
`down` (without `-v`), or explicit reviewed container names. Do not remove Dev,
nginx, Gitea or unrelated containers. Then archive the directory:

```bash
test -d /srv/edol && test ! -L /srv/edol
test ! -e /srv/edol-old
sudo mv -- /srv/edol /srv/edol-old
sudo install -d -m 0700 -o "$(id -un)" -g "$(id -gn)" /srv/edol
install -d -m 0700 /srv/edol/releases /srv/edol/secure
```

`/srv/edol-old` is a retained original, not the independent backup. Do not
reuse its PostgreSQL volume, broker state or identity keys for the fresh
Secure installation. Keep the verified old images and source revision for
rollback. A fresh installation instead creates these new directories without
renaming anything.

### 3. Prepare independent production secrets and trust

From a separate checkout of the reviewed EDOL-048 revision:

```bash
python3 docker/deploy/init_config.py --environment prod --mode secure \
  --output /srv/edol/secure/instance.json
```

Edit the 0600 file privately. Reserve unused host ports and a /24 network.
Defaults are examples: production network `10.253.94.0/24`, Hub/Core/AMS host
ports `8090/8091/8099`, raw MQTT TLS `18885`, origins
`https://edol.s-pon.dev` and `https://auth.edol.s-pon.dev`. Keep Dev's 18884.
Set actual `bind_address`, nginx `proxy_address` and SMTP host/from/user/password,
AUTH/STARTTLS. Preserve independent generated secrets; never copy Dev keys.

Create fresh production MQTT CA/server trust using the following complete
block. Change the example private IP in the SAN if `bind_address` differs:

```bash
set -euo pipefail
umask 077
install -d -m 0700 /srv/edol/secure/ca /srv/edol/secure/certs/mqtt
ca=/srv/edol/secure/ca
certs=/srv/edol/secure/certs
test ! -e "$ca/ca-key.pem" && test ! -e "$certs/mqtt/server-key.pem"
openssl req -x509 -newkey rsa:3072 -nodes -days 3650 -sha256 \
  -keyout "$ca/ca-key.pem" -out "$ca/ca.pem" -subj '/CN=EDOL production MQTT CA'
openssl req -new -newkey rsa:2048 -nodes -keyout "$certs/mqtt/server-key.pem" \
  -out "$ca/server.csr" -subj '/CN=mqtt'
printf '%s\n' 'subjectAltName=DNS:mqtt,DNS:edol.s-pon.dev,IP:192.168.0.200' \
  'extendedKeyUsage=serverAuth' > "$ca/server.ext"
openssl x509 -req -in "$ca/server.csr" -CA "$ca/ca.pem" -CAkey "$ca/ca-key.pem" \
  -CAcreateserial -out "$certs/mqtt/server.pem" -days 365 -sha256 -extfile "$ca/server.ext"
install -m 0600 "$ca/ca.pem" "$certs/mqtt/ca.pem"
export EDOL_STORE_PASSWORD="$(python3 -c 'import json; print(json.load(open("/srv/edol/secure/instance.json"))["secrets"]["truststore"])')"
export EDOL_STORE_DEFAULT=changeit
docker run --rm --user "$(id -u):$(id -g)" -e EDOL_STORE_PASSWORD -e EDOL_STORE_DEFAULT \
  -v "$certs:/export" --entrypoint sh eclipse-temurin:21-jre -ec '
  test ! -e /export/combined.p12
  keytool -importkeystore -srckeystore "$JAVA_HOME/lib/security/cacerts" \
    -srcstorepass:env EDOL_STORE_DEFAULT -destkeystore /export/combined.p12 \
    -deststoretype PKCS12 -deststorepass:env EDOL_STORE_PASSWORD -noprompt
  keytool -importcert -alias edol-prod-mqtt -file /export/mqtt/ca.pem \
    -keystore /export/combined.p12 -storepass:env EDOL_STORE_PASSWORD -noprompt'
unset EDOL_STORE_PASSWORD EDOL_STORE_DEFAULT
```

Keep CA signing keys outside the broker mount; back them up offline. Production
requires certificate/truststore paths inside its instance directory so the
coordinated instance backup covers its TLS trust. It validates leaf
expiry/chain, DNS/IP SANs and matching server key before stopping
services. It does not run Dev certificate signing or regenerate an installed
identity. Plan certificate renewal before expiry. Firmware trust/enrollment is
separate: Dev credentials do not authorize production. Never erase NVS or
reflash automatically during a server deployment.

### 4. Configure ingress and Gitea manually

The owner has created DNS-only records for `edol.s-pon.dev` and
`auth.edol.s-pon.dev`; verify their actual resolution and certificates during
setup. Provision public HTTPS certificates through the existing nginx/ACME
procedure. Back up nginx configuration first and merge
[production routing](../docker/nginx/prod.conf.template) into the existing
configuration, including the separate auth server. Resolve template inputs
and `proxy_address` from the actual nginx network address. Attach nginx to the
external `edol-reverse-proxy` bridge. Retain Dev routes.

On a shared host, EDOL setup must not implicitly change the host resolver,
DHCP configuration, router DNS, Docker daemon DNS or another project's network.
Secure services discover the public HTTPS OIDC issuer during startup. Verify
resolution and hostname-verified TLS from both the host and the application
network. If the public address fails from LAN while the private nginx address
succeeds with the same SNI, investigate routing before changing truststores.
Keep the public issuer and certificate verification unchanged. A future DNS
override must be reviewed and scoped to EDOL containers; the current deployment
configuration does not implement it. Successful startup under a temporary host
DNS workaround does not prove restart/redeployment readiness after rollback.

Inspect existing ACME services/timers, Compose working directory, certificate
and webroot mounts before adding renewal automation. Reuse the existing lineage
and scheduler where applicable. Through the reviewed Certbot container setup,
run `certbot renew --cert-name <lineage> --dry-run --non-interactive` and verify
both domain names. A dry run does not replace the active certificate. Inspect
nginx reload behavior and validate with `nginx -t` before a reviewed reload.
Changing a shared renewal service affects all its sites and needs an explicit
scope; EDOL deployment does not own that service. Record timer/service success,
dry-run success and a future real renewal as separate evidence.

Validate with `docker exec nginx-home nginx -t`, then reload only after success.
Before deployment, production upstreams can return 502. Expose HTTPS443 and
raw MQTT TLS TCP18885 only; forward 18885 to the configured host binding.
Keep PostgreSQL, broker plaintext, Core/AMS private host ports, Keycloak
management port and internal APIs unavailable from WAN. A DNS-only record
does not configure NAT, firewall or MQTT TLS. Restrict private host ports and
the shared proxy bridge to trusted infrastructure.

The template sets enrollment request/body/connection limits at nginx; tune
from bounded traffic measurements without blocking terminal polling or Agent
uploads. The application enrollment map cardinality/NVS protection, broader
DB TLS/secret rotation and external adversarial MQTT ACL validation remain
separate security acceptance items from the existing backlog. Do not label
functional Dev testing as acceptance of those production boundaries.

Add dedicated Gitea repository Actions secrets with verified SSH host keys:
`EDOL_PROD_SSH_HOST`, `EDOL_PROD_SSH_USER`, `EDOL_PROD_SSH_PORT`,
`EDOL_PROD_SSH_KEY`, `EDOL_PROD_SSH_KNOWN_HOSTS`. Their meaning and trusted
host-key verification are identical to Dev's SSH table above, but values and
key are independent. The operator must own `/srv/edol`, have local Docker
access, Python3.11+/OpenSSL/tar and read access to protected state. Docker access
is privileged; authorize only trusted reviewed workflow code.

The CI runner needs Java21/Maven, Python, Node, Docker/Testcontainers access,
OpenSSL, Bash, tar and OpenSSH; the deployment step itself needs no Docker
socket. Keep existing `SONAR_HOST_IP` (scanner URL) and `SONAR_TOKEN` secrets.
The scanner waits for its own Quality Gate and fails on error or timeout;
manual polling of an arbitrary `report-task.txt` is retired. After the scanner,
including on gate failure, CI publishes a Sonar Results summary and updates
the marked PR comment using `GITEACI_TOKEN`. Keep `SONAR_HOST_URL` for browser
links. The report fetches the gate for the exact scanner task/analysis ID;
missing results are shown as unavailable and never treated as a pass. The
scanner's required gate remains responsible for blocking deployment.
Fork PRs without Sonar secrets cannot complete the required gate: review them
in a trusted branch before merge, never grant deployment secrets to untrusted
fork code. Require the `build` job in branch protection and disable direct
pushes/bypasses to `main`. Confirm the actual status-context name in Gitea.

### 5. Render, review, merge and observe deployment

Before merging, from the separate setup checkout:

```bash
revision="$(git rev-parse HEAD)"
python3 docker/deploy/dev_deploy.py render --mode secure \
  --config /srv/edol/secure/instance.json --revision "$revision"
docker compose -f /srv/edol/secure/state/compose.json config --quiet
```

Review the diff and tests, push EDOL-048, open the PR to main, wait for tests
and Sonar, fix actionable failures and require a fresh green run. Merge only
after the server/backup/ingress/SSH prerequisites above are complete. Each
push to main triggers `CI`; `prod-deploy` is a dependent job in the **same**
workflow so it cannot race an unrelated successful CI run. It checks out the
exact resulting main SHA and transfers only allowlisted source paths. Dev
remains manually triggered. CI has no production SSH step on a PR.

For an independent installation without Gitea, use the same reviewed source
and run the supported deployment directly on the Linux host:

```bash
python3 docker/deploy/dev_deploy.py deploy-prod --mode secure \
  --config /srv/edol/secure/instance.json --revision "$(git rev-parse HEAD)" \
  --source-generation "$(git rev-list --count HEAD)"
```

The production lock `/srv/edol/.prod-deploy.lock` covers render and deployment;
concurrent invocations fail closed. A full-history commit-count marker rejects
older queued revisions or different revisions with the same count, including
after a partially failed attempt. Use a full checkout (no shallow clone) and
never rewrite production history or delete this marker to bypass a failure.
Freeze merges during cutover. If a newer
merge arrives during a deploy, inspect results and rerun the newest main CI
rather than starting a parallel manual rollout. Retain all source releases.

Deployment builds/pulls before stopping production writers, starts/verifies
its dedicated PostgreSQL container/volume, backs up globals and every existing
managed database plus instance state/keys/media, then provisions and migrates.
Backups are 0700 directories with 0600 files under `/srv/edol/backups`;
`complete.json` contains SHA-256 hashes and the target revision. Its absence
means an incomplete backup. Verify free disk space beforehand; backup failure
prevents migration. Backups contain secrets: encrypt/off-host-copy them through
the operator's backup system, never upload to Actions. No automatic retention
or backup deletion runs. First launch can contain only globals and empty state
because application databases do not exist yet. The separate legacy backup is
still required. A successful automated backup is not a full restore rehearsal.

Core/Hub/AMS HTTP checks and dependency healthchecks must pass; Notify reports
process liveness only. `state/deployed.json` records the successful SHA, and
`compose.previous.json` retains the prior Compose definition on an update.
Check it against the merged SHA. Inspect safe service status locally; do not
paste expanded Compose, full environments or credential-bearing logs.

### 6. Register only the target owner and printer

Verify public HTTPS/OIDC redirect hostnames, registration, verification email
and login. Register one real user and add the intended printer; create no
inventory or historical jobs yet. Keep DIRECT/AGENT connections disabled and
do not pair devices until the import snapshot has been captured. If pairing
requires an enabled AGENT later, do that only at the device acceptance step.
Confirm the new Hub OWNER membership and its tenant, and the matching Core
and Hub printer UUID/tenant through authorized read-only inspection. Record
explicit source-printer -> target-printer mapping and tenant IDs privately.
A healthy empty dashboard is acceptance of startup, not of restored history.

### 7. Prepare a reviewed data import after identities exist

Do not restore the legacy full dump over the new databases. Keep new users,
OIDC subjects/issuers, memberships, tenant, printer projection, service roles,
Flyway history, Agent/terminal authority and Keycloak untouched. Take a new
coordinated target backup before any import or device enablement.

Restore the legacy dump into an isolated staging database. Design the actual
transformation from the audited schema, not from assumptions about main's
migration number. If versioned conversion is needed, run it only in staging
and stop at Hub V4 until printer UUID mappings have been populated before V5.
Core V8 cannot backfill from a Hub schema in a different database; importing
legacy Core wholesale into the new split databases is not supported.

Prepare explicit table/column allowlists and FK/sequence handling for vendor,
material, filament/spool, print-job/allocation, maintenance and statistics
records. Preserve technical IDs where compatible, map numeric legacy job IDs
to UUIDs while retaining public IDs and remapping children, map all historical
printer references to the approved target printer, and assign every tenant-owned
row to the approved target tenant. Inventory/maintenance child ownership must
remain consistent with parent composite FKs. Resolve colliding display/public
IDs and sequences explicitly. Never duplicate live runtime/active print state,
MQTT receipts, credentials or enrollment records as historical data.

Match media/model/snapshot files to transformed references and preserve hashes;
inspect actual paths before copying. Restore history without triggering event
replay, Telegram notifications or re-consuming filament. Document expected
counts, foreign-key checks, quantities and totals before/after. Compute a
stable mapping and import ledger so retries cannot duplicate history.

Freeze writers before taking the final target snapshot used for rehearsal.
An enabled DIRECT connection can create a job even if the owner creates no
history manually. If the target has changed, abort and rehearse against a fresh
copy; do not delete the new job to satisfy an empty-table guard. Retain its UUID,
check legacy UUID/public-ID collisions, and change a display ID only if needed.
Preserve the new tenant, printer, user, membership, Telegram and receipt rows.
Include sequence handling and any deliberate statistics replacement in the
reviewed transformation. Never rerun a committed import as a generic retry.

Compare deterministic exports of every transformed business table after import,
and separately verify retained live rows and unchanged protected tables. Record
the SQL hash and a success receipt in protected backup storage. On failure,
leave writers stopped and inspect redacted error categories and row counts.
Row rollback does not generally roll back PostgreSQL sequence operations.

Model files are printer-scoped under `models/<printer UUID>/`. Old flat-cache
models may also exist: inspect both layouts and map only reviewed model files
to the target printer. Publish copies without replacing existing files, verify
SHA-256, and record conflicts in a protected manifest. Use the appropriate OS
identity for container-owned storage instead of broadening directory permissions.
Do not copy old active-print metadata over the current printer cache. Camera
snapshots are runtime data with a 24-hour directory cleanup policy when snapshot
storage is enabled; retain old snapshots in the protected legacy archive rather
than treating the live snapshot directory as durable history. Historical plate
images are stored in Hub `print_jobs.plate_image`; copying model files does not
by itself restore a missing historical preview.

Rehearse the entire import into a **copy of the new target**, with no network
access to printers/bots, then verify rollback from its pre-import backup. Only
after reviewing the transformation SQL/tool, source/target mapping and result
should an authorized maintenance operation stop production writers and apply
it transactionally. Do not disable forced RLS or constraints to make it pass.
Exact import code is prepared after the live audit and new IDs are available;
this deployment intentionally cannot guess ownership or import a dump.

### 8. Acceptance and recovery gates

Verify exact source/import counts, no orphan/cross-tenant rows, UUID mappings,
sequences, quantities/totals, historical job detail, spool history, maintenance,
and media downloads. Validate under the real tenant's runtime role as well as
administrative integrity checks: admin visibility does not prove RLS. Confirm
another tenant cannot access imported objects using a separately approved
isolation check. Then enable/pair devices and test the chosen bounded runtime
flows; record production outcomes separately from Dev acceptance.

If build/certificate validation fails before stop, production remains running.
After stop, a failure leaves the target project stopped with volumes/state and
backup artifacts retained. Stop the rollout; do not retry with `clean`, Flyway
`repair`, dropped volumes or an old binary against a newer schema. A failed
migration is not an automatic binary rollback.

For legacy cutover rollback, stop only the new project, switch ingress back to
the backed-up old routing, and restore the old Compose/source/images/config
with its untouched original volumes or the rehearsed backup. Reconcile any
writes since cutoff before reopening access; retain `/srv/edol-old` and the new
state. For later updates, recover **the matching database, keys/broker/media
snapshot and source release together** into a separately reviewed target,
validate it, then switch ingress. Keep both failed and known-good backups.
No generic destructive restore command is prescribed before actual volume,
PostgreSQL version and source identity have been audited.
## Release versioning

EDOL server modules share one SemVer version. The root POM defines
`<revision>1.0.0</revision>` and `<version>${revision}</version>`; child parent
references use `${revision}` and inherit the project version. Dependency versions
are centralized in root dependencyManagement; modules still declare the libraries
that they use. Shared API dependencies obtain `${project.version}` from root
management. This uses [Maven CI friendly versions](https://maven.apache.org/guides/mini/guide-maven-ci-friendly.html).
Build from the reactor root (`mvn -pl <module> -am ...` for a subset).
The inherited Flatten plugin resolves CI friendly coordinates in generated
`.flattened-pom.xml` files for install/deploy without rewriting source POMs.
Generated POMs are ignored by Git and the Docker context.
Agent firmware remains separately versioned.

Use PATCH for compatible fixes, MINOR for compatible features, and MAJOR for
breaking API, MQTT or configuration changes or mandatory manual upgrade steps.
Release candidates use X.Y.Z-rc.N. The version is chosen as a release decision,
not inferred from every commit. Ordinary development keeps the current revision
and adds user-facing changes under CHANGELOG.md Unreleased. Development builds
are identified by their commit; they are not republished as release images.

### Preparing a release

The initial 1.0.0 is already prepared. For a subsequent release, first curate the
Unreleased bullets, then run from the repository root on the working branch:

```powershell
py -B .gitea/scripts/prepare_release.py 1.0.1
py -B .gitea/scripts/release.py check
```

The first command changes only root revision and moves Unreleased into a new
dated section. It refuses an empty changelog, an existing section or a version
that is not newer. It does not commit, tag, push or publish. Review the diff,
run appropriate verification, commit and merge through the normal approved PR
process. Change the example version according to compatibility impact. Each
meaningful commit should have a suitable Unreleased entry; no version bump is
required for a normal commit. The main agent prepares changelog entries; the
commit agent checks them before creating its separately authorized local commit.

### Automatic Gitea publication

Configure Actions secrets EDOL_REGISTRY_USER, EDOL_REGISTRY_TOKEN (package
read/write), and EDOL_RELEASE_TOKEN (repository write, including tags/releases).
The registry is the HTTPS Gitea server host; packages belong to the repository
owner. The runner needs Docker with registry access, Java 21, Python and the
existing Sonar secrets. Protect v* tags and allow the release token's account to
create them. No credentials belong in Git. See the
[Gitea container registry documentation](https://docs.gitea.com/usage/packages/container/).

A main push runs version/changelog checks, the full reactor verify and Sonar
Quality Gate. After those pass, the release job checks for v<revision>:

- If the tag exists and its Release is published, it skips publication, including
  when that tag belongs to an earlier main commit. Ordinary commits do not
  silently rewrite a release. A missing Release or existing draft fails the job
  and requires inspection instead of reporting success.
- If absent, it creates an annotated tag on the verified checkout commit, creates
  a draft Release, builds/pushes Core/Hub/Notify/AMS images and publishes the
  Release with changelog notes and exact image digests.

There is no separate tag-push workflow, so automatic tag creation cannot start a
second competing publication. Do not create a tag or Release manually first.
The first successful main CI with the prepared 1.0.0 creates v1.0.0 automatically.
No manual git tag/push commands are needed.

Images use `<gitea-host>/<owner>/edol-<service>:<version>` and
`:sha-<full-commit-sha>`, with OCI version/revision/source labels. No floating
latest tag is published. Pin recorded digests when consuming images; registry
tags alone are not immutable. A published version is never rebuilt.

Publication is not atomic. Failure after tagging can leave a tag, draft and some
images. A repeated main run fails for an existing tag with a missing or draft
Release. Inspect the failed job, draft and packages before
recovery. Never delete/move the tag or blindly overwrite images. Choose a new
patch version if artifacts must change. A reviewed operator recovery may invoke
`release.py publish` only on the original tagged checkout, with RELEASE_REF set
to that exact tag and the same required credentials, after resolving any existing
draft/package conflicts. The script refuses a duplicate Release creation.

Existing main-push production deployment and Dev source builds retain their
behavior; they do not automatically consume registry images. Thus an ordinary
main push can still deploy production even when no new release is published.
Release publication does not establish runtime acceptance. Production and release
jobs independently depend on the successful build job.

## Production backup diagnostics

Before migrations, backup progress reports fixed artifact identifiers: `globals`,
`database-list`, `edol_devices`, `edol_hub`, `edol_keycloak`, and `instance`.
A failed capture reports `stage=backup artifact=<identifier>` and the subprocess
`exit_code` when available. Timeouts and file-access failures have no exit code.
Command arguments, paths, stderr and protected file contents are never printed.
Any nonzero exit, including tar exit 1, still blocks migrations; a backup is
complete only when `complete.json` exists. A later successful archive attempt
does not establish why an earlier capture failed.

Each backup capture writes subprocess stderr directly to an exclusive file named
`<artifact-filename>.stderr.log` beside the backup artifact (for example,
`instance.tar.gz.stderr.log`). Files are created with mode `0600` inside the
protected backup directory and retained on failure or timeout; existing files
and symlinks are never overwritten. Successful backup manifests include these
logs. Inspect them only on the operator host as the deployment owner. They may
contain credentials or private paths: never paste raw contents into Actions,
issues, or chat. CI continues to print only fixed artifact identifiers and exit
codes. Failure to create a diagnostic file also blocks backup and migrations.
