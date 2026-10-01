# Hub Secure-Dev BFF Preparation Runbook

## Purpose and boundary

This runbook prepares a reviewable Hub-only `secure-multi-tenant` acceptance
environment. It is not authorization to start a browser smoke test, modify a
remote Keycloak realm, deploy production, start Core, connect to MQTT, or use a
non-disposable Hub database.

The accepted remote-dev Keycloak instance is used only as the OIDC identity
provider. Hub remains the owner of user provisioning, memberships, active
tenant selection, and Hub persistence. OAuth tokens remain in the server-side
Hub session and must never be printed, exported, or stored in the database.

## Required approved inputs

Before an operator performs any runtime action, record the following in the
approval record without placing secret values in Git:

| Input | Required value or property |
| --- | --- |
| Deployment mode | `EDOL_DEPLOYMENT_MODE=secure-multi-tenant` |
| Spring profile | `secure-multi-tenant` |
| Hub public hostname | An explicitly approved HTTPS hostname; it is not inferred from the Keycloak hostname. |
| Hub database | A new, isolated, disposable PostgreSQL database with the existing Hub schema-owner, Flyway, and runtime-role separation. |
| OIDC issuer | The approved remote-dev Keycloak issuer, injected through `EDOL_KEYCLOAK_ISSUER_URI`. |
| Hub web-client secret | Injected only through the approved secret mechanism as `EDOL_HUB_WEB_CLIENT_SECRET`. |
| AMS compatibility values | Leave unavailable unless separately needed for the already bounded private ingress; never expose them through public Hub ingress. |

The ordinary Hub datasource and Flyway credentials remain environment-injected
deployment secrets. Do not copy a Keycloak environment file, database password,
client secret, authorization code, refresh token, browser cookie, or session ID
into a command line, log, test fixture, document, or commit.

## Hub configuration contract

The Hub process must receive the following configuration only through the
approved deployment environment or secret store:

```text
EDOL_DEPLOYMENT_MODE=secure-multi-tenant
SPRING_PROFILES_ACTIVE=secure-multi-tenant
EDOL_KEYCLOAK_ISSUER_URI=https://<approved-keycloak-host>/realms/edol
EDOL_HUB_WEB_CLIENT_SECRET=<secret-injection-reference>
```

`application.yaml` already specifies a 30-minute servlet-session idle timeout,
an `EDOL_SESSION` cookie with `Secure`, `HttpOnly`, and `SameSite=Lax`, and
framework handling of forwarded headers. The reverse proxy must be the only
trusted source of `X-Forwarded-*`; it must overwrite client-supplied forwarded
headers, terminate TLS for the approved Hub hostname, and avoid public plain
HTTP access to Hub. A direct HTTP Hub endpoint cannot satisfy the secure-cookie
acceptance condition.

The public callback and post-logout values are not set in this runbook. They
must be calculated from the approved Hub hostname and reconciled through the
separate Keycloak checklist before a browser flow is attempted.

## Disposable PostgreSQL requirements

Use a database created solely for this acceptance run. Apply Hub V1 through V8
normally; do not alter, repair, or roll back migrations. Home uses its
independent repeatable migration after V1 through V8 and is not part of this
runbook. In particular, a secure-to-home downgrade is unsupported and this
procedure must not attempt one.

The disposable database must retain the secure-mode separation between the Hub
schema owner, Flyway executor, and Hub runtime identity. Its lifecycle,
including any later removal, needs a separate operator approval because it is a
data-deletion action.

## Runtime exclusion checks

This is a Hub-only environment. Before an approved browser smoke test, verify
the intended service set contains Hub, its disposable PostgreSQL database, the
trusted HTTPS reverse-proxy path, and the already accepted remote-dev Keycloak
identity provider only.

Do not start Core in `secure-multi-tenant`: its startup remains deliberately
blocked until Stage 5 service authentication is accepted. Do not start or
connect to MQTT, AMS, Notify, printer-facing services, or background work that
would infer a tenant. Hub secure-mode catalog, recovery, and MQTT background
paths must remain unavailable and fail closed.

## Evidence for a later, separately approved smoke test

Record redacted evidence only after receiving specific approval for the
browser smoke test. The evidence should show the configured mode/profile,
trusted HTTPS hostname, disposable database identity, successful login and
PKCE callback, JIT personal tenant with `OWNER` membership, tenant selection,
CSRF rejection, logout, session expiry, and absence of Core/MQTT/background
starts. Never record token values, client secrets, cookies, authorization
codes, database passwords, or personally identifying user data.
