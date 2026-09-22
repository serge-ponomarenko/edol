# EDOL Working Rules

## Communication

- Reply to users in Ukrainian. Write code, documentation, and code comments in English.

## Source of Truth

- Treat the current request, this file, applicable Skills, configuration, tests, and implementation as separate inputs. Resolve conflicts explicitly; do not silently choose documentation over code.
- Before non-trivial changes, state a short plan and verifiable success criteria. Surface material assumptions, ambiguity, and simpler viable options.

## Change Safety

- Check the current branch and `git status` before editing. Preserve pre-existing changes and never discard, overwrite, or reformat them.
- Make the smallest complete change. Do not refactor, clean up, or alter adjacent behavior outside the request.
- Request confirmation before destructive or irreversible actions, production changes, history rewrites, force pushes, or deleting data/files.
- Do not expose, copy, or commit credentials. Treat environment files, printer commands, MQTT publish paths, database migrations, and deployment configuration as high-risk.

## Verification and Documentation

- Verify every change with the most relevant available checks; report any verification limitation.
- Run `mvn -B clean verify` for a full reactor check when appropriate. For Hub-only changes, start with `mvn -pl edol-hub -am test`.
- Before handoff, analyze every generated or modified supported source file with the read-only SonarQube MCP once the implementation is stable. Fix actionable findings; do not suppress findings merely to pass verification. Report and explain any false positive, MCP-analysis artifact, or finding explicitly deferred by the user. Respect requests limited to reporting or explicitly excluding fixes.
- Before finishing, decide whether a documented architectural fact changed. Update `docs/architecture.md` or an ADR only when it did, then check the documentation against code.

## Deferred Work

- Do not leave source-code TODO comments for deferred work. Record postponed cross-cutting work in `docs/ai-workflow.md` under `Project TODO`, including the intended outcome and relevant safety constraints.

## Workflow Improvement

- Notice recurring friction during development. When the same non-trivial procedure, lookup, or external-access workaround appears repeatedly, recommend a reusable project-level improvement instead of continuing to solve it ad hoc.
- Recommend a Skill when a repeatable multi-step workflow, project-specific procedure, or decision process would benefit from explicit reusable instructions.
- Recommend documentation when project knowledge, commands, conventions, troubleshooting steps, or architectural facts are repeatedly rediscovered or inferred from code.
- Recommend an MCP server or MCP integration when work repeatedly requires structured access to an external system such as a database, API, service, runtime, or infrastructure environment.
- Prefer the smallest appropriate mechanism. Do not propose automation for trivial, rare, or already well-documented tasks.
- Do not create Skills, documentation, MCP integrations, or other workflow infrastructure unless requested. Briefly explain the recurring friction observed and the expected benefit.
