# EDT-84 — Tool window bars on the left and right (IntelliJ style)

Status: **awaiting approval** · Owner: skate-editor · Deps: none · Priority: P1

## Goal

From the human (EDT-79 design answers, 2026-11-10): vertical bars on the left and right edges of the editor, with one
button per tool window, as in IntelliJ. A button opens, focuses or closes its window (Hierarchy, Properties, Asset
Browser, Version Control, …).

Done when: every registered window can be toggled from a side bar, and each button's position is saved with the
layout; tests.

## What exists today (integration/37b)

| Area | Today |
|---|---|
| Windows | `WindowRegistry.windows` (27). `dockableWindows` excludes `EditorDialog`s. `WindowCategory` lists the 18 windows shown in **View > Windows**, which are the tool windows. Each one has an `icon` (EDT-51 §2.2). |
| Opening and closing | Each window's `isOpen: ImBoolean`. View > Windows is a checkbox per window. `WindowAction.Show/Hide` events exist. `WindowCloseTracker` calls `onClosed`. There is no "focus" action. A window opened from the menu appears where it was last docked. |
| Default places | `DefaultDockLayout` zones: LEFT (Hierarchy, Project), RIGHT (Properties, Audio Inspector), CENTER (Game Viewport), BOTTOM (the other 13). `DockMemory` remembers each window's dock node (used by Reset position). |
| Editor frame | `ImGuiLayer.setupDockSpace`: a host window between the menu bar and the status bar (`STATUS_BAR_HEIGHT`) containing one `dockSpace` that fills it. EDT-88's full-editor host takes the dock space's area (`MaximizeSession.hostArea`). F12 skips the whole host. |
| Layout files | `EditorLayoutStore`: `imgui.ini` and `imgui.windows` (the open windows), written on exit. Reset Layout clears both. |

## Proposed design

### 1. The bars

- Two vertical strips, **left** and **right**, inside the host window: they run from below the menu bar to above the
  status bar. The dock space sits between them and is narrower by the two bar widths.
- **Width:** one button plus padding (about 32 px at 100 %), scaled with the UI scale (EDT-57).
- Each bar has a **top** and a **bottom** section, as in IntelliJ, so there are four sections: `LEFT_TOP`,
  `LEFT_BOTTOM`, `RIGHT_TOP`, `RIGHT_BOTTOM`. The bottom section is aligned to the bottom of the bar, with a free gap
  between the two sections.
- **Buttons are icon-only** (the window's `icon`; the first letter of its title if it has none), square, with a tooltip
  showing the window's title. ImGui cannot draw vertical text without hand-rotating vertices. IntelliJ's new UI is also
  icon-only (open question 4).
- **Button states**, drawn from theme colours (EDT-07), so every theme works:
  - **closed:** flat;
  - **open:** a subtle background;
  - **open and in front** (its tab selected, or floating): the accent background;
  - **focused:** the accent background plus a 2 px marker on the bar's outer edge.
- **Hidden:** while F12 viewport full screen is on (like everything else), and when the user turns the bars off with
  **View > Tool Window Bars** (an editor setting, on by default; open question 5).
- **EDT-88:** while a window is maximized to the full editor, the bars stay. The host area is the dock space between
  them. Clicking another window's button opens that window, so the layout is restored first (the existing
  `onWindowShown` rule).

### 2. Which windows get a button

- Every window listed in View > Windows (the 18 `WindowCategory` windows), **except the Game Viewport**. The viewport
  cannot be closed and has F12 (open question 1).
- A guard test fails when a dockable categorized window has no button. A new tool window then gets one automatically
  from its default zone (§3).

### 3. Default placement

Derived from `DefaultDockLayout` zones and `WindowCategory`, so the button sits on the side where the window docks by
default:

| Section | Windows |
|---|---|
| LEFT_TOP | Project, Hierarchy |
| LEFT_BOTTOM | Asset Browser, Asset Store, Video to Animation, Animation Editor, Animator, Script Editor (bottom zone: Assets and Scripting) |
| RIGHT_TOP | Properties, Audio Inspector |
| RIGHT_BOTTOM | Console, Profiler, Command History, Render Graph, Shell, Version Control, Help (bottom zone: Debug, Project and Help) |

IntelliJ puts every bottom tool window on the left bar's bottom section. With 13 bottom windows that would put 15
buttons on the left bar and 2 on the right, so this splits them by category (open question 2).

### 4. Click behaviour (as in IntelliJ)

| Window before the click | Click |
|---|---|
| closed | open it and bring it to the front (focus) |
| open, behind another tab, or not focused | bring it to the front and focus it |
| open, in front and focused | close it |

- **Focus** is read from the previous frame, because clicking the bar moves ImGui's focus to the host window.
  `beginWindow` already runs for every window, so it records `focused` and `inFront` (from `begin`'s visibility and
  `isWindowFocused`) in a small shared map, as EDT-88 does for `MaximizeSession.focused`.
- **Opening** sets `isOpen`, as View > Windows does, and the window comes back where ImGui last docked it. Focus
  uses `ImGui.setWindowFocus(title)` after the window's `begin`, as for the default tabs (`ACTIVE_TABS`).
- Showing and hiding windows is view state, not project data, so it is not an undoable Command. The same is true of
  View > Windows and EDT-88.

### 5. Moving buttons

- **Drag** a button within its section, or to another section. A drop line shows where it lands. ImGui drag-and-drop
  uses a payload `TOOL_WINDOW_BUTTON`, with each section as a drop target.
- **Right-click** a button for:
  - **Move to** › Left top / Left bottom / Right top / Right bottom;
  - the window's tab-menu items: Maximize (EDT-88), Help, Reset position, Close.
- **Moving a button to another side also moves its window** (open question 3, recommended). The window is re-docked
  next to a window whose button is in the target section, or else into that section's default zone node (left top →
  LEFT, right top → RIGHT, bottom sections → BOTTOM). This reuses Reset position's `setNextWindowDockID` path
  (`DockMemory`).
- Docking a window elsewhere by dragging its tab does **not** move its button. Keeping that one-way is predictable
  and needs no mapping from arbitrary dock nodes to sides.

### 6. Saving

- A new sidecar next to the others, **`imgui.toolbars`**, managed by `EditorLayoutStore`. It is JSON: section → the
  ordered window names.
- It is written when a button moves (so it survives a crash) and on exit with the rest of the layout.
- **On load**, it is merged with the registry:
  - unknown names are dropped (a removed window);
  - a registered window missing from the file is appended to its default section (a window added in a newer version);
  - a window listed twice keeps its first place.
- **Reset Layout** deletes the file and restores the default placement (§3), together with the dock layout.
- The **Tool Window Bars** visibility setting is an editor setting (`EditorSettings.showToolWindowBars`), not part of
  the layout.

### 7. Model and code (pure where possible)

- `ToolWindowBars` (`editor/imgui`), with no ImGui calls:
  - `sections: Map<BarSection, List<String>>`;
  - `defaultPlacement(windows)`;
  - `merge(saved, registered)`;
  - `move(window, section, index)`;
  - `sectionOf(window)`;
  - `click(state): ClickAction` (§4 table);
  - `encode`/`decode`.
- `ToolWindowBarView` draws the bars, given the host's content rect.
- **`ImGuiLayer.setupDockSpace`:** left bar child → `sameLine` → `dockSpace(id, width - 2 × barWidth, height)` →
  `sameLine` → right bar child. `MaximizeSession.hostArea` becomes the dock space's rect.
- `EditorLayoutStore`: `saveToolBars` / `loadToolBars`, and `clear()` also deletes the file.

## Strings (EN, FR)

- `lbl.toolbars.move_to`, `lbl.toolbars.left_top`, `lbl.toolbars.left_bottom`, `lbl.toolbars.right_top`,
  `lbl.toolbars.right_bottom` (each with a tooltip).
- `menu.view.tool_window_bars` (+ `.tooltip`).
- `tooltip.toolbars.button` ("%s — click to open, click again to close").
- Help: a paragraph in Getting started (where the windows are) and in the interface article.

## Work plan (each step is one commit with tests)

1. **Model and store:** `ToolWindowBars` and `EditorLayoutStore` support.
   - Tests: default placement from zones and categories; merge rules; move and reorder; click table; round trip;
     Reset.
2. **Drawing and clicks:** the bars around the dock space, button states, the click behaviour, and focus/front
   tracking in `beginWindow`.
   - Headless ImGui tests: the dock space and the EDT-88 host area shrink by the bar widths; a simulated click opens a
     closed window, brings a background tab to the front, and closes the focused one; the bars are hidden under F12.
3. **Moving:** drag and drop, Move to, re-docking to the target side, saving on change.
   - Tests: a moved button is saved and comes back after a restart; the window docks on the new side; Reset Layout
     restores the default.
4. **View > Tool Window Bars, strings, help.**
   - Guard test: every categorized dockable window except the Game Viewport has a button.

## Open questions (with recommendations)

1. **Does the Game Viewport get a button?** Recommend **no**: it cannot be closed, and F12 already handles it.
2. **Bottom-zone windows: all on the left bar's bottom (IntelliJ), or split by category?** Recommend **split**:
   Assets and Scripting on the left, Debug, Project and Help on the right. That gives 8 and 9 buttons instead of 15
   and 2.
3. **Are a button's side and its window's dock place linked?** Recommend **one way**: moving a button re-docks its
   window on that side, but dragging a window's tab does not move its button. Alternatives: fully independent
   (simplest, but a left button can open a right-hand window), or fully linked (needs a rule for every dock node,
   including floating windows and the bottom zone).
4. **Icons only, or vertical titles as in classic IntelliJ?** Recommend **icons only** with tooltips. Vertical text
   needs hand-rotated draw-list vertices, and wide bars cost the dock space width.
5. **Can the bars be hidden?** Recommend **yes**: View > Tool Window Bars, on by default, saved in the editor settings.
6. **Keyboard shortcuts (IntelliJ's Alt+1…9)?** Recommend **later**, as a separate task. They would need a shortcut
   per window in Key Bindings.
