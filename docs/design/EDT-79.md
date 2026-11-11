# EDT-79: Git branch button in the status bar (replaces the Version Control window)

Status: **approved 2026-11-10** (decisions below) · Owner: skate-ecs (assigned by the lead; editor code; skate-editor
reviews the status bar and dialog look) · Priority: P2 · Deps: EDT-60

## Decisions (human, 2026-11-10)

The answers to §9. Where they differ from the proposal, they win over the rest of this note, and the sections below
have been updated to match.

| Q | Decision |
|---|---|
| 1 | The branch button is the **last item at the far right** of the status bar, after the scene name. |
| 2 | **One dialog** for Commit… and Changes…. |
| 3 | The Commit dialog **never closes by itself** after a commit; the user closes it. |
| 4 | Commit and Conflicts dialogs (and the Git log window) are **non-modal**; real modals only for confirmations. |
| 5 | Update Project is **shown but disabled**, tooltip "Needs a remote". |
| 6 | Merge into current is a **separate follow-up: EDT-83**. |
| 7 | **Keep the dockable Version Control window**, reworked to behave like IntelliJ's Git log tool window: a commit list with a graph/branch column, author, date and message; selecting a commit shows its changed files and their diffs; filter by branch and by text. It **replaces the planned History dialog**; the popup's *History…* opens it. |
| 8 | UI in `editor/ui/windows/vcs/`, models in `editor/vcs/`. |
| 9 | `EditorError` modal for refusals and failures, `EditorToast` for success. |
| 10 | skate-ecs implements it. |

Out of scope: IntelliJ-style left/right tool window bars (EDT-84).

## In short

The human asked for an IntelliJ-style branch button in the status bar. It takes over the everyday work of the EDT-60
phase 1 Version Control window, which stays as an IntelliJ-style Git log (Q7).

- **The button** sits in the bottom status bar and shows the current branch and how many files changed: `⎇ main ●3`.
- **Its popup** lists the branches with Checkout, New and Delete, plus entries for Update, Commit…, Changes…, History…
  and Resolve Conflicts….
- **Where the window's work goes:**

  | Work | New home |
  |---|---|
  | Changes list, diff, commit, discard | A **Commit dialog** (Ctrl+K). It is also the "Changes" view. |
  | History and per-commit diffs | The **Version Control window**, reworked into a Git log (graph, filters) |
  | Resolving conflicted files | A **Conflicts dialog**, which opens by itself when conflicts appear |
  | Initialize, new branch, delete branch, discard | Small confirmation modals |

- **What is reused unchanged:** EDT-60's service layer, model, session, commands, handler, `.gitignore` template,
  conflict-marker guard and architecture guards. They are untouched except for one new read (§5.3, conflict sides) and
  a few refresh triggers (§6).
- **Diff display** moves into one shared `DiffView`, so the dialogs and the Git log draw diffs the same way.

Nothing new in this note needs a network or credentials. "Update" (pull) stays disabled until EDT-60 phase 2 adds
remotes.

---

## 1. What exists today (checked on integration/32)

| Piece | File | Fate |
|---|---|---|
| Git operations on JGit | `editor/vcs/VcsService.kt`, `JGitVcsService.kt` | **kept**; one read added (§5.3) |
| Grouping, `.meta` pairing, commit/refusal rules | `editor/vcs/VcsStatusModel.kt` | **kept** |
| Background reads/actions, state for the UI | `editor/vcs/VcsSession.kt` (`VcsRunner.Jobs`) | **kept**; refresh triggers added (§6) |
| Execute-only Git commands | `editor/commands/vcs/VcsCommands.kt` | **kept** (still listed in `UndoCoverageGuardTest`) |
| `VcsAction` events → commands, Play/unsaved-scene refusals, reload after checkout | `editor/events/VcsAction.kt`, `ui/handlers/VcsActionHandler.kt` | **kept** |
| `.gitignore` / `.gitattributes`, loader conflict-marker guard | `GitIgnoreTemplate.kt`, `ConflictMarkers.kt` | **kept** |
| The dockable window (changes, diff, commit, history, branches, resolve) | `ui/windows/VersionControlWindow.kt` | **reworked into the Git log** (slice 5, Q7); changes, commit, branches and resolve move out as below |
| View > Windows > *Project* category (only this window) | `imgui/WindowCategory.kt` | kept |
| Default dock zone | `imgui/DefaultDockLayout.kt` | kept |
| Status bar: FPS, memory, toast, scene name | `imgui/EditorStatusBar.kt` (`STATUS_BAR_HEIGHT` 30 px) | gets the branch button |

The status bar is drawn by `WindowManager` through `windowRegistry.statusBar`. It sits under `editor/imgui`, so the
UI-mutation guard applies: the button and popup only **publish events** (`VcsAction`, `WindowAction`). They never call
commands or the service directly.

## 2. The status bar button

```
 60 FPS | 812 / 2048 MB | Screenshot saved              Scene: MainScene   ⎇ main ●3
                                                                    └─ button ─┘
```

- **Place:** the last item at the far right, after the scene name (Q1). It is a frameless `smallButton`, so the bar
  keeps its height.
- **Label states.** All label text comes from string keys; the label gets a stable ImGui ID (`###vcs_branch_button`)
  so it can change freely.

  | State | Label | Tooltip |
  |---|---|---|
  | No project open | (hidden) | — |
  | Not a repository | `⎇ Git: off` (dimmed) | "This project is not under version control. Click to initialize." |
  | Clean | `⎇ main` | "Branch main · no changes" |
  | Changes | `⎇ main ●3` | "Branch main · 3 changed files" |
  | Busy | `⎇ main …` | "Working…" |
  | Conflicts | `⎇ main ⚠2` (danger colour) | "2 files have conflicts. Click to resolve." |
  | Detached HEAD | `⎇ a1b2c3d` | "Not on a branch (commit a1b2c3d)" |
  | Read error | `⎇ main ⚠` | the error text |

- **Click:** opens the branch popup (§3). The one exception: in the *Conflicts* state, a click opens the Conflicts
  dialog directly.
- **Right-click:** the same popup, so there is no second menu to learn.
- The label is computed by a pure `BranchButtonModel(state) → label key, args, colour, tooltip key`, which can be unit
  tested.

## 3. The branch popup

An ImGui popup anchored above the button, about 320 px wide, closed with Esc or a click outside. It follows the
EDT-51 §2.3 menu order: actions first, then the list, destructive entries last.

```
┌──────────────────────────────────────┐
│ 🔍 [ Search branches            ]    │
│ ⟳  Update Project            (off)   │  ← disabled, tooltip "Needs a remote" (Q5)
│ ✓  Commit…   3 files        Ctrl+K   │
│ ≡  Changes…                          │  ← same dialog as Commit
│ ⚠  Resolve Conflicts…   2            │  ← only while conflicts exist
│ 🕘 History…                          │  ← opens the Version Control (Git log) window
│ ＋ New Branch…                       │
│ ───────── Local ─────────            │
│ ★ main                    (current)  │
│   feature/rails                  ▸   │──┐ Checkout
│   experiment                     ▸   │  │ New Branch from "feature/rails"…
│                                      │  │ ──────
│                                      │  └ Delete…
└──────────────────────────────────────┘
```

- **Search** filters the branch list (case-insensitive substring). Enter on a single match checks it out. The filter
  is the pure `BranchPopupModel.filter`.
- **Current branch:** marked, with no submenu except *New Branch from here…*.
- **Other branches** have a submenu:
  - *Checkout*: publishes `VcsAction.SwitchBranch`. The existing refusals still apply: Play, unsaved scenes,
    uncommitted tracked changes (`vcs.error.uncommitted_changes`).
  - *New Branch from "x"…*: opens the New Branch modal with "from x". This needs `createBranch(name, from)` (§5.3).
  - *Delete…*: opens a confirmation modal (Danger button). Unmerged branches are still refused by JGit
    (`vcs.error.not_merged`).
- **Not a repository:** the popup shows only *Initialize Repository…* (a confirmation modal that explains the
  `.gitignore`, then `VcsAction.Initialize`) and a disabled *Clone…* with tooltip "Comes with remotes (EDT-60 phase 2)".
- **Busy** (`VcsSession.state.busy`): every action is disabled, with the tooltip "Working…".
- **Enabling rules** live in the pure `BranchPopupModel`. They reuse `VcsStatusModel.actionRefusal` and
  `commitBlockedReason`, so the popup, the dialogs and the handler cannot disagree.

**Reporting results (Q9):**
- **Success:** an `EditorToast` in the status bar, e.g. "Switched to feature/rails" or "Committed a1b2c3d".
- **Refusal or failure** of an action, wherever it came from (popup, dialog, window): the standard `EditorError`
  modal (EDT-10), so one rule covers every place. `VcsSession` reports action failures through a callback that the
  handler turns into `EditorError`; it no longer keeps them in `state.error`.
- **Read errors** (a failed `git status` during a refresh) are not actions: they show as `⎇ main ⚠` on the button,
  with the text in its tooltip, so a broken repository does not raise a modal every second.

## 4. Where the window's content goes

### 4.1 Commit dialog (also "Changes…")

An `EditorDialog` named `window.vcs_commit`, resizable, size remembered, 900×600 on first use. It is **not modal**,
so you can keep editing scenes while it is open, like IntelliJ's commit tool window. Opened from Commit… and Changes…
in the popup, from Ctrl+K and from Search Anywhere.

```
┌ ✓ Commit Changes ─────────────────────────────────────────────── ? ┐
│ ⎇ main   [⟳]                                                       │
├──────────────────────────────┬─────────────────────────────────────┤
│ ☑ All (3)                    │ Scenes/MainScene.scene               │
│ ── Scenes (1) ──             │ @@ -12,3 +12,3 @@                    │
│ ☑ M MainScene.scene          │ -  "x": 1                            │
│ ── Assets (2) ──             │ +  "x": 2                            │
│ ☑ A rail.png  (+ .meta)      │                                      │
│ ☐ M spin.kts                 │                                      │
├──────────────────────────────┴─────────────────────────────────────┤
│ Message                                                            │
│ [ Add the rail texture                                           ] │
│ 2 files selected        [Discard…]            [Cancel] [Commit]    │
└────────────────────────────────────────────────────────────────────┘
```

- **Content:** exactly the window's *Changes* tab, moved here.
  - The list uses `VcsStatusModel.entries/grouped/pathsFor`, with `.meta` files paired to their asset and new files
    selected by default.
  - The right side is the shared `DiffView` (§4.4).
  - Discard opens the existing discard confirmation.
- **Footer** (EDT-51 §2.6): *Discard…* is SECONDARY, *Cancel* closes, *Commit* is PRIMARY.
  - Commit stays disabled with a tooltip giving the reason (`commitBlockedReason`).
  - Ctrl+Enter in the message box commits.
  - After a successful commit, a toast reports it and the message is cleared. The dialog **never closes by itself**
    (Q3); the user closes it.
- **Unsaved scenes:** the dialog shows a notice "MainScene has unsaved changes" with a *Save all* button. EDT-60 §2
  said "unsaved scenes are offered for saving first", and the window never did this.
- **Conflicts:** while any exist, the list shows only a banner "Resolve conflicts first" with a button that opens the
  Conflicts dialog. The commit rule stays as before.

### 4.2 Version Control window = Git log (Q7)

The dockable `window.version_control` stays (View > Windows > Project, default dock zone) and becomes IntelliJ's Git
log tool window. It replaces the History dialog the proposal had.

```
┌ ⎇ Version Control ──────────────────────────────────────────────────────── ? ┐
│ Branch [All ▾]  🔍 [ text filter              ]  [⟳]                         │
├──────────────────────────────────────────────────────┬───────────────────────┤
│ ●  [main] Add the rail texture     Alex   10/11 14:02│ Scenes/Main.scene     │
│ │ ● [feature] Grind tweaks         Alex   10/11 12:40│ Assets/rail.png       │
│ ●─┘ Initial commit                 Alex   09/11 09:15├───────────────────────┤
│                                                      │ DiffView of the file  │
└──────────────────────────────────────────────────────┴───────────────────────┘
```

- **Commit list:** a table with a **graph column** (one lane per line of history, dots and connecting lines drawn
  with the draw list, branch tips as `[name]` labels, the current branch highlighted), the message's first line,
  author and date. It shows the history of **every local branch**, newest first.
- **Selecting a commit** lists its changed files on the right; clicking a file shows its `DiffView` below them.
- **Filters:** a branch combo (*All*, or one branch: only commits reachable from its tip) and a text field that
  matches message, author or commit id (case-insensitive).
- **Empty states:** no project, not a repository (button that opens the Initialize modal), no commits.
- Read-only, as in EDT-60. "Restore this file from here" stays out of scope.
- Pure models: `CommitGraph.layout(commits)` (lanes per row, parents from the commits) and `GitLogFilter` (branch
  reachability, text match), both unit-tested. The service log gains parents and branch tips (§5.3).

### 4.3 Conflicts dialog

An `EditorDialog` named `window.vcs_conflicts`.

**Opening:** it opens by itself when a refresh first sees conflicts. It does not reopen for the same set of files
after the user closes it; the status bar button stays red. It can also be opened from the popup or the button.

```
┌ ⚠ Resolve Conflicts ──────────────────────────────────────────── ? ┐
│ 2 files changed on both sides. Choose a version for each file.     │
├──────────────────────────────┬─────────────────────────────────────┤
│ ! Scenes/MainScene.scene     │  Mine (main)   |  Theirs (feature)  │
│ ! Assets/rail.png.meta       │  diff of mine against theirs        │
├──────────────────────────────┴─────────────────────────────────────┤
│                            [Keep mine]  [Take theirs]    [Close]   │
└────────────────────────────────────────────────────────────────────┘
```

- **Choices:** the same whole-file choices as EDT-60 (human decision): *Keep mine* and *Take theirs*, which publish
  `VcsAction.Resolve`. Both act on the selected file; all files can be selected at once.
- **Preview:** the right side shows the two versions as a `DiffView` (mine against theirs). That needs one new
  read-only service call (§5.3). Binary files show both sizes.
- **After resolving:** when the last conflict is gone, the dialog shows "All conflicts resolved. Commit the merge."
  with a button that opens the Commit dialog.
- **Loader guard:** `ConflictMarkers` still refuses to load scenes with conflict markers, so a scene cannot be opened
  half-merged while the dialog is closed.

### 4.4 Shared `DiffView`

A small renderer, `editor/ui/windows/vcs/DiffView.kt`, extracted from the window's `diffView()`:
- text hunks with added and removed colours from the theme (EDT-52 colours instead of hard-coded RGB);
- binary files as "size before → after";
- "No differences".

The dialogs and the Git log use it. Its line-colour mapping is a pure function and gets a test.

### 4.5 Small modals

All are `EditorModal`s with the standard footer:

| Modal | Content | Main button |
|---|---|---|
| Initialize Repository | what gets created (`.git`, `.gitignore`, `.gitattributes`, first commit) | *Initialize* (PRIMARY) |
| New Branch | name field (validated live with the same rule as `createBranch`), "from: x", ☑ "Switch to it" | *Create* |
| Delete Branch | "Delete branch x? Only merged branches can be deleted." | *Delete* (DANGER) |
| Discard Changes | the existing discard modal, moved into the Commit dialog | *Discard* (DANGER) |

## 5. Code layout

### 5.1 New files

```
editor/vcs/                (pure, no ImGui)
  BranchButtonModel.kt     label key/args, colour role, tooltip, click target from VcsSession.State
  BranchPopupModel.kt      entries, enabled/disabled + reason, branch filter
  CommitGraph.kt           graph lanes of the Git log
  GitLogFilter.kt          branch and text filters of the Git log
editor/ui/windows/vcs/     (already under the UI-mutation, tooltip, label and stable-ID guards)
  BranchStatusButton.kt    draws the button + popup inside EditorStatusBar; publishes VcsAction / opens dialogs
  CommitDialog.kt          EditorDialog: changes list, DiffView, message, Commit/Discard
  ConflictsDialog.kt       EditorDialog: conflicted files, mine/theirs preview, Keep mine / Take theirs
  VcsModals.kt             Initialize, New Branch, Delete Branch modals
  DiffView.kt              shared diff renderer
```

- **Folder:** `editor/ui/windows/vcs/` already sits under the guard roots (`ui/windows`, `ui/menus`, `imgui`,
  `search/providers`), so no guard needs changing (open question Q8).
- **Registration:** the dialogs are registered in `WindowRegistry` like the other `EditorDialog`s. They open through
  `WindowAction.Show("window.vcs_commit")` and similar events, so the status bar, the shortcut and Search Anywhere use
  one path. Each needs `help.vcs_commit.what/how` (and so on) in EN and FR (`WindowStandardGuardTest`).

### 5.2 Wiring

- **`EditorStatusBar`** takes the `VcsSession` (passed from `Editor` through the registry, as the window gets it today)
  and draws `BranchStatusButton` before the scene name.
- **`VcsActionHandler`:**
  - publishes `EditorError` for refusals when no dialog is open;
  - publishes `EditorToast` on success (§3);
  - handles `VcsAction.CreateBranch(name, from, checkout)`. The new fields have defaults, so existing calls keep
    working.
- **Ctrl+K** (if free; it is checked in slice 3, and it can be rebound in Key Bindings) is a new `EditorShortcuts`
  entry that opens the Commit dialog.

### 5.3 Service additions (small, read-mostly)

| Call | Why |
|---|---|
| `conflictSides(path): Pair<ByteArray?, ByteArray?>` (index stages 2 and 3) | Conflicts dialog preview |
| `createBranch(name, from: String? = null, checkout: Boolean = true)` | "New branch from x" and "don't switch" |
| `VcsSession.State.headDetached` / short id | button label in detached HEAD |
| `log(limit, allBranches = true)` with `VcsCommit.parents`; `VcsBranches.tips` (branch → commit id) | Git log graph and branch filter (Q7) |

Each gets unit tests in `JGitVcsServiceTest` (a conflict created by a real merge, as the existing tests already do).

## 6. Refresh without a window

The window refreshed when it gained focus. The button needs fresh counts without that, so `VcsSession` refreshes on:
- opening the project (`followProject`, as now);
- opening the popup or any VCS dialog;
- the editor's OS window regaining focus (the GLFW focus callback, published as an editor event);
- the existing `FileSystemChanged` hot-reload events, scene saves (`SceneAction` saved) and the end of every VCS
  command (already done).

Refreshes are **debounced to one per second** (`VcsSession.requestRefresh()` coalesces). A large project does not
re-run `git status` on every file event. Reads stay on `JobSystem.runIO`.

## 7. Reworking the window (slice 5, Q7)

The window keeps its name, category, dock zone and help article (rewritten for the Git log). Removed from it: the
Changes tab, the commit bar, the branch combo, the new-branch popup and the resolve buttons, which now live in the
popup and dialogs. Window-only keys that are no longer used (`vcs.tab.*`, `vcs.branch.tooltip`, …) are deleted.

`ImGuiStableIdGuardTest`, `WindowStandardGuardTest`, `DefaultDockLayoutTest`, `EditorStringKeysTest` and
`StringTablesTest` must stay green. A saved `imgui.ini` that still names the old window is harmless, since ImGui
ignores unknown windows.

## 8. Slicing

Each slice builds, passes all tests and is committed on its own.

| Slice | Content | Tests |
|---|---|---|
| 1 | `BranchButtonModel`, `BranchPopupModel`, `DiffView` extracted (the window uses it too), debounced refresh triggers | models, refresh debounce, DiffView colour mapping |
| 2 | Status bar button + popup: branch list, search, checkout, new (from), delete; Initialize; toasts/errors; `createBranch(from, checkout)` | popup enabling rules, CreateBranch handler, service tests |
| 3 | Commit dialog (changes, diff, discard, commit, unsaved-scene notice), Ctrl+K, Search Anywhere "Commit…" | handler/command tests, commit-dialog selection model |
| 4 | Conflicts dialog + `conflictSides`, auto-open once per conflict set | `conflictSides` on a real merge, auto-open rule |
| 5 | Version Control window reworked into the Git log: all-branch log with parents and tips, graph column, branch and text filters, commit files and diffs | `CommitGraph`, `GitLogFilter`, all-branch log service test |

**Done when** (from the backlog, adjusted by Q7): every EDT-60 phase 1 action is reachable from the status bar
workflow, the window no longer holds those actions and works as a Git log, and tests cover the models, the new service
calls and the handler paths.

| EDT-60 phase 1 action | New place |
|---|---|
| Initialize | popup (not a repository) → modal |
| Status | button count + Commit dialog |
| Diff | Commit, History and Conflicts dialogs (`DiffView`) |
| Commit | Commit dialog, Ctrl+K |
| History | Version Control window (Git log) |
| Branches: create / switch / delete | popup |
| Discard | Commit dialog → modal |
| Resolve conflicts | Conflicts dialog |
| Conflict-safe loading | unchanged (`ConflictMarkers`) |

## 9. Open questions (answered: see Decisions at the top)

1. **Where does the button go?** Recommend **right side, before the scene name**, as in IntelliJ. The left side holds
   FPS, memory and toasts, which grow and shift.
2. **One dialog for "Commit…" and "Changes…"?** Recommend **yes**. IntelliJ's commit view *is* the changes view; two
   near-identical dialogs would drift apart. Both entries stay in the popup because the human listed both.
3. **Does the Commit dialog close after committing?** Recommend: **close when nothing is left to commit, otherwise stay
   open** with the remaining files, so several small commits in a row stay quick.
4. **Modal or not?** Recommend **non-modal** for Commit, History and Conflicts (you may need to look at a scene while
   deciding), and real modals only for the confirmations (Initialize, Delete branch, Discard).
5. **"Update Project" before remotes exist?** Recommend **shown but disabled**, with the tooltip "Needs a remote
   (EDT-60 phase 2)". The popup layout is then final, and phase 2 only enables it. Hiding it would hide a request the
   human made.
6. **"Merge into current" in the branch submenu**, as in IntelliJ? EDT-60 phase 1 had no merge. Recommend a **separate
   follow-up task** once this lands; the Conflicts dialog is then ready for it. Merging is where users lose work, so it
   deserves its own review.
7. **Keep a dockable view for people who liked the window?** Recommend **no**. The dialogs are resizable and remember
   their size, and two UIs for the same thing double the tests and the strings.
8. **Folder for the new UI:** a new `editor/ui/vcs/` (which would need adding to the guard roots) or
   `editor/ui/windows/vcs/` (already guarded)? Recommend **`editor/ui/windows/vcs/`** (used in §5.1): no guard change,
   and these are windows (dialogs) anyway. The pure models live in `editor/vcs/`.
9. **How errors from the popup show:** recommend the **`EditorError` modal for refusals and failures** and an
   **`EditorToast` for success**. A toast alone is too easy to miss for "your branch was not switched".
10. **Who implements it?** The lead gave EDT-79 to skate-ecs, but the code is editor-domain (`editor/ui`, status bar,
    dialogs). Recommend keeping it with skate-ecs for continuity with EDT-60, with skate-editor reviewing the status
    bar and dialog look (EDT-51/52 conformance).

## 10. Implementation notes (2026-11-10)

How the build differs from the sections above, and why:

- **Conflict preview:** the service call is `conflictDiff(path): FileDiff` (stage 2 against stage 3) instead of
  `conflictSides`. The text diff lives next to JGit (`TextDiff`), which only `JGitVcsService` may import.
- **Resolve several files:** `VcsAction.Resolve` takes a list of paths and resolves them in one Git action. One action
  per file would be refused as "busy" after the first one.
- **Per-view diffs:** each view owns a `DiffSlot` (Commit dialog, Conflicts dialog, Git log), so an open dialog and the
  window never overwrite each other's diff. A slot ignores a result that arrives after the view asked for another file.
- **Errors (Q9):** every refused or failed action, from any place, goes through `VcsSession.onActionFailed` to an
  `EditorError` (with `messageArgs`, a new defaulted field). `state.error` keeps only read errors (button ⚠ + tooltip).
  Successes become toasts through the handler's `notify`.
- **Refresh triggers (§6):** `requestRefresh()` is coalesced to one read per second in `followProject()` (run every
  frame by the branch button). Triggers: popup or dialog opened, the Git log window focused, the OS window regaining
  focus (ImGui's `appFocusLost`, so no GLFW hook was needed), the last unsaved scene saved, `SceneAction.Saved`,
  `ProjectEvent.Saved`, `FileSystemChangedEvent` and `ScriptEditorAction.Saved`.
- **Git log graph:** `CommitGraph` gives each row its dot lane plus the lanes coming in from above, going out below
  and passing through. The first parent always continues in the commit's lane, so the main line stays straight. With a
  text filter on, the kept rows are no longer adjacent, so the graph column is hidden.
- **Search Anywhere:** "Commit..." and "Git History". Checkout and New Branch stay in the popup, which has the branch
  list and the name modal.
- **Status-bar labels** avoid glyphs missing from the editor font (`●`, `⚠`): `main · 3`, `main · 2 conflict(s)`,
  `main · !`, after the branch icon.
- **skate-editor review fixes:** the theme gained `success`, `warning` and `danger` colours (all three shipped themes,
  read through `ThemeColors`). Diff lines, status text, the button and the graph use them, and the lane palette comes
  from `GraphStyle`. Sizes in the Git log and the popup are in font heights. Conflicts use a new `Icons.WARNING`.
  Keep mine / Take theirs moved from the footer to a bar above the lists, so the footer is the standard [Close].
  Right-click menus: a commit (New Branch from Here, which `createBranch` now accepts as a commit id; Copy Commit ID;
  Copy Message), empty list space (Commit, Refresh, Clear filters), a commit's file (Show Diff, Copy Path) and a
  conflicted file (Keep mine, Take theirs, Copy Path). No filter results offer Clear filters.
