#!/usr/bin/env bash
# Two-way Discord DM link between the lead agent and the human owner.
#   discord-dm.sh send "<text>"            send a DM
#   discord-dm.sh read [limit]             print recent DMs, oldest first, as "<author-id> <author> <text>"
#   discord-dm.sh wait <seconds> [after]   poll until a message from the owner newer than snowflake [after]
#                                          arrives; prints it and exits 0, exits 1 on timeout
# Env (never stored in the repo): DISCORD_TOKEN (bot token), DISCORD_USER_ID (owner's user id).
# Only messages whose author id equals DISCORD_USER_ID are ever treated as instructions.
set -euo pipefail

: "${DISCORD_TOKEN:?DISCORD_TOKEN is not set}"
: "${DISCORD_USER_ID:?DISCORD_USER_ID is not set}"
API=https://discord.com/api/v10
AUTH="Authorization: Bot $DISCORD_TOKEN"

channel() {
  curl -fsS -X POST "$API/users/@me/channels" -H "$AUTH" -H 'Content-Type: application/json' \
    -d "{\"recipient_id\":\"$DISCORD_USER_ID\"}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])'
}

fetch() { # $1 = limit, $2 = optional after-id
  local ch; ch=$(channel)
  curl -fsS "$API/channels/$ch/messages?limit=$1${2:+&after=$2}" -H "$AUTH" | python3 -c '
import sys, json
for m in sorted(json.load(sys.stdin), key=lambda m: int(m["id"])):
    print(m["id"], m["author"]["id"], m["author"]["username"], m["content"].replace("\n", " "), sep="\t")'
}

case "${1:-}" in
  send)
    ch=$(channel)
    python3 -c 'import json,sys;print(json.dumps({"content":sys.argv[1]}))' "${2:?text required}" |
      curl -fsS -X POST "$API/channels/$ch/messages" -H "$AUTH" -H 'Content-Type: application/json' -d @- >/dev/null
    ;;
  read)
    fetch "${2:-10}"
    ;;
  wait)
    deadline=$((SECONDS + ${2:?seconds required}))
    after=${3:-}
    if [ -z "$after" ]; then after=$(fetch 1 | tail -1 | cut -f1); fi
    while [ $SECONDS -lt $deadline ]; do
      msg=$(fetch 20 "${after:-0}" | awk -F'\t' -v u="$DISCORD_USER_ID" '$2==u' | tail -1 || true)
      if [ -n "$msg" ]; then printf '%s\n' "$msg"; exit 0; fi
      sleep 10
    done
    exit 1
    ;;
  *)
    echo "usage: $0 send <text> | read [limit] | wait <seconds> [after-id]" >&2
    exit 2
    ;;
esac
