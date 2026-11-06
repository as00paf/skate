# TLG-11: discord-dm.sh reads attachments, manual test

Needs `DISCORD_TOKEN` and `DISCORD_USER_ID` (or `~/.config/skate/discord.env`).

1. From your Discord account, DM the bot a message with two attachments (e.g. a screenshot and a `.txt`).
2. Run `tools/herdr/discord-dm.sh read 3`. Your message ends with `[files: <name1>, <name2>]`. Note its id (first column).
3. Run `tools/herdr/discord-dm.sh files <id>`. Each line shows the file name, its size and the saved path under `~/.cache/skate/discord/<id>/`; the files open correctly.
4. Run `tools/herdr/discord-dm.sh files <id> /tmp/dm-test`. The files are saved in `/tmp/dm-test`.
5. Run `files` with the id of a message the bot sent. It prints "message is not from the owner" and exits 3, and nothing is downloaded.
6. No command output contains the token.

Automated (offline, fake curl): `tools/herdr/discord-dm.test.sh` and `shellcheck tools/herdr/discord-dm.sh tools/herdr/discord-dm.test.sh`.
