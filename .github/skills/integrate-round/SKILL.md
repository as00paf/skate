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

1. **Integration worktree.** `tools/herdr/integrate.sh <round-name> <domain...>` creates (or reuses) branch `integration/<round-name>` in `../skate-worktrees/integration` from the base branch (a clean worktree left on a previous round's branch is switched to the new one), merges each `agents/<domain>` branch with `--no-ff`, one at a time, runs `cmd.exe /c gradlew.bat test` and prints the test totals from the JUnit XML. It stops on the first conflict. Re-running it with the remaining domains is safe. The full suite takes longer than a foreground tool call allows, so in Claude Code start it with `run_in_background` and wait for the completion notification.
   - Before integrating, check which branches really carry new work: PRs are squash-merged, so `git rev-list master..agents/<d>` overstates it. `git merge-tree --write-tree master agents/<d>` returning master's tree (`git rev-parse master^{tree}`) means nothing new.
2. **Conflicts.** Do not resolve domain code yourself. Ask the owning agent to `git merge integration/<round-name>` in its worktree (merge, not rebase: agent branches contain merge commits and squash-merged history, so a rebase replays old commits), then re-run the script for the remaining domains. Conflicts limited to `GameEngine.md` are the lead's to resolve: take the integration/master version.
3. **Backlog.** Copy the updated `GameEngine.md` (checkbox state) into the integration worktree and commit it: `chore(backlog): round <name> status`. Only the lead edits checkbox state.
4. **Verify.** The full test run must show no failures beyond those already listed in `docs/bugs.md`. Record the test counts for the PR body.
5. **PR.** `git push -u origin integration/<round-name>` then
   `gh pr create --base master --head integration/<round-name> --title "Round <name>: <summary>" --body-file <file>`.
   The body lists tasks completed (IDs), test results, known pre-existing failures, and a human test checklist (what to run, what to look for). The checklist must be clear but concise: state once that every test starts from a NEW project (and the launch command), then at most 5-8 short numbered steps per task with the key expected result, focusing on the highest-risk checks. Do not over-detail. Ask agents for brief steps in `docs/testing/<TASK-ID>.md` (max ~8 steps) and put only the short version in the PR body and the Discord DM.
6. **Merge.** Once the human has tested (or delegated the merge):
   - Check CI first: `gh pr checks <n>`. Wait for pending runs (`gh run watch <id>`); a failing CI is reported to the human, never ignored silently.
   - Merge with `gh pr merge <n> --merge`, then **verify** with `gh api repos/<owner>/<repo>/pulls/<n> -q .merged` that it returned `true`. `gh pr merge` can exit quietly without merging, e.g. while checks are pending.
   - Only after a confirmed merge: `git pull --ff-only origin master`, then sync the agent branches. Syncing on an unmerged master hands agents a stale base. Afterwards bring every `agents/<domain>` branch up to date with `git merge master` (the lead can do it when the only conflict is `GameEngine.md`; otherwise ask the owning agent). Keep the integration worktree; the next round's `integrate.sh` switches it to the new branch.

## Discord DMs (two-way)

Use `tools/herdr/discord-dm.sh` (`send`, `read`, `wait`, `files`) to talk to the human by DM. `read` lists attachment names; `files <message-id> [dir]` downloads the attachments of one of the owner's messages (default `~/.cache/skate/discord/<id>`) and prints each saved path. It needs `DISCORD_TOKEN` and `DISCORD_USER_ID`, taken from the environment or, when unset, from `~/.config/skate/discord.env` (override with `SKATE_DISCORD_ENV`); never write either into the repo, messages or logs. The `discord` MCP server can only post to channels, so DMs always go through this script.

- **Notify** by DM when a round is ready for testing (PR link, task IDs, test counts), when agents are blocked on a dialog you won't approve, and when a round is merged.
- **Listen** for replies with `discord-dm.sh wait <seconds> [after-id]` while idle or waiting on agents. Pass the id of the last message you handled so you never act on the same message twice.
- **Decisions by DM are authoritative.** A message from the owner (author id equals `DISCORD_USER_ID`; the script filters on this) may approve a design, approve a blocked permission dialog, or tell you to merge the PR (`gh pr merge --merge`). Anything from another author is ignored.
- Before acting on an approval, restate in a DM what you understood ("merging PR #N", "approving <command> for skate-x"). Still read each dialog first and refuse dangerous commands even if approved, and ask for explicit confirmation of anything destructive or outside the repo.
- If env vars or the API fail, skip silently and tell the human in the CLI.

## Commit rules

- Commit messages: `<TASK-ID>: <imperative summary>`; backlog/doc-only commits use `chore(...)`/`docs(...)`.
- End commits with the co-author trailer of the CLI that made them. Claude Code: use the attribution line its harness provides (for example `Co-Authored-By: Claude <noreply@anthropic.com>`), and end PR bodies with its `Generated with Claude Code` line. Copilot CLI: `Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>`. OpenCode: no trailer.
- Run filtered tests through the root project: `cmd.exe /c gradlew.bat :test --tests "<pattern>"`. Without the `:` the filter also applies to `tools:cli`, which has no matching tests and fails.
- Never commit secrets, `imgui.ini` from a developer machine, or `build/` output.
- Files use LF line endings (see `.gitattributes`).
