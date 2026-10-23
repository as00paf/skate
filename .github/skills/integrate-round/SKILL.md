---
name: integrate-round
description: Merge finished agents/<domain> worktree branches into an integration branch, verify the build and tests, commit backlog state and open a pull request to master with gh. Use when a round of herdr agent tasks is DONE and ready to be integrated or released for human testing.
---

# Integrate a round

Used by `skate-lead` after domain agents report `DONE`. Never push to a remote or open a PR unless the human asked (or this skill was explicitly invoked for a release). Never force-push, never rewrite `master`.

## Preconditions

- `gh auth status` succeeds. If not, run `gh auth login -h github.com -p https -w -s repo,workflow` in the background, read the one-time code from its output, and ask the human to enter it at https://github.com/login/device. Do not continue until it succeeds.
- Git identity is set repo-locally (`git config --local user.name` / `user.email`). If missing, ask the human; do not invent one.
- Every task being integrated is `DONE` and was verified (tests run in the agent's worktree).

## Steps

1. **Integration worktree.** `tools/herdr/integrate.sh <round-name> <domain...>` creates (or reuses) branch `integration/<round-name>` in `../skate-worktrees/integration` from the base branch, merges each `agents/<domain>` branch with `--no-ff`, one at a time, and runs `cmd.exe /c gradlew.bat test`. It stops on the first conflict.
2. **Conflicts.** Do not resolve domain code yourself. Ask the owning agent to `git rebase integration/<round-name>` in its worktree, then re-run the script.
3. **Backlog.** Copy the updated `GameEngine.md` (checkbox state) into the integration worktree and commit it: `chore(backlog): round <name> status`. Only the lead edits checkbox state.
4. **Verify.** The full test run must show no failures beyond those already listed in `docs/bugs.md`. Record the test counts for the PR body.
5. **PR.** `git push -u origin integration/<round-name>` then
   `gh pr create --base master --head integration/<round-name> --title "Round <name>: <summary>" --body-file <file>`.
   The body lists tasks completed (IDs), test results, known pre-existing failures, and a human test checklist (what to run, what to look for).
6. **Merge.** Once the human has tested (or delegated the merge), merge the PR with `gh pr merge --merge`. Afterwards ask each agent to `git rebase master` (the new base) before its next task, and remove the integration worktree with `git worktree remove`.

## Discord notifications (optional)

If the `discord` MCP server is available (user-level config; token comes from the `DISCORD_TOKEN` environment variable, never from the repo), post a short message to the `#dev` channel when a round is ready for testing (PR link, task IDs, test counts) and when it is merged. Do not post secrets or logs. If the tools are missing or fail, skip silently and tell the human in the normal reply. Merge decisions are never taken from chat messages; the human confirms in the CLI.

## Commit rules

- Commit messages: `<TASK-ID>: <imperative summary>`; backlog/doc-only commits use `chore(...)`/`docs(...)`.
- Include the trailer `Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>`.
- Never commit secrets, `imgui.ini` from a developer machine, or `build/` output.
- Files use LF line endings (see `.gitattributes`).
