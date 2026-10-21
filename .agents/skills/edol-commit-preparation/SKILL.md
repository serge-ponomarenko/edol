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
   body, never literal `\n` text as a line separator. Keep one-line changes as
   a subject only; preserve intentional code/path literals.
5. When handing a message to an independently authorized commit step in
   PowerShell, write the complete message to a temporary UTF-8 file outside
   the working tree and use `git commit -F <message-file>`. Use a literal
   here-string with real line breaks as shown below. PowerShell does not
   interpret `\n` or `\r\n` as newlines; do not serialize the message with
   `JSON.stringify()` into a shell command.
   Read the file back before committing and check its subject, blank line,
   and bullets. Fix accidental escaped separators before creating a commit.
6. After that separately authorized commit, verify the recorded message with
   `git log -1 --format=%B` and compare it with the prepared message, allowing
   only CRLF/LF and trailing-newline differences. If formatting differs,
   report the hash and mismatch without claiming success, retrying, or
   amending. Amend it only with the user's explicit approval.
7. Report the proposed message, affected paths, relevant test command and any
   unverified runtime boundary. Commit only after the user explicitly asks for
   it and confirms the intended staged files and message.

## PowerShell Message Handoff

This example prepares a message only; the commit executor separately checks
scope and authorization before running `git commit -F $messagePath`.

```powershell
$message = @'
EDOL-048-61: [Project] Preserve commit message line breaks

- Write commit messages through a UTF-8 file.
- Verify the recorded body against the prepared message.
'@
$messagePath = [System.IO.Path]::GetTempFileName()
$utf8 = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllText($messagePath, $message, $utf8)
$prepared = [System.IO.File]::ReadAllText($messagePath, $utf8)
if ($prepared -cne $message) {
    throw 'Commit message changed while writing the UTF-8 file.'
}
$prepared # Review real line breaks before the authorized commit step.
```

Do not copy the example ticket or summary without deriving them from the
current stage. After committing, capture and compare the recorded message:

```powershell
$recorded = @(git log -1 --format=%B)
if ($LASTEXITCODE -ne 0) { throw 'Cannot read the recorded commit message.' }
$expectedText = $prepared.Replace("`r`n", "`n").TrimEnd([char[]]"`r`n")
$recordedText = ($recorded -join "`n").Replace("`r`n", "`n").TrimEnd([char[]]"`r`n")
if ($recordedText -cne $expectedText) {
    throw 'Recorded commit message differs; report the hash without amending.'
}
```
