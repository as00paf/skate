#!/usr/bin/env bash
# Merges agents/<domain> branches into integration/<round> (in its own worktree) and runs the tests.
#
#   tools/herdr/integrate.sh <round-name> <domain> [domain ...]
#
# Env: BASE (base ref for a new integration branch, default: current HEAD branch),
#      SKIP_TESTS=1 to skip the gradle run.
set -euo pipefail

(($# >= 2)) || { sed -n '2,8p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }
round="$1"; shift
repo="$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel)"
branch="integration/$round"
wt="$repo/../skate-worktrees/integration"
base="${BASE:-$(git -C "$repo" rev-parse --abbrev-ref HEAD)}"

git -C "$repo" config --local user.name >/dev/null || { echo "git identity not set (git config --local user.name/user.email)" >&2; exit 1; }

if [[ ! -d "$wt" ]]; then
  if git -C "$repo" show-ref --verify --quiet "refs/heads/$branch"; then
    git -C "$repo" worktree add "$wt" "$branch"
  else
    git -C "$repo" worktree add -b "$branch" "$wt" "$base"
  fi
fi
[[ "$(git -C "$wt" rev-parse --abbrev-ref HEAD)" == "$branch" ]] || { echo "worktree $wt is not on $branch" >&2; exit 1; }

for d in "$@"; do
  echo "== merging agents/$d"
  if ! git -C "$wt" merge --no-ff --no-edit "agents/$d"; then
    echo "CONFLICT in agents/$d: abort with 'git -C $wt merge --abort' and ask skate-$d to rebase onto $branch" >&2
    exit 1
  fi
done

if [[ "${SKIP_TESTS:-0}" != 1 ]]; then
  (cd "$wt" && cmd.exe /c gradlew.bat test 2>&1 | grep -E "FAILED|tests completed|BUILD|^e:" || true)
fi
echo "integration branch: $branch ($wt)"
