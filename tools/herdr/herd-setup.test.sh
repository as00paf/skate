#!/usr/bin/env bash
# Offline test of the herd setup (TLG-22): the SessionStart hook (session-start.sh) and the question-rule /
# task-observer guard in sync-agents.sh. Needs bash, git and jq. Run: tools/herdr/herd-setup.test.sh
# check() evals its condition later, so conditions are single-quoted on purpose and the variables they read
# (ctx, err) look unused to shellcheck.
# shellcheck disable=SC2016,SC2034
set -euo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
repo=$(git -C "$here" rev-parse --show-toplevel)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
fails=0
pass() { echo "ok   $1"; }
fail() { echo "FAIL $1" >&2; fails=$((fails + 1)); }
check() { if eval "$2"; then pass "$1"; else fail "$1"; fi; }

hook() { env -u HERDR_ENV "$@" bash "$here/session-start.sh"; }
context() { jq -er '.hookSpecificOutput | select(.hookEventName == "SessionStart") | .additionalContext' <<<"$1"; }

# --- session-start.sh ---------------------------------------------------------------------------------------------
check "hook is silent outside herdr" '[[ -z "$(hook TASK_OBSERVER_ROOT="$work/none")" ]]'

out=$(hook HERDR_ENV=1 TASK_OBSERVER_ROOT="$work/none")
check "hook prints valid SessionStart JSON in herdr" 'context "$out" >/dev/null'
ctx=$(context "$out")
check "hook names task-observer and the shared workspace" '[[ $ctx == *task-observer* && $ctx == *"$work/none"* ]]'
check "hook states the question rule" '[[ $ctx == *"rule 11"* && $ctx == *"only skate-lead asks the human"* ]]'
check "no observations: no review reminder" '[[ $ctx != *"review is due"* ]]'

obs="$work/obs"
mkdir -p "$obs/skill-observations/observation-log"
printf -- '---\nstatus: open\n---\n' >"$obs/skill-observations/observation-log/a.md"
printf -- '---\nstatus: open\n---\n' >"$obs/skill-observations/observation-log/b.md"
printf -- '---\nstatus: resolved\n---\n' >"$obs/skill-observations/observation-log/c.md"
review() { # last-review-date contents -> additionalContext
  if [[ -n "$1" ]]; then printf '%s\r\n' "$1" >"$obs/skill-observations/last-review-date.txt"; else rm -f "$obs/skill-observations/last-review-date.txt"; fi
  context "$(hook HERDR_ENV=1 TASK_OBSERVER_ROOT="$obs")"
}
ctx=$(review "")
check "counts only open observations" '[[ $ctx == *"2 open observations; last review: never."* ]]'
check "never reviewed: reminder fires" '[[ $ctx == *"review is due"* ]]'
check "reviewed 30 days ago: reminder fires" '[[ $(review "$(date -d "30 days ago" +%F)") == *"review is due"* ]]'
check "reviewed 2 days ago: reminder stays silent" '[[ $(review "$(date -d "2 days ago" +%F)") != *"review is due"* ]]'

odd="$work/we\"ird\\path"
mkdir -p "$odd"
check "quotes and backslashes in the path keep the JSON valid" 'context "$(hook HERDR_ENV=1 TASK_OBSERVER_ROOT="$odd")" | grep -qF "$odd"'

check "settings.json registers the hook" \
  'jq -e ".hooks.SessionStart[].hooks[] | select(.command | contains(\"tools/herdr/session-start.sh\"))" "$repo/.claude/settings.json" >/dev/null'

# --- sync-agents.sh guard -----------------------------------------------------------------------------------------
fake="$work/repo"
mkdir -p "$fake/tools/herdr" "$fake/.github"
cp "$here/sync-agents.sh" "$fake/tools/herdr/"
cp -r "$repo/.github/agents" "$repo/.github/skills" "$fake/.github/"
git -C "$fake" init -q

check "sync succeeds on the repo's agent sources" '"$fake/tools/herdr/sync-agents.sh" >/dev/null'
lacking=()
for f in "$fake"/.claude/agents/*.md "$fake"/.opencode/agents/*.md; do
  if ! grep -q 'rule 11' "$f" || ! grep -q '^## Session start: task-observer' "$f"; then lacking+=("${f#"$fake"/}"); fi
done
check "every generated agent has the question rule and task-observer" '((${#lacking[@]} == 0))'
((${#lacking[@]} == 0)) || printf '     lacking: %s\n' "${lacking[@]}" >&2
check "every source agent is generated for Claude and OpenCode" \
  '[[ $(ls "$fake/.github/agents" | wc -l) -eq $(ls "$fake/.claude/agents" | wc -l) && $(ls "$fake/.github/agents" | wc -l) -eq $(ls "$fake/.opencode/agents" | wc -l) ]]'

sed -i 's/rule 11/rule eleven/g' "$fake/.github/agents/skate-render.agent.md"
sed -i 's/^## Session start: task-observer/## Session start/' "$fake/.github/agents/skate-audio.agent.md"
rm "$fake/.claude/agents/skate-render.md"
if err=$("$fake/tools/herdr/sync-agents.sh" 2>&1 >/dev/null); then fail "sync must refuse sources without the rule"; else pass "sync refuses sources without the rule"; fi
check "the error names each broken file" '[[ $err == *"skate-render.agent.md: no question rule"* && $err == *"skate-audio.agent.md: no '"'"'## Session start: task-observer'"'"' section"* ]]'
check "a refused sync generates nothing" '[[ ! -e "$fake/.claude/agents/skate-render.md" ]]'

if ((fails)); then echo "$fails check(s) failed" >&2; exit 1; fi
echo "all herd setup checks passed"
