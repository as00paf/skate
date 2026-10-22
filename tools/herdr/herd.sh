#!/usr/bin/env bash
# Starts the Skate agent herd inside herdr (https://herdr.dev).
#
#   tools/herdr/herd.sh [options] [domain ...]
#
# Domains: render assets ecs physics animation audio input gameplay gameui
#          scripting online editor tooling qa   (default: all)
#
# Options:
#   --kind copilot|opencode  Agent CLI to run (default: copilot)
#   --worktrees              One git worktree + herdr workspace per domain agent
#                            (branch agents/<domain>) instead of tabs in one workspace
#   --no-lead                Don't start the skate-lead orchestrator
#   --no-start               Create the layout only; don't launch agents
#   --workspace LABEL        herdr workspace label to use/create (default: skate)
#   -- ARGS...               Extra arguments passed to every agent CLI
#                            (e.g. -- --allow-all-tools)
set -euo pipefail

ALL_DOMAINS=(render assets ecs physics animation audio input gameplay gameui scripting online editor tooling qa)
kind=copilot
worktrees=0
lead=1
start=1
ws_label=skate
domains=()
extra=()

while (($#)); do
  case "$1" in
    --kind) kind="$2"; shift 2 ;;
    --worktrees) worktrees=1; shift ;;
    --no-lead) lead=0; shift ;;
    --no-start) start=0; shift ;;
    --workspace) ws_label="$2"; shift 2 ;;
    -h|--help) sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    --) shift; extra=("$@"); break ;;
    all) domains=("${ALL_DOMAINS[@]}"); shift ;;
    -*) echo "unknown option: $1" >&2; exit 2 ;;
    *)
      if [[ " ${ALL_DOMAINS[*]} " != *" $1 "* ]]; then
        echo "unknown domain: $1 (valid: ${ALL_DOMAINS[*]})" >&2; exit 2
      fi
      domains+=("$1"); shift ;;
  esac
done
((${#domains[@]})) || domains=("${ALL_DOMAINS[@]}")
[[ "$kind" == copilot || "$kind" == opencode ]] || { echo "--kind must be copilot or opencode" >&2; exit 2; }

for bin in herdr jq git "$kind"; do
  command -v "$bin" >/dev/null || { echo "missing required command: $bin" >&2; exit 1; }
done
herdr status server >/dev/null 2>&1 || { echo "herdr server is not running (run 'herdr' first)" >&2; exit 1; }

repo="$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel)"
[[ "$kind" == opencode ]] && "$repo/tools/herdr/sync-agents.sh"

if ((worktrees)); then
  # Worktrees only contain committed files, so the agent definitions must be committed.
  agent_dir=".github/agents"; [[ "$kind" == opencode ]] && agent_dir=".opencode/agents"
  if ! git -C "$repo" ls-tree -r --name-only HEAD -- "$agent_dir" | grep -q .; then
    echo "--worktrees: $agent_dir is not committed; commit it first so worktrees contain the agents." >&2
    exit 1
  fi
fi

agent_running() { herdr agent get "$1" >/dev/null 2>&1; }

ws_id="$(herdr workspace list | jq -r --arg l "$ws_label" '[.result.workspaces[] | select(.label == $l)][0].workspace_id // empty')"
fresh_root=""
fresh_tab=""
if [[ -z "$ws_id" ]]; then
  created="$(herdr workspace create --cwd "$repo" --label "$ws_label" --no-focus)"
  ws_id="$(jq -r '.result.workspace.workspace_id' <<<"$created")"
  fresh_root="$(jq -r '.result.root_pane.pane_id' <<<"$created")"
  fresh_tab="$(jq -r '.result.tab.tab_id' <<<"$created")"
  echo "created workspace $ws_label ($ws_id)"
fi

new_tab_pane() { # label cwd -> pane id
  herdr tab create --workspace "$ws_id" --cwd "$2" --label "$1" --no-focus | jq -r '.result.root_pane.pane_id'
}

worktree_pane() { # domain -> pane id (root pane of the worktree workspace)
  local d="$1" out wsid
  # Keep worktrees on the same (Windows) drive as the repo: cmd.exe/gradlew.bat can't run from \\wsl$ paths.
  local path="${repo%/*}/$(basename "$repo")-worktrees/$d"
  if ! out="$(herdr worktree create --cwd "$repo" --branch "agents/$d" --path "$path" --label "skate-$d" --no-focus 2>/dev/null)"; then
    out="$(herdr worktree open --cwd "$repo" --branch "agents/$d" --label "skate-$d" --no-focus)"
  fi
  wsid="$(jq -r '.result.workspace.workspace_id' <<<"$out")"
  herdr pane list --workspace "$wsid" | jq -r '.result.panes[0].pane_id'
}

start_agent() { # name pane
  local name="$1" pane="$2" i
  ((start)) || { echo "  $name -> $pane (layout only)"; return; }
  local args=(agent start "$name" --kind "$kind" --pane "$pane" --timeout 120000 --)
  if [[ "$kind" == copilot ]]; then
    args+=(--agent "$name")
  else
    # OpenCode's TUI has no --agent flag: select the primary agent through inline config,
    # and use a private server so the shared background service doesn't ignore that env.
    args+=(--standalone)
    herdr pane run "$pane" "export OPENCODE_CONFIG_CONTENT='{\"default_agent\":\"$name\"}'" >/dev/null
  fi
  args+=("${extra[@]}")
  for i in 1 2 3 4 5 6 7 8 9 10; do # a fresh shell is busy until its first prompt
    if herdr "${args[@]}" >/dev/null 2>/tmp/herd-$name.err; then
      echo "  $name -> $pane ($kind)"; return
    fi
    grep -q agent_pane_busy /tmp/herd-$name.err || break # only retry while the shell starts
    sleep 2
  done
  echo "  $name -> FAILED: $(cat /tmp/herd-$name.err)" >&2
  if grep -q agent_pane_busy /tmp/herd-$name.err; then
    local fg
    fg="$(herdr pane process-info --pane "$pane" | jq -r '[.result.process_info.foreground_processes[].cmdline] | join(" | ")')"
    echo "    pane $pane is running: $fg" >&2
    echo "    Your shell startup may be waiting for input; guard it with [[ \$HERDR_ENV == 1 ]] or answer it in the pane, then re-run." >&2
  fi
}

echo "workspace $ws_label ($ws_id), kind=$kind, worktrees=$worktrees"

if ((lead)); then
  if agent_running skate-lead; then
    echo "  skate-lead already running"
  else
    if [[ -n "$fresh_root" ]]; then
      pane="$fresh_root"; fresh_root=""; herdr tab rename "$fresh_tab" lead >/dev/null
    else
      pane="$(new_tab_pane lead "$repo")"
    fi
    herdr pane rename "$pane" lead >/dev/null 2>&1 || true
    start_agent skate-lead "$pane"
  fi
fi

for d in "${domains[@]}"; do
  name="skate-$d"
  if agent_running "$name"; then echo "  $name already running"; continue; fi
  if ((worktrees)); then
    pane="$(worktree_pane "$d")"
  elif [[ -n "$fresh_root" ]]; then
    pane="$fresh_root"; fresh_root=""; herdr tab rename "$fresh_tab" "$d" >/dev/null
  else
    pane="$(new_tab_pane "$d" "$repo")"
  fi
  herdr pane rename "$pane" "$d" >/dev/null 2>&1 || true
  start_agent "$name" "$pane"
done

cat <<MSG

Dispatch work through the lead, or directly:
  herdr agent prompt skate-lead "Plan the next round from GameEngine.md and dispatch ready P0 tasks."
  herdr agent prompt skate-render "Work on REN-01 from GameEngine.md." --wait
  herdr agent read skate-render --source recent-unwrapped --lines 120
MSG
