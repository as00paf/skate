#!/usr/bin/env bash
# Generates OpenCode agents (.opencode/agents/<name>.md) from the Copilot CLI
# agent definitions (.github/agents/<name>.agent.md), which are the source of truth.
set -euo pipefail

repo="$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel)"
src="$repo/.github/agents"
dst="$repo/.opencode/agents"
mkdir -p "$dst"

count=0
for f in "$src"/*.agent.md; do
  name="$(basename "$f" .agent.md)"
  description="$(sed -n 's/^description: //p' "$f" | head -n1)"
  description="${description#\"}"; description="${description%\"}"
  {
    echo "---"
    echo "description: \"${description//\"/\\\"}\""
    echo "mode: all"
    echo "---"
    echo "<!-- Generated from .github/agents/${name}.agent.md by tools/herdr/sync-agents.sh. Do not edit. -->"
    # Strip the Copilot front matter (first --- ... --- block).
    awk 'BEGIN{n=0} n<2 && /^---[[:space:]]*$/ {n++; next} n>=2' "$f"
  } > "$dst/$name.md"
  count=$((count + 1))
done
echo "synced $count agents into ${dst#"$repo"/}"
