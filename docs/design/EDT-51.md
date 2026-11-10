# EDT-51: Editor consistency audit

Status: **human decisions applied (§6); awaiting lead approval of the EDT-52 plan (§5)** · Owner: skate-editor · Feeds: EDT-52 (apply), EDT-53 (help text), EDT-57 (settings)

## In short

The editor has 20 windows and dialogs, a hamburger menu, a settings popup and about 15 smaller popups. Each one was
built on its own, so they look and behave differently:

- **Header icons:** only 2 of the 19 titled windows have an icon in their title (Profiler, Shell).
- **Help:** no window has a `?` help button.
- **Right-click menus:** 6 windows have a right-click menu on their content (Hierarchy, Properties, Viewport, Project,
  Console, Shell), and the Asset Browser has one on assets. No window has a menu on its tab.
- **Empty states:** 5 windows use the shared centred empty state. 11 others show a plain grey line, and Shell and
  Key Bindings show nothing.
- **Window vs. dialog vs. modal:** there is no rule.
  - The settings windows cannot dock but are still listed in View > Windows.
  - Key Bindings can dock, although you open it, change something and close it, like a dialog.
  - Input Testing is a dialog, although it is a live monitor you want next to the viewport.
  - New Project, Switch Project and Search Anywhere use ImGui's internal `Modal` window flag instead of a real modal
    popup, so they neither dim nor block the editor.
- **Menus:**
  - The hamburger menu mixes nested menus with a flat list of project items.
  - Settings exist in three places: the inline sliders in the Settings submenu, Editor Settings, and the title-bar gear.
- **Leftovers:** "Environment" and "Physics Tuner" are still in the default layout and the string tables, but neither
  window exists any more.

This note lists the current state of everything (§1), proposes **one standard** (§2), says which windows change kind
(§3), lists the missing settings (§4), and splits EDT-52 into chunks that can run in parallel (§5).
**§6 records the human's decisions; §5 is the EDT-52 chunk plan.**

---

## 1. Inventory: what exists today

Legend: ✅ has it · ◐ partly · ❌ missing · — not applicable.
"Kind" is how the window behaves today:
- **Window**: dockable, listed in View > Windows, saved in the layout.
- **Floating**: cannot dock but is still a normal window.
- **Dialog**: an `EditorDialog`, which closes with Esc or Close and is not in the layout.
- **Pseudo-modal**: uses the `Modal` window flag but is not a real modal popup.

### 1.1 Windows and dialogs

| # | Window (string key) | Kind today | Open by default | Header icon | Right-click menu | `?` help | Empty state |
|---|---|---|---|---|---|---|---|
| 1 | Scene Hierarchy (`window.hierarchy`) | Window | ✅ | ❌ | ◐ on objects only, not on empty space | ❌ | ✅ shared (no project / no scene / empty scene with a Create button / no search results) |
| 2 | Properties (`window.properties`) | Window | ✅ | ❌ | ◐ on component headers (copy, reset, remove) | ❌ | ✅ shared (no project / no scene / nothing selected) |
| 3 | Game Viewport (`window.game_viewport`) | Window, no title bar | ✅ | — (no title bar) | ✅ create / duplicate / delete / focus / reset camera | ❌ | ✅ shared (no project / no scene) |
| 4 | Project (`window.project`) | Window | ✅ | ❌ | ✅ files and empty space (new, rename, delete, favourites, open in Explorer…) | ❌ | ✅ shared (no project / empty folder / no results) |
| 5 | Asset Browser (`window.asset_browser`) | Window | ✅ | ❌ | ◐ on assets in the Animations, Prefabs, Sounds and Textures tabs; none on empty space | ❌ | ◐ shared on the base tab only; the other tabs show nothing |
| 6 | Console (`window.console`) | Window | ✅ | ❌ | ✅ copy / clear | ❌ | ◐ plain grey line |
| 7 | Profiler (`window.profiler`) | Window | ✅ | ✅ chart | ❌ | ❌ | ◐ plain grey line |
| 8 | Command History (`window.command_history`) | Window | ✅ | ❌ | ❌ | ❌ | ◐ plain grey line |
| 9 | Render Graph (`window.render_graph`) | Window | ❌ | ❌ | ❌ | ◐ one inline hint line | ◐ plain grey line *(EDT-54 is rewriting it)* |
| 10 | Shell (`window.shell`) | Window | ❌ | ✅ code file | ✅ on the output, plus a Presets popup | ❌ | ❌ |
| 11 | Script Editor (`window.script_editor`) | Window with its own menu bar | ✅ (since EDT-55) | ❌ | ❌ | ❌ | ◐ plain grey line |
| 12 | Audio Inspector (`window.audio_inspector`), includes the mixer | Window | ❌ | ❌ | ❌ | ❌ | ◐ plain grey lines (no object / no audio component) |
| 13 | Key Bindings (`window.keybindings`) | Window | ❌ | ❌ | ◐ "+" and mouse popups per binding, plus a conflict modal | ❌ | ❌ |
| 14 | Editor Settings (`window.editor_settings`) | Floating, but listed in View > Windows | ❌ | ❌ | ❌ | ◐ a tooltip on each setting | — |
| 15 | Project Settings (`window.project_settings`) | Floating, but listed in View > Windows | ❌ | ❌ | ❌ | ❌ | ◐ plain text (no project) |
| 16 | Input Testing (`window.input_testing`) | Dialog | ❌ | ❌ | ❌ | ❌ | ◐ plain grey lines (no gamepad / no project) |
| 17 | Export Project (`window.export_wizard`) | Dialog | ❌ | ❌ | ❌ | ❌ | ◐ plain grey lines (no project / no scenes) |
| 18 | New Project wizard (`window.project_wizard`) | Pseudo-modal, centred, fixed size | first launch | ❌ | ❌ | ❌ | — *(EDT-59 is rewriting it)* |
| 19 | Switch Project (`window.project_switcher`) | Pseudo-modal, pinned to the centre | ❌ | ❌ | ❌ | ❌ | ◐ plain grey line |
| 20 | Search Anywhere (`window.search`) | Pseudo-modal, cannot move | ❌ | ❌ | ❌ | ❌ | ◐ "No results for …" |

### 1.2 Menus

| Menu | Where | What is in it today | Problems |
|---|---|---|---|
| **Hamburger** (☰) | Top-left of the title bar | Submenus: File (New/Save/Save As/Import **Scene**), Edit (Undo, Redo, Cut, Copy, Paste), Settings, Input (Input Testing only), View (Windows ▸, Open all, Close all, Reset layout). Then flat items: Recent Projects ▸, New Project, Open Project, Close Project, Save Project, Save Project As, Export, Quit. | **File** holds only scene actions, while project actions sit loose at the root. **Input** has a single item. There is no Play menu and no Help menu. Duplicate and Delete are missing from Edit. |
| **Settings submenu** | Inside the hamburger menu | Gamepad overlay size slider and show checkbox, unit system, language, Key Bindings…, Settings… | The inline sliders and combos duplicate Editor Settings and are the only menu with live controls. |
| **Gear popup** | Top-right of the title bar | Editor Settings, Project Settings | This is a second way to open settings, separate from the Settings submenu. Both items use the same gear icon. |
| **Window controls** | Top-right | Settings, Search, Minimize, Maximize/Restore, Close | Maximize/Restore has **no tooltip**. Settings and Search have tooltips, but not through the shared `tippedWith` helper. |
| **Scene tab menu** | Right-click on a scene tab | Save, Save As, Rename, Close, Close Others, Close All, Delete, plus a close confirmation | This is the standard to copy: actions first, closing actions next, destructive actions last. |
| **Viewport menu** | Right-click in the viewport | Create Empty / Light ▸ / Camera / Obstacle, Duplicate, Delete, Focus, Reset Camera | Fine. It should also offer Create UI ▸ like the Hierarchy. |
| **Create UI** | Submenu (`UiCreateMenu`) | Canvas and widgets | Shared by the Hierarchy and the Viewport; fine. |

### 1.3 Popups, overlays and modals

| Surface | Kind | Notes |
|---|---|---|
| Error dialog (`ErrorDialog`) | Real modal popup | Good. It is the model for the modal standard. |
| Remove-component confirmation (`MImGui.showConfirmationModal`) | Real modal popup | EDT-49 is fixing the double-display and layout bug. |
| Scene-tab close confirmation | Real modal popup | Same helper as the row above. |
| Binding conflict (Key Bindings) | Real modal popup | Good. |
| Add Component popup (Properties) | Popup | Good. |
| Script completion list | Floating tooltip window | Fine as it is. |
| Gamepad overlay, Trick UI | Overlays drawn over the viewport | Not windows; out of scope. |
| Status bar, toasts | Chrome | Out of scope. Toasts already follow one style. |
| **Stale entries** | — | `window.environment` and `window.physics_tuner` are in `DefaultDockLayout` and the strings, and `window.settings` is in the strings, but none of these windows exists. Delete them. |

---

## 2. The standard

Each rule is something a test can check (EDT-52 adds a `WindowStandardGuardTest`).

### 2.1 Three kinds, one rule for choosing

| Kind | Use when… | Behaviour | Base class |
|---|---|---|---|
| **Window** | You keep it open while working, and it shows live scene or project state. | Dockable, listed in View > Windows by category, saved in the layout, has a tab. | `EditorWindow` |
| **Dialog** | You open it, change something, and close it (settings, key bindings, export). | Floating, cannot dock, **not** in View > Windows or the layout. Closes with the title-bar X, Esc, or the footer buttons. Remembers its size and position. | `EditorDialog` |
| **Modal** | You must answer before going on: confirm a destructive action, an error, unsaved changes, New or Open Project, progress (EDT-56). | A real `beginPopupModal`: dims and blocks the editor, centred, Esc = Cancel, Enter = the main action. | new `EditorModal` (built from `ErrorDialog`) |

The **command palette** (Search Anywhere) is a fourth, special case. It is a transient popup that closes when it loses
focus or on Esc, and stays as it is (EDT-48 fixes its focus bug).

### 2.2 Header

- **Icon:** every window and dialog title starts with one icon. It comes from a new `open val icon: String` on
  `EditorWindow`, and `title()` adds it with a stable ID.
- **`?` button (decided):** every window and dialog has a `?` button at the right end of a one-line header strip,
  drawn by a shared `WindowHeader`. The window's own tools (search box, filters, toggles) go on the left of the same strip. This
  replaces the ad-hoc toolbars at the top of the Console, Project, Asset Browser and Profiler.
  (EDT-67) The strip is a two-column table: the tools get, and are clipped to, the width left of a fixed `?` column,
  so they never reserve room for `?` themselves. The `?` is a full frame-height button (`HelpButtonLayout`), since
  the merged icon glyph is taller than a `smallButton`. Modal hosts (New Project, Switch Project) show their `?`
  at the top of the modal through `EditorModal.helpWindow`.
- **Viewport:** the Game Viewport keeps no title bar. Its `?` goes at the right end of the viewport toolbar.

Icons (decided; ★ = a new glyph to add to `Icons`, all from Font Awesome, which the editor already uses):

| Window | Icon | Window | Icon |
|---|---|---|---|
| Scene Hierarchy | ★ `SITEMAP` | Console | ★ `LIST` (a log list) |
| Properties | ★ `SLIDERS` | Profiler | `CHART_LINE` |
| Game Viewport | — (none) | Command History | ★ `HISTORY` |
| Project | `FOLDER` | Render Graph | ★ `PROJECT_DIAGRAM` |
| Asset Browser | `FILE_IMAGE` | Shell | ★ `TERMINAL` |
| Script Editor | `EDIT` | Audio Inspector | `MUSIC` |
| Key Bindings | `KEYBOARD` | *(Input Testing is removed, §3.1)* | — |
| Editor Settings | `GEAR` | Project Settings | `CUBE` |
| Export Project | `EXPORT` | New / Switch Project | `PLUS` / `FOLDER_OPEN` |

### 2.3 Right-click menus

There are two levels, and both use the same layout.

1. **Tab / title-bar menu, on every window and dialog.** It is drawn by the base class with
   `beginPopupContextItem` right after `begin`, so it attaches to the tab. Items:
   **Help**, then **Close**. Windows also get **Reset position** (re-docks the window to its default zone).
2. **Content menu, on every window that shows a list or a canvas.**
   - Right-clicking **an item** opens actions on that item.
   - Right-clicking **empty space** opens create and paste actions for that window (for example, Create ▸ in the
     Hierarchy, or Import in the Asset Browser).

   A window with nothing to act on (Profiler, Command History) has only the tab menu.

Layout of every menu, following the scene tab menu:
- Each line shows an icon, the label and, on the right, the shortcut. Each item has a tooltip (already enforced).
- Order: the main actions, then copy and paste, then a separator, then the destructive actions (Delete, Clear),
  always last.
- Disabled items are grayed out instead of hidden, as on the toolbar (EDT-45).

### 2.4 Help

- The `?` button and the tab menu's Help item both open the same **help popover**. It is non-modal, closes when you
  click outside or press Esc, and has three short sections:
  - **What it is**
  - **How to use it**
  - **Related windows** (links that open those windows)
- Text keys: `help.<window>.what`, `help.<window>.how`, `help.<window>.related`. EDT-53 writes the text for beginners.
  EDT-52 only adds the button and placeholder strings in EN and FR.
- The per-setting tooltips in Editor Settings stay. They complement the window help.

### 2.5 Empty states

- Use `EmptyState.render` everywhere: a centred dimmed icon, a message, an optional hint and an optional action
  button. No plain grey `textDisabled` lines for "nothing here".
- Four standard cases, each with the same wording everywhere:

  | Case | Message | Action |
  |---|---|---|
  | No project | "No project open" | New Project / Open Project (exists: `EmptyState.noProject`) |
  | No scene | "No scene open" | New Scene |
  | Nothing selected | "Select an object to see its …" | none |
  | Empty list or no results | Says what is missing, e.g. "No commands yet", "No results for …" | Clear search, when a search is active |

- These windows switch from plain lines to the shared component: Console, Profiler, Command History, Render Graph,
  Script Editor, Audio Inspector, Project Settings, Export, Switch Project, Search Anywhere, and the
  Asset Browser's Animations, Prefabs, Sounds and Textures tabs. Shell gets one too (it has none today).

### 2.6 Footer buttons (dialogs and modals)

- Buttons are right-aligned, in the order **[Cancel] [Main action]**. A third **Apply** button appears only on the
  two settings dialogs.
- The main action is highlighted. A destructive main action ("Delete") uses the danger color.
- Today the settings windows use OK / Cancel / Apply, `EditorDialog` has a lone **Close** button, and the wizards
  each have their own layout.

### 2.7 Menus (hamburger and settings), decided

The new menus are approved. The Settings submenu stays, with its content reviewed. Every item keeps its icon,
shortcut and tooltip.

```
File      New Project…  Open Project…  Recent ▸  Save Project  Save Project As…  Close Project
          ─  New Scene  Save Scene  Save Scene As…  Import Scene…
          ─  Export…    ─  Quit
Edit      Undo  Redo  ─  Cut  Copy  Paste  Duplicate  Delete  ─  Search Anywhere…
View      Windows ▸ (by category)  Open all  Close all  Reset layout
Play      Play / Pause  Stop  Reset
Settings  Language ▸ (English • Français)  Units ▸ (Metric • Imperial)
          ☑ Show tooltips  ☑ Show gamepad overlay
          ─  Editor Settings…  Project Settings…  Key Bindings…
Tools     Test Input…  Shell
Help      Getting started  Keyboard shortcuts  About Skate
```

**Settings submenu review**, as the quick place for things you flip often:

| Today | Decision | Why |
|---|---|---|
| Gamepad overlay size (slider) | **Moves to Editor Settings › Interface only** | Dragging a slider inside a menu is fiddly, and you rarely change it. |
| Show gamepad overlay (checkbox) | **Stays** (moved here from View) | A quick toggle. |
| Unit system (combo) | **Stays, as a Units ▸ submenu with radio items** | A combo inside a menu closes the menu when it opens; radio items do not. |
| Language (combo) | **Stays, as a Language ▸ submenu with radio items** | Same reason. |
| — | **New: Show tooltips (checkbox)** | The EDT-57 toggle, one click away. |
| Key Bindings…, Settings… | **Stay**, renamed Editor Settings… and joined by Project Settings… | The same three dialogs as the gear popup, which stays as a shortcut. |

- **Input** submenu: removed; Test Input… moves to **Tools** (§3.1).
- **"Getting started"** opens the Game Viewport's help popover. **"About"** is a small modal that shows the version.

---

## 3. Windows that change kind

| Item | Today | Becomes | Why |
|---|---|---|---|
| Editor Settings | Floating window, listed in View > Windows | **Dialog** | You open, change and close it. It should not be in the layout. |
| Project Settings | Floating window, listed in View > Windows | **Dialog** | Same reason. |
| Key Bindings | Dockable window | **Dialog** (decided), with a new **Test** tab (§3.1) | Same reason. Opened from the gear, Settings ▸ Key Bindings… and Tools ▸ Test Input…. |
| Input Testing | Dialog | **Removed**; its content moves to Key Bindings and Project Settings (§3.1) | Decided by the human: no separate window. |
| New Project wizard | Pseudo-modal | **Modal** | It must be answered and should block the editor. Coordinate with the EDT-59 rewrite. |
| Switch Project | Pseudo-modal | **Modal** | Same reason. |
| Search Anywhere | Pseudo-modal | **Palette popup** (§2.1) | It is transient, not a question. It closes when it loses focus. |
| Export Project | Dialog | stays a **Dialog**; its progress becomes a **Modal** (EDT-56) | You can still read the options; only a running export blocks the editor. |
| Remove-component confirmation, Scene-tab close confirmation, Error | Modal popups | stay **Modals**, rebuilt on `EditorModal` | One shared look (EDT-49 fixes the remove-component layout). |
| Audio Inspector (with mixer) | Window | stays a **Window** | A live inspector. |

### 3.1 Where Input Testing's content goes (proposal)

Today Input Testing has three parts. Each moves to where you already are when you need it:

| Part today | Moves to | How it works there |
|---|---|---|
| **Devices**: keys held, mouse position, delta and buttons, connected gamepads | **Key Bindings dialog › Test tab** | You are binding keys, so you want to see right away what the editor receives. Press something, and it lights up. |
| **Event log** (press/release history, with pause) | **Key Bindings dialog › Test tab**, under the devices | Adds an **Action** column: which bound action each event fired. That is the question you are asking when testing bindings. |
| **Gamepad tuning**: stick and trigger visuals, raw vs. processed values, dead-zone mode, sizes, press threshold (saved to the project) | **Project Settings › Input** (a new section) | Dead zones are a per-project game setting, so they belong with the project. The live stick and trigger drawings stay beside the sliders, so you still tune while you watch. |
| Live gamepad view while playing | the existing **Gamepad overlay** on the viewport | Already there; toggled from Settings ▸ Show gamepad overlay. |

- **Tools ▸ Test Input…** and a Search Anywhere action open Key Bindings on its Test tab.
- `InputTestingWindow` is deleted. Its drawing code (`InputTestingModel`, the stick and trigger drawing) is moved,
  not rewritten. Keys keep the `lbl.input_testing.*` and `lbl.deadzone.*` names in `editor/input`, so translations
  carry over.
- Input owns this content (INP), so the move is chunk **I** in §5, done with INP.

---

## 4. Missing settings (for EDT-57)

- "Exists" = the field is already in the code but cannot be changed from the UI.
- "New" = a new field.
- Priority: **P1** = do in EDT-57, **P2** = nice to have.

### 4.1 Editor Settings (per user, `editor.config`)

| Section | Setting | State | Priority |
|---|---|---|---|
| Interface | **Show tooltips** (on/off, also in Settings ▸). Off hides button and menu tooltips (`Tooltips.show` / `tipped`). **Inspector field explanations stay** (`Tooltips.showIfPresent`, the `component.*` and `ui.inspector.*` `.tooltip` keys), as decided. | New | **P1** |
| Interface | UI scale / font size | Exists (`fontSize`) | **P1** |
| Interface | Gamepad overlay size (moved out of the Settings submenu) | Exists (`gamepadOverlaySize`) and is already shown | done |
| General | Open the last project on startup | Exists (`loadLastProjectOnStartup`) | **P1** |
| General | Clear the recent projects list (button) | New | P2 |
| General | Ask before deleting objects or components | New | P2 |
| Gizmos | Snap step for move / rotate (degrees) / scale | Exists in `GizmoSystem` but not saved | **P1** |
| Gizmos | Default space (local / world) | Exists in `GizmoSystem` but not saved | P2 |
| Scene view | Editor camera move speed and mouse sensitivity | New (hard-coded constants in `EditorCamera`) | **P1** |
| Scene view | Show grid; grid size | New (the `GridLines` component exists) | P2 |
| Play mode | Clear the console on Play | New | P2 |
| Play mode | Undo history size | New (no limit today) | P2 |
| Graphics | Overall quality, MSAA, texture quality | Exist (`GraphicsSettings`); only shadow quality is shown | **P1** |

### 4.2 Project Settings (per project, stored in the `.skateproject` / `game.settings.json`)

| Section | Setting | State | Priority |
|---|---|---|---|
| General | Default (startup) scene picker | Exists (`defaultScene`) | **P1** |
| General | Project icon | Exists (`iconPath`) | P2 |
| Game | Default game language and supported languages | The language exists (`GameSettings.locale`); the list is new | **P1** |
| Game | Window title, start fullscreen or windowed, resolution | Resolution exists (`resolution`); the rest is new | **P1** |
| Game | VSync, target frame rate | New | P2 |
| Physics | Physics rate (Hz) | Exists (`physicsFPS`) | **P1** |
| Physics | Default gravity for new scenes | New (gravity is per scene today) | P2 |
| Audio | Default bus volumes (master / music / SFX / UI) | Exists (`AudioSettings`); shown only in the Audio Inspector mixer | P2 |
| Graphics | Graphics backend | Exists and is shown (EDT-43) | done |
| Input | Gamepad dead zones and trigger threshold, with live stick/trigger view | Exists in Input Testing; moves here (§3.1) | **P1** (chunk I) |

---

## 5. EDT-52 plan: chunks

### 5.1 Rules for every chunk

- **Order:** chunk **A** goes first and alone. B–I then run in parallel. Their files do not overlap; each chunk lists
  the only files it may edit.
- **Strings:** chunk A adds one labelled block per chunk at the end of `assets/strings/editor/core.properties` and
  `core_fr.properties` (`# --- EDT-52 B: scene ---` …). A chunk adds keys only inside its own block, so merges do not
  conflict. Domain strings stay in their area files (`editor/input`, `editor/audio`, `editor/scripting`).
- **Help text:** each chunk adds `help.<window>.what/how/related` with a one-line placeholder in EN and FR. EDT-53
  writes the real text later.
- **Guard:** chunk A adds `WindowStandardGuardTest` with an allow-list of the windows not yet converted. Each chunk
  removes its windows from that list; it must not add any. EDT-52 is done when the list is empty.
- **Tests per chunk:** the guard, plus a unit test for any new pure logic (menu model, empty-state choice, footer
  order). No ImGui rendering tests.
- **Each chunk ends with** `DONE EDT-52<letter>` and its human test steps in `docs/testing/EDT-52<letter>.md`.

### 5.2 Chunks

| Chunk | Owner | Only these files | Delivers | Size | Waits for |
|---|---|---|---|---|---|
| **A: foundation** | EDT | `editor/imgui/EditorWindow.kt`, `EditorDialog.kt`, `EmptyState.kt`, `ErrorDialog.kt`, `WindowRegistry.kt`, `WindowCategory.kt`, `DefaultDockLayout.kt`, `Tooltips.kt`, `imgui/data/Icons.kt`; new `EditorModal.kt`, `WindowHeader.kt`, `HelpPopover.kt`; new `architecture/WindowStandardGuardTest.kt`; strings | Each window declares its `icon`; base classes draw the header strip with `?`, the tab menu (Help / Close / Reset position) and the help popover; `EditorModal` and the standard footer; new glyphs (`SITEMAP`, `SLIDERS`, `HISTORY`, `PROJECT_DIAGRAM`, `LIST`, `TERMINAL`, `QUESTION_CIRCLE`); stale `window.environment` / `window.physics_tuner` / `window.settings` removed; the per-chunk string blocks; the guard with its allow-list | M | — |
| **B: scene** | EDT | `SceneHierarchyWindow.kt`, `PropertiesWindow.kt`, `GameViewWindow.kt`, `viewport/*`, `ui/menus/ViewportContextMenu.kt`, `imgui/EditorScenesTabBar.kt`, `MImGui.showConfirmationModal` | Headers, empty-space right-click menus (Hierarchy: Create ▸; Viewport adds Create UI ▸), `?` at the end of the viewport toolbar, confirmations on `EditorModal` | M | A, EDT-49 |
| **C: project & assets** | EDT | `ProjectWindow.kt`, `AssetBrowserWindow.kt`, `assetBrowser/*` | The header strip replaces the ad-hoc toolbars; empty-space menus; empty states in every Asset Browser tab | M | A |
| **D: debug** | EDT | `ConsoleWindow.kt`, `ProfilerWindow.kt`, `CommandHistoryWindow.kt`, `ShellWindow.kt` | Headers (Console = list, Shell = terminal), shared empty states, Command History right-click (Undo to here) | S | A, EDT-47 (Console) |
| **E: settings dialogs** | EDT | `EditorSettingsWindow.kt`, `ProjectSettingsWindow.kt`, `KeyBindingsWindow.kt`, `ActionBindingsPanel.kt`, `ExportWizardWindow.kt` | Settings and Key Bindings become `EditorDialog`s with the standard footer; they leave View > Windows; an empty **Test** tab in Key Bindings and an empty **Input** section in Project Settings, for chunk I to fill | M | A |
| **F: modals & palette** | EDT | `ProjectSwitcherDialog.kt`, `SearchEverywhereWindow.kt`, `ProjectWizardWindow.kt` | Switch Project and New Project become real modals; Search Anywhere becomes the palette popup; a Test Input action in Search Anywhere | S | A, EDT-48; skip `ProjectWizardWindow.kt` if EDT-59 has rewritten it |
| **G: menus** | EDT | `imgui/EditorMenuBar.kt`, `ui/menus/FileMenuBuilder.kt`, `EditMenuBuilder.kt`, `ViewMenuBuilder.kt`, `SettingsMenuBuilder.kt`, `InputMenuBuilder.kt` (deleted), `WindowControlsRenderer.kt`; new `PlayMenuBuilder.kt`, `ToolsMenuBuilder.kt`, `HelpMenuBuilder.kt`, `AboutModal.kt` | The §2.7 menu tree; the Settings submenu as reviewed (radio submenus, Show tooltips checkbox bound to the EDT-57 setting once it exists); a tooltip on Maximize/Restore; About modal; a pure `HamburgerMenuModel` with a test of the item order | M | A |
| **H: domain windows** | AUD, SCR | `AudioInspectorWindow.kt`, `AudioMixerPanel.kt` (AUD); `ScriptEditorWindow.kt` (SCR) | Headers, `?`, shared empty states | S | A |
| **I: input testing move** | INP (+ EDT review) | `InputTestingWindow.kt` (deleted), `InputTestingModel.kt`; new `ui/windows/input/InputTestPanel.kt` and `DeadZonePanel.kt`; the Test tab and Input section hooks added by E; `assets/strings/editor/input*.properties` | §3.1: devices and event log (with the new Action column) in Key Bindings › Test; dead-zone tuning in Project Settings › Input; window and menu entries removed; tests for the event-to-action column and dead-zone save | M | A, E |

**Parallelism:** after A, there are up to 7 agents at once (B, C, D, E, F, G, H), and I follows E. The critical path
is **A → E → I**.

**Out of EDT-52:** the help text (EDT-53), the new settings themselves (EDT-57; G only adds the menu checkbox), the
Render Graph window (EDT-54) and the loading modal (EDT-56).

---

## 6. Decisions (from the human)

| # | Question | Decision | Where applied |
|---|---|---|---|
| 1 | Where `?` goes | **Header strip** at the top of each window | §2.2 |
| 2 | Key Bindings | **Dialog** | §3 |
| 3 | Input Testing | **Not a window; its content moves elsewhere** (where to: proposed in §3.1) | §3.1, chunk I |
| 4 | Settings submenu | **Keep it, review its content** | §2.7 |
| 5 | Show tooltips off | **Inspector field explanations stay visible** | §4.1 |
| 6 | Icons | **Console = list/log icon, Shell = terminal** | §2.2 |
| 7 | New Play, Tools and Help menus | **Approved**, in the hamburger menu | §2.7 |

**For the lead to confirm:**
1. The §3.1 destinations for Input Testing's content.
2. The chunk owners: I goes to INP, H is split between AUD and SCR.
3. Whether G should wait for EDT-57 for the Show tooltips checkbox, or add it bound to a placeholder setting.
