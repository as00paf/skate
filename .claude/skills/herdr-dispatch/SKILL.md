---
name: herdr-dispatch
description: Start, dispatch to, poll and read results from the Skate domain agents running in herdr, including how to handle blocked permission dialogs safely. Use when planning a round, sending a task to a skate-* agent, or collecting DONE/BLOCKED/NEEDS results.
---

# Dispatch and collect with herdr

## Start agents

`tools/herdr/herd.sh --no-lead --worktrees <domain...>` (idempotent; skips agents already running). Worktrees live in `../skate-worktrees/<domain>` on branch `agents/<domain>`. Worktrees only contain committed files, so commit `.github/` and `.opencode/agents/` first. The script can exceed shell time limits, so start large batches in groups of 3 or fewer.

## Dispatch

```bash
herdr agent prompt skate-<domain> "Work on **<ID>** from GameEngine.md (<title>). Follow your agent instructions. Acceptance: <Done when>. End with the DONE/BLOCKED status line."
```

One task per agent at a time. Mark the task `[~] @skate-<domain>` in `GameEngine.md` first. Do not dispatch two tasks that touch the same files unless the agents use separate worktrees.

## Poll and read

- `herdr agent get skate-<domain> | jq -r '.result.agent.agent_status'` returns `working`, `blocked`, `idle` or `done`.
- `herdr agent wait skate-<domain> --until idle --until done --until blocked --timeout 900000`.
- `herdr agent read skate-<domain> --source recent-unwrapped --lines 200 | grep -E 'DONE|BLOCKED|NEEDS'` works only when the agent is not blocked. When blocked use `--source visible`.
- Check that the DONE line names the task you dispatched; older lines stay in the scrollback.

## Blocked dialogs

Read the visible screen first. Approve (`herdr agent send-keys skate-<domain> enter`) only when the command is plainly within the dispatched task: edits and commits inside its own worktree, `git rebase` or `git merge` of branches named in the prompt, gradle builds and tests, read-only greps. Never approve deleting outside the worktree, pushing, force operations, installing system packages, or anything touching credentials. Choose option 1 (once), never "don't ask again". If in doubt, ask the human.

## Result handling

- `DONE`: verify by running the task's tests (from the agent's worktree) and skimming the diff, then mark `[x]`.
- `BLOCKED`: answer, or mark `[!] reason` and escalate.
- `NEEDS <PREFIX>`: add a task to that domain's section (next free ID) and dispatch it when ready.
- Tests failing for reasons listed in `docs/bugs.md` are pre-existing and are not the agent's responsibility.
