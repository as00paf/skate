#!/usr/bin/env bash
# Claude Code SessionStart hook for herdr agent sessions (TLG-22, registered in .claude/settings.json).
#
# Inside a herdr pane (HERDR_ENV=1) it injects the task-observer activation as additionalContext, with the shared
# workspace and the log's review state, so every herd session loads task-observer before its first tool call.
# Outside herdr (the human's own sessions) it prints nothing.
#
#   TASK_OBSERVER_ROOT  workspace root shared by the whole herd (default: ~/.claude); the log is
#                       $TASK_OBSERVER_ROOT/skill-observations/
set -u

[[ "${HERDR_ENV:-}" == 1 ]] || exit 0

root="${TASK_OBSERVER_ROOT:-$HOME/.claude}"
d="$root/skill-observations"
msg="herdr agent session: before the first tool call, and before reading files or planning, make one batched call that lists the task-observer workspace ($root) and invokes the task-observer skill, then run its Session Start Protocol. The workspace is shared by the whole herd; never anchor it in this checkout or worktree. Questions for the human follow GameEngine.md rule 11: only skate-lead asks the human."

# Count files whose status field reads open, not every file (resolved ones stay a day).
open=0
if [[ -d "$d/observation-log" ]]; then
  open=$(find "$d/observation-log" -maxdepth 1 -name '*.md' -exec grep -l '^status: open$' {} + 2>/dev/null | wc -l | tr -d ' ')
fi
last=$(tr -d '\r' 2>/dev/null < "$d/last-review-date.txt")
last=${last:-never}
if [[ "$open" -gt 0 ]]; then
  msg="$msg $open open observations; last review: $last."
  cutoff=$(date -d '7 days ago' +%F 2>/dev/null || date -v-7d +%F)
  if [[ "$last" == never || "$(printf '%s\n%s\n' "$last" "$cutoff" | sort | head -1)" == "$last" ]]; then
    msg="$msg The review is due: tell skate-lead in your status line; the lead offers it to the human."
  fi
fi

# JSON string: escape backslashes and quotes, drop control characters.
msg=$(printf '%s' "$msg" | sed 's/\\/\\\\/g; s/"/\\"/g' | tr -d '\000-\037')
printf '{"hookSpecificOutput":{"hookEventName":"SessionStart","additionalContext":"%s"}}\n' "$msg"
