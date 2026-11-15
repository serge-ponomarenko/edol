# Changelog

## Unreleased

### Fixed

- Identify failed production backup artifacts and command exit codes without exposing protected diagnostics; failed backups continue to block migrations.
- Retain production backup stderr in private operator-side files without exposing it in CI logs.
