# ANI-21 — Animator window: build and edit state machines (design note)

Status: **approved by the human (2026-11-10)**, with the answers recorded in §11. Implementation follows §9.
Task: ANI-21 (`P1`, REWRITE), human request (Discord 2026-11-10): "a user-friendly Animator window (state graph): add,
rename, duplicate and delete states, assign clips, edit transitions, conditions and parameters, with undo; writes the
animator controller asset."
Depends on: ANI-14 (Animation Editor, done). Works with: GPL-26 (skater configs become project assets).

---

## 1. Goal

A user who has never opened a JSON file can:

1. make a new controller, or open the skater's;
2. add states and give each a clip (from the character or from the project's animation files);
3. draw transitions between states and say when they happen ("when `speed` is greater than 2", "when `jump` is set",
   "when the clip has finished");
4. declare the parameters gameplay or scripts write;
5. undo any of it, save, put the controller on a character and press Play: the character follows the graph;
6. while playing, watch which state is active and change parameter values to try transitions.

Today steps 1 to 4 mean editing `*.controller.json` by hand. The runtime, the asset format and a validated editing
facade already exist (§2). What is missing is the window, a few format additions (§4) and the plumbing between the
saved file and the animators that use it (§5.4).

---

## 2. What exists today

All paths are relative to `src/main/kotlin/com/pafoid/skate/`.

| Piece | Where | What it gives us |
|---|---|---|
| Asset format | `engine/animation/graph/AnimationControllerAsset.kt` | `AnimationControllerAsset(name, defaultState, states, transitions, motions, poses, clipFits)`, JSON through kotlinx (`toJson`/`fromJson`, `ignoreUnknownKeys = true`). `init` rejects an unknown default state, duplicate state names and transitions to unknown states. `issues(availableClips)` lists unresolved clips/motions and event times outside 0..1. |
| States | same file, `StateDef` | `name`, `motion` (a key in `motions`, else a clip name), `speed`, `loop`, `events` (`AnimEventDef(time 0..1, name)`). |
| Transitions | same file, `TransitionDef` | `from` (null = **any state**, with `exceptFrom`), `to`, `conditions`, `duration` (s), `hasExitTime` + `exitTime` (normalized). **List order is priority**: the first match wins. |
| Conditions | same file, `TransitionCondition` | `parameter`, `op` ∈ `GREATER, LESS, EQUALS, TRUE, FALSE, TRIGGER`, `threshold`. A transition fires when *all* its conditions hold. |
| Motions | `engine/animation/graph/MotionDef.kt` | `ClipRef`, `Blend1DDef` (one float parameter, thresholds), `Blend2DDef` (two parameters, positions), immutable `with*` helpers. |
| Runtime | `engine/animation/graph/AnimationStateMachine.kt` | Pure; weight-based crossfades, interruption freezes the blended pose, events fire per cycle, triggers are consumed when their transition starts, `blendOverrides` (per target state) replace asset durations. |
| Parameters | `engine/animation/graph/AnimParameters.kt` | Untyped bags of floats, bools and triggers. **They are not declared anywhere in the asset**: a parameter exists only as a name inside a condition or a blend tree. |
| Editing facade | `engine/animation/graph/ControllerEditor.kt` | `addState`, `updateState`, `removeState` (drops its transitions, moves the default), `renameState` (rewrites transitions and `exceptFrom`), `setDefaultState`, `add/update/remove/moveTransition`, `add/removeEvent`, `set/remove/updateMotion`, blend-tree updates. Every operation rebuilds and validates the asset and throws on failure. Its KDoc already says it is "meant to sit behind editor commands … take `editor.asset` before/after an edit for undo/redo snapshots". **This note builds on it rather than replacing it.** |
| Animator | `engine/ecs/components/Animator.kt` | `controllerPath` (saved with the scene), `resolveController` (reads the file through `AssetsManager.readText`, else the file system, else the classpath; logs `issues` and keeps single-clip playback on any problem), `useController` (clips from `animations`, `clipFits`, procedural `poses` for missing clips, blend trees), `mask`, `play(name)` = **sets the bool parameter `name`** (and clears the previous request). |
| Gameplay → parameters | `engine/ecs/systems/CharacterAnimationDriver.kt`, `game/player/SkateBehavior.kt` | Each frame the behavior writes floats (`speed`, `verticalSpeed`, `airTime`, `landImpact`, `manualBalance`, `turn`); a state change calls `animator.play(state)`, i.e. a bool request; `animationBlendTimes` override transition durations. |
| Skater controller | `src/main/resources/configs/skater.controller.json` (`Assets.Configs.SKATER_CONTROLLER`, `game/prefabs/Skater.kt`) | 13 states, 18 transitions, 5 procedural poses, 1 clip fit, no blend trees. Every condition is `<stateName> TRUE`: the request-bool convention above. |
| Inspector | `editor/imgui/components/Animator.kt` | Clip list (drop `ANIMATION` payloads), controller picker, mask picker, current-state readout. |
| Picker data | `editor/animation/AnimatorControllerCatalog.kt` | Bundled controllers + every `*.controller.json` under the project; `stateReadout` ("a -> b"). |
| Commands | `editor/commands/objects/SetAnimatorControllerCommand.kt`, `SetAnimatorMaskCommand.kt`, handled by `editor/ui/handlers/AnimatorActionHandler.kt` | Undoable assignment of a controller/mask to an Animator. |
| ANI-14 pattern | `editor/animation/clipeditor/*`, `editor/commands/animation/ClipEditCommand.kt`, `SaveAnimationClipCommand.kt`, `editor/ui/windows/AnimationEditorWindow.kt` | Document + snapshot command (`KeyedCommand`, one class, translated name per action) + execute-only atomic save + a pure, unit-tested controller behind a thin ImGui window. Drags preview live and commit one command on release. |
| Asset layout | `docs/design/AST-26.md` (approved) | Controllers live in `Assets/Animations/`. Engine defaults are `engine:<Type>/…`; a project copy with `.meta` `engine.source` **overrides** them. GPL-26 moves the skater controller to `engine:Animations/skater.controller.json` under that rule. **On `integration/32` the `engine:` resolver and the override copy are not in the code yet** (only `editor/project/EngineAssetCopier.kt` and `ProjectLayout.engineDefaultsDir`, the older layout), although AST-27 is ticked in the backlog. §5.2 therefore has a fallback that does not need them. |
| Node editor libraries | imgui-java 1.90.0 jar | Ships `imgui.extension.imnodes` and `imgui.extension.nodeditor`. Neither is used in the editor today. |

---

## 3. The window

### 3.1 Layout

```
┌ Animator ─ toolbar: controller ▾ | New | Save * | Preview on: Skater ▾ | ● Live (play mode) ───────────┐
├──────────────────┬───────────────────────────────────────────────────────┬─────────────────────────────┤
│ Parameters       │  graph canvas                                         │ Inspector                   │
│  + Float/Bool/   │                                                        │ (selected state, transition │
│    Trigger       │   [Any State]──────────────┐                           │  or nothing = controller)   │
│  speed    0.0    │        │                    ▼                          │                             │
│  jump     ☐      │   ▶[idle]═════▶[walking]═══▶[running]                  │  State "walking"            │
│  land     ⚡     │       ▲  ◀════════  │                                  │   Clip: [walking ▾] [Edit]  │
│                  │       └──[jump]◀───┘                                   │   Speed 1.0  Loop ☑         │
│ States (list)    │                                                        │   Events …                  │
│  idle (default)  │  right-click: add state, add transition, delete …      │   Transitions out (order):  │
│  walking …       │  wheel zoom, middle-drag pan, F frame all              │    → running  speed > 2     │
└──────────────────┴───────────────────────────────────────────────────────┴─────────────────────────────┘
```

- **Toolbar:**
  - The **controller** combo lists the same entries as the Animator inspector's picker (`AnimatorControllerCatalog`).
  - **New** creates `Assets/Animations/<name>.controller.json` with one state (§5.1).
  - **Save** shows `*` while there are unsaved changes.
  - **Preview on** picks the character whose clips fill the clip pickers: the skater, or the selected object.
- **Parameters panel:** the declared parameters (§4.1) with type and default; add, rename, delete and change type.
  In play mode the panel shows **live values** and lets you edit them (§6).
- **States list:** the same states as the canvas, for keyboard users and big graphs. Selecting a state in the list
  selects it on the canvas.
- **Canvas:** one rounded box per state.
  - The default state has a ▶ marker and the active state (play mode) is highlighted.
  - A special **Any State** box appears when a transition has `from = null`.
  - Each transition is an arrow. A→B and B→A are drawn as two offset arrows; several transitions with the same
    source and target are drawn as one arrow with a count badge.
- **Inspector:** shows the selected state, the selected transition, or the controller's own settings when nothing is
  selected.

### 3.2 Interactions (all undoable)

| Action | How | Command action key |
|---|---|---|
| Add state | Right-click on the canvas > Add state, or **+** in the States list. The box appears under the mouse with a free name ("New State", "New State 2") and no clip yet (§4.4). | `add_state` |
| Add state from a clip | Drag an animation (Project window / Asset Browser `ANIMATION` payload, or a `.skanim`) onto the canvas: the state is named after the clip and plays it. | `add_state` |
| Rename | Double-click the name, or edit Name in the inspector. Duplicate names are refused with a message (`ControllerEditor.renameState`). Transitions follow. | `rename_state` |
| Duplicate | Ctrl+D or right-click > Duplicate. Copies motion, speed, loop and events to "<name> 2", offset by 30 px. Transitions are not copied (Q3). | `duplicate_state` |
| Delete | Delete key or right-click > Delete. Its transitions go too. The last state cannot be deleted; deleting the default moves the default to the first state (both existing `ControllerEditor` behaviour). | `delete_state` |
| Set default | Right-click > Set as default state, or the inspector checkbox. | `set_default` |
| Move boxes | Drag. One command on release, like ANI-14 key drags. | `move_states` |
| Assign a clip | Inspector > Clip combo: clips on the preview character, then the project's animation files, then the procedural placeholder poses. A project file not yet on the character is recorded in the controller (§4.3). "Edit" opens the clip in the Animation Editor (ANI-14, `OpenAnimationClip`). | `set_motion` |
| Speed, loop | Inspector fields, committed on release or Enter. | `set_state` |
| State events | Inspector list: name + time. The time is entered as a **frame of the clip** when the clip's length is known and stored normalized (0..1) as today. | `add_event`, `remove_event`, `set_state` |
| Add transition | Right-click on a state > Make transition, then click the target. Or drag from a box's edge to another box. | `add_transition` |
| Edit transition | Inspector: conditions (§3.3), "Wait for the end of the clip" (`hasExitTime`, exit time shown in %), blend time in seconds, "Any state except…" for any-state transitions. | `set_transition` |
| Priority | Inspector of a state: its outgoing transitions in evaluation order, with ▲▼. Any-state transitions are listed separately ("checked before/after…"). The real order is still one global list (`moveTransition`). | `move_transition` |
| Delete transition | Select the arrow + Delete. | `remove_transition` |
| Parameters | Add (name, type, default), rename (conditions and blend trees follow), change type (conditions whose operator no longer fits are flagged, not deleted), delete (refused while used, with "used by: …"). | `add_parameter`, `rename_parameter`, `set_parameter`, `remove_parameter` |
| Controller settings | Name; "Missing clip" warnings from `issues`; the procedural poses and clip fits listed read-only (they keep working; editing them is out of scope). | `set_controller` |

Shortcuts work while the window is focused: Delete, Ctrl+D, F (frame all), Ctrl+C / Ctrl+V for states (Q8).
Undo and Redo stay global (Ctrl+Z / Ctrl+Y, `UndoRedoManager`). The window does not bind Ctrl+S: the editor uses it
for the scene (the same choice as ANI-14).

### 3.3 Conditions, in words

The condition editor is a row of three widgets whose choices depend on the parameter type. The JSON does not change.

| Parameter type | Operator choices shown | Stored as |
|---|---|---|
| Float | "is greater than", "is less than", "equals" + number | `GREATER`, `LESS`, `EQUALS` + `threshold` |
| Bool | "is on", "is off" | `TRUE`, `FALSE` |
| Trigger | "is set" (consumed when the transition starts) | `TRIGGER` |

- A transition with no condition and no exit time never fires, which matches the runtime (`findTransition` skips it).
  The inspector says so in a warning line.
- Each arrow's tooltip reads like a sentence: "walking → running when speed > 2 (blend 0.20 s)".
- When gameplay replaces a blend time (`blendOverrides` from the character config, GPL-06), the transition inspector
  shows "Blend time is set by the character config while playing" next to the field.

### 3.4 Request parameters (the skater convention)

The skater's controller drives states with bool parameters named after them: `Animator.play("ride")` turns on `ride`
and turns off the previous one. The window makes this visible instead of hiding it:

- A bool parameter whose name equals a state name is tagged **request** in the Parameters panel, with the tooltip
  "Turned on by `play("ride")` from gameplay or scripts".
- The transition context menu offers **"Go here when requested"**: it adds a transition from the source state with
  condition `<target> is on`, and declares the bool parameter if it is missing. That is exactly what all 18 skater
  transitions are.

---

## 4. Asset format changes

All changes are **additive and optional**, so every existing controller file still loads unchanged. Old files parse
because the serializer already has `ignoreUnknownKeys = true` and every new field has a default. The format has no
`version` field today; §4.6 adds one.

### 4.1 Declared parameters

```json
"parameters": [
  { "name": "speed", "type": "FLOAT", "default": 0.0 },
  { "name": "ride",  "type": "BOOL" },
  { "name": "kick",  "type": "TRIGGER" }
]
```

- New `ParameterDef(name, type: FLOAT | BOOL | TRIGGER, default: Float = 0)`; bools use 0/1.
- **Runtime:** `AnimationStateMachine` seeds `AnimParameters` from the defaults when it is built. Undeclared names
  behave as today (0 / false), so gameplay that writes names the asset never declared keeps working.
- **Validation:** `issues()` gains *warnings*, not errors: a condition on an undeclared parameter, or an operator that
  does not fit the declared type. Errors keep blocking `resolveController`; warnings are only shown in the window.
- **Old files:** when `parameters` is empty, the window infers the list from the conditions and blend trees (an
  operator `TRUE`/`FALSE` gives a bool, `TRIGGER` a trigger, anything else a float) and writes it on the next save.
  For the skater file this yields 12 request bools.

### 4.2 Graph layout

```json
"layout": { "idle": { "x": 0, "y": 0 }, "walking": { "x": 220, "y": 0 }, "@any": { "x": -220, "y": -120 } }
```

- Box positions in canvas units, keyed by state name (`@any` is the Any State box). The runtime ignores the field.
- `renameState` and `removeState` keep it in step.
- A file without a layout is laid out once automatically: the default state on the left, then a breadth-first order
  along the transitions, in a grid. Nothing is written until the user saves.
- Keeping the layout in the same file keeps controllers single-file and git-diffable, like scenes (Q2).

### 4.3 Clip sources

```json
"clips": [ "Animations/kickflip.skanim" ]
```

- Today a state's clip name must already be on the character's Animator (`animations`), so "assign a clip" from the
  project would also need an edit of the character, in another window. The new optional map `clips` (clip name →
  project-relative path, AST-09 style) lets a controller carry its own clips.
- `Animator.resolveController` loads the listed clips that are not already on the animator, through
  `AssetsManager.loadAnimationSync`. A clip already on the animator with the same name wins, which matches the rule
  for procedural `poses`.
- *As built:* `clips` is a list of asset paths, not a name → path map. The clip name is the one stored in the file,
  and `AssetPaths.PATH_KEYS` already treats a `clips` string array as asset references (a map would not be).
- Export packing already follows `*Path`/`clips` fields through `AssetPaths` (`"clips"` is in its key list), so the
  files are packed. This needs a check in ANI-21a, plus a test.

### 4.4 States without a clip

- A new state has no clip yet (`motion = ""`).
- Today that would make the controller fail to build (`AnimationStateMachine` requires every motion). Instead, an empty
  motion plays the **rest pose** (a new `RestMotion`, duration 1 s) and `issues()` reports it as a *warning*.
- A freshly made controller therefore always runs, and the window marks the state with a ⚠ "No clip".

### 4.5 What does not change

- Transition semantics and priority.
- The `poses`, `clipFits` and blend-tree JSON.
- The runtime state machine, apart from the parameter defaults and `RestMotion`.
- The scene format: `Animator.controllerPath` stays a path.

### 4.6 Format version

Add `"version": 1` with a default of 1, so later changes can migrate. This follows the convention of `.skanim`
(ANI-13) and the AST versioning rules.

---

## 5. Editing model, files and reload

### 5.1 Documents and commands (same pattern as ANI-14)

The new code lives in `editor/animation/controllereditor/` and `editor/commands/animation/`:

- **`ControllerDocument(file, asset, saved, revision, readOnly)`.** One per open file, cached by path, so reopening
  keeps undo coherent. This is the ANI-14 `ClipDocument` rule.
- **`ControllerEditCommand(document, before, after, action)`.** `KeyedCommand` with key
  `command.ControllerEditCommand.<action>` (§3.2 lists the actions); execute and undo swap whole assets. The assets are
  small and immutable, and `ControllerEditor` already produces the validated `after`. A refused edit (duplicate name,
  last state) never becomes a command: the message is shown in the window.
- **`SaveControllerCommand`.** Execute-only, with an `UndoCoverageGuardTest` entry ("writes the .controller.json; undo
  never rewrites files"). It writes atomically (temp file + move) and reports failure through `ReportsFailure`, the
  same as `SaveAnimationClipCommand`.
- **`AnimatorWindowController`.** Pure, no ImGui: selection, canvas view (pan/zoom), drag gestures, the clip-picker
  data, the parameter inference and the play-mode link (§6). The window stays a thin view over it.
- **Pure canvas maths** (`ControllerGraphView`): box and arrow geometry, hit tests, offsets for paired arrows, the
  auto-layout.

### 5.2 Where controllers live

- **New controllers** go in `Assets/Animations/` (AST-26 decision 2), named `<Name>.controller.json`. That is already
  what `AnimatorControllerCatalog.isController` and the inspector's drop target accept.
- **The skater controller** is an engine default.
  - Before GPL-26 it is the bundled resource `configs/skater.controller.json`.
  - After GPL-26 it is `engine:Animations/skater.controller.json`.
  - Either way the window opens it **read-only**, with a banner and a button **"Make a project copy to edit"**:
    - After GPL-26, the copy is the AST-26 **override**: it goes to `Assets/Animations/skater.controller.json` with
      `.meta` `engine.source`, so every skater in the project picks it up with no change to scenes. The copy itself
      goes through the assets team's override-copy API.
    - Before GPL-26, the button saves a copy into `Assets/Animations/` and offers to point the selected Animator at it
      (`SetAnimatorControllerCommand`, undoable).
- **Packed builds:** nothing changes. The editor never writes into the engine jar.

### 5.3 Opening the window

- **Animator inspector:** an **Edit** button next to the controller picker opens the window on that controller and
  sets "Preview on" to this object.
- **Project window:** double-click on `*.controller.json` (editor team, one line in `ProjectWindow.handleDoubleClick`,
  as requested for `.skanim` in ANI-14).
- **View > Windows > Animator** (category Animation, docked at the bottom like the Animation Editor). Without a
  controller open, it shows a short "Open or create a controller" page.

### 5.4 Saved changes reach the characters

- After a successful save, the window publishes `AnimatorControllerSaved(path)`.
- An editor handler re-runs `resolveController` on every Animator in the open scenes whose `controllerPath` resolves to
  that file. That includes the engine-default path when the saved file is its override.
- The state machine restarts in its default state. That is acceptable in edit mode; for play mode see Q5.
- The catalog's scan is refreshed so a new file appears in the inspector's picker (`AnimatorControllerCatalog.refresh`).

---

## 6. Play mode: watching and poking the graph

When the game runs in the editor and "Preview on" names an object whose Animator uses the open controller:

- The active state is highlighted. A running crossfade shows as a progress bar on its arrow. A small time bar
  (`normalizedTime`) runs inside the active box.
- Fired events flash briefly on their state (`firedEvents`).
- The Parameters panel shows live values from `animator.parameters`. Editing a value there writes the runtime
  parameter **only**: it is not undoable and not saved, and the panel says "live". Gameplay may overwrite it next frame
  (`writeAnimationParameters`); the tooltip says so.
- Edits to the graph during play are allowed and saved as usual. Whether they apply immediately is Q5.

The read side uses only public state: `currentState`, `nextState`, `normalizedTime`, `firedEvents` and `parameters`.
It needs one small addition, a public fade progress (`fadeElapsed / fadeDuration`).

---

## 7. Relation to other windows

- **Animation Editor (ANI-14):** "Edit" on a state opens its clip. `.skanim` clips open directly; imported clips go
  through ANI-14's "editable copy" flow, and the state is offered the copy. Events stay in two places, as in ANI-12
  §2.1: clip events in the `.skanim`, state events (normalized) in the controller. Both fire at runtime.
- **Animator inspector:** keeps the controller picker and the mask, and gains the Edit button. The current-state
  readout stays.
- **Video to Animation:** unchanged. A created clip can be dragged onto the canvas (§3.2).

---

## 8. Testing

All without GL or ImGui, as unit tests:

- **Format:** `parameters`, `layout`, `clips`, `version` and an empty motion round-trip. Old files load unchanged (the
  bundled skater controller is the fixture). `issues` gives warnings vs errors. Parameter inference on the skater file
  yields its 12 bools.
- **Runtime:** parameter defaults seed `AnimParameters`. `RestMotion` plays. Clips from `clips` are loaded by
  `resolveController`, with a fake reader and a fake animation loader.
- **Commands:** every §3.2 action applies, undoes and redoes through a real `UndoRedoManager`. Refused edits add no
  history. A drag is one command. Duplicate gives a free name. Rename and delete keep `layout` and `exceptFrom` in step.
- **Window controller:**
  - Selection, connect mode, the clip-picker contents (character clips, project files, poses), read-only engine
    defaults and "Make a project copy".
  - Save writes atomically and makes the document clean; undo after a save makes it dirty again.
  - Save publishes the reload event.
- **Canvas maths:** hit tests, paired-arrow offsets, frame-all, the auto-layout without overlaps.
- **Acceptance (`Done when`):** build a controller from scratch through the window controller only (new, three states,
  clips, two transitions with a float and a trigger, parameters, save). Then load it on a test skeleton through
  `Animator.resolveController`, set the parameters and check that `AnimationStateMachine` walks
  idle → walk → jump. Finally, open the bundled skater controller read-only and make a project copy.
- **Architecture guards:** the window passes `WindowStandardGuardTest` (icon, help strings EN/FR, header),
  `TooltipGuardTest`/`TooltipToggleGuardTest`, `UiMutationPipelineGuardTest` (the window calls the controller only)
  and `UndoCoverageGuardTest` (the new execute-only save is listed).

---

## 9. Slicing and owners

| Slice | Owner (reviewer) | Content | Depends on |
|---|---|---|---|
| **ANI-21a** Format and runtime | skate-animation | §4: `parameters`, `layout`, `clips`, `version`, empty motion → `RestMotion`, warnings in `issues`, parameter defaults at runtime, clip loading in `resolveController`, packing check. Tests. | ANI-21 approved |
| **ANI-21b** Editing core | skate-animation (skate-editor) | §5.1: document, commands, save command, `AnimatorWindowController`, `ControllerGraphView`, parameter inference, the acceptance test from §8. No UI. | 21a |
| **ANI-21c** Animator window | skate-animation (skate-editor, lead decision as for ANI-14) | §3: the ImGui window, canvas drawing and input, inspector, parameters panel, strings EN/FR, help, registration (the one-line `WindowRegistry`/`WindowCategory`/`DefaultDockLayout` entries, as in ANI-14), Edit button in the Animator inspector, reload on save (§5.4). | 21b |
| **ANI-21d** Play-mode view | skate-animation | §6: live highlight, fade progress, events flash, live parameter values. | 21c |
| **EDT-xx** Open from the Project window | skate-editor | Double-click `*.controller.json` publishes the open event (§5.3). Can share a task with the pending `.skanim` double-click from ANI-14. | 21c |
| **GPL-26** (existing) | skate-gameplay with skate-animation | Skater controller as `engine:Animations/skater.controller.json` with project override. The window's "Make a project copy" then calls the assets team's override copy. | AST-27 (done) |

21a and 21b are each one round of work. 21c is the biggest slice and may need two. GPL-26 is independent: before it
lands, the window uses the "save a copy and point the Animator at it" fallback (§5.2).

---

## 10. Out of scope

- **Blend-tree editing.** States can point at an existing blend tree (shown read-only, "Blend 1D on speed: 3 clips"),
  but creating and editing trees is the follow-up task ANI-23 (Q6).
- **Editing procedural poses and clip fits.** They are shown read-only.
- **Sub-state machines, layers and per-layer masks.** The mask stays on the Animator (ANI-03).
- **A rendered character preview inside the window.** The scene view or game view shows the character; ANI-14's
  skeleton preview could be reused later.
- **Migration tooling.** Old files load as they are (§4), so none is needed.

---

## 11. Open questions (with my recommendation) and the human's answers

The human approved the note on 2026-11-10. Decisions:

| Q | Decision |
|---|---|
| 1 | Draw the graph ourselves with the draw list. |
| 2 | Layout inside the controller file. |
| 3 | Plain Duplicate without transitions, plus a "Duplicate with transitions" item. |
| 4 | Declared parameters optional in the file. **And:** controller files must be fully editable in the editor through a user-friendly interface (states, transitions, conditions, parameters); nobody should need to edit the file by hand. |
| 5 | Apply on Save, rebuilding the animators, even in play mode. |
| 6 | No blend trees here; the follow-up is **ANI-23** (ANI-22 is the Video to Animation scroll fix). |
| 7 | Parameter names from gameplay later: **GPL-31**. |
| 8 | Copy/paste within the window. |
| 9 | Same as ANI-14 for now, plus the shared prompt: **EDT-82**. |

The recommendations as proposed:

1. **Draw the graph ourselves, or use imnodes / imgui-node-editor from the imgui-java jar?**
   *Recommendation: draw it ourselves with the draw list, as ANI-14 does.*
   - The libraries are built around pins and links between pins. A state machine has neither: arrows go from box to
     box, A→B and B→A need offsets, and Any State is special.
   - Custom drawing keeps the geometry and hit tests pure and unit-testable, and follows the editor theme.
   - It does not start using another native extension. The extensions are in the imgui-java natives already, but
     nothing in the editor exercises them yet.
   - The cost is about 300 lines of canvas code.
2. **Where does the layout live?**
   *Recommendation: inside the controller file (`layout`), ignored by the runtime.* One file per controller, diffable,
   moves with the file. A sidecar or the `.meta` would split the asset and get lost on copy.
3. **Does Duplicate copy transitions?**
   *Recommendation: no by default, with a second menu item "Duplicate with transitions"* that copies incoming and
   outgoing transitions retargeted to the copy. A plain copy is predictable: the duplicate does nothing until wired.
4. **Declared parameters: required or optional?**
   *Recommendation: optional in the file, always present in the window.* Old files keep loading; the window infers the
   list (§4.1) and saves it. Undeclared names stay legal at runtime, so gameplay code does not have to change.
5. **Do graph edits apply to a running game immediately?**
   *Recommendation: yes on Save, by rebuilding the animators that use the file (§5.4), even in play mode.* The state
   restarts at the default, which is clear and safe. Rebuilding on every edit (no save) would surprise people and lose
   the "saved = what runs" rule.
6. **Blend trees in this task?**
   *Recommendation: no.* The skater controller has none. A follow-up task (ANI-23, "Blend tree editor") adds a tree
   editor in the state inspector, once the basic window is in use.
7. **Should gameplay publish the names of the parameters it writes**, so the window can suggest `speed`, `airTime`, …?
   *Recommendation: yes, later, as a small skate-gameplay task.* `CharacterBehavior` exposes the list, and the
   Parameters panel offers it under "Written by the character". For ANI-21, inference from the file (§4.1) is enough.
8. **Copy/paste of states between controllers?**
   *Recommendation: within the window only (Ctrl+C / Ctrl+V of selected states and the transitions between them)*, in
   ANI-21c if time allows, otherwise in the follow-up task.
9. **Unsaved changes when the project closes?**
   *Recommendation: the same as ANI-14 for now (documents stay in memory, `*` marks them), plus a shared editor-wide
   "unsaved assets" prompt from the editor team covering the scene, clips and controllers.* That prompt is a separate
   EDT task, not part of ANI-21.

---

## 12. Cross-domain requests (for the lead)

- **skate-editor:**
  - the double-click open from the Project window (§5.3);
  - review of ANI-21b/c;
  - the editor-wide unsaved-assets prompt (Q9, now EDT-82).
- **skate-assets:** a small API to "make the project override of an engine default" (copy + `.meta` `engine.source`)
  that the window can call. I could not find the `engine:` resolver or the override copy on `integration/32`, so
  please confirm whether AST-27 landed elsewhere. GPL-26 depends on it too.
- **skate-gameplay:**
  - GPL-26 as planned;
  - later, the list of parameters a behavior writes (Q7, now GPL-31).
