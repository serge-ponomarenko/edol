# EDOL Working Rules

## Communication

- Reply to users in Ukrainian. Write code, documentation, and code comments in English.
- When the conversation context appears close to its limit and the user requests work that clearly requires a long execution, suggest moving to a new chat before starting if doing so would be beneficial.

## Source of Truth

- Treat the current request, this file, applicable Skills, configuration, tests, and implementation as separate inputs. Resolve conflicts explicitly; do not silently choose documentation over code.
- Before non-trivial changes, state a short plan and verifiable success criteria. Surface material assumptions, ambiguity, and simpler viable options.

## Knowledge Ownership

- Git is canonical for current architecture, accepted normative ADRs, API/MQTT/tenant/security contracts, code, tests, immutable Flyway, and supported executable deployment or verification procedures.
- The scoped Home MCP Obsidian project `edol` is canonical for development backlog, temporary plans, operator evidence, dated smoke results, checkpoints and handoffs. Use `.agents/skills/edol-knowledge-management/SKILL.md` for current paths and safe read/write behavior; verify MCP authentication and user authorization separately.
- Do not move a repeatable safety procedure solely into Obsidian. Do not treat a historical note as authorization for live printer, firmware, Docker, database, identity or deployment operations.
- `docs/ai-workflow.md` covers MCP, subagent setup and access to project knowledge. README is frozen until the multi-tenant migration is complete.

## Change Safety

- Check the current branch and `git status` before editing. Preserve pre-existing changes and never discard, overwrite, or reformat them.
- Make the smallest complete change. Do not refactor, clean up, or alter adjacent behavior outside the request.
- Request confirmation before destructive or irreversible actions, production changes, history rewrites, force pushes, or deleting data/files.
- Do not expose, copy, or commit credentials. Treat environment files, printer commands, MQTT publish paths, database migrations, and deployment configuration as high-risk.
- For local test PostgreSQL inspection, discover an existing profile through the `edol-postgres` MCP before connecting; use only a local profile in read-only mode and read `docs/ai-workflow.md` for the user-local profile location and safety constraints. Never add, modify, or commit connection credentials while diagnosing access.

## Verification and Documentation

- Verify every change with the most relevant available checks; report any verification limitation.
- Run `mvn -B clean verify` for a full reactor check when appropriate. For Hub-only changes, start with `mvn -pl edol-hub -am test`.
- Before handoff, analyze every generated or modified supported source file with the read-only SonarQube MCP once the implementation is stable. Fix actionable findings; do not suppress findings merely to pass verification. Report and explain any false positive, MCP-analysis artifact, or finding explicitly deferred by the user. Respect requests limited to reporting or explicitly excluding fixes.
- Before finishing, decide whether a documented architectural fact changed. Update `docs/architecture.md` or an ADR only when it did, then check the documentation against code.

## Versioning and Releases

- EDOL server modules share the root `pom.xml` revision property. Child parents use `${revision}`; module-specific versions are not supported. Keep shared dependency versions in root dependencyManagement and declare each module's actual dependencies locally.
- Before a meaningful change is committed, the main agent maintains concise user-facing entries under `CHANGELOG.md` Unreleased (Added, Changed, Fixed, Security, Deprecated, Removed as relevant). Group related work; do not copy every commit subject. Documentation-only or test-only changes may need no entry; explain that decision in the handoff. Never edit a published release section.
- A normal commit request authorizes one local commit, not a version bump, release, merge, tag or push. The commit agent verifies changelog readiness and returns missing substantive entries to the main agent; it does not invent release notes.
- For an explicit release preparation request, choose or confirm the SemVer version from the intended compatibility impact, then run `py -B .gitea/scripts/prepare_release.py X.Y.Z`. This prepares only root revision and a dated changelog section; review and verify the diff before the separately authorized commit/merge. For the initial prepared 1.0.0, do not prepare a second section.
- After an authorized merge/push to main, passing CI creates an annotated tag and publishes Docker images plus a Gitea Release only when the root version has no existing tag. Ordinary main commits at an already published version do not publish another release; an existing tag with a missing Release or draft fails for operator inspection. Do not manually tag the feature branch or create a Release first. Follow docs/operations.md for publication failures; never move a release tag or blindly rerun a partial publication.

## Deferred Work

- Do not leave source-code TODO comments for deferred work. Maintain postponed cross-cutting work in the authorized Home MCP Obsidian EDOL backlog (`projects/edol/work/EDOL-048/backlog.md`), recording intended outcomes and safety constraints. Follow `edol-knowledge-management`; if Obsidian is unavailable, report the missing write and give a concise in-conversation handoff, never an ad hoc Git TODO.

## Workflow Improvement

- Notice recurring friction during development. When the same non-trivial procedure, lookup, or external-access workaround appears repeatedly, recommend a reusable project-level improvement instead of continuing to solve it ad hoc.
- Recommend a Skill when a repeatable multi-step workflow, project-specific procedure, or decision process would benefit from explicit reusable instructions.
- Recommend documentation when project knowledge, commands, conventions, troubleshooting steps, or architectural facts are repeatedly rediscovered or inferred from code.
- Recommend an MCP server or MCP integration when work repeatedly requires structured access to an external system such as a database, API, service, runtime, or infrastructure environment.
- Prefer the smallest appropriate mechanism. Do not propose automation for trivial, rare, or already well-documented tasks.
- Do not create Skills, documentation, MCP integrations, or other workflow infrastructure unless requested. Briefly explain the recurring friction observed and the expected benefit.

## Low-Cost Subagents

- Delegate only a clearly matching, self-contained operation to one project role; the main agent retains design, safety, and integration decisions. Do not create nested agent workflows.
- After a completed coherent stage, route every explicit request to commit changes (including Ukrainian equivalents such as `закоміть це` or `закомітай`) to `commit-agent`. Treat that intent as authorization for the agent to identify, stage, and create one local commit for the completed stage; route a request to run or select verification to `test-agent`; route a request for current local PostgreSQL schema, constraint, data, or Flyway evidence to `db-inspect-agent`.
- Do not delegate architecture, security, cross-service contracts, migration design, production/deployment work, source implementation, or an ambiguous task solely to reduce model use. Read `docs/codex-subagents.md` for role contracts and the periodic delegation review rule.
