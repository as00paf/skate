---
name: herdr-dispatch
description: Start, dispatch to, poll and read results from the Skate domain agents running in herdr, including how to handle blocked permission dialogs safely. Use when planning a round, sending a task to a skate-* agent, or collecting DONE/BLOCKED/NEEDS results.
---

# Dispatch and collect with herdr

## Start agents

`tools/herdr/herd.sh --kind claude --no-lead --worktrees <domain...>` (idempotent; skips agents already running; `--kind` defaults to `copilot`). Worktrees live in `../skate-worktrees/<domain>` on branch `agents/<domain>`. Agent definitions come from `.github/agents/*.agent.md`; `tools/herdr/sync-agents.sh` generates `.claude/agents/` and `.opencode/agents/` and mirrors `.github/skills` into `.claude/skills` (`herd.sh` runs it for non-Copilot kinds). Worktrees only contain committed files, so commit `.github/`, `.claude/` and `.opencode/agents/` first, and merge master into the agent branches. The script can exceed shell time limits, so start large batches in groups of 3 or fewer.

## Dispatch

```bash
herdr agent prompt skate-<domain> "Work on **<ID>** from GameEngine.md (<title>). Follow your agent instructions. Acceptance: <Done when>. End with the DONE/BLOCKED status line."
```

One task per agent at a time. Mark the task `[~] @skate-<domain>` in `GameEngine.md` first. Do not dispatch two tasks that touch the same files unless the agents use separate worktrees.

## Poll and read

- `herdr agent get skate-<domain> | jq -r '.result.agent.agent_status'` returns `working`, `blocked`, `idle` or `done`.
- `herdr agent wait skate-<domain> --until done --until blocked --timeout 900000`. Right after `agent prompt` the agent can still read `idle` for a moment, so `--until idle` may return before it has started; wait for `done`/`blocked`, or confirm it is `working` first.
- Long waits exceed a foreground Claude Code tool call (2 min default, 10 min max): pass a `timeout` on the Bash call or run the wait in the background.
- `herdr agent read skate-<domain> --source recent-unwrapped --lines 200 | grep -E 'DONE|BLOCKED|NEEDS'` works only when the agent is not blocked. When blocked use `--source visible`.
- Check that the DONE line names the task you dispatched; older lines stay in the scrollback.

## Blocked dialogs

Read the visible screen first. Approve (`herdr agent send-keys skate-<domain> enter`) only when the command is plainly within the dispatched task: edits and commits inside its own worktree, `git rebase` or `git merge` of branches named in the prompt, gradle builds and tests, read-only greps. Never approve deleting outside the worktree, pushing, force operations, installing system packages, or anything touching credentials. Choose option 1 (once), never "don't ask again". If in doubt, ask the human.

Claude Code agents usually run in auto mode: most actions need no dialog, and the classifier denies risky ones itself, so a denial shows up in the agent's report rather than as a `blocked` state. Its dialogs offer `1. Yes`, `2. Yes, and don't ask again ...`, `3. No`; the same rule applies (option 1 only). `esc` cancels a dialog.

## Result handling

- `DONE`: verify by running the task's tests (from the agent's worktree) and skimming the diff, then mark `[x]`.
- `BLOCKED`: answer, or mark `[!] reason` and escalate.
- `NEEDS <PREFIX>`: add a task to that domain's section (next free ID) and dispatch it when ready.
- Tests failing for reasons listed in `docs/bugs.md` are pre-existing and are not the agent's responsibility.
- Filtered test runs go through the root project: `cmd.exe /c gradlew.bat :test --tests "<pattern>"` (without `:` the filter also hits `tools:cli` and fails).
