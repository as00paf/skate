# INP-11 — Project input actions

Status: **approved** (human, 2026-11-18). Design by skate-input.

Answers (human, 2026-11-18):
- Q1: one file, `Assets/Data/input.actions.json` (option B).
- Q2: plain names for now; no `nameKey` in INP-11.
- Q3: "Change id…" stays, with a warning.
- Q4: "active at start" now; an `InputContext` component later, as a separate task.
Origin: EDT-106 audit (`docs/design/EDT-106.md`), human decision 2026-11-18: P2, design note first.
UI rule from EDT-106: the owner (INP) writes the UI, skate-editor reviews it.

## 1. Problem

Every input action is defined in Kotlin today:

| Source | Actions | Registered by |
|---|---|---|
| `engine/input/UiNavigationActions` | `ui.*` (confirm, cancel, navigate, …) | engine, always |
| `engine/gameplay/CharacterActions` | `moveX/Y`, `lookX/Y`, `jump`, `sprint`, `crouch`, `toggle_mount` | engine, always |
| `engine/gameplay/camera/CameraCycle` | camera cycle | engine |
| `game/input/SkateActions` | `skate.*` | game, through `ActionPresets` |

Key Bindings can only change the bindings of these actions. The changes are stored as overrides in the project file
(`GameplaySettings.inputMappings.bindings`, a `BindingProfile`).

A game made in the editor without Kotlin (a puzzle game, a shooter driven by `.kts` scripts) cannot add "Fire",
"Rotate piece" or "Open map". It has to reuse `jump` / `sprint` under the wrong names, or write code. Scripts can
already push contexts and read any action by name (`ScriptInput.action`, `isActionDown`, `wasActionPressed`,
`pushContext`), so the only missing piece is **defining** actions and contexts as data.

## 2. Goals and non-goals

Goals:
- A project defines its own actions (id, name, context, category, kind, default bindings) and contexts in a
  project asset, with no code.
- Key Bindings lets you add, rename, edit and remove them, and edit their default bindings, with undo.
- The editor's Play mode and the exported game load the same definitions, and scripts read them by id.
- Nothing changes for code-defined actions or for existing projects.

Non-goals (later tasks):
- Player rebinding in the exported game, saved in `game.settings.json`. Today the exported game has no per-player
  bindings, and this design leaves room for them (§4.3).
- The action picker on component fields (INP-12, which builds on the catalogue from §4.4).
- Composite 2D actions (`move` as one Vector2). An axis stays a pair of actions, as `moveX` / `moveY` are today.

## 3. Options considered

| | Where definitions live | For | Against |
|---|---|---|---|
| A | inside the project file (`InputMappings.actions`) | export and loading already work; one undo path | the project file grows; harder to diff, merge and copy between projects |
| **B (proposed)** | **project asset `Assets/Data/input.actions.json`** | diffable and mergeable in Git; copyable to another project; same pattern as `*.character.json`; read through `AssetsManager.readText` in both editor and pack | a new file to load, save and pack |
| C | one file per context (`Assets/Data/Input/<context>.actions.json`) | smaller files | more files and more loading code for little gain at this scale |

B matches the task text ("a project asset"). The project file only stores the asset's path, so a project can
rename or move it.

## 4. Design

### 4.1 Asset format (`engine/input/ProjectActionSet.kt`, pure, `@Serializable`)

```json
{
  "version": 1,
  "contexts": [
    { "id": "vehicle", "name": "Vehicle", "priority": 10, "blocking": "consume", "activeAtStart": false }
  ],
  "actions": [
    { "id": "fire", "name": "Fire", "context": "gameplay", "category": "Combat", "kind": "button",
      "defaults": [ { "type": "key", "key": 70 }, { "type": "pad", "button": 2 } ], "events": true },
    { "id": "steer", "name": "Steer", "context": "vehicle", "category": "Driving", "kind": "axis",
      "defaults": [ { "type": "key", "key": 68 }, { "type": "key", "key": 65, "scale": -1.0 },
                    { "type": "axis", "axis": 0 } ] }
  ]
}
```

- `ProjectActionSet(version, contexts: List<ProjectContext>, actions: List<ProjectAction>)`.
- `ProjectAction` maps 1:1 to `ActionDefinition`. `name` is shown in UIs. An optional `nameKey` points to a game
  string for translated names; without it, `name` is used as is. `events` maps to `publishEvents`.
- `kind` (`button` | `axis`) is metadata for the UI and for INP-12. An axis row shows "+" and "−" binding slots.
  The reader does not change: both kinds are a float with a scale, as today.
- Bindings use short serial names (`key`, `mouse`, `pad`, `axis`) so the file reads well in a diff. This needs
  `@SerialName` on the `ActionBinding` subclasses. Old `BindingProfile` JSON (in the project file) uses the class
  names, so the profile decoder keeps accepting the old names. A test covers decoding both.
- Validation (pure, tested):
  - ids match `[a-z][a-z0-9_.]*`;
  - no duplicate ids;
  - an action id may not reuse a code-defined id (§4.2);
  - an action whose context is neither built in nor declared in the file is rejected with a warning.
  Invalid entries are skipped and reported, and the rest of the file loads.

### 4.2 Loading at runtime (`engine/input/ProjectActions.kt`)

- `InputMappings` gains `actionsPath: String? = null`. New projects get `Assets/Data/input.actions.json`.
  Old projects have null, which means no project actions: no migration needed.
- When `InputSystem.mappings` is set (project open, `ApplyMappings`), `ProjectActions` reads the file through an
  injected `readText` (`AssetsManager.readText`: a project file in the editor, the pack in an export). It then
  registers an `ActionPresets` entry `"project"`, so the actions are applied again on every scene load like the
  game presets.
- Applying the set:
  - defines each valid action in the `ActionMap`;
  - **removes** the actions it defined before and that are now gone. This needs a new
    `ActionMap.remove(id)`, which keeps overrides of a removed id aside the way `pending` already does.
  - Code-defined ids win: a project action that collides with one is skipped with a warning. The editor
    prevents the collision in the first place.
- Contexts with `activeAtStart = true` are pushed on the `InputContextStack` when the scene starts playing
  (priority and blocking from the file) and popped when it stops. That way a game with no code can still use its
  own context. Other contexts are pushed by scripts (`pushContext("vehicle", …)`), as today.
- The project's priority/blocking defaults are only used by `activeAtStart` and by a new overload
  `pushContext(id)` that reads them from the set. The current explicit-argument calls are unchanged.

### 4.3 How the binding layers stack

1. **Definitions with defaults:** engine code, game presets and the project asset (new).
2. **Project overrides:** the existing `InputMappings.bindings`. They stay for **code** actions only, as today.
   For a **project** action, editing a binding in Key Bindings edits its defaults in the asset directly. There is
   no project override for project actions, so they have only one source of truth.
3. **Player overrides:** future, in `game.settings.json`. Overrides are keyed by action id, so they work the same
   for both kinds of action.

### 4.4 Catalogue for pickers (INP-12)

`ActionMap.definitions()` already lists everything. INP-11 adds `ActionDefinition.origin`
(`ENGINE` | `GAME` | `PROJECT`) and `kind`, so pickers and the Key Bindings list can group and label actions.
This field is runtime only and is not saved in profiles.

### 4.5 Editor UI (Key Bindings window, Actions tab; INP writes it, skate-editor reviews)

- Toolbar: **New action** opens a small dialog with:
  - id (validated live, with the reason shown when invalid);
  - name;
  - context (built-in contexts, project contexts, then "New context…");
  - category (free text, with existing categories suggested);
  - kind.
  Default bindings are then captured with the existing click-to-rebind flow.
- Every row shows its origin. Code actions keep today's behaviour (rebind, reset, with a lock icon and a
  "defined in code" tooltip). Project actions get a context menu with:
  - **Edit** (name, category, context, kind, events);
  - **Duplicate**;
  - **Delete** (confirmation; it lists the scene component fields that reference the id, using INP-12's
    `@InputActionRef` once that exists).
- **Changing an action's id** is a separate "Change id…" item with a warning. It rewrites the project's overrides
  and the `@InputActionRef` fields in open scenes. Scripts are not rewritten; the warning says so.
- **Contexts** section (collapsible, under the action list): project contexts with name, priority, blocking and
  "active at start". Built-in contexts are shown read-only.
- Conflict detection, the shared-input hint and profile import/export work unchanged on project actions.
- Strings go in `editor/input.properties` and `input_fr.properties`, each with tooltips.

### 4.6 Commands, saving and reload

- `EditProjectActionsCommand(before: ProjectActionSet, after: ProjectActionSet)`:
  - a snapshot command, as `EditBindingsCommand` is today;
  - `execute` and `undo` write the asset (an atomic write: temp file, then rename) and re-apply it to the live
    `ActionMap`;
  - a failed write shows the error dialog and leaves the map as it was.
- Each edit saves at once, like binding edits do today. That avoids yet another "unsaved" state for EDT-82.
- The file is reloaded when it changes on disk (VCS pull, external editor), using the asset hot-reload watcher
  where one exists, else on window focus.
- Export: the asset must be in the pack. `Assets/Data` is packed today; skate-assets confirms this in review.

### 4.7 Files

| File | Owner | Change |
|---|---|---|
| `engine/input/ProjectActionSet.kt` | INP | new: format and validation |
| `engine/input/ProjectActions.kt` | INP | new: load, apply/unapply, start contexts |
| `engine/input/ActionMap.kt` | INP | `remove(id)`, `origin`, `kind`, `@SerialName` on bindings |
| `engine/input/InputMapping.kt` | INP | `actionsPath` |
| `engine/ecs/systems/InputSystem.kt` | INP | wire `ProjectActions`; push/pop start contexts on play |
| `editor/ui/windows/ActionBindingsPanel.kt`, `ActionBindingsModel.kt`, new `ProjectActionDialog.kt` | INP (EDT review) | UI |
| `editor/commands/input/EditProjectActionsCommand.kt` | INP (EDT review) | new |
| `engine/core/Engine.kt` | ECS | pass `assetsManager::readText` to `InputSystem` (one line) |
| `editor/systems/ProjectManager.kt` | EDT | new projects get `actionsPath` and an empty asset |
| `engine/scripting/...ScriptInput` | SCR | optional `pushContext(id)` overload that uses the project defaults |

## 5. Implementation steps

Each step builds and passes `gradlew test`, and the skate demo plays the same after every step.

1. `ProjectActionSet` with its JSON and validation; binding serial names that still decode the old names;
   `ActionMap.remove`, `origin` and `kind`. Unit tests only.
2. `ProjectActions` and the `InputSystem` wiring, plus start contexts on play. Tests with a fake `readText`
   and a fake stack (apply, re-apply, removal, collision with a code id, start-context push and pop).
3. The command and the save/reload path. Tests: undo and redo restore the file and the map; a failed write
   changes nothing.
4. Key Bindings UI (new action dialog, row menu, contexts section). Tests on the model: grouping by origin, id
   validation messages, which actions a delete affects.
5. NEEDS requests: ECS (`Engine` wiring), EDT (new-project default and review), SCR (`pushContext(id)`).

Done when: a new project defines "fire" and a "vehicle" context in the editor, a `.kts` script reads
`isActionDown("fire")` in Play and in an export, rebinding and undo work, and the tests above pass.

## 6. Risks

- **Binding serial names.** Changing them touches saved profiles. Mitigation: the decoder accepts both names, with
  a round-trip test on an old profile.
- **Id changes break scripts.** Mitigation: "Change id…" is separate from renaming, with a warning. The normal
  rename only changes the name.
- **Preset re-application order.** Project actions must not overwrite code actions. Mitigation: collisions are
  skipped (§4.2) and tested.

## 7. Questions for the human

1. **Where the actions live:** one file, `Assets/Data/input.actions.json` (B, proposed), or inside the project
   file (A)?
2. **Name translation:** should a project action's name be translatable now (`nameKey` pointing to a game string
   table, which projects cannot create from the editor yet), or is a plain name enough for INP-11?
3. **Changing an id:** allow "Change id…" with a warning (proposed), or make ids fixed after creation (delete and
   recreate to change one)?
4. **No-code contexts:** is "active at start" on a context enough for games without scripts, or do you also want
   an `InputContext` component that pushes its context while the object is enabled? That would be a separate,
   later task.
