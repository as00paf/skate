# EDT-10: General editor UX pass

The checklist for EDT-10. Each item is either resolved in the EDT-10 commit or listed under "Left out" with a reason. It comes from four read-only audits of the editor: shortcuts, console-only errors, undo coverage, and icons and empty states.

## 1. Keyboard shortcuts

One catalog, `editor/data/EditorShortcuts.kt`, describes every editor shortcut: its id, label key, combo and scope.
- The combo comes from `EditorInputMappings` when the shortcut can be rebound, and is fixed otherwise.
- Menus, tooltips, the Key Bindings window and conflict detection all read from it, so a rebinding shows up everywhere.

- [x] **S1 Wrong combos shown.**
  - Save Project showed Ctrl+S, which saves the scene. It now shows nothing.
  - New Scene showed Ctrl+N, which did nothing. Ctrl+N is now bound.
  - Import Scene showed Ctrl+I, a stub with no binding. The combo is removed.
  - The toolbar tooltips said Q/W/E/R, but the tools are on 1/2/3/4.
  - Menu and tooltip combos now come from the catalog. Shortcut text is removed from the tooltip strings and appended by the `tipped(..., shortcut)` helper.
- [x] **S2 Missing modifier checks.**
  - Ctrl+Shift+Z ran undo; it now runs redo.
  - Ctrl+Shift+S ran a plain save; it is now Save Scene As.
  - Gizmo keys no longer fire while Ctrl is held.
- [x] **S3 One key, two actions.** A focused window can claim a shortcut for the frame (`EditorFocus`). The global handler then skips it:
  - Home in the hierarchy no longer also resets the camera.
  - Ctrl+C in the console no longer also copies the selected object.
  - Ctrl+S in the script editor no longer also saves the scene.
- [x] **S4 Esc repeats.** Deselect used held-key state and fired every frame. It now uses key-press state.
- [x] **S5 Missing common shortcuts.**
  - Ctrl+N: new scene
  - Ctrl+O: open project
  - Ctrl+Shift+S: save scene as
  - Ctrl+Shift+Z: redo
  - F: focus the selection
  - F2: rename in the hierarchy (it was bound but did nothing)
  - F5: Play/Stop, which also works while playing
- [x] **S6 Unused or hardcoded bindings.**
  - `reset` (R, never read) becomes `cameraReset`, default Home, replacing the hardcoded Home key.
  - `pause` (P, never read) becomes `playToggle`, default F5.
  - The hierarchy now reads its navigation mappings (Up/Down/Home/End) instead of hardcoded keys.
  - `resetToDefault()` now resets every binding.
- [x] **S7 Key Bindings window complete.**
  - The window is built from the catalog. Camera Up/Down were mislabeled and actually edited Forward/Backward. The missing rows are added: up/down, search, fullscreen, focus, play.
  - The Hierarchy "Deselect" row did nothing; it now works.
  - The missing `lbl.keybindings.*` / `lbl.hierarchy.*` strings are added.
  - Esc cancels a key capture.
  - Conflicting bindings are flagged.
  - A read-only table lists the fixed shortcuts.
  - The duplicate binding editor in Editor Settings is replaced by a button that opens the Key Bindings window.
- [x] **S7b Stale mappings.** Found during the work:
  - `EditorInputHandler` and `KeyBindingsWindow` kept the mappings object from start-up, but settings load later and replace it, so a rebinding could target a stale copy. They now read the live object.
  - Pressing OK in Editor Settings wrote its own old copy of the mappings back over the live ones, undoing a rebinding.
- [x] **S8 Test.** `EditorShortcutsTest` checks that:
  - the defaults have no conflicts;
  - conflict detection works;
  - rebinding changes the displayed combo;
  - every catalog entry has a label.

## 2. Icons

- [x] **I1 One icon per action.** `ActionIcons` names the icon for each common verb:
  - delete: TRASH
  - rename: EDIT
  - duplicate: CLONE
  - copy: COPY
  - cut: CUT
  - paste: PASTE
  - save: SAVE
  - open: FOLDER_OPEN
  - close: WINDOW_CLOSE
  - refresh: ARROW_ROTATE
  - settings: GEAR
  - quit: SIGN_OUT

  Fixes:
  - Quit used TRASH.
  - Duplicate used the Copy icon.
  - Scene-tab Save As, the Close items and Expand/Collapse All had no icon.
  - Settings, View and Console menu items had no icon.

  `ActionIconsGuardTest` fails when a menu item or button for one of these verbs uses another icon.
  The guard also caught three Search Everywhere actions: Save Scene As used the open-folder icon, and Close Scene and Close Other Scenes used the trash icon.
- [x] **I2 Missing tooltips.** The hierarchy Lock and Visibility menu items had no tooltip. Their labels change with state, so the EDT-05 guard did not see them. Both now have one.

## 3. Empty states

`EmptyState` (in `editor/imgui`) draws a dimmed icon, a message, an optional hint and optional action buttons, centred in the panel.

- [x] **E1 No project.** The hierarchy, properties, Project window and viewport show "No project open" with New Project and Open Project buttons.
- [x] **E2 No scene, project open.** The hierarchy, properties and viewport say how to open or create a scene.
- [x] **E3 Empty scene.** The hierarchy says "This scene is empty" and has a Create Empty Object button.
- [x] **E4 No selection.** The inspector shows the scene's settings and now says so.
- [x] **E5 Empty asset folder.** Each Asset Browser tab shows an empty state. Empty folders in the Project window show "(empty folder)".
- [x] **E6 No search results.** The hierarchy, Project window and Asset Browser tabs show "No results".

## 4. Error dialogs

`ErrorDialog` is a modal with a title, a message, the detail (for example the exception text) and OK. It is fed by `EditorError.Show` events and queues several errors. Failures are still logged.

- [x] **D1 Open project.** Covers a missing file, a wrong extension, a corrupt file, and a missing default scene or scene file. `OpenProjectFailed` had no listener, and exceptions escaped into the event system's catch-all.
- [x] **D2 Startup last project.** A corrupt last project no longer throws out of `EditorScreen.init`: the error dialog shows, then the wizard.
- [x] **D3 Create project.** Covers "already exists" and any other failure. `CreateProjectFailed` had no listener, so the wizard stayed open with no feedback.
- [x] **D4 Save.** Save project, Save scene and Save scene as. The commands ignored the result. Save Scene As also renamed the scene before a write that could fail; the old name now comes back on failure.
- [x] **D5 Open scene file.** Covers a missing or corrupt file; the failure used to be only logged.
- [x] **D6 Materials.** Saving or loading a `.skmat`.
- [x] **D7 Save script.** The failure reason was never read, and the document was marked saved anyway.
- [x] **D8 Animator controller.** Setting a controller that fails to load showed nothing, and the command was still added to the undo history.

## 5. Undo coverage

- [x] **U1 Direct inspector writes.**
  - The generic inspector wrote every non-String field directly, and so did these custom inspectors: Render, Camera, the lights, Sprite, Particle, Script and UI.
  - `InspectorEditTracker` now snapshots each component's serialized value fields around its inspector call. It turns each finished gesture into one `EditComponentFieldsCommand` (an undoable before/after edit that marks the scene dirty). This covers every inspector, including future ones.
  - The camera inspector's duplicate position and rotation controls wrote the Transform behind its tracker. They are removed; the Transform inspector edits those values.
- [x] **U2 Reset to default.** It now runs `ResetComponentCommand`; it used to call `component.reset()` with no undo.
- [x] **U3 Creation commands with an empty undo.** Create Light, Create Primitive and Duplicate now undo by removing the object. Duplicate no longer changes the original's render id.
- [x] **U4 Apply Animation.** The command had an undo but was marked execute-only, so the undo never ran. Its undo now also removes the Animator it added.
- [x] **U5 Apply Texture.** It now undoes by putting the previous RenderComponent back.
- [x] **U6 Animation drop on the inspector.** Dropping an animation on the Animator inspector now goes through `ApplyAnimationCommand`, like the viewport drop.
- [x] **U6b Ambient light.** The colour picker published one command per frame of a drag. It now writes the value directly, and the tracker records one edit per drag.
- [x] **U6c Dead inspectors.** `Skeleton.kt` and `DayNightCycleComponent.kt` in `imgui/components` were never called (those components use the generic inspector) and wrote components directly. They are deleted.
- [x] **U7 Guard and audit tests.**
  - `UndoCoverageGuardTest` fails when:
    - a component inspector is called outside the tracked path;
    - UI code calls `.reset()`, `addComponent(`, `removeComponent(` or `addChild(`;
    - an `ExecuteOnlyCommand` is not in the allowlist. The allowlist gives a reason for each entry: file IO, project or scene-tab lifecycle, or editor-only state.
  - `InspectorEditTrackerTest` covers the tracker.

## Left out

- **W/E/R for the gizmo tools.** W is the camera's forward key (WASD). The tools stay on 1/2/3/4, and the tooltips now say so.
- **Ctrl+A, select all.** The editor has no multi-selection.
- **Modifier fields in `EditorInputMappings`.** Rebindable shortcuts keep a single key, and Ctrl is fixed per action (search, duplicate). Allowing any modifier needs a new mapping format and a rebinding UI for it. The catalog shows the fixed Ctrl, so the displayed combos are right.
- **Error dialogs for file create, rename, move and delete.** These already show their error inline in the Project window, where the action happens.
- **Asset load failures (`AssetEvent.Failed`).** These are engine-side, asynchronous and frequent (textures fall back to a default). A modal per failure would be noise. They stay in the Console.
- **Undoing Delete Scene.** It deletes a file, and it asks for confirmation first. Undo never rewrites or deletes files (the same rule as the material save).
- **Selection undo.** Selection is not undoable anywhere in the editor, by design.
- **Scene dirty flag on undo.** Some viewport commands set `isDirty` outside the command, so undo does not restore the flag. This is cosmetic: the scene is still saved correctly.
- **Time scale reset on Stop.** Stopping Play sets `DayNightCycleComponent.timeScale` back to 1.0, which overwrites a saved value. It belongs to the play-mode time scale feature, so it is reported to the lead instead of changed here.
