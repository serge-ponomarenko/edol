# Home Deployment Profile

## Boundary

`docker/compose.home.yaml` is the only base home composition. It starts Core,
Hub, PostgreSQL, and NanoMQ only. It does not include Keycloak, OIDC settings,
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

## Database and migration boundary

The home Hub starts standard immutable Flyway history plus the home-only
repeatable migration location. It disables inherited Stage 3 RLS only in the
home database, validates that there are zero or one tenants and no users or
memberships, and records exactly one installation tenant in
`hub.home_installations`. Hub derives its tenant context only from this row;
there is no tenant UUID environment variable, request header, or MQTT field.
The clean home template uses one home PostgreSQL user for Hub runtime and
Flyway and overrides the secure `hub_schema_owner` Flyway initialization SQL;
it does not create or reuse Stage 3 secure database roles.

Use new dedicated home persistent paths. Never point this composition at a
secure-multi-tenant database or its volumes. Moving an existing legacy database
to home requires a reviewed backup-first preflight. Changing
`EDOL_DEPLOYMENT_MODE` alone is not a conversion and secure mode remains
blocked until its later controlled migration restores secure prerequisites.

## Operator setup

Copy `docker/.env.home.example` outside Git as `../../.env_edol_home`, replace
its placeholders, and restrict it to the operator. It contains ordinary home
database and Core configuration only; it must not contain Keycloak or OAuth
values. Select distinct, conflict-checked host ports and do not use live
printer configuration for a clean smoke check.

From the repository's `docker` directory, validate the standalone file:

```bash
docker compose --env-file ../../.env_edol_home -f compose.home.yaml config
```

Do not start the composition on a host with live devices until its operator
review confirms the Core printer configuration and MQTT network boundary.
