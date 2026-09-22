# ADR 0002: Secure Multi-Tenant Architecture

- **Status:** Accepted
- **Date:** 2026-09-22

## Context

EDOL currently operates as a multi-printer system with an initial tenant
foundation, but not as a securely isolated multi-tenant system. Hub stores one
database-marked default tenant and tenant-owned printer, filament, vendor, and
material records. Other Hub data derives ownership through foreign keys. Core
owns printer identity, connectivity, runtime state, and the authoritative
printer catalog. Notify and AMS consume Core HTTP APIs and MQTT events.

The current implementation has no persisted EDOL user or tenant membership
model. Hub, Core, and AMS HTTP security is permissive, inter-service calls are
not authenticated, MQTT events do not carry tenant identity, and application
database roles are not separated from schema ownership. Tenant filtering is
primarily implemented by repository and service predicates. Hub also contains
native SQL and asynchronous persistence paths that cannot be protected solely
by ordinary Hibernate query conventions.

The current user experience is effectively one user to one tenant, but the
domain must support users belonging to multiple tenants and tenants containing
multiple users. An EDOL tenant is a business concept and must not become an
identity-provider realm, organization, or group.

The architecture must preserve these service boundaries:

- Hub owns user-facing business logic and Hub domain persistence.
- Core remains passive: it exposes APIs and publishes events, but does not call
  Hub, AMS, Notify, or another EDOL service.
- Notify and AMS will be migrated after the initial Hub and Core work.
- An AMS Terminal is a constrained device, not a human user or backend service.

The decision is based on the repository's Spring Boot 4.0.2, Spring Security
7.0.2, Hibernate ORM 7.2.1.Final, and PostgreSQL 17 stack. Hibernate
discriminator tenancy protects ORM operations in a tenant-aware session, but it
does not inject tenant predicates into native SQL. PostgreSQL row-level
security therefore remains necessary as the database isolation boundary.

## Decision

### Domain tenancy and membership

Hub is the system of record for EDOL tenants, users, memberships, and tenant
roles. The identity provider is the system of record only for authentication
identity and credential lifecycle.

- Introduce a global `User` identified by the immutable OIDC `issuer` and
  `subject` pair. Do not store passwords, MFA secrets, or email-verification
  state in EDOL.
- Introduce `TenantMembership` from the first authentication release rather
  than placing `tenant_id` on `User`.
- Start with the `OWNER` tenant role. The model remains extensible to more roles
  without changing the user-to-tenant relationship.
- Store the active tenant in the server-side Hub session only after validating
  membership. Auto-select a single membership and require explicit selection
  when multiple memberships exist.
- Never trust a tenant identifier supplied directly by browser JavaScript.

For an existing installation, the first successfully provisioned EDOL user may
claim the one unclaimed legacy default tenant only when the database contains
no users or memberships and an explicit bootstrap window is open. The claim,
user creation, and `OWNER` membership creation are one transaction. A clean
installation creates a tenant and `OWNER` membership during JIT provisioning.
The schema-foundation migration removes the historical seeded default tenant
only when an ownership audit proves that it has no printer or other tenant-owned
data; a non-empty legacy tenant is never deleted or recreated.
If EDOL provisioning fails after the identity provider has created the
identity, the next login retries the idempotent EDOL transaction.

### Browser authentication and identity provider

Use Keycloak as the self-hosted OpenID Provider and authorization server. Use
one EDOL realm; do not create one realm or Keycloak group per EDOL tenant.

Hub is a confidential Backend-for-Frontend:

```text
Browser -- secure session cookie --> Hub -- Authorization Code + PKCE --> Keycloak
```

OAuth access and refresh tokens remain server-side. Hub uses secure,
`HttpOnly`, `SameSite=Lax` session cookies, protects state-changing
browser requests with CSRF, and does not use the Resource Owner Password
Credentials grant. Login, logout, registration, password reset, email
verification, and MFA are Keycloak capabilities presented through an
EDOL-branded theme and application flow; users never need the Keycloak
administration console.

The current single-instance Hub deployment uses an in-memory server-side
servlet session and authorized-client store. A Hub restart invalidates sessions
and requires login again. Refresh tokens are not serialized into the browser or
the EDOL business database. Horizontal Hub scaling requires a separately
reviewed shared encrypted session store and is outside the initial migration.

### Service identities and tenant propagation

Each EDOL service receives a separate OAuth client identity. Initial
deployments may use individually managed client secrets and short-lived access
tokens. Asymmetric `private_key_jwt` client authentication is a later hardening
step, not a prerequisite for the first secure rollout.

Initial clients and audiences are:

| Client | Target audience | Purpose |
| --- | --- | --- |
| `edol-hub-web` | Hub login callback | Browser OIDC client |
| `edol-hub-service` | `edol-core-api`, later `edol-ams-api` | Hub service calls |
| `edol-notify-service` | `edol-core-api` | Notify service calls |
| `edol-ams-service` | `edol-core-api`, `edol-hub-api` | AMS service calls |

Initial resource scopes are deliberately operation-oriented rather than
tenant-specific:

| Scope | Allowed operations | Initial clients |
| --- | --- | --- |
| `tenant.context` | Supply a trusted tenant context to an EDOL resource server | Hub, Notify, AMS service clients |
| `core.printer.read` | Read the printer catalog and non-secret printer metadata | Hub, Notify |
| `core.printer.manage` | Create, update, decommission, and manage connection configuration | Hub only |
| `core.state.read` | Read current printer/AMS state | Hub, Notify, AMS |
| `core.command.execute` | Pause, resume, stop, push state, fetch metadata, and other printer commands | Hub, Notify |
| `core.media.read` | Read camera, plate, and timelapse media | Hub, Notify |
| `core.agent.upload` | Upload agent telemetry/media for an assigned printer | Future per-agent identities only |
| `hub.spool.read` | Resolve tenant-visible spool information | AMS only |
| `hub.spool.change` | Apply the existing validated spool-change workflow | AMS only |
| `ams.terminal.manage` | Create, revoke, rotate, and re-pair terminals | Hub only |

Connection secrets are not included in general printer-read responses.
`core.printer.manage` protects the narrower connection-management contract.
Endpoint-to-scope mappings are recorded as resource-server tests so adding an
endpoint without an explicit mapping fails review.

Service access tokens expire after five minutes and client-credentials flows do
not issue refresh tokens. Credentials are unique per service, stored outside
the repository, and rotated independently with at most a 24-hour controlled
overlap. Browser access tokens also use a five-minute lifetime, remain in the
server-side authorized-client store, and may be refreshed only within a
30-minute idle and eight-hour maximum Hub session. Offline tokens are not used.
The exact Keycloak realm export records these values and is reviewed with each
security stage.

Resource servers validate issuer, signature, expiration, not-before time,
audience, client identity, and required scopes. Core is not exposed directly to
browser users in the normal application model.

Authentication identity and tenant context remain separate. Trusted services
propagate the active tenant in `X-EDOL-Tenant-Id` alongside their OAuth access
token. A resource server accepts the header only from an allowlisted service
client with a tenant-context authority and overwrites or rejects any
untrusted value at its ingress boundary.

Hub does not delegate the browser's access token to Core. Hub performs user and
membership authorization, then Core independently enforces service scope and
resource tenant ownership. Logs correlate the authenticated service, tenant,
request, and, where available, the Hub audit actor without treating an audit
actor header as authorization.

### Trust boundaries and communication map

The table describes the target contract. Compatibility exceptions are limited
to the migration stages described later in this ADR.

| Edge | Caller identity | Authentication | Authorization | Tenant-context source | User delegation | Trust assumptions |
| --- | --- | --- | --- | --- | --- | --- |
| Browser to Hub | Authenticated EDOL user represented by a Hub session | Secure session cookie established by OIDC Authorization Code + PKCE | Hub membership and tenant role | Server-side active tenant selected from current memberships | Direct user request; no browser bearer token forwarding | Browser input, including tenant headers and resource IDs, is untrusted |
| Browser and Hub to IdP | `edol-hub-web` and the authenticating user | OIDC redirect, authorization code, PKCE, confidential-client authentication | Keycloak authentication flow and client policy | None; IdP does not authorize EDOL tenants | Authentication only | Hub trusts configured issuer metadata, TLS, signatures, state, and nonce validation |
| IdP to Hub callback | Configured Keycloak issuer | Signed OIDC response over TLS | Hub accepts only its registered client/redirect flow | Hub maps issuer/subject to EDOL User, then validates membership | Establishes user identity, not tenant delegation | IdP is authoritative for identity and credential state only |
| Hub to Core | `edol-hub-service` | OAuth client-credentials JWT over TLS | Core audience, client, scope, tenant, and resource checks | Hub active tenant in `X-EDOL-Tenant-Id` | No user token delegation; Hub records the user decision for audit | Core trusts tenant context only from an allowlisted Hub client with tenant-context authority |
| Core to MQTT | Dedicated Core publisher | Broker client identity over TLS | Publish ACL restricted to Core event topics | Persisted Core printer ownership | None | Broker trusts only Core to publish Core integration events |
| MQTT to Hub | Dedicated Hub subscriber | Broker client identity over TLS | Subscribe ACL plus envelope and local ownership validation | Event envelope produced from Core ownership | None | Broker delivery is authenticated but event content is still validated; QoS 1 may duplicate |
| MQTT to Notify | Dedicated Notify subscriber | Broker client identity over TLS | Subscribe ACL plus envelope and recipient mapping | Event envelope | None | Notify must not infer tenant from an untrusted printer UUID alone |
| MQTT to AMS | Dedicated AMS subscriber | Broker client identity over TLS | Subscribe ACL plus envelope and AMS device/printer mapping | Event envelope | None | AMS validates the event against its persisted trusted state |
| Notify to Core | `edol-notify-service` | OAuth client credentials over TLS | Notify-specific catalog, state, command, and media scopes | Trusted Notify processing context created from validated event/configuration | None | Core accepts the header only from the Notify client and still checks resource ownership |
| AMS to Core | `edol-ams-service` | OAuth client credentials over TLS | AMS-specific Core state-read scope | Authenticated terminal, validated event, or AMS configuration | None | Core does not trust terminal-supplied tenant values forwarded without AMS validation |
| AMS to Hub | `edol-ams-service` | OAuth client credentials over TLS | Hub audience and narrow AMS scopes | AMS trusted processing context | None | Hub validates service identity, tenant, scope, and target ownership |
| Hub to AMS | `edol-hub-service` | OAuth client credentials over TLS | AMS audience, terminal-management scope, and Hub `OWNER` decision | Hub active tenant | No user token delegation; Hub authorizes the user before the service call | AMS trusts tenant context only from the Hub service client |
| AMS Terminal to AMS | Individual terminal identity | Opaque device credential over server-authenticated TLS | Active credential, terminal, endpoint, tenant, and printer scope | Persisted AMS terminal record | None | Pairing input is untrusted until atomically verified; a pairing code is never an operational credential |
| Printer or agent to Core | Individual printer protocol identity or future agent machine identity | Printer protocol security or scoped agent credential over TLS | Assigned printer and telemetry/upload scope | Persisted Core printer mapping | None | Device-provided printer, path, and file metadata remain untrusted inputs |

### Passive Core ownership contract

Core stores an opaque tenant UUID on each Core printer, but it does not own or
query the Hub tenant aggregate and has no foreign key to `hub.tenants`.

- Existing Core printers receive tenant ownership through one guarded migration
  backfill using the already validated Hub printer projection.
- New Core printer ownership is established only by an authenticated
  Hub-to-Core provisioning request. Core persists the trusted tenant context;
  it does not accept an arbitrary tenant from a request body.
- Normal printer updates must match the persisted tenant and cannot reassign
  the printer. A future tenant-transfer feature requires a separate explicit
  contract and decision.
- Core never calls Hub to discover, validate, or repair tenant ownership.

The cross-schema backfill is a one-time data migration, not a runtime
dependency.

### Persistence isolation

Use Hibernate discriminator tenancy and PostgreSQL row-level security together.

Directly tenant-owned entities use Hibernate `@TenantId` and a
`CurrentTenantIdentifierResolver`. Child entities normally do not duplicate
`tenant_id` when ownership can be derived safely through a mandatory foreign
key. Cross-aggregate association entities carry `tenant_id` where it is needed
to enforce that both sides belong to the same tenant. Composite foreign keys
enforce links to direct tenant-owned parents. Links to parents whose ownership
is derived use PostgreSQL constraint triggers and RLS `WITH CHECK`; the trigger
functions use invoker privileges and schema-qualified objects rather than a
general security-definer bypass.

The tenant resolver is fail-closed. A missing tenant never resolves to the
legacy default tenant. Open EntityManager in View is disabled, and tenant-owned
work executes within explicit transactions. Background and asynchronous work
must carry a trusted tenant and establish a new tenant-scoped transaction; it
must not pass a live entity or persistence session between threads.

At transaction start, the application sets tenant state on the same database
connection with parameterized `set_config('edol.tenant_id', value, true)`.
Transaction-local state prevents tenant leakage when pooled connections are
returned. Policies use null-safe `current_setting(..., true)`, `USING`, and
`WITH CHECK`. A missing or malformed setting denies access.

Pre-tenant user and membership discovery uses a separate authenticated
`IdentityContext`, never an invented tenant. Hub sets the validated OIDC issuer
and subject transaction-locally as `edol.oidc_issuer` and
`edol.oidc_subject`. User policies allow only the row matching that pair;
membership and tenant-selection policies allow only memberships belonging to
that user. A clean JIT flow generates the new tenant UUID, establishes that
tenant context, and creates the tenant and owner membership atomically. The
legacy first-owner claim is a distinct bootstrap transaction guarded by the
zero-user/zero-membership invariant and bootstrap window.

Schema ownership, Flyway execution, and application runtime use separate roles.
Runtime roles are not table owners, superusers, or `BYPASSRLS` roles. Tenant
tables use `FORCE ROW LEVEL SECURITY`. Flyway runs with a dedicated migration
identity outside ordinary application request processing.

Core does not receive a universal RLS-bypass datasource. Cross-tenant runtime
printer discovery uses a dedicated, read-only catalog identity with all of the
following restrictions:

- `NOSUPERUSER`, `NOBYPASSRLS`, and not a table owner;
- column-level `SELECT` only on the minimal Core printer and connection catalog
  required to start printer runtimes;
- role-specific `FOR SELECT` policies only on those tables;
- no DML, no Hub schema access, and no access to operational Core tables;
- application access only through a narrow runtime catalog enumerator, never
  through an HTTP controller or general repository.

After enumeration, operational reads and writes use the normal datasource in a
tenant-scoped transaction. Each post-stage audit must re-check the catalog
role's grants, policies, dependency paths, and negative access tests.

### MQTT tenancy

Core remains the only producer of Core integration events. Tenant-owned events
adopt an additive envelope:

```json
{
  "schemaVersion": 2,
  "eventId": "018f...",
  "eventType": "print.progress",
  "tenantId": "018f...",
  "printerId": "018f...",
  "timestamp": "2026-09-22T12:00:00Z",
  "payload": {}
}
```

During migration, the same message also retains existing top-level `event` and
payload fields. Core derives `tenantId` from persisted printer ownership, not
from telemetry input. Consumers validate the envelope and their local
printer-to-tenant relationship. `eventId` enables idempotent handling of MQTT
QoS 1 duplicate delivery. Producer and subscriber identities, TLS, and broker
ACLs are introduced without breaking older consumers and are required before
legacy event fields are removed.

### AMS Terminal identity

AMS owns the terminal and pairing registry. An authenticated Hub `OWNER`
creates, revokes, or re-pairs a terminal through an authenticated Hub-to-AMS
management API.

The initial protocol uses a short-lived, single-use EDOL pairing credential.
The terminal submits it to AMS over TLS and receives a persistent per-device
credential exactly once. The pairing code is tenant-bound, rate-limited,
expires, has a strict attempt limit, is stored only as a digest, and is consumed
atomically.

The persistent credential is an opaque random 256-bit secret represented with
a terminal identifier. AMS stores only a keyed digest and validates it in
constant time. The ESP32 stores it using encrypted NVS/flash and uses server-
authenticated TLS. Credentials are endpoint- and printer-scoped, revocable,
rotatable, and replaceable through re-pairing. Factory reset or terminal
replacement requires revocation and a new pairing operation.

OAuth 2.0 Device Authorization Grant is not used for the first implementation.
It adds authorization-server, polling, and user-code UX complexity without a
current requirement for the terminal to hold delegated OAuth authority. It may
be reconsidered if terminals later need direct, standardized user delegation.

| Concern | EDOL-managed enrollment | RFC 8628 / Keycloak Device Authorization Grant |
| --- | --- | --- |
| User experience | User starts pairing in EDOL and enters the resulting code on the terminal | Terminal obtains and displays a user code; user opens a verification URI and authorizes while the terminal polls |
| Resulting identity | Narrow AMS terminal record and opaque per-device credential | OAuth tokens issued to a device client, with token refresh and authorization-server lifecycle |
| Tenant binding | Hub-authorized tenant is bound when the pairing record is created | Requires mapping the authorized IdP user back to EDOL tenant selection during device authorization |
| ESP32 complexity | One TLS exchange plus ordinary authenticated AMS calls | Device authorization request, interval-aware polling, OAuth error handling, token storage, expiry, and refresh |
| Revocation and rotation | Implemented directly on the AMS terminal record | Requires OAuth client/token revocation plus EDOL terminal lifecycle mapping |
| Best fit | Current fixed-purpose AMS API | Future device needing standardized delegated OAuth access across resource servers |

### Authorization and response semantics

- A valid resource UUID never grants access by itself.
- User access requires an authenticated EDOL user, active membership, tenant
  role, and matching resource ownership.
- Service access requires a valid service token, audience, scope, trusted
  tenant context, and matching resource ownership.
- Terminal access requires an active device credential and matching terminal,
  tenant, endpoint, and printer scope.
- A resource owned by another tenant is reported as not found. Insufficient
  privileges for a visible current-tenant resource are forbidden.

### Migration compatibility

The migration does not lock down Core globally before Notify and AMS have been
migrated. Authenticated Hub traffic uses the secure tenant-aware contract first.
Only an explicit, measured allowlist of legacy endpoints remains for existing
Notify and AMS consumers, restricted to existing printers and deployment
network controls. Legacy access cannot provision printers or reach new tenants.

Hub user authentication also retains a temporary AMS-only compatibility
boundary for the existing spool lookup and spool-change calls. It is restricted
to the exact current endpoints, the legacy tenant, and internal network ingress;
it is not part of the browser security chain. The boundary is removed when AMS
receives its service identity.

Before Keycloak is introduced, Hub may temporarily run current legacy traffic
inside an explicitly named `LegacyDefaultTenantCompatibilityScope`. This is a
migration boundary, not resolver behavior: it sets the known legacy tenant only
at enumerated entry points, logs and meters every use, and leaves normal
`TenantContext` resolution fail-closed. The scope, its configuration, and its
tests are removed in the user-authentication stage, not deferred to final
hardening.

## Rationale

`TenantMembership` represents the expected many-to-many domain without tying
business ownership to the IdP. A BFF keeps OAuth tokens away from browser
JavaScript and fits Hub's existing server-rendered UI and same-origin API calls.
Keycloak provides the required identity lifecycle and standards support without
turning EDOL into an authentication platform.

The identity-provider comparison is:

| Requirement | Keycloak | ZITADEL | Spring Authorization Server |
| --- | --- | --- | --- |
| Self-hosting and PostgreSQL | Separate self-hosted service with documented PostgreSQL support; EDOL operates upgrades, backup, TLS, and mail | Separate self-hosted service requiring PostgreSQL and an HTTP/2-capable proxy; similarly needs operational ownership | Embedded/custom Spring application; EDOL must design and operate the complete product and persistence model |
| Registration and account lifecycle | Built-in configurable registration, login/logout, reset credentials, email verification, and account console | Provides hosted/self-hosted login and user lifecycle capabilities, but introduces its own organization/project administration model | Protocol endpoints are available, but EDOL must build user registration, login UX, recovery, verification, account management, and administration |
| MFA readiness | Built-in OTP, WebAuthn/passkeys, recovery codes, and configurable authentication flows | Supports MFA/passwordless capabilities | EDOL must select, implement, store, recover, and operate MFA mechanisms |
| OIDC, tokens, and JWKS | Standards-based OIDC endpoints, signing-key management, JWKS, session and revocation controls | Standards-based OIDC/JWT and public-key verification | Supports OIDC, JWT/JWK configuration, and protocol extension, but key lifecycle is an EDOL responsibility |
| Machine identities | Per-client service accounts and client-credentials grants with scoped roles | Service accounts support client credentials, private-key JWT, and other token forms | Supports client credentials and registered clients, but EDOL must build provisioning and administration |
| Asymmetric client authentication | Signed JWT client assertions using a client private key are supported | Private-key JWT is supported for service-account access | `private_key_jwt` is supported, but EDOL must implement client/key registration and rotation operations |
| Spring Security integration | Standard `oauth2Login`, OAuth client, and resource-server integration; no legacy Keycloak adapter required | Same standards-based Spring integration is possible | Native Spring ecosystem fit, but that does not supply missing identity-product capabilities |
| EDOL tenant separation | One realm can authenticate users while Hub remains the tenant authority | EDOL could remain authoritative, but ZITADEL organizations create an avoidable competing tenancy concept | Fully controllable, but custom implementation increases the risk of coupling identity and EDOL tenancy |
| Operational trade-off | Mature but substantial service; selected because it covers the required lifecycle without custom auth development | Credible alternative with comparable service operation; no concrete EDOL benefit justifies changing the selected direction | Lowest external-product dependency but highest security engineering and long-term maintenance burden |

Keycloak is therefore selected on EDOL fit, not feature count: it supplies the
identity lifecycle the application should not build, supports the required
human and machine flows, integrates through standard Spring Security, and can
remain outside the EDOL tenant source of truth.

Hibernate tenancy provides safe defaults and developer ergonomics for normal
ORM access. PostgreSQL RLS provides a second boundary for identifier loads,
mistaken repository methods, and native SQL. Transaction-local database state,
separate runtime roles, and forced policies are required for RLS to remain
effective with connection pooling.

A service token plus a trusted tenant header is simpler than token exchange and
keeps EDOL tenants out of the IdP. It does not eliminate the impact of a fully
compromised trusted service, so each service has an independent, least-
privilege identity, short token lifetime, network restrictions, and auditable
tenant propagation.

Keeping Core passive avoids a circular runtime dependency and makes tenant
ownership explicit at provisioning time. The narrow read-only catalog role
supports Core's cross-tenant printer runtime responsibility without creating a
general application escape from RLS.

## Alternatives

- **Put `tenant_id` directly on `User`:** rejected because it encodes the
  temporary one-user/one-tenant UX and requires redesign for multiple
  memberships and roles.
- **Represent EDOL tenants as Keycloak realms, groups, or organizations:**
  rejected because the IdP would become the business ownership authority and
  EDOL data lifecycle would be coupled to identity administration.
- **ZITADEL:** viable OIDC, PostgreSQL, service identity, and device-flow
  alternative, but it does not provide a concrete EDOL advantage sufficient to
  replace the selected Keycloak self-hosting and customization model.
- **Spring Authorization Server:** rejected because it is an authorization-
  server framework rather than a complete user lifecycle product. EDOL would
  have to build registration, recovery, verification, MFA, administration, and
  operational tooling.
- **Hibernate tenancy only:** rejected because native SQL is not automatically
  tenant-filtered and a single missing ORM boundary could disclose data.
- **PostgreSQL RLS only:** rejected because it gives weaker application-level
  feedback and does not encode tenant-aware entity creation and ORM behavior.
- **Add `tenant_id` to every child table:** rejected because mandatory ownership
  chains already provide an unambiguous tenant. Duplication is reserved for
  direct roots and cross-aggregate associations where it enforces same-tenant
  references or materially simplifies safe access.
- **Tenant-specific access tokens or token exchange:** deferred because they
  require IdP awareness of EDOL tenant state and substantially increase token
  issuance complexity. A signed internal tenant assertion does not materially
  reduce the impact of full Hub compromise in the initial deployment.
- **Universal Core RLS bypass role or datasource:** rejected because it would
  turn a background-runtime need into a general tenant-isolation escape hatch.
- **OAuth Device Authorization Grant for ESP32:** deferred because EDOL needs
  device enrollment and narrow device credentials, not ongoing delegated user
  OAuth authority.

## Consequences

- Multi-tenant security is introduced through staged, independently auditable
  releases rather than one global switch.
- Direct tenant roots and selected cross-aggregate tables gain tenant columns;
  most child tables retain derived ownership.
- Native SQL, asynchronous work, scheduled work, and connection-pool reuse need
  explicit isolation tests.
- Database deployment must support separate migration, application, and narrow
  Core catalog identities.
- Notify and AMS retain a bounded compatibility window; EDOL cannot claim full
  end-to-end lockdown until those consumers migrate.
- Existing MQTT consumers require an additive envelope rollout before legacy
  fields can be removed.
- A compromise of an authorized service remains capable of misusing the
  tenants that service can act for. Least-privilege clients, network controls,
  rotation, audit logs, and later asymmetric client authentication reduce this
  residual risk.
- Significant changes to this decision require an ADR amendment or superseding
  ADR. The living implementation plan may be updated after each stage without
  rewriting this decision history.

## References

- [Hibernate ORM 7.2 Introduction](https://docs.jboss.org/hibernate/orm/7.2/introduction/pdf/Hibernate_Introduction.pdf)
- [PostgreSQL 17 Row Security Policies](https://www.postgresql.org/docs/17/ddl-rowsecurity.html)
- [PostgreSQL configuration-setting functions](https://www.postgresql.org/docs/17/functions-admin.html)
- [Spring Security JWT Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [OAuth 2.0 Security Best Current Practice, RFC 9700](https://www.rfc-editor.org/rfc/rfc9700.html)
- [OAuth 2.0 Device Authorization Grant, RFC 8628](https://www.rfc-editor.org/rfc/rfc8628.html)
- [Keycloak Server Administration Guide](https://www.keycloak.org/docs/latest/server_admin/)
- [Keycloak supported databases](https://www.keycloak.org/server/db)
- [ZITADEL self-hosting requirements](https://zitadel.com/docs/self-hosting/manage/requirements)
- [ZITADEL self-hosted database documentation](https://zitadel.com/docs/self-hosting/manage/database)
- [ZITADEL service-account API access](https://zitadel.com/docs/guides/integrate/zitadel-apis/access-zitadel-apis)
- [Spring Authorization Server Reference](https://docs.spring.io/spring-authorization-server/reference/index.html)
- [Spring Authorization Server configuration model](https://docs.spring.io/spring-authorization-server/reference/configuration-model.html)
- [ESP-IDF storage security](https://docs.espressif.com/projects/esp-idf/en/latest/esp32/api-reference/storage/storage-security.html)
