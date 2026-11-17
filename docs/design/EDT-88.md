# EDT-88 — Maximize tool windows over the Game Viewport, or to the whole editor

Status: **approved** (human, 2026-11-17) · Owner: skate-editor · Deps: EDT-67 · Priority: P1

## Goal

A tool window (Script Editor, Animation Editor, Video to Animation, and every other window with the standard
header) can be maximized and restored, in one of two modes:

- **Over the Game Viewport**: the window becomes a tab in the Game Viewport's dock node, in front of the viewport.
  The Hierarchy, Properties and bottom panels stay visible.
- **Full editor**: the window fills the whole editor area between the menu bar and the status bar. The other panels
  are hidden until the window is restored.

Ways to do it:
- a **Maximize** button in the header, next to `?`;
- a shortcut;
- double-clicking the window's tab;
- tab-menu items.

**Restore** puts the layout back exactly as it was. As a side effect, the Game Viewport's node gets a tab bar when it
holds more than one window, so you can also dock windows over the viewport by dragging them.

## What exists today (integration/33c)

| Area | Today |
|---|---|
| Default layout | `ImGuiLayer.setupLayout` splits the dock space into left / right / bottom / centre. The centre node gets `NoTabBar`, `NoWindowMenuButton` and `NoCloseButton`. ImGui saves these local node flags in `imgui.ini`, so every saved layout keeps them. |
| Why docking over the viewport fails | With `NoTabBar` the node can show only one window and has no tabs to switch with. `DefaultDockLayout` puts the Script Editor in the bottom zone for this reason (EDT-55 comment). |
| Game Viewport window | Draws no title bar and no tab. Its `?` is the last toolbar button (EDT-51 §2.2). Its flag "`noTabItem = 1 shl 23`" is really ImGui 1.90's `NavFlattened`, so the name is wrong and the flag does nothing useful. It ignores `beginWindow`'s return value, so it keeps drawing (image, view rect, framebuffer resize) while hidden. |
| Viewport full screen | F12 (`EditorShortcuts.FULLSCREEN`) runs `ToggleFullScreenCommand`, which flips `ImGuiLayer.isViewportMaximized`. While it is set, the dock space and every window are skipped and only the framebuffer image is drawn. The dock layout survives because it is never touched. This is an undoable Command although it changes no project data. |
| Layout persistence | `EditorLayoutStore`: `imgui.ini` (dock nodes, window geometry) plus a list of open windows. It is written on exit. ImGui also rewrites `imgui.ini` by itself every few seconds (`io.IniSavingRate`), because `io.iniFilename` is set. |
| Dock tracking | `DefaultDockLayout.docks` records each window's current dock id (used by "Reset position"). |
| Viewport focus (EDT-73) | The Game Viewport reports focus and hover every frame. `ViewportFocusReport.consume` clears the report, so a viewport that is not drawn, or is hidden behind another tab, reads as unfocused and the camera stops. |
| Shortcut space | Camera fly uses Space (up) and Left Shift (down). The Script Editor types text. |

## Proposed design

### 1. Tab bar on the Game Viewport's node (needed for "over viewport")

- **New layouts:** the centre node gets `AutoHideTabBar` instead of `NoTabBar`, and keeps no-window-menu and
  no-close. While the viewport is alone in its node, nothing changes: there is no tab bar and the toolbar sits where
  it does today. When a second window is docked there, ImGui shows the tab bar (`Game Viewport | Script Editor`).
- **Existing layouts:** a one-time fix-up the frame after the layout loads. Look up the central node
  (`dockBuilderGetCentralNode`), clear `NoTabBar` and set `AutoHideTabBar`. This works for saved layouts without
  editing the ini text.
- **Game Viewport window:**
  - It gets a normal tab label (icon and "Game Viewport"), which is visible only while the tab bar shows.
  - Drop the misnamed `1 shl 23` flag.
  - When `beginWindow` returns false (hidden behind another tab), return early after `end()`. It then reports
    unfocused, does not update the mouse view rect, and does not resize the framebuffer to the hidden size.
- **Toolbar:** its layout is relative to the window's content, and the tab bar lives in the dock node above the
  content, so the toolbar does not move inside the window. A test checks the toolbar rect with and without a
  second tab.

### 2. The maximize model (pure, testable)

`WindowMaximize` (in `editor/imgui`) holds one state:

```
None | Full(window) | OverViewport(window, layoutSnapshot)
```

It has no ImGui calls. It takes requests:
- `toggle(window, mode)`;
- `restore()`;
- `onPlay()`, `onStop()`;
- `onViewportFullscreen(on)`;
- `onWindowClosed(window)`;
- `onResetLayout()`;
- `onExit()`.

It returns what the layer must do (draw the overlay host, dock the window into the central node, reload a layout
snapshot, focus the viewport). Only one window is maximized at a time. Maximizing another window restores the
current one first.

### 3. Full editor mode: a host over the dock space, the layout is not touched

This mode works the same way as F12 does today:
- The dock space is still submitted, with `KeepAliveOnly`. Its nodes stay alive but are not drawn, and the other
  windows' `imgui()` is not called.
- The maximized window is drawn in a host covering the dock space's area (between the menu bar and the status bar),
  using the `NoDocking`, `NoMove`, `NoResize`, `NoCollapse` and `NoSavedSettings` flags.
- `EditorWindow.beginWindow` does this when `WindowMaximize` says this window is full. It uses the title with the
  same text and the ID `###<name>.maximized`. The window's own code does not change.
- The menu bar, status bar, modals, error dialog and loading screen still draw.

Results:
- **Restore is exact by construction:** nothing in the dock tree, the ini or the open-window list changes.
- **Crash-safe:** ImGui's autosave writes the same layout.
- **Cost:** ImGui state keyed by the window ID (scroll positions, open tree nodes, active text field) is separate for
  the maximized window, so it is not shared with the normal one. Kotlin-side state (the Script Editor's buffers and
  caret, the Animation Editor's selection) is shared, because it is the same object. See open question 6.

### 4. Over the Game Viewport: a real dock, then restore from a snapshot

1. **Snapshot:** call `saveIniSettingsToMemory()` and keep the result in `OverViewport`. Write the layout to disk
   once (`saveLayout`), then set `io.iniFilename = null` so ImGui's autosave cannot persist the maximized layout.
2. **Dock:** `dockBuilderDockWindow(title, centralNodeId)`, then focus the window so its tab is in front.
3. **Restore**, between frames, before `newFrame`:
   - reload the snapshot with `loadIniSettingsFromMemory`;
   - set `io.iniFilename` back;
   - focus the window in its old tab.

Moving the window back with `dockBuilderDockWindow(previousDockId)` is not enough for an exact restore. If the
window was alone in its node, ImGui deleted that node and merged the split when the window left, so the split, size
and tab order would be lost. The snapshot restores all of it.

**Risk:** ImGui documents ini loading as a start-up call. Reloading docking layouts between frames is a known, used
pattern, but it must be proven here first, so step 0 of the work is a headless spike:
- build the default layout;
- snapshot it;
- dock a window into the centre;
- reload the snapshot;
- assert `saveIniSettingsToMemory()` matches byte for byte and the windows are in their original nodes.

If the spike fails, the fallback is to record the dock tree (`ImGuiDockNode` parent/children, split axis, sizes,
selected tab) at maximize time and rebuild it with `dockBuilderSplitNode`/`dockBuilderDockWindow`. That is more code
and the same tests.

**Note:** other docking changes made while maximized over the viewport are discarded by Restore (open question 5).

### 5. Controls

| Control | Behaviour |
|---|---|
| Header button | `WINDOW_MAXIMIZE` icon left of `?`. It changes to `WINDOW_RESTORE` while the window is maximized. A click toggles the **default mode** (open question 1). The header layout (`HelpButtonLayout.row`) reserves two buttons instead of one. `HelpButtonClipTest` is extended so both stay unclipped at every density and font size. |
| Tab menu (EDT-51 §2.3) | **Maximize**, **Maximize over Game Viewport** (or **Restore**), then the existing Help / Reset position / Close. |
| Tab double-click | After `begin`, the last item is the tab, so `isItemHovered && isMouseDoubleClicked(0)` toggles the default mode. Only for docked windows. A floating window's title-bar double-click keeps ImGui's collapse behaviour. |
| Shortcut | New `EditorShortcuts.MAXIMIZE_WINDOW`, rebindable in Key Bindings, default **Shift+Space** (open question 2). It acts on the focused standard window. It is ignored while a text field takes keys (Script Editor typing, `io.wantTextInput`) and while the Game Viewport is focused, where Shift and Space are the camera's down and up keys. |
| Status bar | While maximized: "⤢ Script Editor maximized — Restore (Shift+Space)". The Restore button is always reachable, even if the window's own header scrolls away. |
| Search Anywhere | "Maximize window" and "Restore layout" actions. |

**Which windows:** every docked window with the standard header (`EditorWindow`, not `EditorDialog`), except the
Game Viewport (it is the target, and has F12). `EditorWindow.canMaximize` defaults to true. Dialogs and modals
override it to false.

### 6. Play mode

| Situation | Behaviour (recommended) |
|---|---|
| Play while maximized over the viewport | The Game Viewport tab comes to the front (`setWindowFocus`), so the game is visible and receives the pointer. The tool window stays a tab beside it. Stop leaves the tabs as they are. |
| Play while maximized to the full editor | Restore first, then Play, so the game is visible. Stop does not re-maximize (open question 4). |
| Maximize during Play | Over the viewport: allowed; the game keeps running behind the tab. Full editor: allowed; the game keeps running unseen and the status bar says Play is on. |
| F12 viewport full screen | It takes over: the dock space and the maximize host are skipped, as F12 does now. Turning F12 off returns to the maximized state. Maximizing from the shortcut while F12 is on turns F12 off first. |

### 7. Viewport input focus (EDT-73)

- **Over the viewport:** when the tool window's tab is in front, the Game Viewport is a hidden tab. With the early
  return (§1) it reports unfocused and not hovered, so fly, look and zoom stop, and the pointer view rect is not
  updated from a hidden image. Selecting the viewport tab gives focus back as today.
- **Full editor:** the Game Viewport is not drawn at all, so `consume` returns unfocused and the camera stays still.
  Shortcuts scoped to the viewport (gizmo keys, Focus, Delete in the viewport) are already gated through
  `EditorFocus`; a test checks they do nothing while a maximized tool window is focused.

### 8. Saving the layout

- **Full editor:** nothing changes on disk. The host window has `NoSavedSettings`.
- **Over the viewport:** ImGui's autosave is off while maximized (§4). On exit, Reset Layout, project close or
  closing the maximized window, restore first, then save or continue.
- **Not persisted:** the editor always starts restored (open question 3). A crash while maximized leaves the layout
  saved just before maximizing.
- **Reset Layout while maximized:** drop the maximize state (no reload), then reset as today.

## Strings (EN, FR)

- `btn.window.maximize` (+ `.tooltip`), `btn.window.restore` (+ `.tooltip`)
- `context.window.maximize`, `context.window.maximize_over_viewport`, `context.window.restore` (+ tooltips)
- `lbl.keybindings.maximize_window`, `lbl.status_bar.maximized` (window name, shortcut)
- `search.action.maximize_window`, `search.action.restore_layout`
- Help: one paragraph in the window-standard / interface article, and a sentence in the Game Viewport article
  about docking over it.

## Work plan (each step is one commit with tests)

1. **Spike (test only):** prove the runtime snapshot reload in headless ImGui with docking enabled (§4). Report
   back if it fails, before step 4.
2. **Viewport tab bar:**
   - `AutoHideTabBar` for new layouts, plus the fix-up for saved ones;
   - the Game Viewport early return when hidden;
   - drop the bogus flag.
   - Tests: the viewport alone has no tab bar and its toolbar rect is unchanged; a second docked window shows the
     tab bar; a hidden viewport reports unfocused.
3. **`WindowMaximize` model and Full editor mode:**
   - header button, tab menu, double-click, shortcut, status-bar Restore;
   - play / F12 / close / reset / exit rules.
   - Tests: model transitions; `saveIniSettingsToMemory()` identical before and after a full maximize/restore;
     only the maximized window and the editor chrome draw; the shortcut is ignored while typing or with the
     viewport focused; the header buttons are unclipped at every density.
4. **Over the Game Viewport mode** with snapshot restore.
   - Tests: the window is docked in the central node and in front; restore gives an identical ini and the original
     dock ids; autosave is off while maximized and back after; Play brings the viewport tab to the front.

## Open questions (with recommendations)

1. **Default mode for the button, double-click and shortcut?** Recommend **Full editor** (like Unity's Shift+Space
   and Godot's distraction-free mode), with "Maximize over Game Viewport" in the tab menu. Also add an Editor
   Settings › Interface choice, "Maximize: Full editor / Over Game Viewport", for people who prefer the other.
2. **Shortcut?** Recommend **Shift+Space**, rebindable, ignored while typing and while the viewport is focused. If
   you want no overlap with camera fly at all, Ctrl+Shift+M is the alternative.
3. **Remember the maximized state across restarts?** Recommend **no**: always start restored.
4. **Re-maximize after Stop** when Play restored a full-editor maximize? Recommend **no** for now. Add it later as a
   setting if it is missed.
5. **Over the viewport, Restore discards other docking changes made while maximized.** Recommend accepting this, as
   maximize is a temporary view. The alternative (move only that window back, rebuilding a deleted split) is more
   code and cannot be exact for tab order.
6. **Full editor mode uses a separate ImGui window.** Scroll position and open tree nodes of the maximized view are
   separate from the docked view. Kotlin-side state (text, caret, selection) is shared. Recommend accepting this; it
   is what keeps the restore exact and crash-safe.
7. **Every standard window, or only the three named ones?** Recommend **every docked standard window** except the
   Game Viewport. It costs nothing extra, since the button lives in the shared header.
8. **F12 viewport full screen is an undoable Command** (`ToggleFullScreenCommand`) although it changes no project
   data, so it clutters Undo. Recommend making it plain view state like maximize, in a separate small task, not part
   of EDT-88.

## Decisions (approved 2026-11-17)

Every recommendation above is accepted:

1. Default mode: **Full editor**, with "Maximize over Game Viewport" in the tab menu, plus an Editor Settings ›
   Interface choice "Maximize: Full editor / Over Game Viewport".
2. Shortcut: **Shift+Space**, rebindable, ignored while typing and while the Game Viewport is focused.
3. The maximized state is not remembered: the editor always starts restored.
4. Stop does not re-maximize a full-editor maximize that Play restored.
5. Accepted: Restore over the viewport discards other docking changes made while maximized.
6. Accepted: the full-editor host keeps its own ImGui scroll and tree state.
7. Every docked standard window except the Game Viewport can be maximized.
8. Making F12 plain view state (not an undoable Command) is split out to **EDT-89**, not part of EDT-88.
