#!/usr/bin/env bash
# Generates OpenCode agents (.opencode/agents/<name>.md), Claude Code agents
# (.claude/agents/<name>.md), and Kiro agents (.kiro/agents/<name>.md) from the
# Copilot CLI agent definitions (.github/agents/<name>.agent.md), which are the
# source of truth. Also mirrors .github/skills into .claude/skills and
# .kiro/skills.
set -euo pipefail

repo="$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel)"
src="$repo/.github/agents"
oc_dst="$repo/.opencode/agents"
cl_dst="$repo/.claude/agents"
ki_dst="$repo/.kiro/agents"
mkdir -p "$oc_dst" "$cl_dst" "$ki_dst"

# TLG-22: every agent states the question rule (GameEngine.md rule 11: only skate-lead asks the human) and loads
# the task-observer skill at session start. Refuse to generate agents that lost either.
missing=()
for f in "$src"/*.agent.md; do
  grep -q 'rule 11' "$f" || missing+=("$(basename "$f"): no question rule (GameEngine.md rule 11)")
  grep -q '^## Session start: task-observer' "$f" || missing+=("$(basename "$f"): no '## Session start: task-observer' section")
done
if ((${#missing[@]})); then
  printf 'sync-agents: %s\n' "${missing[@]}" >&2
  exit 1
fi

count=0
for f in "$src"/*.agent.md; do
  name="$(basename "$f" .agent.md)"
  description="$(sed -n 's/^description: //p' "$f" | head -n1)"
  description="${description#\"}"; description="${description%\"}"
  note="<!-- Generated from .github/agents/${name}.agent.md by tools/herdr/sync-agents.sh. Do not edit. -->"
  # Strip the Copilot front matter (first --- ... --- block).
  body="$(awk 'BEGIN{n=0} n<2 && /^---[[:space:]]*$/ {n++; next} n>=2' "$f")"
  {
    echo "---"
    echo "description: \"${description//\"/\\\"}\""
    echo "mode: all"
    echo "---"
    echo "$note"
    echo "$body"
  } > "$oc_dst/$name.md"
  {
    echo "---"
    echo "name: $name"
    echo "description: \"${description//\"/\\\"}\""
    echo "---"
    echo "$note"
    echo "$body"
  } > "$cl_dst/$name.md"
  {
    echo "---"
    echo "name: $name"
    echo "description: \"${description//\"/\\\"}\""
    echo 'tools: ["read", "write", "shell", "web"]'
    echo "---"
    echo "$note"
    echo "$body"
  } > "$ki_dst/$name.md"
  count=$((count + 1))
done
echo "synced $count agents into ${oc_dst#"$repo"/}, ${cl_dst#"$repo"/}, and ${ki_dst#"$repo"/}"

rm -rf "$repo/.claude/skills"
cp -r "$repo/.github/skills" "$repo/.claude/skills"
echo "synced skills into .claude/skills"

rm -rf "$repo/.kiro/skills"
cp -r "$repo/.github/skills" "$repo/.kiro/skills"
echo "synced skills into .kiro/skills"
