#!/usr/bin/env bash
# Two-way Discord DM link between the lead agent and the human owner.
#   discord-dm.sh send "<text>"            send a DM
#   discord-dm.sh read [limit]             print recent DMs, oldest first, as
#                                          "<message-id> <author-id> <author> <text> [files: a.png, b.txt]"
#   discord-dm.sh wait <seconds> [after]   poll until a message from the owner newer than snowflake [after]
#                                          arrives; prints it and exits 0, exits 1 on timeout
#   discord-dm.sh files <message-id> [dir] list and download the attachments of one of the owner's DMs to [dir]
#                                          (default ${XDG_CACHE_HOME:-~/.cache}/skate/discord/<message-id>); prints
#                                          "<file>\t<bytes>\t<saved path>" per attachment; exits 3 if the message
#                                          is not from the owner
# Env (never stored in the repo): DISCORD_TOKEN (bot token), DISCORD_USER_ID (owner's user id).
# When unset they are read from ${SKATE_DISCORD_ENV:-~/.config/skate/discord.env} (chmod 600, outside the repo).
# Only messages whose author id equals DISCORD_USER_ID are ever treated as instructions or downloaded.
# The token is only sent to discord.com, through a header file (never on a command line or in the output);
# attachments are fetched from the Discord CDN without it.
set -euo pipefail

env_file="${SKATE_DISCORD_ENV:-$HOME/.config/skate/discord.env}"
if [[ -z "${DISCORD_TOKEN:-}" || -z "${DISCORD_USER_ID:-}" ]] && [[ -r "$env_file" ]]; then
  set -a
  # shellcheck disable=SC1090
  . "$env_file"
  set +a
fi

: "${DISCORD_TOKEN:?DISCORD_TOKEN is not set}"
: "${DISCORD_USER_ID:?DISCORD_USER_ID is not set}"
API=https://discord.com/api/v10

# curl against the Discord API; the Authorization header is read from a pipe so the token is not in argv.
api() {
  curl -fsS -H @<(printf 'Authorization: Bot %s\n' "$DISCORD_TOKEN") "$@"
}

channel() {
  api -X POST "$API/users/@me/channels" -H 'Content-Type: application/json' \
    -d "{\"recipient_id\":\"$DISCORD_USER_ID\"}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])'
}

fetch() { # $1 = limit, $2 = optional after-id
  local ch; ch=$(channel)
  api "$API/channels/$ch/messages?limit=$1${2:+&after=$2}" | python3 -c '
import sys, json
for m in sorted(json.load(sys.stdin), key=lambda m: int(m["id"])):
    text = m["content"].replace("\n", " ").replace("\t", " ")
    names = [a.get("filename", "?") for a in m.get("attachments", [])]
    if names:
        text += (" " if text else "") + "[files: " + ", ".join(names) + "]"
    print(m["id"], m["author"]["id"], m["author"]["username"], text, sep="\t")'
}

# Prints "<safe file name>\t<bytes>\t<url>" for each attachment of message $1; exits 3 if it is not the owner's.
attachments() {
  local ch; ch=$(channel)
  api "$API/channels/$ch/messages/$1" | python3 -c '
import sys, json, os, re
from urllib.parse import urlparse
owner = sys.argv[1]
m = json.load(sys.stdin)
if m["author"]["id"] != owner:
    print("message is not from the owner; refusing to download", file=sys.stderr)
    sys.exit(3)
seen = set()
for i, a in enumerate(m.get("attachments", [])):
    url = a.get("url", "")
    if urlparse(url).scheme != "https" or urlparse(url).hostname not in ("cdn.discordapp.com", "media.discordapp.net"):
        print("skipping attachment with unexpected url host", file=sys.stderr)
        continue
    name = re.sub(r"[^A-Za-z0-9._-]", "_", os.path.basename(a.get("filename", ""))).lstrip(".") or f"attachment{i}"
    while name in seen:
        name = f"{i}_{name}"
    seen.add(name)
    print(name, a.get("size", 0), url, sep="\t")' "$DISCORD_USER_ID"
}

case "${1:-}" in
  send)
    ch=$(channel)
    python3 -c 'import json,sys;print(json.dumps({"content":sys.argv[1]}))' "${2:?text required}" |
      api -X POST "$API/channels/$ch/messages" -H 'Content-Type: application/json' -d @- >/dev/null
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
  files)
    id=${2:?message id required}
    if [[ ! $id =~ ^[0-9]+$ ]]; then echo "message id must be a snowflake (digits only)" >&2; exit 2; fi
    dir=${3:-${XDG_CACHE_HOME:-$HOME/.cache}/skate/discord/$id}
    list=$(attachments "$id")
    if [ -z "$list" ]; then echo "no attachments" >&2; exit 0; fi
    mkdir -p "$dir"
    while IFS=$'\t' read -r name size url; do
      curl -fsS -o "$dir/$name" "$url"
      printf '%s\t%s\t%s\n' "$name" "$size" "$dir/$name"
    done <<<"$list"
    ;;
  *)
    echo "usage: $0 send <text> | read [limit] | wait <seconds> [after-id] | files <message-id> [dir]" >&2
    exit 2
    ;;
esac
