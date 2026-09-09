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
- Before finishing, decide whether a documented architectural fact changed. Update `docs/architecture.md` or an ADR only when it did, then check the documentation against code.
