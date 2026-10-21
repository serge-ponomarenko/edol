# Next-session Prompt: Stage 9 Discovery and Reviewable Plan

Work in EDOL branch EDOL-048 and firmware repository main. Communicate in
Ukrainian; code and documentation in English. Read AGENTS.md and applicable
skills, inspect branch/status and preserve unrelated work.

## Baseline

- EDOL implementation/security tests: 73969225f0e5872c42b92bcd946374518b7d10f4.
- Final firmware: 0749cecdf9120dd5f120804bead5cdd3370739a7, including the CA
  newline fix and hardware-only SEC-06 regression. Commit is local; no push
  is established by the handoff.
- Discover the latest documentation commit with git log; do not assume HEAD is
  still either baseline or that a firmware commit was pushed.
- Read docs/deployment/stage8-terminal-acceptance-record.md,
  docs/deployment/stage8-terminal-enrollment-smoke-runbook.md,
  docs/architecture.md, docs/ai-workflow.md,
  docs/adr/0002-secure-multi-tenant-architecture.md and
  docs/migrations/secure-multi-tenant-migration-plan.md (Stages 8 and 9).

## Stage 8 is accepted; reuse its evidence

Physical functional smoke and SEC-01 through SEC-07 have recorded PASS evidence.
The 2026-10-21 real ESP32 test used production HttpService/WiFiClientSecure:
trusted HTTPS 200 before/after three denials, explicit -9984 verification error,
zero plaintext arrivals on TLS ports and explicit HTTP 200. The previously
malformed CA hook is fixed using a build-local header. No source-inspection-only
or native mocked TLS acceptance is being asserted.

Normal firmware was restored on confirmed COM16 without erasing settings or
enrollment. The existing AMS port 18088 was closed at restoration; a fresh
live state recovery was not observed that day. This is a runtime availability
limit, not evidence of credential rejection. Start existing disposable services
only when needed and authorized; do not recreate infrastructure. Do not repeat
enrollment/NFC/lifecycle testing or flash hardware for Stage 9 discovery.

## Stage 9 discovery and reviewable plan only

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
