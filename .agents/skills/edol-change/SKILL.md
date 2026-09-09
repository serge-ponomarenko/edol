---
name: edol-change
description: Plan and implement a non-trivial EDOL change while tracing module impact, choosing targeted checks, and maintaining factual architecture documentation.
---

# EDOL Change Workflow

Use this skill for non-trivial changes that cross a module, API, persistence boundary, or documented flow. Follow the repository rules in `AGENTS.md`.

1. Read `docs/architecture.md`, then trace the request through the relevant production code, configuration, and tests. Treat it as a map, not a substitute for source verification.
2. Identify affected module boundaries and choose the narrowest complete implementation and validation scope.
3. If the change alters a responsibility, dependency boundary, flow, lifecycle, or integration contract described in `docs/architecture.md`, update that document and verify it against the final code. Otherwise leave documentation unchanged.
4. Use `edol-runtime-contract` for printer runtime, telemetry, MQTT, or cross-service state contracts. Use `edol-schema-change` for Flyway or persistent-domain changes.

Do not use this skill for a simple isolated edit with an obvious local check.
