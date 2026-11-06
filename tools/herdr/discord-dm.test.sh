#!/usr/bin/env bash
# Offline test of discord-dm.sh (TLG-11): a fake curl on PATH serves canned Discord responses, so no network or real
# token is needed. Run: tools/herdr/discord-dm.test.sh
set -euo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/bin"

token="test-token-$RANDOM$RANDOM"
owner=111
cat >"$work/bin/curl" <<'EOF'
#!/usr/bin/env bash
# Fake curl: records argv and the auth header it was given, answers by URL.
log=$FAKE_DIR/calls.log
[[ $* == *"$FAKE_TOKEN"* ]] && echo "TOKEN_IN_ARGV" >>"$log"
out=""; url=""; auth=""
while [ $# -gt 0 ]; do
  case "$1" in
    -o) out=$2; shift ;;
    -H) if [[ $2 == @* ]]; then auth=$(cat "${2#@}"); fi; shift ;;
    -X|-d) shift ;;
    http*) url=$1 ;;
  esac
  shift
done
printf 'ARGV %s | AUTH %s\n' "$url" "$auth" >>"$log"
case "$url" in
  */users/@me/channels) echo '{"id":"900"}' ;;
  */channels/900/messages/5) cat <<JSON
{"id":"5","author":{"id":"$FAKE_OWNER","username":"owner"},"content":"see",
 "attachments":[{"filename":"shot.png","size":3,"url":"https://cdn.discordapp.com/a/shot.png"},
                {"filename":"../../evil name.txt","size":4,"url":"https://cdn.discordapp.com/a/evil.txt"},
                {"filename":"x.sh","size":1,"url":"https://example.com/x.sh"}]}
JSON
  ;;
  */channels/900/messages/6) echo '{"id":"6","author":{"id":"222","username":"stranger"},"content":"","attachments":[{"filename":"a","size":1,"url":"https://cdn.discordapp.com/a"}]}' ;;
  */channels/900/messages\?*) cat <<JSON
[{"id":"6","author":{"id":"222","username":"stranger"},"content":"hi","attachments":[]},
 {"id":"5","author":{"id":"$FAKE_OWNER","username":"owner"},"content":"see\tthis","attachments":[{"filename":"shot.png"},{"filename":"log.txt"}]}]
JSON
  ;;
  https://cdn.discordapp.com/*) printf 'data' >"$out" ;;
  *) echo "unexpected url $url" >&2; exit 22 ;;
esac
EOF
chmod +x "$work/bin/curl"

export PATH="$work/bin:$PATH" FAKE_DIR=$work FAKE_TOKEN=$token FAKE_OWNER=$owner
export DISCORD_TOKEN=$token DISCORD_USER_ID=$owner SKATE_DISCORD_ENV=/nonexistent
fail() { echo "FAIL: $*" >&2; exit 1; }

# read: attachment names are shown, tabs in the text do not split columns.
read_out=$("$here/discord-dm.sh" read 5)
grep -qF $'5\t111\towner\tsee this [files: shot.png, log.txt]' <<<"$read_out" || fail "read: $read_out"

# files: owner message -> safe names, only CDN hosts, saved in the given dir.
files_out=$("$here/discord-dm.sh" files 5 "$work/out" 2>"$work/err")
[ -f "$work/out/shot.png" ] || fail "shot.png not downloaded"
[ -f "$work/out/evil_name.txt" ] || fail "unsafe name not sanitized: $(ls "$work/out")"
[ ! -e "$work/out/x.sh" ] || fail "non-CDN attachment downloaded"
grep -q "unexpected url host" "$work/err" || fail "non-CDN skip not reported"
grep -qF $'shot.png\t3\t'"$work/out/shot.png" <<<"$files_out" || fail "files: $files_out"

# files: a stranger's message is refused with exit 3 and nothing is written.
set +e; "$here/discord-dm.sh" files 6 "$work/stranger" 2>/dev/null; code=$?; set -e
[ "$code" -eq 3 ] || fail "stranger message: exit $code"
[ ! -e "$work/stranger" ] || fail "stranger attachments written"

# files: the id must be a snowflake.
set +e; "$here/discord-dm.sh" files '../x' 2>/dev/null; code=$?; set -e
[ "$code" -eq 2 ] || fail "bad id: exit $code"

# The token never appears in argv or output, and is only sent to discord.com.
! grep -q TOKEN_IN_ARGV "$work/calls.log" || fail "token on a curl command line"
! grep -qF "$token" <<<"$read_out$files_out$(cat "$work/err")" || fail "token printed"
grep 'ARGV https://cdn' "$work/calls.log" | grep -qF "$token" && fail "token sent to the CDN"
grep 'ARGV https://discord.com' "$work/calls.log" | grep -qF "Authorization: Bot $token" || fail "API calls not authenticated"

echo "discord-dm.sh: all tests passed"
