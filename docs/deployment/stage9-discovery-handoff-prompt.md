# Next-session Prompt: Finish Stage 8 Gate, Then Stage 9 Discovery

Work in EDOL branch EDOL-048 and firmware repository main. Communicate in
Ukrainian; code and documentation in English. Read AGENTS.md and applicable
skills, inspect branch/status and preserve unrelated work.

## Baseline

- EDOL implementation/security tests: 73969225f0e5872c42b92bcd946374518b7d10f4.
- Firmware: fddb88107670e5d9cbdda155777a9bcc3d99893d.
- Discover the latest documentation commit with git log; do not assume HEAD is
  still either baseline or that a firmware commit was pushed.
- Read docs/deployment/stage8-terminal-acceptance-record.md,
  docs/deployment/stage8-terminal-enrollment-smoke-runbook.md,
  docs/architecture.md, docs/ai-workflow.md,
  docs/adr/0002-secure-multi-tenant-architecture.md and
  docs/migrations/secure-multi-tenant-migration-plan.md (Stages 8 and 9).

## First: complete the sole Stage 8 security evidence gap

Physical functional smoke and SEC-01/02/03/04/05/07 have recorded PASS evidence.
Reuse it. SEC-06 remains blocked: the earlier TCP/Loading evidence did not prove
certificate rejection or measured absence of HTTP downgrade. Do not call Stage 8
accepted until its record contains adequate execution evidence.

Use the minimal on-device production-TLS procedure in the acceptance record.
Identify the current COM port; COM16 was absent on 2026-10-21. Preserve the
existing credential, Wi-Fi and enrollment. Do not flash without operator
authorization. Finish and commit factual Stage 8 documentation if the gate
passes; report the precise blocker otherwise. Do not repeat ordinary physical
enrollment/NFC/lifecycle testing or rebuild disposable infrastructure.

## Then: Stage 9 discovery and reviewable plan only

Inventory remaining legacy HTTP/Basic/shared-credential ingress, MQTT v2/legacy
fields and consumers, agent/media identity, broker ACLs and transport, service
credential rotation, database transport, secret-bearing DTOs and upload limits.
Trace actual code/config/tests; distinguish Home behavior from secure mode.

Produce a module/route/topic inventory, prerequisites, observed versus untested
traffic metrics, smallest ordered changes, versioning/rollout compatibility,
targeted security checks and rollback requirements. Identify existing
counter/observation-window evidence before proposing compatibility removal.

Review the existing Stage 9 enterprise-oriented requirements against the chosen
practical deployment model. Explain necessity and simpler viable options for
per-client MQTT TLS, private_key_jwt and database TLS; do not silently impose or
remove them. Login/registration UX is a separate product task.

Stop after presenting the plan and acceptance criteria. Request approval before
Stage 9 implementation, migration or deployment. No production/accepted Stage 7
changes, broker publish, real printer command, data deletion, eFuse operation,
new infrastructure or push is authorized by this handoff. A source/disposable
acceptance is not a production rollout. Never expose codes, credentials or
Authorization headers. Keep operator involvement to one step at a time.
