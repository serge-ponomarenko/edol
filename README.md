# EDOL

EDOL is a self-hosted system for Bambu Lab printer monitoring, print history,
filament inventory, spool allocation, maintenance, camera/model media and
Telegram notifications. Core connects directly to a reachable printer or
through an EDOL Agent. AMS terminals support printer-scoped spool workflows.

![Demo](edol_demo.gif)

## Features

- Live printer status, print progress and camera snapshots.
- Print history with model previews, filament allocation and spool usage.
- Filament inventory organized by vendor, material and color, with remaining
  weight, purchase details and consumption costs.
- Multi-spool allocation for prints and printer-scoped AMS terminal workflows.
- Maintenance schedules based on printing hours or dates, with service history.
- Tenant-managed Telegram notifications and status images.
- Direct LAN printer connections or remote connections through an EDOL Agent.
- Home and Secure deployment modes, REST APIs and MQTT service events.

Use EDOL for a home workshop or a multi-printer installation where print history,
material accounting and maintenance need to stay together.

## Printer compatibility

EDOL integrates with Bambu Lab's local MQTT, FTPS and camera interfaces. It needs
the printer's LAN access code and network reachability from Core or an Agent.
It does not bypass Bambu Lab authorization or use Bambu Connect as a transport.

| Printer family | Models available in EDOL configuration | Verification scope |
| --- | --- | --- |
| A1 | A1, A1 Mini | Configurable; model/firmware-specific hardware acceptance required |
| P1 | P1P, P1S | P1S has owner-observed DIRECT and Agent operation; this does not validate every P1 firmware |
| X1 | X1, X1 Carbon | Configurable; model/firmware-specific hardware acceptance required |
| H2 | H2D | Configuration entry exists; full hardware compatibility is not established here |
| P2 | No dedicated model entry | Compatibility not established |

Camera support depends on the printer: Core implements both the legacy camera
protocol and RTSPS. Select the appropriate camera provider when adding a printer;
a model entry alone does not verify its camera, file access or command behavior.

### Firmware and Authorization Control

On firmware with Bambu Lab Authorization Control, use **LAN Only Mode with
Developer Mode enabled**, where supported, for access to the local interfaces
EDOL requires. Standard LAN mode and Cloud Connected mode can restrict third-party
access. See Bambu Lab's [third-party integration and Developer Mode explanation](https://blog.bambulab.com/updates-and-third-party-integration-with-bambu-connect/).
Check the settings available on your exact printer and firmware before deployment.

For existing installations, the previous README recorded these pre-Authorization
firmware reference points for Cloud Connected operation:

| Printer family | Historical firmware reference |
| --- | --- |
| A1 | `01.04.00.00` |
| P1 | `01.08.01.00` |
| X1 | `01.08.02.00` |
| H2 / P2 | No pre-Authorization version listed |

These values are retained as historical compatibility information, not a
maintained list of the latest supported firmware or a recommendation to downgrade.
They have not all been independently tested with the current EDOL release.
Newer firmware may work through LAN Only / Developer Mode; verify monitoring,
model download, camera and the operations you intend to use separately.

### Printer connection details

Add and edit printers in Hub. Keep the LAN access code in protected printer
settings; do not put it in Git, screenshots or shared logs.

| Setting | Meaning |
| --- | --- |
| Serial number | Printer identity used by the Bambu integration |
| Connection mode | `DIRECT` for Core-to-printer access; `AGENT` for an enrolled EDOL Agent |
| MQTT host / port | Printer LAN address and MQTT TLS port, normally `8883` |
| FTP host / port | Printer LAN address and implicit FTPS port `990` |
| Model directory | Printer-side folder containing print models; commonly root (empty) or `cache`, depending on model/firmware |
| LAN access code | Code from the printer's network settings |
| Camera provider | Legacy camera or RTSPS, as supported by the printer |

To inspect the printer's model directory with an FTPS client, connect to
`ftps://<printer-ip>:990` using **implicit TLS**, username `bblp` and the printer's
LAN access code as the password. List the folders to determine the model directory;
do not assume every model uses the same layout. This is the printer's FTPS
endpoint, separate from EDOL's own HTTPS and MQTT services.

## Current architecture

The Java 21 / Spring Boot 4 Maven reactor contains:

| Module | Responsibility |
| --- | --- |
| `edol-core-api` | Shared runtime and AMS DTOs |
| `edol-core` | Printer connections, telemetry, commands, agents and media |
| `edol-hub` | Web dashboard, users, tenants, inventory and print history |
| `edol-notify` | Tenant Telegram integration |
| `edol-ams` | AMS workflows and terminal enrollment |

PostgreSQL 17 stores application and identity data. Mosquitto handles MQTT.
Secure installations also run Keycloak with the bundled EDOL login theme.
See [architecture](docs/architecture.md) and [tenant ownership](docs/contracts/tenant-ownership.md).

## Choose an installation mode

| Mode | Intended use | Identity and access |
| --- | --- | --- |
| `home` | One owner on a trusted private network | Installation tenant; no OIDC registration; keep every endpoint private |
| `secure` | HTTPS access with multiple isolated tenants | Keycloak OIDC, verified email, OWNER membership, service identities and forced database RLS |

Secure Dev functional testing has been completed by the owner. The owner has
also deployed Secure production and restored legacy business data under the
new tenant/printer identities; history, inventory and maintenance are visible
in the dashboard. Production is under observation. A persistent DNS solution
scoped to EDOL remains unresolved after the host-wide DNS workaround was
reverted. Running containers do not establish restart/redeployment readiness
or full Internet-facing and physical-device acceptance.

## Fresh installation

Use a Linux Docker host with Docker Compose v2 (`up --wait` support), Python
3.11+, OpenSSL, Git and tar. Source builds use the supplied Dockerfiles;
a host Java/Maven installation is needed only for local development.

Clone this repository into a source directory separate from persistent state.
For example, with an accessible repository URL:

```bash
git clone https://github.com/serge-ponomarenko/edol.git ~/edol-source
cd ~/edol-source
sudo install -d -m 0700 -o "$(id -un)" -g "$(id -gn)" /srv/edol
install -d -m 0700 /srv/edol/releases /srv/edol/home
python3 docker/deploy/init_config.py --environment prod --mode home \
  --output /srv/edol/home/instance.json
```

Review the generated private address, ports and subnet in a protected editor.
Keep the generated passwords. Then deploy Home from the reviewed checkout:

```bash
revision="$(git rev-parse HEAD)"
python3 docker/deploy/dev_deploy.py deploy-prod --mode home \
  --config /srv/edol/home/instance.json --revision "$revision" \
  --source-generation "$(git rev-list --count HEAD)"
```

Open the private `hub_origin` from the configuration and add a printer. Home
requires a trusted network; do not publish its ports to the Internet.

For **Secure production**, follow the complete
[production installation and cutover runbook](docs/operations.md#production-installation-and-cutover):
create independent secrets and MQTT trust, configure public HTTPS and SMTP,
then render and deploy. All example domains, addresses and ports are operator
inputs; third-party installations must replace them. Gitea is optional for a
manual installation.

The first deployment provisions empty databases and immutable migrations. It
creates no application account, printer, filament, spool or print history.
Secure users register, verify their email and receive a personal tenant with
OWNER access on first login. Add printers through Hub; pairing and enabling
connections are explicit steps. Configure Telegram in the tenant's settings.

Later deployments preserve databases, device bindings, broker authority,
installation keys and media. Protect the entire instance configuration,
certificate/key directories, state, retained source releases and PostgreSQL
volume. Never use `docker compose down -v` as an update procedure.

## Updates and CI

Gitea PRs targeting `main` run deployment/realm checks, the full Maven reactor
(including Docker-dependent tests) and SonarQube Quality Gate. A push to `main`
repeats those checks for the resulting commit; only a successful `build` job
permits automatic `prod-deploy`. Protect `main` against direct pushes and
require the CI status. Production SSH secrets are separate from Dev secrets.

Dev deployment remains manual (`dev-secure-deploy` / `dev-home-deploy`).
Production changes use the independent `/srv/edol` instance. See
[operations](docs/operations.md) for server preparation, secrets, ingress,
backups, readiness, failure recovery and legacy-data migration gates.

Upgrading a legacy single-tenant installation requires a coordinated backup,
an isolated restore rehearsal and an explicit mapping to the new tenant and
printer. Do not restore an old full database over a registered Secure instance
or run the current migrations blindly against the old production volume.

## Development

Use JDK 21, Maven and an available Docker daemon for migration/RLS tests:

```bash
mvn -B clean verify
python3 -B -m unittest discover -s docker/deploy/tests
node docker/keycloak/realm/validate-realm-schema.mjs
```

See [printer compatibility](#printer-compatibility) before connecting hardware.
Register connection details in Hub; old single-printer `BAMBU_*` environment
setup and NanoMQ Compose instructions are retired.

## Documentation

- [Operations](docs/operations.md): installation, ingress, updates, backups and migration.
- [Architecture](docs/architecture.md): services, storage and runtime flows.
- [Tenant ownership](docs/contracts/tenant-ownership.md): tenant boundaries and database isolation.
- [Agent pairing](docs/contracts/agent-pairing.md): Agent enrollment and lifecycle.
- [Agent MQTT](docs/contracts/agent-mqtt.md): broker identity and messaging contracts.

## Useful links

- [OpenBambuAPI](https://github.com/Doridian/OpenBambuAPI) — community documentation of Bambu interfaces and protocols.
- [bambu-printer-manager](https://synman.github.io/bambu-printer-manager/) — related printer-management tooling and API documentation.
- [SpoolEase](https://www.spoolease.io/index.html) — NFC filament-management project and hardware information.
- [SpoolEase source](https://github.com/yanshay/spoolease) — source code and build information.
- [Bambu Lab third-party integration](https://blog.bambulab.com/updates-and-third-party-integration-with-bambu-connect/) — vendor guidance on Authorization Control and Developer Mode.

These are reference and related-project links, not runtime dependencies of EDOL.
