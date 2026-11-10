# EDT-60: Git version control in the editor

Status: **proposal, awaiting approval** · Owner: skate-editor · Priority: P2

⚠ marks a statement I could **not verify** while writing this (no network access; nothing prototyped).
Each one has to be checked during the first implementation step, before anything depends on it.

## In short

A **Version Control** window, built on JGit (a pure-Java Git library) so users do not need Git installed. It lets you:
- initialize a repository
- see what changed
- view diffs
- commit
- switch and create branches
- pull and push

The work ships in phases:
1. **Local only** (no network, no passwords).
2. **GitHub over HTTPS**, sign-in with the GitHub *device code* (no password typed into Skate).
3. **SSH keys.**
4. **Git LFS** for big binary assets, opt-in.

Credentials go **only** into the operating system's credential store. They are never written to project files,
`.git/config` or `editor.config`.

Two existing problems would make Git painful and should be fixed first (§7):
- Scene files most likely change on every save, because object ids come from a session counter.
- A merge conflict in a scene file would write `<<<<<<<` markers into JSON, which the loader cannot read.

---

## 1. What a Skate project looks like on disk (checked in the code)

| Path | What it is | In Git? |
|---|---|---|
| `<Name>.skateproject` | Project file; paths are relative (EDT-41) | ✅ commit |
| `Scenes/*.scene` | Scenes, pretty-printed JSON (`Serializer`, `prettyPrint = true`, `encodeDefaults = true`) | ✅ commit |
| `Assets/**` | Models, textures, sounds, scripts, prefabs, `strings.properties` | ✅ commit (big binaries: see §6) |
| `Assets/**/*.meta` | Asset GUID sidecars (`AssetRegistry`, "the `.meta` sidecar is the source of truth") | ✅ **must** commit; losing them breaks every reference |
| `game.settings.json` | The game's language and backend (EDT-39) | ✅ commit |
| `Builds/` | Export output (created by `ProjectManager.createProject`) | ❌ ignore |
| `.skate/` | **Does not exist yet.** Proposed home for per-user, per-project editor state (open scenes, per-scene editor camera from EDT-58, local Git UI state) | ❌ ignore |
| `Library/` | **Does not exist yet.** Proposed home for rebuildable caches (import cache, thumbnails; today they are only in memory) | ❌ ignore |
| `%APPDATA%\Skate\editor.config`, `imgui.ini` | Per-user editor settings and layout (`EditorPaths`) | outside the project; never in Git |

## 2. The Version Control window

A dockable **window** (EDT-51 kind rule: you keep it open while working), in a new **Project** category, docked at
the bottom by default.

```
┌ ⎇ Version Control ───────────────────────────────────────────── ? ┐
│ ⎇ main ▾   ↑2 ↓0  origin: github.com/me/skate-game   [Fetch] [Pull] [Push] │
├────────────────────────────┬───────────────────────────────────────┤
│ Changes (5)          [All] │  Scenes/MainScene.scene               │
│ ▾ Scenes                   │  - "translation": [0.0, 5.0, 0.0]     │
│   ☑ M MainScene.scene      │  + "translation": [2.0, 5.0, 0.0]     │
│ ▾ Assets                   │  …                                    │
│   ☑ A textures/rail.png    │  (binary file, 412 KB → 418 KB)       │
│   ☑ A textures/rail.png.meta│                                      │
│   ☐ M scripts/spin.kts     │                                       │
├────────────────────────────┴───────────────────────────────────────┤
│ Message: [ Add the rail texture                                   ] │
│ [Commit]  [Commit and push]          History ▸   Discard selected ▸ │
└────────────────────────────────────────────────────────────────────┘
```

| Feature | Behaviour |
|---|---|
| **Not a repository** | Empty state (EDT-51): "This project is not under version control." Buttons: **Initialize** and **Clone…**. Initialize creates `.git`, `.gitignore` (§4), `.gitattributes` (§6) and a first commit. |
| **Status** | Changed files grouped by Scenes, Prefabs, Scripts, Assets and Settings. A `.meta` file is shown and staged together with its asset; selecting one selects both. Refreshes when the window gains focus, after a save, and on the existing hot-reload file events. |
| **Diff** | Text files: unified diff with colors. Binary files: size before → after, plus an image preview for textures. Scenes: a text diff first; a readable summary ("Box: position changed") is a later phase (§7). |
| **Commit** | A message is required. Unsaved scenes are offered for saving first. Commits are refused during Play (EDT-45 state; same rule as `PlayModeGuards.canSaveScene`). The author is taken from Git's own config, otherwise asked once and stored in `editor.config`. |
| **History** | The log of the current branch (message, author, date, files). Selecting a commit shows its diff. "Restore this file from here" is allowed; history is never rewritten from the editor. |
| **Branches** | Dropdown: switch, new from current, delete (merged branches only). Switching is refused when there are uncommitted changes (Commit or Discard first). After a switch, the project reloads. |
| **Pull / Push / Fetch** | Background job with progress (the EDT-56 loading modal). Pull = fetch + merge (no rebase in the UI). Push sets upstream on first push. A rejected push says "Pull first". |
| **Discard** | Reverts the selected files after a confirmation modal. This is the only destructive action. |

**What changes on disk:** after checkout, pull or discard, the editor stops Play, reloads the changed scenes and
re-scans assets. If the open scene changed, it is reloaded; if it has unsaved edits, the operation is refused first.

**Out of scope:** rebase, cherry-pick, stash, submodules, tags, blame, interactive staging of individual lines.
The Shell window can still run `git` for these, when Git is installed.

## 3. Architecture

```
editor/vcs/
  VcsService.kt          interface: status, diff, commit, log, branches, checkout, fetch, pull, push, init, clone
  JGitVcsService.kt      the JGit implementation (the only file that imports org.eclipse.jgit)
  VcsStatusModel.kt      pure: groups files, pairs assets with .meta, decides what is enabled
  GitIgnoreTemplate.kt   the .gitignore / .gitattributes text
  auth/CredentialStore.kt     interface: get / put / delete by host
  auth/OsCredentialStore.kt   Windows Credential Manager (later macOS Keychain, Linux Secret Service)
  auth/GitHubDeviceFlow.kt    pure state machine plus a small HTTP adapter (java.net.http, no new library)
editor/ui/windows/VersionControlWindow.kt
editor/commands/vcs/*Command.kt
```

- **Commands:** Git actions cannot be undone with Ctrl+Z, so they are `ExecuteOnlyCommand`s with a stated reason.
  They are listed in `UndoCoverageGuardTest`, and the window publishes events like every other window
  (`UiMutationPipelineGuardTest`).
- **Threading:** JGit calls run on `JobSystem.runIO`, never on the frame. Results come back with `runOnMain`.
- **Layering:** nothing goes into `engine/`; exported games never contain JGit. An architecture guard keeps
  `org.eclipse.jgit` imports inside `editor/vcs/`.
- **Dependency:** `org.eclipse.jgit:org.eclipse.jgit` plus `org.eclipse.jgit.ssh.apache` (phase 3) and
  `org.eclipse.jgit.lfs` (phase 4).
  - ⚠ Pick the newest JGit line that supports Java 17. I believe JGit 7.x needs Java 17 and 6.x needs Java 11;
    check before adding.
  - ⚠ Check the JAR size added to the editor (JGit is several MB, plus Apache MINA for SSH).

## 4. Project `.gitignore`

Written by **Initialize**. If a `.gitignore` already exists, the Skate lines are added once, inside a marked block,
so the user's own lines are kept.

```gitignore
# --- Skate (managed by the editor; edit outside this block) ---
# Export output
Builds/
# Per-user editor state for this project
.skate/
# Rebuildable caches
Library/
# Temporary and crash files
*.tmp
*.bak
# OS files
Thumbs.db
desktop.ini
.DS_Store
# --- end Skate ---
```

- `.meta` files are **not** ignored. A test checks that no `.gitignore` line matches `*.meta`, `*.scene` or
  `*.skateproject`.
- `.skate/` and `Library/` are reserved now, so later features (EDT-58 camera, a thumbnail cache) put their files
  there. That needs a short convention note in `docs/design/conventions.md`.

## 5. Signing in

**Rule:** a token or password is never written to a project file, `.git/config`, a remote URL, `editor.config` or a
log. Remote URLs are stored without credentials. If the user pastes `https://user:token@host/…`, the editor removes
the credentials, offers to store them in the OS store, and says so.

| Method | Phase | How it works |
|---|---|---|
| **GitHub device code** (recommended) | 2 | The editor asks GitHub for a short code, shows "Open github.com/login/device and enter **ABCD-1234**" with a **Copy code** button, and checks every few seconds until you approve. The resulting token goes to the OS store. No password ever reaches Skate. |
| **OS credential store** | 2 | Windows Credential Manager first, under the entry `skate:git:<host>`. Used by every HTTPS remote, GitHub or not. A **Sign out** button deletes the entry. |
| **Other HTTPS hosts** | 2 | A username and personal-access-token modal, stored in the OS store. |
| **SSH keys** | 3 | Uses `~/.ssh` keys and `~/.ssh/config`, and asks for the key passphrase in a modal, kept in memory only. Shows "Add this host?" with the fingerprint on first connect. |

Device flow details:
- ⚠ The endpoints, as documented by GitHub:
  - POST `https://github.com/login/device/code` with `client_id` and `scope`
  - poll POST `https://github.com/login/oauth/access_token` with
    `grant_type=urn:ietf:params:oauth:grant-type:device_code`
  - poll responses: `authorization_pending`, `slow_down` (add 5 s to the interval), `expired_token`, `access_denied`
- It needs a **GitHub OAuth App owned by the project, with device flow enabled**. Its `client_id` is public and can
  ship in the editor; there is no client secret.
  - ⚠ Who owns the app is a decision for you (§9).
  - ⚠ The scope is `repo`, which grants access to **all** of the user's repositories. A GitHub App with per-repository
    permissions is narrower but more work to set up; I need to check that it supports device flow for Git over HTTPS.
- ⚠ How the token is sent over HTTPS: I believe GitHub accepts it as the *password* with any non-empty username
  (e.g. `x-access-token`); check this.
- ⚠ OS store access from the JVM. Options:
  - JNA's `WinCred` / `Advapi32` binding: JNA is probably already on the classpath through LWJGL or GraalVM; check.
  - The `java-keyring` library, which covers Windows, macOS and Linux.
  - Calling `git credential fill/approve` when Git is installed.

  I propose JNA on Windows (the editor is Windows-only today, since its natives are). When no store is available,
  credentials are kept **in memory for the session only**, with a notice.
- ⚠ The JGit SSH module (Apache MINA based) reads `~/.ssh/config`, and since JGit 6 it can use an SSH agent, including
  Pageant on Windows. Check both on Windows with an ed25519 key.

## 6. Large binary assets (Git LFS)

- **Default: off.** Most small projects stay well under GitHub's limits.
  - ⚠ I believe GitHub warns at 50 MB per file and rejects files over 100 MB.
  - ⚠ Its LFS free quota has changed over time; check the current numbers before quoting them in the UI.
- **Warnings without LFS:**
  - Before a commit, list staged files over **50 MB** with "This file is too big for GitHub without Git LFS. Enable
    LFS for this type?".
  - A one-time hint when the repository's binary assets pass about 500 MB.
- **Opt-in at Initialize** (a checkbox) or later in Project Settings. Enabling it writes `.gitattributes` rules for
  `*.png *.jpg *.tga *.hdr *.wav *.ogg *.mp3 *.fbx *.glb *.gltf *.obj *.blend` as `filter=lfs diff=lfs merge=lfs -text`.
  Already-committed files are **not** rewritten (no history surgery); only new versions go to LFS.
- **Always** (with or without LFS), `.gitattributes` contains:
  - `* text=auto`
  - `*.scene text eol=lf`, `*.prefab text eol=lf`, `*.meta text eol=lf`, `*.skateproject text eol=lf`
  - the binary types listed above as `binary`

  This keeps Windows line endings out of diffs.
- ⚠ JGit's LFS module handles clean/smudge in-process. Check push and fetch of LFS objects against GitHub's LFS
  server, and check that a clone by command-line Git sees the same files. If JGit's LFS support is not good enough,
  LFS needs Git installed; the UI says so and the feature stays off.

## 7. Scenes, prefabs and merge conflicts

### 7.1 Save the same scene twice → same bytes (do this first)

- `GameObject.uId` comes from a session counter (`var uId = ID_COUNTER++`) and is not `@Transient`.
  `PrefabService` already ignores `uId` when it compares objects, which suggests it is saved.
- ⚠ If it is saved, re-saving an unchanged scene after a restart writes different ids. Every commit then touches
  every object, and two people editing different objects conflict on the id lines.
- **Fix** (for ECS/AST, not EDT): give objects a persistent id, assigned once and kept on load, the way assets
  already have GUIDs. Alternatively, stop saving the session id.
- Add a guard test: load → save → load → save gives identical bytes, for MainScene and the Feature Showcase.
- Other sources of noise to check in the same test: map/set ordering in components, float formatting, and timestamps.
  The `.skateproject` `lastOpenedDate` is only set at creation today, so it is fine.

### 7.2 Conflicts

- Text conflict markers inside a `.scene`, `.prefab`, `.meta` or `.skateproject` file would make it unreadable. So
  after a pull or merge, the editor handles conflicts in these files **before** anything loads them:
  - The file is put back to **our** version on disk (still marked as conflicted in Git), and the Version Control
    window shows a **Resolve** list.
  - Each file offers **Keep mine**, **Take theirs**, or **Open theirs as a copy** (saved as
    `MainScene (theirs).scene`, so you can copy objects across by hand), then **Mark resolved**.
  - Scripts and other text files get the same three choices, plus **Open in Script Editor** with the markers kept.
- The loader also refuses any scene or prefab that contains `<<<<<<<`, with a clear error (EDT-10 error dialog)
  instead of a JSON parse error. This protects against merges done outside the editor.
- **Later:** a structured three-way merge of scenes, object by object, keyed by the persistent id from §7.1.
  Different objects merge automatically; only the same property changed on both sides becomes a conflict. It needs
  §7.1 first and is its own task.
- ⚠ Whether JGit honours a `merge=binary` attribute in `.gitattributes` (which would stop it writing markers in the
  first place). The plan above works either way.

## 8. Tests

All are unit tests on temporary folders. There is no network: a local bare repository stands in for the remote, as
a `file://` URL.

| Area | Tests |
|---|---|
| Initialize | Creates `.git`, `.gitignore` and `.gitattributes`. Appends the Skate block to an existing `.gitignore` exactly once. Never ignores `.meta`, `.scene` or `.skateproject`. |
| Status model | Groups by kind; pairs an asset with its `.meta`; files under `Builds/`, `.skate/` and `Library/` never appear. |
| Commit and history | Commit with a message; empty message refused; refused during Play; the log shows the commit. |
| Branches | Create, switch, delete merged; switch refused with uncommitted changes. |
| Remote | Push to a bare repository, clone it, pull a new commit; rejected push → "pull first". |
| Conflicts | A conflicting `.scene` is restored to our version and listed. Keep mine, Take theirs and Copy each do the right thing. The loader rejects conflict markers. |
| Credentials | A fake `CredentialStore` receives the token. Nothing secret is written under the project folder (scan every file after sign-in). Credentials pasted in a URL are removed. |
| Device flow | State machine with a fake HTTP client: pending → success; `slow_down` raises the interval; `expired_token` and `access_denied` end it with a message. |
| Determinism (ECS/AST) | Save → load → save gives identical bytes (§7.1). |
| Guards | `org.eclipse.jgit` only under `editor/vcs/`; Git commands listed as execute-only. |

## 9. Phases, owners and decisions for you

| Phase | Content | Owner |
|---|---|---|
| 0 | Persistent object ids and the identical-bytes save test (§7.1) | **ECS / AST** (needed before phase 1 is useful) |
| 1 | Local: init, status, diff, commit, history, branches, discard, conflict-safe loading | EDT (+ lead or QA adds the Gradle dependency) |
| 2 | HTTPS remotes, OS credential store, GitHub device flow, clone from the project wizard (with EDT-59) | EDT |
| 3 | SSH keys | EDT |
| 4 | Git LFS opt-in | EDT + AST |
| 5 | Structured scene merge and readable scene diffs | EDT + ECS |

**Decisions I need from you:**
1. **The GitHub OAuth App:** who registers and owns it (it identifies "Skate" on GitHub's consent screen)?
   And is the broad `repo` scope acceptable, or should we spend the extra effort on a GitHub App?
2. **Do we need Git installed?** I propose **no** (pure JGit). If JGit's LFS support turns out weak, LFS alone would
   require Git.
3. **LFS off by default** with size warnings: OK?
4. **Conflict handling:** whole-file Keep mine / Take theirs / Copy for now, structured merge later: OK?
5. **Phase 0 goes to ECS/AST** before EDT starts phase 1: OK?

## 10. Phase 1 as built (2026-11-10)

- **Dependency:** JGit `7.1.0.202411261347-r` (Java 17). `build.gradle.kts` leaves `org.eclipse.jgit*` jars out of
  the game jar (`isEditorOnlyLib`); `VcsLayeringGuardTest` keeps JGit imports in `editor/vcs/JGitVcsService.kt`.
- **Code:** `editor/vcs/` (`VcsService`, `JGitVcsService`, `VcsStatusModel`, `VcsSession`, `GitIgnoreTemplate`,
  `ConflictMarkers`), `editor/commands/vcs/VcsCommands.kt` (execute-only), `VcsActionHandler`, `VersionControlWindow`
  (View > Windows > Project, docked at the bottom). Strings: `assets/strings/editor/vcs*.properties`.
- **Choices made without a human decision (easy to change):**
  - The commit author comes from Git's config, or else the OS user name (`<user>@users.noreply.skate`). The "ask
    once" modal waits for phase 2, when an identity matters for pushes.
  - A commit takes exactly the selected rows (and their `.meta`); the index is reset to HEAD first, except during a
    merge, so the merge commit keeps both parents.
  - A branch switch is refused while *tracked* files have changes; untracked files do not block it.
  - Git actions are refused during Play. Switch, discard and resolve are also refused while a scene has unsaved edits.
    After a switch, the project reloads. After a discard or resolve, it reloads only when scenes, prefabs or project
    files changed.
  - The repository must be `<project>/.git`. A project inside another repository (e.g. the engine checkout) counts as
    not versioned.
- **Conflict-safe loading:** the editor's scene decoders (`ProjectManager`, `OpenSceneFileCommand`) refuse files with
  conflict markers, with a clear message. `PrefabStore` (engine, AST) does not check yet.
