# AI-Assisted Development

`AGENTS.md` contains persistent repository rules. Project-local Skills in `.agents/skills` provide EDOL-specific workflows. This document records planned integrations; it is not an active instruction source.

## Current Skills

- `edol-change` for non-trivial EDOL changes and documentation-impact review.
- `edol-runtime-contract` for Core runtime, MQTT, or cross-service printer-state contracts.
- `edol-schema-change` for JPA and Flyway changes.

## MCP Status

No MCP server is configured.

- Prefer Git CLI for local history and diffs. The repository remote is Gitea, so a GitHub integration is not a default fit.
- Consider a read-only Gitea integration only when issue or pull-request context is regularly required.
- Consider read-only, tenant-scoped PostgreSQL or runtime diagnostics only after a concrete recurring diagnostic need. Such tooling must not expose DDL/DML, MQTT publishing, printer commands, or production deployment actions.
- Keep versioned project documentation as the primary engineering context unless an external documentation system is explicitly adopted as authoritative.

## Subagent Status

No persistent subagent configuration is configured. Delegate only when independence adds value:

- **Architecture scout:** read-only tracing of cross-module contracts before a risky change; does not edit.
- **Implementer:** owns one bounded change; does not overwrite concurrent work.
- **Verifier/reviewer:** independently inspects the diff and relevant contracts or runs checks; does not expand scope.
- **Documentation reviewer:** checks documented architectural facts against code only after an architectural change.
