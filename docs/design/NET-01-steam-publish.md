# NET-01 — Publish to Steam from the editor

Status: **awaiting approval** (human + lead). No code is written until approved. Author: `skate-online`, 2026-11-17.
Paths are relative to `src/main/kotlin/com/pafoid/skate/`.

> **File name.** `docs/design/NET-01.md` is the approved *multiplayer architecture* note of the archived NET-01
> (`docs/backlog-archive.md`), referenced by NET-02…06 and by the agent rules. The task ID was reused, so this note
> lives in `NET-01-steam-publish.md` (same pattern as `ANI-04-ik-wiring.md`). The lead may prefer to renumber the
> task instead (see Q8).

Marker: **⚠ UNVERIFIED** = a steamcmd/SteamPipe detail taken from Valve's documentation and memory, not checked against a
real Steamworks app on 2026-11-17 (the agent machine has no Steamworks partner account). Each one gets a spike in
step 01a before the code that depends on it.

## 1. Goals / non-goals

Goals
- From the editor, upload an exported game to a Steam **branch** of the user's own app: app ID, depot ID(s), branch,
  build description, with progress, a live log, cancel, and the resulting BuildID.
- Repeatable: the per-project settings are remembered, so a second upload is "Export + Upload" in one click.
- Safe: no Steam password is ever written to disk or the log by Skate.
- Steam stays optional: nothing in the runtime or the export depends on it; the feature hides cleanly when unused.

Non-goals (v1)
- Store-page, pricing, achievements or other Steamworks back-office settings (those stay on partner.steamgames.com).
- Setting the **default** (public) branch live. Valve does not allow it from steamcmd (⚠ UNVERIFIED, documented as
  "default cannot be set live automatically"); the user does it in the Steamworks App Admin.
- macOS/Linux depots (exports are Windows-only today).
- Bundling steamcmd or the Steamworks SDK with the editor (licensing, §6).
- Uploading to Steam's workshop (UGC). This is a separate feature.

## 2. How SteamPipe uploads work (summary)

1. `steamcmd.exe` (Valve's command-line Steam client) logs into a Steam account that has *Edit App Metadata* and
   *Publish App Changes To Steam* rights on the app (Valve recommends a dedicated **build account**).
2. `+run_app_build <abs path>\app_build_<appid>.vdf` reads an **app build script** (Valve KeyValues/VDF) that names
   the app, the description, the content root, a scratch output dir, an optional `SetLive` branch, an optional
   `Preview` (dry run), and per-depot file mappings/exclusions.
3. steamcmd chunks and uploads only changed content, then prints the new **BuildID**. With `SetLive` the build goes
   live on that (non-default) branch.

Example script Skate would generate (one depot, all files of the export):

```
"AppBuild"
{
    "AppID" "1234560"
    "Desc" "Skate export 2026-11-17 14:02 — v0.3 playtest"
    "ContentRoot" "C:\\Games\\MyGame\\export\\"
    "BuildOutput" "C:\\Users\\me\\AppData\\Local\\Skate\\steampipe\\1234560\\output\\"
    "SetLive" "playtest"
    "Preview" "0"
    "Depots"
    {
        "1234561"
        {
            "FileMapping" { "LocalPath" "*" "DepotPath" "." "Recursive" "1" }
            "FileExclusion" "*.pdb"
            "FileExclusion" "steam_appid.txt"
        }
    }
}
```

## 3. User flow

Entry points: **File → Publish → Steam…** and a **Publish to Steam** button on the Export wizard's success page
(EDT-52 window standard: header icon, help article `assets/help/<lang>/steam_publish.md`, context menu).

The **Steam Publish** window has three sections:

1. **Setup** (collapsed once valid)
   - steamcmd: detected path + version state (*Found* / *Not installed* → **Install steamcmd…**, *Browse…*).
   - Steam account: build-account username; state *Signed in (cached)* / *Not signed in* → **Sign in…** (§5).
2. **Target** (per project)
   - App ID (digits only), depots table (depot ID, content = whole export / sub-folder, exclusions; v1 UI
     shows one depot with "Add depot" behind an *Advanced* toggle), branch (combo of recently used + free text;
     empty = "do not set live"; `default` refused with an explanation), build description (template with
     `{project}`, `{date}`, `{version}`, `{git}` placeholders), **Preview only (dry run)** checkbox.
   - Content: *Export now* (runs the normal export into a Steam staging folder first) or *Use existing export folder*.
3. **Upload**: Validate → **Upload** button; progress bar with phase text (Exporting, Signing in, Scanning, Uploading
   x/y MB, Committing, Setting live); live log (filtered, §5.4); **Cancel**; on success the BuildID, branch, and
   *Open in Steamworks* (copies `https://partner.steamgames.com/apps/builds/<appid>` to the clipboard; no browser
   launch from the editor in v1).

Validation before anything runs (each a translated issue, like `ExportWizard.Issue`): steamcmd missing, not signed in,
app ID/depot ID not numeric, branch `default`, content folder empty or missing the game jar/launcher, export
contains `steam_appid.txt` (warned and excluded, §7), launcher is `run-game.bat` (warning, §7/Q4).

## 4. steamcmd install and discovery

`SteamCmdLocator` (same shape as `BlenderLocator`, AST-19 §3), first match wins, each candidate checked by running
`steamcmd +quit` once (self-update; exit code + "Loading Steam API...OK" line ⚠ UNVERIFIED):

1. Editor setting `steamCmdPath` (per user, `EditorSettings`).
2. Managed install: `%LOCALAPPDATA%\Skate\steamcmd\steamcmd.exe`.
3. Steamworks SDK: a configured SDK root → `sdk\tools\ContentBuilder\builder\steamcmd.exe`.
4. `PATH`.

**Install steamcmd…** (needs explicit confirmation in a modal that names Valve as the source):
download `https://steamcdn-a.akamaihd.net/client/installer/steamcmd.zip` (public Valve URL, ⚠ UNVERIFIED that it is
still current) on a JobSystem IO worker, unzip into the managed folder, run `steamcmd +quit` so it downloads its
own update (~ tens of MB, progress shown as indeterminate with the log). No checksum is published by Valve; we
rely on HTTPS and on steamcmd's own self-update signature checks. Uninstall = delete the managed folder (button
in Setup). The downloader reuses the editor's HTTP client (`editor/assetstore/StoreHttp.kt`) behind an interface
so tests never touch the network.

## 5. Credentials

### 5.1 What Skate stores
- **Username** of the build account: editor settings (per user, not in the project, not committed).
- **Nothing else.** No password, no Steam Guard code, no token in Skate files. The project file holds only app ID,
  depots, branch history, and the description template.

### 5.2 Sign-in once, then steamcmd's cached session
steamcmd keeps its own login session in its install folder (`config\config.vdf` + login token) after a successful
login with Steam Guard; later `+login <user>` (no password) reuses it (⚠ UNVERIFIED how long it lasts; Valve
documents it as persistent until revoked or password change). So:

1. **Sign in…** opens a modal: username, password (masked `ImGui.inputText` with `Password` flag into a
   `CharArray`-backed buffer), and after the first attempt, a Steam Guard code field if steamcmd asks for one
   (email code or mobile authenticator code).
2. Skate starts `steamcmd +login <user> +quit` and **writes the password and then the Guard code to steamcmd's
   stdin** when it prompts (`password:` / `Steam Guard code:` ⚠ UNVERIFIED prompt texts). The password is never
   on the command line, because command lines are visible to other processes (Task Manager, WMI).
3. The buffers are zeroed right after they are written; the modal closes; state becomes *Signed in (cached)*.
4. Uploads run `steamcmd +login <user> +run_app_build <vdf> +quit`. If steamcmd reports the cached session expired
   (⚠ UNVERIFIED message), the upload stops with *Sign-in required* and reopens the modal; no automatic retry
   with stored secrets.
5. **Sign out** deletes steamcmd's cached login for that user (`+logout`? ⚠ UNVERIFIED; fallback: delete
   `config\config.vdf` in the managed install only, with confirmation).

This needs a process runner that can write to stdin. The existing `editor/tools/external/ProcessRunner` closes
stdin; the publisher gets its own small `InteractiveProcess` interface (start, `onLine`, `writeLine(CharArray)`,
cancel) with a JDK implementation and a scripted fake for tests.

### 5.3 Why not store the password (rejected options)
- Plain or obfuscated in settings: leaks via backups, screenshots of settings JSON, bug reports.
- Windows Credential Manager via JNA: workable, but adds a native dependency and still lets anything running as the
  user read it; and steamcmd's cached session already removes the need. Can be added later if the cache proves
  short-lived (Q1).
- Environment variables / CI secrets: out of scope for the editor; a future Skate CLI command (§9) can read a
  password from stdin for CI.

### 5.4 Log redaction
Every steamcmd line goes through `SteamLogFilter` before it reaches the log window or the editor console: drops echo of
anything typed at a prompt, masks the username after `Logging in user '…'` with the first 2 chars, and masks any
32+ hex/base64 token. The full raw log is **not** written to disk; the filtered log can be saved by the user.

## 6. Licensing

| Component | Status | What we do |
|---|---|---|
| **steamcmd** | Free from Valve; the Steamworks SDK terms do not grant redistribution | Never bundled. Downloaded by the user's explicit action from Valve's URL, or pointed at in the user's SDK. |
| **Steamworks SDK** | Requires a Steamworks partner account and the *Steamworks SDK Access Agreement* | Never bundled or downloaded by Skate. Optional source of steamcmd (§4). |
| `steam_api64.dll` | Redistributable with a game under the Steamworks agreement | Already shipped inside steamworks4j (MIT) for the runtime `SteamPlatformServices`. Unchanged. |
| **steamworks4j** | MIT | Unchanged. The publish feature does **not** use it (it is a runtime client API, not a SteamPipe uploader). |
| Partner agreement / app fee | The user's own Steam Direct app | Out of Skate's hands. The help article says the user needs a Steamworks app, a depot, and a build account with upload rights. |

The help article and the install modal carry a one-line notice: "steamcmd is Valve software, downloaded from Valve;
its use is governed by the Steam Subscriber Agreement and your Steamworks agreement." (to be checked by the human).

## 7. What the export needs for Steam

- **No `steam_appid.txt` in the depot.** It is a development aid; a Steam-launched game gets its app ID from the
  client. The exporter writes none today; the build script excludes it anyway.
- **Launcher.** Steam's launch option needs an executable. `run-game.bat` works, but it opens a console window and
  needs Java installed on the player's machine. A playable Steam build really needs **AST-31** (jpackage `.exe` +
  bundled runtime). v1 uploads whatever the export produces and warns when the launcher is a `.bat` (Q4).
- **Single file vs folder** (AST-43, the listed dependency): SteamPipe uploads folders and deduplicates 1 MB
  chunks, so it doesn't need a single jar. Separate `game.bin` + jar gives smaller patches, because a code-only change
  doesn't re-chunk the assets. So the Steam upload prefers AST-43's **separate-file mode** and supports both. AST-43
  only has to keep a deterministic file layout (stable names, no timestamps inside files that didn't change) so
  that SteamPipe deltas stay small. That's the request to AST.
- Steam staging export dir: `<project>/build/steam/<appid>/content/` (git-ignored), so the user's normal export folder
  is untouched.

## 8. Code layout and ownership

Pure logic in `engine/platform/steampipe/**` (owned by NET, no editor imports, unit-testable, reusable from the Skate
CLI), UI in the editor (written by NET under the "domain agents may write their own back end's UI" rule, reviewed
by `skate-editor`):

| Piece | Path | Notes |
|---|---|---|
| `SteamPublishTarget` (appId, depots, branch, descTemplate, preview) + validation | `engine/platform/steampipe/` | Project-level data class; serialized in the project settings (needs AST/EDT hook, §10). |
| `AppBuildScript` / `VdfWriter` | same | Deterministic VDF text (escaping `\` and `"`, fixed key order, CRLF). Golden-file tests. |
| `SteamCmdOutputParser` | same | Line → `SteamPipeEvent` (LoginOk, GuardCodeRequired, PasswordPrompt, SessionExpired, Progress(phase, done, total), BuildFinished(buildId), Error(msg)). Table-driven tests with captured sample output. |
| `SteamLogFilter` | same | Redaction (§5.4), tested. |
| `SteamCmdLocator`, `SteamCmdInstaller` | same | Behind `FileSystem`/`Http`/`InteractiveProcess` interfaces; fakes in tests. |
| `SteamPublisher` | same | State machine VALIDATING → EXPORTING → SIGNING_IN → UPLOADING → SETTING_LIVE → SUCCEEDED/FAILED/CANCELLED; driven by parser events; tested with a scripted fake steamcmd. |
| `SteamPublishWindow`, sign-in modal, install modal, menu entry | `editor/ui/windows/steam/` | Through the service layer (no commands needed: publishing is not undoable → `ExecuteOnlyCommand` if any project setting changes). |
| Strings | new area `editor/platform.properties` + `_fr` | Keys `steam.publish.*`, tooltips per EDT-05. |
| Help | `assets/help/{en,fr}/steam_publish.md` | Setup checklist for a Steamworks app and build account. |

Architecture guard: a new test asserts `engine/platform/steampipe/**` imports nothing from `editor.*` and that no
code in `steampipe` passes a password through a command-line argument (string check on `"+login"` argument count).

## 9. Implementation steps (after approval)

- **01a Spike** (needs the human: a Steamworks app with a test depot, a build account, and 30 minutes): run steamcmd
  by hand once, capture the full output of first login (password + Guard), cached login, `run_app_build` with
  `Preview 1` and with `SetLive` on a private branch, an expired/failed login, and a cancel. These captures become
  the parser's test fixtures and resolve every ⚠ UNVERIFIED marker. Without them the parser is guesswork.
- **01b** VDF writer, target model and validation, log filter, output parser (from 01a fixtures). Unit tests only.
- **01c** Locator, installer, interactive process, publisher state machine, all with fakes.
- **01d** Editor window, sign-in/install modals, menu + export-wizard entry, strings, help article (EDT review).
- **01e** (optional, TLG) `skate publish steam --project … --branch …` CLI command that reuses `SteamPublisher` and
  reads the password from stdin for CI.

Done when (from the backlog): a build uploads to a Steam branch from the editor. Verified by the human on their app.
Unit tests cover everything except the real steamcmd.

## 10. Cross-domain needs

- **AST**: keep a deterministic export file layout and expose an export-to-folder call the publisher can run
  (`ProjectExporter` already does; only the output dir differs). Add `SteamPublishTarget` to the project settings
  serialization, or give NET a typed per-project extension slot.
- **EDT**: review the window; add **File → Publish** submenu and a button on the export wizard success page.
- **AST-31**: needed for a build players can launch from Steam without Java (Q4).

## 11. Questions for the human

1. **Password storage.** OK to rely only on steamcmd's cached session (sign in once with password + Steam Guard, never
   stored by Skate)? Or do you also want "remember password" through Windows Credential Manager?
2. **steamcmd source.** OK for Skate to download `steamcmd.zip` from Valve's URL after you confirm (managed install
   in `%LOCALAPPDATA%\Skate\steamcmd`)? Or should it only use a steamcmd you point it at (e.g. from your Steamworks SDK)?
3. **Set live.** Should a successful upload set the chosen branch live automatically (`SetLive`), or upload only and
   let you set it live in Steamworks? Proposed: automatic for non-default branches, with a checkbox, default on.
4. **Launcher.** Should Steam upload wait for AST-31 (jpackage `.exe` + bundled Java), or ship v1 with the `.bat`
   launcher and a warning? Proposed: ship v1 with the warning; schedule AST-31 before the first public build.
5. **Depots.** Is one Windows depot enough for v1, or do you need several now (for example a separate DLC or
   soundtrack depot)?
6. **Spike.** Can you give 30 minutes with your Steamworks app (a test branch and a build account) for step 01a? No
   real upload is needed for most of it (`Preview` mode).
7. **CLI/CI.** Do you want the `skate publish steam` CLI command (01e) for automated uploads, or editor only?
8. **Task ID** (lead): keep this note as `NET-01-steam-publish.md`, or renumber the task (e.g. NET-07) so that
   NET-01 keeps meaning the multiplayer architecture?

## 12. Risks

- steamcmd output is not a stable API: the parser can break on a Valve update. Mitigation: unknown lines only
  go to the log; the outcome is decided by the BuildID line, the exit code, and an `ERROR` prefix. Progress
  falls back to indeterminate.
- steamcmd sometimes returns exit code 0 on failure (⚠ UNVERIFIED, widely reported). Success therefore requires a
  parsed BuildID.
- The first steamcmd run self-updates and can take minutes. The install step does it, not the first upload.
- Upload sizes: a full upload of a multi-GB `game.bin` is slow. The separate-file mode and deterministic layout
  (§7) keep later patches small.
