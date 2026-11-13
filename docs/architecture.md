# EDOL Architecture

## System Map

EDOL is a Java 21, Spring Boot 4 Maven reactor with four services, PostgreSQL and MQTT. The Compose definitions use Mosquitto. The root `pom.xml` is the build entry point; Docker Compose is the local/self-hosted runtime composition.

| Module | Responsibility | Main boundaries |
| --- | --- | --- |
| `edol-core-api` | Shared printer-state and AMS DTOs. | Consumed by all services; changes are cross-service API changes. |
| `edol-core` | Printer provisioning, direct/agent telemetry, commands, model metadata, camera, runtime and recovery state. | Bambu printer protocols, agents, MQTT, `core` PostgreSQL schema, HTTP state API. |
| `edol-hub` | Persistent operational domain and dashboard: inventory, spools, print jobs, allocation, maintenance, labels. | `hub` PostgreSQL schema, Core HTTP API, Core MQTT events, browser clients. |
| `edol-notify` | Printer-scoped Telegram commands and printer/print notifications. | Core HTTP API, Core MQTT events, Hub tenant Telegram configuration, Telegram. |
| `edol-ams` | Printer-scoped AMS workflow and secure terminal enrollment. | Core and Hub HTTP APIs, Core MQTT events, `ams` PostgreSQL schema. |

## Runtime and Data Flows

1. A printer reports telemetry directly or through an agent. Core resolves the printer, updates its in-memory runtime state, and derives application events.
2. Core handles those events for print lifecycle, metadata, camera, recovery, and commands, then publishes integration events under `edolcore/#`.
3. Hub consumes the Core integration-event contract for persistent print and inventory effects. In `secure-multi-tenant` mode, Notify consumes only its declared printer and print topics, and AMS consumes only `edolcore/ams` and `edolcore/print/ams`; both validate the additive v2 envelope before using its tenant context. Home retains the legacy trusted-network subscriptions.
4. Hub, Notify, and AMS also call Core over HTTP for current printer state or commands. Hub owns its persistence; Core owns printer connectivity and runtime state.

Core runtime is per printer. New printer, connection and session identifiers use UUID v7; historical UUID v4 values are retained. Its HTTP API exposes a printer catalog and explicit `{printerId}` state, command, and media endpoints; default-printer HTTP endpoints are not supported. Hub, AMS and Notify use only explicit printer endpoints. Any change that carries printer identity through MQTT or HTTP must be coordinated across every producer and consumer.

DIRECT Paho report/disconnect callbacks restore the tenant from the trusted
runtime registry on the callback thread. Asynchronous startup recovery and scheduled connectivity checks
execute within that same trusted runtime tenant boundary. Recovery and reconnect
do not inherit a scheduler/caller tenant or resolve one from printer payloads.
Direct connection and camera
configuration reads use the transactional printer-management service so the
secure RLS tenant setting is established; network I/O remains outside that
configuration transaction. Transport connectivity alone is not proof of
processed telemetry: overview readiness waits for usable printer state.

Live `printing` follows printer G-code PREPARE/RUNNING/PAUSE telemetry,
independently of model metadata acquisition. Metadata retries restore the
printer's trusted runtime tenant on each scheduler invocation. DIRECT model
configuration uses the same short transactional management lookup as camera;
FTPS I/O runs outside that transaction. Snapshot commands are restricted to
persisted AGENT connections. Core's camera background loop requests an Agent
snapshot through the existing `UPLOAD_SNAPSHOT` command; the asynchronous,
credential-bound HTTPS upload stores the image. This path does not depend on
metadata parsing and does not enable a second Agent snapshot scheduler.
Secure background snapshot requests require both a usable persisted MQTT
binding in the current tenant and the printer's online runtime state. Unpaired,
revoked or offline Agents are skipped; ordinary commands still reject missing
authority. A replacement binding and new online state allow capture to resume
without restarting Core. MQTT disconnect/authentication failure is not an
authenticated revocation notification to firmware; re-enrollment currently uses
the existing operator-opened setup portal.

Firmware gives both MQTT clients to the main loop and camera/FTPS/HTTP clients
to one low-priority bulk task. MQTT callbacks copy bounded transfer requests;
they never perform bulk I/O or recursively service MQTT. There is one active
operation, at most one coalesced pending model, no snapshot queue and a
15-second cooldown after completion. Model requests outrank snapshots. Worker
model events cross a bounded single-producer/single-consumer queue; only the
main loop publishes them. Disconnected events remain queued, with at most three
publish attempts one second apart and a 60-second expiry. New bulk operations
wait for that queue to drain; completed state is also collected after Wi-Fi
loss. Home uses the same ownership/scheduling boundary.
Secure bulk operations verify EDOL HTTPS before opening printer bulk TLS,
reuse that prepared connection, and never intentionally disconnect printer or
EDOL MQTT. Record-buffer admission is only a necessary memory floor; failed
handshakes/transient allocations reject the transfer without weakening trust.
All TLS
clients preserve the invalid socket descriptor after SDK cleanup.

Agent model transfer requires a validated positive FTPS SIZE, exactly that many
bytes, a RETR preliminary reply and final 226. A one-byte HTTP tail is withheld
until FTP completion, preventing premature Core acceptance. LAN sockets close
before HTTP finalization. Only the expected HTTP response completes the Agent
operation; a lost acknowledgement remains uncertain. Core metadata acquisition
coalesces duplicate failures into one pending retry and stops after four retries;
metadata success/stop cancels pending work. Per-printer state and trusted tenant
execution are retained, including independent DIRECT/AGENT logical printers.

Dashboard command POSTs carry the server-rendered CSRF token and report HTTP
rejection or uncertain network outcomes. The Hub proxy retains its authenticated
RestTemplate interceptor and printer ownership check; Core selects DIRECT Bambu
or the persisted, usable Agent MQTT authority. HTTP acceptance is distinct from
observing a printer state change in subsequent telemetry.

Hub projects Core printers with the same UUID. Jobs, maintenance, allocation,
recovery, statistics, command routes and dashboard state are printer-scoped.
Catalog unavailability permits serving existing projections, but ambiguous
ownership blocks migration and a catalog mismatch blocks runtime validation.
Core decommissioning marks the Hub projection unavailable while preserving
Hub history. AMS staging and Notify progress are also printer-scoped.

## Deployment Modes and Trust Boundaries

`EDOL_DEPLOYMENT_MODE` (`edol.deployment.mode`) must explicitly select
`home` or `secure-multi-tenant`. Home is a single-owner installation on
a trusted network and derives its tenant from `hub.home_installations`.
Secure mode uses OIDC, active memberships, independent service identities
and forced RLS. Database conversion requires its own reviewed migration.
See [operations](operations.md) for deployment and recovery constraints.

Hub owns EDOL users, tenants, memberships and roles. Keycloak owns OIDC
identity and credential lifecycle. Hub is a confidential OIDC BFF with
Authorization Code + PKCE S256, server-side session and token storage, CSRF,
and an active-membership check before establishing the request tenant. Hub
authorizes browser actions and calls downstream APIs with its service identity.
Each resource server validates the caller, audience, scope, trusted tenant
context and resource ownership. See [ADR 0002](adr/0002-secure-multi-tenant-architecture.md)
for the cross-service trust model and [tenant ownership](contracts/tenant-ownership.md)
for persisted boundaries.

Secure browser login, self-registration, email verification and password recovery
use the bundled `edol` Keycloak login theme, inheriting native Keycloak forms
and validation. The realm template permits email-based registration and requires
email verification. On first successful OIDC login, Hub's existing JIT provisioning
creates a personal tenant and active OWNER membership when self-service is enabled.
The secure profile enables this by default; an explicit
`EDOL_HUB_SELF_SERVICE_REGISTRATION_ENABLED=false` disables new personal tenants.
Existing memberships and the closed-by-default legacy-owner bootstrap retain
their existing checks. Registration never grants membership of an existing tenant.
Keycloak registration and Hub provisioning are separate operator controls; see
[operations](operations.md) for enabling both in an existing installation.

### Printer HTTP and Service Events

Core resolves the current tenant's persisted printer before mapped secure
`/api/printers/**` handlers with a `printerId` path variable execute.
Aggregate state is tenant-filtered. Runtime discovery uses a distinct
read-only catalog identity. Home retains trusted-network access.
Connection response DTOs omit printer access codes in both modes. A blank
or omitted code on update preserves the current value. Deploy the compatible
Core implementation before the corresponding Hub editor.

Core derives event tenancy from persisted printer ownership and publishes one
additive v2 event with schema version, event ID/type, tenant and printer UUIDs,
RFC 3339 timestamp and nested payload alongside the legacy fields. Secure Hub,
Notify and AMS readers validate v2 and envelope-only v3. V2 requires a matching
legacy `event` marker. V3 accepts its absence and rejects a contradictory
marker. Home uses the legacy consumer contract.

Secure Hub MQTT ingress is opt-in. Hub verifies tenant/printer ownership and
uses a tenant-RLS receipt ledger for idempotent delivery. Printer-scoped
current-job and allocation caches publish updates after transaction commit.
The processing transaction can see its own pending values and rollback
discards them. Secure readers log restart-scoped counters for validated v2/v3,
legacy markers, rejected envelopes and processing failures, without logging
payloads or using tenant/printer/topic IDs as counter dimensions. Validated
delivery includes duplicates and later processing failures or denials.

AMS resolves tenant context from a validated v2/v3 envelope, an authenticated
terminal binding or an approved printer mapping. Unknown mappings fail before
downstream calls. Hub restricts AMS JWT ingress to the exact spool routes and
Core accepts tenant headers only from allowlisted service clients. Notify
selects recipients from the trusted processing tenant.
The trusted-LAN AMS routes `/ams/state`, `/ams/find` and `/ams/set-spool`
require a printer ID. Spool staging and its expiration belong to that printer.

### Tenant Telegram configuration

Hub owns one Telegram bot/chat configuration per tenant. Only an authenticated
active OWNER can read its settings page or save changes; browser writes require
CSRF. Hub V10 adds forced-RLS `hub.telegram_settings`. Tokens use AES-256-GCM
with a fresh nonce and tenant UUID as authenticated data. The independent
`EDOL_HUB_TELEGRAM_ENCRYPTION_KEY` is a base64-encoded 32-byte installation key,
never a database value or browser field. A missing key permits startup and
disabling settings but prevents storing/enabling a bot. Losing/changing it
without a deliberate migration makes existing tokens unreadable. Token status,
chat ID and enabled state are the only browser read fields; blank token input
keeps the saved value. Token removal is explicit and requires disabling the bot.
A unique token fingerprint prevents two tenants from polling the same bot.

`hub.telegram_tenant_directory` is an intentional non-secret catalog of enabled
tenant UUIDs only. Its SELECT RLS policy permits discovery without a tenant;
INSERT/DELETE remain tenant-scoped. It contains no token, fingerprint or chat.
Settings and directory writes share a transaction; settings updates lock the
existing row before retaining/replacing a token. No service obtains an RLS
bypass or database-owner credentials for this flow.

Notify's exact GET routes `/internal/notify/telegram/tenants` and
`/internal/notify/telegram/settings` require the configured Notify `azp`,
`hub.notify.read`, `tenant.context`, and the normal Hub JWT issuer/audience
validation. The settings route additionally requires exactly one tenant UUID
header and opens that RLS scope before reading/decrypting. Responses are
`no-store`. Use the approved private transport for the secret-bearing service response.

With `edol-notify.telegram.settings-source=hub` (the secure profile), Notify
refreshes the directory and each tenant's settings every 15 seconds by default.
Each enabled tenant has its own bot context, chat authorization and polling
offset. Incoming commands must match both that context and its chat; a shared
chat ID on distinct bots does not infer a tenant. Validated MQTT events select
the bot using their trusted tenant, while Core's printer checks remain in force.
An unconfigured tenant is skipped before downstream reads or messages. Bot
replacement/disable invalidates the old context and cancels its polling task.
Hub directory failure suspends all tenant bots; a settings failure suspends only
that tenant, with no environment fallback. Refresh latency and already in-flight
requests remain bounded operational considerations, not immediate revocation
or guaranteed-delivery acceptance.

Secure polling is EDOL-owned: it uses Telebof request/formatting objects without
`BotClient.startPolling/stop`, whose 1.4.0 lifecycle calls `System.exit(1)`.
Bot requests are serialized per context because Telebof has a mutable request
builder. Exceptions are sanitized and Telebof URL/response logging is disabled.
Historical commands are discarded on activation/replacement; activation sends
no startup/test message. Status notifications read the existing rendered JPEG from
Core's `/api/printers/{id}/camera/status-image/content` endpoint using the same
Notify service token and trusted tenant header as state reads. Core retains its
`core.media.read` and printer ownership boundary. A dedicated client preserves
those interceptors, disables redirects and bounds connect/read time to 3/5
seconds. Only HTTP 200 JPEG responses with valid start/end markers up to 8 MiB
are accepted. Empty, rejected, malformed, oversized or unavailable snapshots
fall back to the already authorized text status, without caching another
tenant's image or reading a Core-local filesystem path.
Notify stages accepted bytes in an owner-only Java temporary file for Telebof's
synchronous photo upload and removes it in `finally`; local preparation failure
also falls back to text. Telegram transport failures remain visible rather than
being silently retried as potentially duplicate messages. The binary endpoint
reuses the existing status-image renderer and metadata prerequisites without
changing the overlay. It returns 204 when no readable image is available, with
Cache-Control: no-store. It does not substitute a raw camera snapshot. No Core
volume, new authority or fresh printer capture command is introduced.
Telegram delivery has no durable retry or outbox. Chat members
can use the existing commands, so choosing a group chat grants that operational
access.

### AMS Terminal Identity and Operations

AMS uses its own `ams` schema with tenant-scoped terminal and pairing
records, forced RLS, and narrow security-definer functions for pre-tenant
pairing consumption and terminal credential lookup. AMS runtime remains a
non-owner, `NOSUPERUSER`, `NOBYPASSRLS` role. A dedicated `NOLOGIN`,
`NOINHERIT` terminal-function owner has only function-required privileges and
`BYPASSRLS`; it is not an application datasource. Hub exposes owner-authorized
terminal management on the printer edit page and calls AMS using Hub's service
identity with a server-established tenant header. The UI shows a newly issued
pairing code once with `no-store`, lifecycle metadata, and recoverable pairing
conflicts.

A terminal sends only the pairing code during enrollment. AMS locates the
pending pairing by HMAC digest and derives the bound tenant and printer from
that record; the terminal never supplies tenant or printer identity. A
successful enrollment returns one random `EDOL-Terminal terminalId.secret`
credential with `Cache-Control: no-store`, and all operational terminal calls
authenticate with that credential. Codes are eleven uppercase Latin letters, expire in five minutes, and are single-use.
Invalid attempts are rate-limited
by source without disclosing pairing state. Pairing-code HMAC verification
accepts only current and immediately previous configured key versions.
Plaintext codes and credentials are neither persisted nor logged.

The standard ESP32 image stores the terminal credential in Preferences (NVS)
and does not require Secure Boot, Flash Encryption, encrypted NVS, eFuses, or
hardware-backed storage. Its transport is explicit: HTTP is allowed only for a
trusted LAN and exposes credentials to a network interceptor; HTTPS continues
to validate its configured CA and never falls back to HTTP after a TLS failure.
Home retains its existing trusted-network routes.

Enrollment uses `POST /api/terminal/v1/enroll`. The credential authorizes
`GET /api/terminal/v1/state`, `GET /api/terminal/v1/find` and
`POST /api/terminal/v1/set-spool` for the persisted printer binding.
Hub management calls AMS under `/internal/terminals`. Revoke, rotate,
replace and factory reset retire the previous credential. The terminal returns
to enrollment on an authentication or authorization rejection. Home retains
its separate trusted-network routes.

The terminal firmware HTTP service dispatches its two request slots to one
FreeRTOS worker. DNS, clock readiness and HTTP/TLS transport run there; the
Arduino main loop retains touch, LVGL, NFC, settings/enrollment persistence and
application of completed responses. Atomic request states publish ownership
and completion; an active slot cannot be cleared or reused. The worker performs
one transport operation at a time without automatic POST retries. TLS validation
and explicit transport selection remain unchanged. A new NFC lookup clears the
previous local staging immediately, preventing assignment while lookup is pending.
The main screen renders a pending lookup before a spool becomes assignable and
readies its existing LVGL timer on staging-version changes. Repeated scans of
the same ID during its lookup are coalesced; a different pending ID is retained
for a subsequent lookup. This does not cache or bypass server ownership checks.

### Agent Integration

The opt-in secure Agent derives its tenant and printer from Core's persisted
binding. [Agent pairing](contracts/agent-pairing.md) defines HTTPS enrollment,
device and management APIs, upload guards and firmware trust. [Agent MQTT](contracts/agent-mqtt.md)
defines broker authentication, ACLs, command routing and revocation. Home
uses its own trusted-network Agent endpoints and topics.

## Persistence and Lifecycle

Core initializes enabled printer runtimes after application readiness. Hub
validates the catalog projection and attempts recovery independently per
enabled printer. Core and Hub validate their ORM schema against Flyway.
Core, Hub and AMS each own their schema and migrations. Core stores models
and snapshots on application-owned mounted paths. See [tenant ownership](contracts/tenant-ownership.md)
for isolation and [operations](operations.md) for migration constraints.

Use the [ADR index](adr/README.md) for accepted decisions and
[AI workflow](ai-workflow.md) for developer verification and MCP setup.
