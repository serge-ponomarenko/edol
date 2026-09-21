---
name: edol-commit-preparation
description: Prepare a concise EDOL commit message and review summary from the current local diff without staging, committing, or pushing.
---

# EDOL Commit Preparation

Use this skill when the user asks to prepare an EDOL commit. Follow
`AGENTS.md`; this skill never stages files, creates a commit, or pushes.

1. Read the current branch, `git status`, the focused diff and recent relevant
   commit subjects. Preserve unrelated working-tree changes.
2. Derive the ticket prefix from explicit user context or the branch. If the
   subtask number is not unambiguous, present a candidate and ask the user to
   confirm it before a commit is requested.
3. Format the subject like the nearest repository examples:
   `EDOL-043-26: [Hub] Short imperative summary`. Use one primary scope such as
   `Core`, `Hub`, `Project`, `Notify` or `AMS`; add a narrow second scope only
   when recent history establishes it.
4. For a multi-area change, add concise `- ` bullets that state externally
   meaningful changes and validation. Use real lines and paragraphs in the
   body, never literal `\\n` text. Keep one-line changes as a subject only.
5. When handing a message to an independently authorized commit step in
   PowerShell, pass body paragraphs as separate `-m` arguments or use a
   literal multiline value. Never rely on `\\n` inside a double-quoted
   PowerShell argument to create line breaks.
6. After that separately authorized commit, verify the recorded message with
   `git log -1 --format=%B` before reporting success. Amend it only with the
   user's explicit approval.
7. Report the proposed message, affected paths, relevant test command and any
   unverified runtime boundary. Commit only after the user explicitly asks for
   it and confirms the intended staged files and message.
