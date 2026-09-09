---
name: edol-schema-change
description: Safely change EDOL JPA persistence or Flyway migrations while preserving schema contracts and data-migration evidence.
---

# EDOL Schema Change Workflow

Use this skill when modifying JPA entities, repositories, relationship mappings, or Flyway migrations in Core or Hub.

1. Identify the owning module and schema (`core` or `hub`), relevant entities, repositories, services, and migration history before editing.
2. Treat committed or deployed Flyway migrations as immutable. Do not infer whether an uncommitted migration has been applied; inspect available evidence or ask when that distinction changes the safe action.
3. Check that migration SQL, JPA mapping, constraints, IDs, and data-access code remain consistent. Account for existing data and startup validation.
4. Run the affected module tests. If no isolated PostgreSQL migration check is available, report that limitation rather than claiming database compatibility.
5. Update `docs/architecture.md` only when ownership, persistence boundaries, or another documented architectural fact changes. Record an ADR only for an explicitly accepted architecture decision.
