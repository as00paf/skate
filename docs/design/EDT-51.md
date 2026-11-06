# EDT-51: Editor consistency audit

Status: **proposal, awaiting approval** · Owner: skate-editor · Feeds: EDT-52 (apply), EDT-53 (help text), EDT-57 (settings)

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
**§6 lists the decisions I need from you.**

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
| 11 | Script Editor (`window.script_editor`) | Window with its own menu bar | ❌ *(EDT-55 makes it default)* | ❌ | ❌ | ❌ | ◐ plain grey line |
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
- **`?` button:** every window and dialog has a `?` button at the right end of a one-line header strip, drawn by a
  shared `WindowHeader`. The window's own tools (search box, filters, toggles) go on the left of the same strip. This
  replaces the ad-hoc toolbars at the top of the Console, Project, Asset Browser and Profiler.
- **Viewport:** the Game Viewport keeps no title bar. Its `?` goes at the right end of the viewport toolbar.

Proposed icons (★ = a new glyph to add to `Icons`, all from Font Awesome, which the editor already uses):

| Window | Icon | Window | Icon |
|---|---|---|---|
| Scene Hierarchy | ★ `SITEMAP` | Console | ★ `TERMINAL` |
| Properties | ★ `SLIDERS` | Profiler | `CHART_LINE` |
| Game Viewport | — (none) | Command History | ★ `HISTORY` |
| Project | `FOLDER` | Render Graph | ★ `PROJECT_DIAGRAM` |
| Asset Browser | `FILE_IMAGE` | Shell | `FILE_CODE` → ★ `TERMINAL`? (see §6) |
| Script Editor | `EDIT` | Audio Inspector | `MUSIC` |
| Key Bindings | `KEYBOARD` | Input Testing | ★ `GAMEPAD` |
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
  Script Editor, Audio Inspector, Project Settings, Input Testing, Export, Switch Project, Search Anywhere, and the
  Asset Browser's Animations, Prefabs, Sounds and Textures tabs. Shell gets one too (it has none today).

### 2.6 Footer buttons (dialogs and modals)

- Buttons are right-aligned, in the order **[Cancel] [Main action]**. A third **Apply** button appears only on the
  two settings dialogs.
- The main action is highlighted. A destructive main action ("Delete") uses the danger color.
- Today the settings windows use OK / Cancel / Apply, `EditorDialog` has a lone **Close** button, and the wizards
  each have their own layout.

### 2.7 Menus (hamburger and settings)

Proposed hamburger menu (every item keeps its icon, shortcut and tooltip):

```
File      New Project…  Open Project…  Recent ▸  Save Project  Save Project As…  Close Project
          ─  New Scene  Save Scene  Save Scene As…  Import Scene…
          ─  Export…    ─  Quit
Edit      Undo  Redo  ─  Cut  Copy  Paste  Duplicate  Delete  ─  Search Anywhere…
View      Windows ▸ (by category)  Open all  Close all  Reset layout  ─  Show gamepad overlay
Play      Play / Pause  Stop  Reset
Tools     Input Testing…  Key Bindings…  Shell
Help      Getting started  Keyboard shortcuts  About Skate
```

- **Settings submenu removed.** Its inline controls (overlay size, units, language) already exist in Editor Settings.
  Settings open from **one place: the gear** in the title bar, which lists Editor Settings…, Project Settings… and
  Key Bindings…, each with its own icon.
- **Input submenu** merges into **Tools**.
- **"Getting started"** opens the Game Viewport's help popover. **"About"** is a small modal that shows the version.

---

## 3. Windows that change kind

| Item | Today | Becomes | Why |
|---|---|---|---|
| Editor Settings | Floating window, listed in View > Windows | **Dialog** | You open, change and close it. It should not be in the layout. |
| Project Settings | Floating window, listed in View > Windows | **Dialog** | Same reason. |
| Key Bindings | Dockable window | **Dialog** | Same reason. Opened from the gear menu and from Editor Settings. |
| Input Testing | Dialog | **Window** (Debug category, docks at the bottom) | A live monitor you watch while playing, next to the viewport. |
| New Project wizard | Pseudo-modal | **Modal** | It must be answered and should block the editor. Coordinate with the EDT-59 rewrite. |
| Switch Project | Pseudo-modal | **Modal** | Same reason. |
| Search Anywhere | Pseudo-modal | **Palette popup** (§2.1) | It is transient, not a question. It closes when it loses focus. |
| Export Project | Dialog | stays a **Dialog**; its progress becomes a **Modal** (EDT-56) | You can still read the options; only a running export blocks the editor. |
| Remove-component confirmation, Scene-tab close confirmation, Error | Modal popups | stay **Modals**, rebuilt on `EditorModal` | One shared look (EDT-49 fixes the remove-component layout). |
| Audio Inspector (with mixer) | Window | stays a **Window** | A live inspector. |

---

## 4. Missing settings (for EDT-57)

- "Exists" = the field is already in the code but cannot be changed from the UI.
- "New" = a new field.
- Priority: **P1** = do in EDT-57, **P2** = nice to have.

### 4.1 Editor Settings (per user, `editor.config`)

| Section | Setting | State | Priority |
|---|---|---|---|
| Interface | **Show tooltips** (on/off; every `Tooltips.show` honours it, but property tooltips in the inspectors could stay on, see §6) | New | **P1** |
| Interface | UI scale / font size | Exists (`fontSize`) | **P1** |
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

---

## 5. How to split EDT-52

**Chunk A goes first, alone.** It builds the shared pieces. After it is merged, chunks B–H only touch their own files,
so they can run in parallel. To avoid string-file conflicts, chunk A creates one labelled block per chunk at the end of
`assets/strings/editor/core.properties` and `core_fr.properties`, and each chunk adds its keys inside its own block.

| Chunk | Owner | Files | Work |
|---|---|---|---|
| **A: foundation** | EDT | `editor/imgui/EditorWindow.kt`, `EditorDialog.kt`, new `EditorModal.kt`, new `WindowHeader.kt`, new `HelpPopover.kt`, `EmptyState.kt`, `WindowRegistry.kt`, `WindowCategory.kt`, `DefaultDockLayout.kt`, `ErrorDialog.kt`, `imgui/data/Icons.kt`, strings, and a new `architecture/WindowStandardGuardTest.kt` | Add `icon`, the tab menu, `?` and the help popover in the base classes. Add `EditorModal` and the standard footer. Add the new icons. Remove the stale layout entries. Add the guard test, with an allow-list that the later chunks empty. |
| **B: scene** | EDT | `SceneHierarchyWindow.kt`, `PropertiesWindow.kt`, `GameViewWindow.kt`, `viewport/*`, `ui/menus/ViewportContextMenu.kt`, `imgui/EditorScenesTabBar.kt`, `MImGui.showConfirmationModal` | Header, empty-space menus, `?` on the viewport toolbar, confirmations on `EditorModal` (after EDT-49). |
| **C: project & assets** | EDT | `ProjectWindow.kt`, `AssetBrowserWindow.kt`, `assetBrowser/*` | Header strip replaces the ad-hoc toolbars; empty-space menus; empty states in every tab. |
| **D: debug** | EDT (QA can help) | `ConsoleWindow.kt`, `ProfilerWindow.kt`, `CommandHistoryWindow.kt`, `ShellWindow.kt`, `InputTestingWindow.kt`, `InputTestingModel.kt` | Header and empty states. Input Testing becomes a window. *Render Graph is left to EDT-54.* |
| **E: settings dialogs** | EDT | `EditorSettingsWindow.kt`, `ProjectSettingsWindow.kt`, `KeyBindingsWindow.kt`, `ActionBindingsPanel.kt`, `ExportWizardWindow.kt` | Become dialogs, standard footer, header. *The new settings themselves are EDT-57.* |
| **F: modals** | EDT | `ProjectSwitcherDialog.kt`, `SearchEverywhereWindow.kt` (after EDT-48), `ProjectWizardWindow.kt` (only if EDT-59 has not rewritten it) | Real modals or the palette popup. |
| **G: menus** | EDT | `imgui/EditorMenuBar.kt`, `ui/menus/*MenuBuilder.kt`, `WindowControlsRenderer.kt` | The §2.7 menu layout, one settings entry point, Help menu, About modal. |
| **H: domain windows** | AUD, SCR | `AudioInspectorWindow.kt` + `AudioMixerPanel.kt` (AUD); `ScriptEditorWindow.kt` (SCR, together with EDT-55) | Header, `?`, empty states. |

**Order:** A → (B, C, D, E, F, G, H in parallel) → EDT-53 (help text) and EDT-57 (settings).
**Done when:** `WindowStandardGuardTest` passes with an empty allow-list.

---

## 6. Decisions I need from you

1. **Help placement.** Is a `?` at the right end of a one-line header strip inside each window OK? The other option is
   to put Help only in the tab's right-click menu, which keeps the window tidier but is harder to find.
2. **Key Bindings as a dialog** (no longer dockable): OK?
3. **Input Testing as a dockable window**: OK?
4. **Settings entry point.** Remove the Settings submenu and its inline sliders, so settings open only from the gear
   (plus Search Anywhere): OK?
5. **Show tooltips = off.** Should it also hide the property tooltips in the inspectors (what a field does), or only
   button and menu tooltips? I suggest **only buttons and menus**, so beginners keep the field explanations.
6. **Shell icon.** Keep the code-file icon, or use a terminal (which the Console would then share)? I suggest Console =
   terminal and Shell = code file, as it is today.
7. **New Play, Tools and Help menus** in the hamburger menu (§2.7): OK?
