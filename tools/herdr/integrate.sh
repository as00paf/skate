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
if [[ "$(git -C "$wt" rev-parse --abbrev-ref HEAD)" != "$branch" ]]; then
  # Reuse the worktree left on a previous round's branch, but never discard work in it.
  [[ -z "$(git -C "$wt" status --porcelain)" ]] || { echo "worktree $wt has uncommitted changes" >&2; exit 1; }
  if git -C "$repo" show-ref --verify --quiet "refs/heads/$branch"; then
    git -C "$wt" switch "$branch"
  else
    git -C "$wt" switch -c "$branch" "$base"
  fi
fi

for d in "$@"; do
  echo "== merging agents/$d"
  if ! git -C "$wt" merge --no-ff --no-edit "agents/$d"; then
    echo "CONFLICT in agents/$d: abort with 'git -C $wt merge --abort' and ask skate-$d to rebase onto $branch" >&2
    exit 1
  fi
done

if [[ "${SKIP_TESTS:-0}" != 1 ]]; then
  (cd "$wt" && cmd.exe /c gradlew.bat test 2>&1 | grep -E "FAILED|tests completed|BUILD|^e:" || true)
  # Gradle only prints counts on failure; sum the JUnit XML for the PR body.
  find "$wt" -path '*/build/test-results/test/*.xml' -exec cat {} + 2>/dev/null | python3 -c '
import re, sys
t = {k: 0 for k in ("tests", "failures", "errors", "skipped")}
for m in re.finditer(r"<testsuite [^>]*", sys.stdin.read()):
    for k in t:
        v = re.search(k + r"=\"(\d+)\"", m.group(0))
        t[k] += int(v.group(1)) if v else 0
print("test totals: " + ", ".join(f"{v} {k}" for k, v in t.items()))'

fi
echo "integration branch: $branch ($wt)"
