# EDOL Low-Cost Codex Subagents

## Purpose and Native Configuration

EDOL uses three project-scoped Codex custom agents for routine work that is
well-bounded and produces evidence for the main agent. Their configuration is
in `.codex/agents`; `.codex/config.toml` enables one child thread at a time.
The main agent remains responsible for design, architecture, security,
cross-service contracts, migration design, implementation, and final
integration.

The current client supports custom role files with a model, reasoning effort,
sandbox mode, MCP configuration, and developer instructions. Role descriptions
are the native semantic routing guidance; they are not exact-string triggers.
The routing reminder in `AGENTS.md` supplies the relevant user intents to the
main agent.

## Roles

| Role | Model and effort | Scope and routing | Tools and skills | Escalate when |
| --- | --- | --- | --- | --- |
| `commit-agent` | `gpt-5.6-luna`, low | After a completed coherent stage, every explicit request to commit, including `закоміть це`, authorizes it to identify the stage, stage only its files, create one local commit, and report the hash and residue. | Git CLI; `edol-commit-preparation`. | The completed-stage scope is ambiguous; unrelated changes cannot be separated; a reset, stash, amend, push, or design decision would be needed. |
| `test-agent` | `gpt-5.6-luna`, medium | Request to run, select, or report targeted verification. It chooses the smallest meaningful check and classifies failure evidence. | Maven; `edol-remote-docker-verification`; `edol-local-runtime-verification` only when a smoke check is explicit. | A failure needs substantial debugging or code change; a migration, security, cross-module, or external-state decision is involved. |
| `db-inspect-agent` | `gpt-5.6-luna`, low | Need for current local PostgreSQL schema, constraint, data-shape, index, or Flyway evidence during analysis. It returns facts only. | Existing `edol-postgres` MCP, registered only in this role; `docs/ai-workflow.md`. | No local read-only profile exists; evidence conflicts with assumptions; the request needs migration design, DDL/DML, remediation, security, or deployment work. |

`gpt-5.6-luna` is selected from the locally available model catalog because it
is the fast, efficient available model and these agents have narrow output
contracts. Test selection and failure classification use medium reasoning;
commit and read-only evidence gathering use low reasoning. The roles do not
spawn child agents.

## Safety and Permission Boundaries

The commit and test roles use `workspace-write`: Git index/commit metadata and
Maven build artifacts require writes. Their developer instructions prohibit
implementation and unrelated writes. The database role is configured
`read-only` and its MCP instructions require a local profile with
`access_mode: ro`.

Codex custom-agent sandbox settings are defaults. A parent session's live
sandbox/approval override is inherited by a child and can take precedence, and
the current Codex role format has no per-role allowlist for individual Git,
Maven, or MCP methods. The written role contracts therefore remain a required
second boundary. The DB MCP registration mirrors the existing `.ai/mcp/mcp.json`
server command only; it contains no database profile, password, or production
access configuration.

## Choosing the Right Abstraction

Use an agent only when it isolates a repeated bounded task and returns
verifiable evidence. Keep a deterministic operation as a script, reusable
procedure as a skill, stable project knowledge as documentation, and external
system access as an MCP integration. Current examples are:

- The remote Docker tunnel and local runtime smoke check remain scripts inside
  their verification skills because lifecycle and cleanup are deterministic.
- SonarQube remains a read-only MCP integration; it does not need a separate
  agent.
- Repository searches remain with the main agent because spawning a child costs
  more than a focused `rg` command.
- Schema, migration, runtime-contract, and architecture work remain with the
  main agent and their existing skills.

When repeated work is observed over time, evaluate in this order whether it
should become a script, a skill, documentation, an MCP integration, or a
specialized low-cost agent. Do not choose an agent automatically.
