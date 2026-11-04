#!/usr/bin/env bash
# Generates OpenCode agents (.opencode/agents/<name>.md) and Claude Code agents
# (.claude/agents/<name>.md) from the Copilot CLI agent definitions
# (.github/agents/<name>.agent.md), which are the source of truth. Also mirrors
# .github/skills into .claude/skills.
set -euo pipefail

repo="$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel)"
src="$repo/.github/agents"
oc_dst="$repo/.opencode/agents"
cl_dst="$repo/.claude/agents"
mkdir -p "$oc_dst" "$cl_dst"

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
  count=$((count + 1))
done
echo "synced $count agents into ${oc_dst#"$repo"/} and ${cl_dst#"$repo"/}"

rm -rf "$repo/.claude/skills"
cp -r "$repo/.github/skills" "$repo/.claude/skills"
echo "synced skills into .claude/skills"
