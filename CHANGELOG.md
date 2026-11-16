# Changelog

Notable changes to EDOL are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Entries describe meaningful changes rather than individual commits.

## [Unreleased]

### Fixed

- Send CSRF tokens with allocation edits and finalization in secure Hub, and report rejected requests instead of silently leaving allocation unchanged.
- Keep DIRECT printer MQTT session state in memory so non-root Core does not require a writable application directory; report connection failure stages and reason codes.
- Run Core with the deployment operator's UID/GID so private models and metadata remain readable by instance backups.
- Reject secure Dev/production deployments when the configured trusted proxy differs from the running nginx endpoint, before stopping services.
- Align Agent upload proxy timeouts in Dev and production for model transfers lasting up to fifteen minutes.
- Isolate release preparation tests from the repository's current Unreleased entries.
- Identify failed production backup artifacts and command exit codes without exposing protected diagnostics; failed backups continue to block migrations.
- Retain production backup stderr in private operator-side files without exposing it in CI logs.

## [1.0.0] - 2026-11-15

Initial versioned baseline; publication is performed by the matching Git tag workflow.
The historical milestones below describe the functionality included in this baseline.

### Added

- Establish this changelog from the repository history.
- Publish release images with version and full commit SHA tags, OCI identity labels
  and image digests in Gitea release notes after reactor checks and the Quality Gate.

### Changed

- Unify all Maven modules under one root revision, centralize shared dependency
  versions and remove redundant base starter declarations.
- Prepare releases through one command; successful main CI creates version tags
  and publishes images and release notes automatically for new versions.

## Historical baseline (unversioned)

The following groups summarize history from the initial commit `ad7e086` through
`a7cf576`. They are development milestones, not published releases or evidence of
production acceptance. No release tags existed in the local checkout when this
changelog was created. Dates below describe development periods, not release dates;
parallel development can overlap.

### Secure operations and deployment preparation (2026-11-11 to 2026-11-15)

#### Added

- Tenant-managed Telegram notification settings and authorized status images.
- Branded Keycloak login and self-service registration.
- Clean manual development deployments and production cutover procedures.

#### Changed

- Organize printer and workspace settings in Hub.
- Consolidate maintained architecture, contracts and operational documentation.
- Apply SMTP settings during secure development deployments and prepare external MQTT TLS.

#### Fixed

- Restore AMS access to Core state and correct secure AMS readiness timeouts.
- Restore Sonar pull-request reporting and resolve code quality findings.
- Replace deprecated Hub transaction interceptor configuration.

Evidence: `869df1e`, `618de48`, `9c3b3db`, `e5ee970`, `96b555a`,
`b3cc6a3`, `5de8d73`, `33867ab`, `71f43df`, `3d655dc`, `0b71fe4`,
`c4079b6`, `5f9d425`, `a7cf576`.

### Secure Agent and terminal lifecycle (2026-10-10 to 2026-10-27)

#### Added

- AMS terminal enrollment, management, replacement and pairing workflows.
- Secure Agent pairing and MQTT integration, with bounded upload validation.
- Terminal security regression coverage and hardware acceptance workflows.

#### Fixed

- Enforce printer HTTP ownership and hide printer access codes.
- Restore tenant context for background printer tasks and connection recovery.
- Restore Agent camera workflows, print metadata and revoke handling.
- Bound model retries and repair dashboard commands.
- Publish Hub runtime state after transaction commit.

Evidence: `4931440`, `f6cf3c6`, `fd0c4bd`, `972e23b`, `7396922`,
`d701243`, `04ed4cc`, `9d499e8`, `477136b`, `52134d0`, `f467c40`,
`9f40005`, `9d42b1c`, `185ed93`, `d52fdd0`, `484b442`.

### Secure multi-tenant foundation (2026-09-22 to 2026-10-10)

#### Added

- Tenant foundation, persistence isolation and authenticated service tenancy.
- Keycloak infrastructure and Hub backend-for-frontend tenancy.
- Tenant-aware MQTT envelopes and service migration across Core, Hub, Notify and AMS.
- Isolated local, remote Docker and disposable secure development verification workflows.

#### Fixed

- Secure service bootstrap, authentication and serialization integration gaps.
- Keep printer statistics reads free of side effects and store MQTT receipt times in UTC.

Evidence: `9ce143d`, `097d67b`, `33acc38`, `6226971`, `703dd21`,
`d1a8c3d`, `aa97e92`, `4e88bc3`, `fca5de3`, `1db5017`, `9d9963b`,
`7e5f66a`, `e9d842c`, `12bd3d6`.

### Multi-printer runtime and PostgreSQL migration (2026-06-08 to 2026-09-22)

#### Added

- Agent communication mode and active print recovery.
- Per-printer runtime contexts, runtime lifecycle and Printer Management API v2.
- PostgreSQL schemas and initial Flyway migrations.
- Multi-printer Hub and service integration, operational overview and printer speed controls.

#### Fixed

- Improve metadata retries, Agent connectivity and upload resilience.
- Recover allocation previews after restart and improve print completion handling.

Evidence: `88c9d77`, `ce54605`, `a66597a`, `bbe259c`, `06a399c`,
`2857075`, `7500374`, `ad87f88`, `53d83aa`, `3b4334a`, `0175158`,
`99ef7b5`, `27a45fa`, `19c4e6e`, `e91f59c`, `fb19a66`.

### Initial application and filament accounting (2026-05-10 to 2026-06-05)

#### Added

- Initial application, Docker Compose deployment and AMS service.
- Spool usage, allocation, replacement and purchase date tracking.
- Filament defaults, drying recommendations and configurable print job pagination.
- Deployment database backups, SonarQube quality gates and Hub test coverage.

#### Changed

- Rename Dashboard to Hub and migrate spool accounting to decimal grams.
- Improve allocation, filament search and dashboard presentation.

#### Fixed

- Correct filament deletion, small spool usage and notification image handling.

Evidence: `ad7e086`, `be5b4f3`, `a35cd42`, `ff12f00`, `703eff4`,
`794250a`, `035032b`, `95435ba`, `f686514`, `3738267`, `8760397`,
`aedac6e`, `4e3b0ab`, `40b4c51`, `a7632ac`, `f9a1391`.
