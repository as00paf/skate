# INP-10: Decouple input from skateboarding

Status: **implemented** (approved by skate-lead, including A1–A5). Steps 1–4, 6 and 7 by `skate-input`, step 5 by `skate-gameplay`.

Implementation notes (differences from the text below):
- `Engine` registers `CharacterActions` before any project loads (not `PlayerMotionSystem.init`). The sampler is injected into `PlayerMotionSystem` and is optional.
- Any change to the context stack *or* the bindings latches held actions, and the first query of an action is never a press. So keys held when Play starts don't mount or jump.
- `ScriptInput.pushContext` takes `blocking` as a string (`"none"`, `"consume"`, `"all"`) so it reads the same in Kotlin, Lua and TS. Lua/TS default to priority 0 and `"consume"`. The new `ScriptInput` members have inert defaults so existing implementations still compile.
- The Key Bindings "shadowing hint" lists actions of *other* contexts on the same physical input (`ActionBindingsModel.sharedWith`), because the default stack alone never shadows anything.

## Goal

`engine/input/**` (plus `InputSystem`, `InputStateComponent`, `InputEvents`) becomes a game-agnostic input layer:

- action maps;
- input contexts that games can define, kept on a push/pop stack with priorities;
- generic sampling primitives.

Everything about skateboarding (actions, default bindings, flick/gesture interpretation, mount-as-skate mode) lives in `game/**`. Scripts and the runtime UI use the same generic API. The skate game plays exactly as before, and an architecture guard keeps it that way.

## Current state (audit, master @ a0dff11e)

The INP-03 action core (`ActionMap`, `ActionBinding`, `BindingProfile`, `ActionInputReader`, dead zones) is already generic. The coupling is in these places:

| # | Where | Problem |
|---|---|---|
| C1 | `InputContext` enum `{GAMEPLAY, UI, EDITOR}` | Closed set: a game cannot add `vehicle`, `skate`, `menu.map`, ... |
| C2 | `ActionInputReader.activeContexts: MutableSet` | A flat set with no priority, ownership or blocking. A pause menu, a vehicle or a cutscene must each remember to add and remove the right entries. Nothing stops two owners from clobbering each other. |
| C3 | `DefaultActions` | Mixes generic UI navigation with a character preset (`moveX/Y`, `lookX/Y`, `jump`, `sprint`, `crouch`, `toggle_mount` "board or vehicle"). `InputSystem` registers that preset unconditionally, so every game gets it. |
| C4 | `InputSystem.update` | Writes character fields (`jump*`, `sprint`, `crouch`, `mount`, `cameraLook` with hardcoded look sensitivities 2.0 / 0.1) into every `InputStateComponent`. Jump edge state is shared across entities (INP-01 I7). |
| C5 | `InputStateComponent` | A fixed character schema. `pausePressed`/`resetPressed` are never written. |
| C6 | `InputEvents.kt` | `JumpPressed`, `JumpReleased`, `MovementInput`, `TrickInput`, `CameraLook` are game semantics. `TrickInput`/`CameraLook` are never published. Jump/Movement only have test subscribers. |
| C7 | `InputBuffer` | `getFlickVelocity`, `getJoystickFlickVelocity` and `getRightStickFlickVelocity` name a skate gesture. It samples only pad 0, left/right stick by hardcoded axis indices, and allocates a `Vector2f` per query. |

Consumers outside my domain:
- `PlayerMotionSystem` and `FollowCameraSystem` read `InputStateComponent`.
- `UiActions`/`Engine.uiSystems` depend on `DefaultActions` UI ids.
- `ReaderScriptInput` exposes `action(name)`.
- `ActionBindingsModel`/`ActionBindingsPanel` group by `InputContext` and label it with `context.name.lowercase()`.
- `EditorInputHandler` reads `DefaultActions.UI_CANCEL`.
- `SkateBehavior` reads skate intent from generic fields (`sprint` → push, `crouch` → manual, `jumpPressed` → ollie).

## Design

### 1. Open input contexts

```kotlin
@Serializable @JvmInline
value class InputContext(val id: String) {
    companion object {
        val GAMEPLAY = InputContext("gameplay")
        val UI = InputContext("ui")
        val EDITOR = InputContext("editor")
    }
}
```

- The engine ships only these three generic ids. Games declare their own (`InputContext("skate")`, `InputContext("vehicle")`).
- `ActionDefinition.context` keeps its name and type name, so `InputContext.GAMEPLAY` call sites compile unchanged. `.name` becomes `.id`; only the editor panel uses it.
- Conflict detection stays per context (INP-03 rule).
- Persistence is unaffected: `BindingProfile` stores overrides by action id, not by context.

### 2. Context stack with priority (replaces `activeContexts`)

Pure class `engine/input/InputContextStack.kt`, unit-tested without GLFW:

```kotlin
enum class ContextBlocking {
    /** Lower contexts keep working (HUD-style overlays). */
    NONE,
    /** Physical sources bound in this context are hidden from lower contexts; others pass through. Default. */
    CONSUME_BOUND,
    /** Every lower context is silenced (modal: pause menu, text entry, cutscene). */
    ALL,
}

class ContextHandle internal constructor(...)  // opaque; returned by push, required by pop

class InputContextStack {
    fun push(context: InputContext, priority: Int = 0, blocking: ContextBlocking = CONSUME_BOUND, owner: String = ""): ContextHandle
    fun pop(handle: ContextHandle): Boolean        // removes exactly that entry; double pop is a no-op returning false
    fun isActive(context: InputContext): Boolean
    fun entries(): List<Entry>                     // top first, for the input-testing window
    val revision: Int
}
```

Rules (all pure, all tested):
- Order is by `priority` descending, ties broken by push order (later wins). Pushing the same context twice is allowed (two owners). The context stays active until both handles pop.
- Resolving `actionValue`/`isActionDown(id)`:
  - The action's context must be in the stack and not under an `ALL` entry.
  - Each of its bindings is dropped when an entry above it has `CONSUME_BOUND` and binds the same `sourceId()`.
  - This lets a `vehicle` context take over the gamepad A button from `gameplay.jump` without anyone unbinding anything.
- The consumed-source set is cached per stack/map `revision`, so the per-frame cost stays a hash lookup per binding.
- Edges: when a context is popped or blocked, its held actions report released. The next frame they do not report `wasActionPressed` for a source that was already held when the context appeared. This avoids a "jump fires when the pause menu closes" glitch: a press that was held carries over, but a new press is required.
- Base stack built by `InputSystem`: `gameplay` (priority 0) and `ui` (`InputContextStack.UI_PRIORITY` = 1000, `NONE`). This matches today's `{GAMEPLAY, UI}` default, so nothing changes until something pushes. `ui` sits above game contexts, because a context consumes sources only from the contexts below it. A ride context (priority 10) therefore never takes gamepad A or the left stick away from the pause menu. A modal that must silence everything pushes above 1000. (Changed in GPL-21: with `ui` at 0, riding hid `ui.confirm` on A.)
- The editor pushes `editor` itself (`EditorInputHandler`). The engine never pushes it.

Public API:
- `InputSystem.contexts: InputContextStack`.
- `InputSystem.activeContexts` stays for one round as a deprecated read-only view, then goes away.
- `InputReader` gains `fun isContextActive(context: InputContext): Boolean`.

### 3. Engine action sets: UI only

- `DefaultActions` is split:
  - **`engine/input/UiNavigationActions`**: the `ui.*` ids and defaults now in `DefaultActions` plus `UiActions.bindDefaults`. The engine registers these always, because the runtime UI and the pause menu depend on them.
  - **`CharacterActions`** (moves to `engine/gameplay/`, GPL): `moveX/Y`, `lookX/Y`, `jump`, `sprint`, `crouch`, `interact` (renamed from `toggle_mount`; the id stays `toggle_mount` for saved profiles) with the current defaults. This is an *opt-in* preset for any character controller, not an input concept. `PlayerMotionSystem.init` registers it if absent, so editor Play of a project without game code still works.
- `InputSystem` no longer calls `DefaultActions.register`.
- Action ids are unchanged (`jump`, `moveX`, `ui.confirm`, ...), so existing `InputMappings.bindings` overrides load as before.

### 4. `InputSystem` becomes device + action plumbing only

Per frame, `InputSystem` does this and nothing else:
- Refresh gamepads and publish hot-plug events.
- Advance `ActionInputReader` edges.
- Update `InputHistory` (§6).
- Tick rumble.

It no longer touches `InputStateComponent` or knows jump/move/look.

The character sampling moves to `engine/gameplay/CharacterInputSampler` (GPL), called by `PlayerMotionSystem`:
- It reads `CharacterActions` through the `InputReader`.
- It uses `reader.wasActionPressed(JUMP)` for the jump edge, which fixes INP-01 I7 (shared edge state).
- It takes the look sensitivities from `CharacterConfigs` instead of `InputSystem` constants.
- The mouse-look path (`cursor captured → deltas`) moves with it, unchanged.

`InputStateComponent` keeps its serialized shape for scene compatibility. It is filled by the sampler, and its KDoc says it is the *character* input snapshot. It drops the never-written `pausePressed`/`resetPressed` (the scene serializer has `ignoreUnknownKeys = true`, so old scenes still load). A later rename to `CharacterInputState` is GPL/ECS's call and is not part of INP-10.

### 5. Events

`InputEvents.kt` keeps only device events (`GamepadConnected`, `GamepadDisconnected`). Opt-in generic action events replace the game events:

```kotlin
data class ActionEvent(val action: String, val phase: ActionPhase, val value: Float) : InputEvent("input.action")
enum class ActionPhase { STARTED, CANCELED }   // down edge / up edge
```

- They are published only for actions whose `ActionDefinition.publishEvents = true` (default false), so the bus is not flooded every frame.
- `JumpPressed`, `JumpReleased`, `MovementInput`, `TrickInput` and `CameraLook` are deleted. They have no production subscribers, and `InputSystemTest` moves to `ActionEvent`.

### 6. Generic sampling instead of "flick"

`InputBuffer` is replaced by `InputHistory` (pure, preallocated ring buffer, no per-query allocation):

```kotlin
class InputHistory(capacity: Int = 60) {
    fun track(name: String, x: () -> Float, y: () -> Float)   // any 2D source: an action pair, a raw axis pair, the pointer
    fun sample(time: Float)                                   // called by InputSystem once per frame
    fun velocity(name: String, window: Float, out: Vector2f): Vector2f
    fun displacement(name: String, window: Float, out: Vector2f): Vector2f
    fun forEachSample(name: String, window: Float, fn: (t: Float, x: Float, y: Float) -> Unit)
}
```

- The engine tracks nothing by default; `track` costs nothing until called. The pointer is tracked on request.
- No engine identifier mentions flicks, tricks or sticks-as-feet. The math (window lookup, finite difference) is unit-tested in `InputHistoryTest`.

### 7. Game layer (`game/input/**`, owned by `skate-gameplay`)

| File | Content |
|---|---|
| `SkateActions.kt` | `InputContext("skate")` and skate actions with today's effective defaults, so play is unchanged: `skate.push` (L-Shift / RT, today via `sprint`), `skate.manual` (L-Ctrl / LB, today via `crouch`), `skate.ollie` (Space / A, today via `jump`). Registered at game start (`GameMain`) and by the skate prefab setup for editor Play. |
| `SkateInputContext.kt` | Pushes `skate` (priority 10, `CONSUME_BOUND`) when `SkateBehavior.mounted` becomes true and pops it on dismount. On foot, the same keys mean sprint/crouch/jump again, without branching inside the behavior. |
| `FlickRecognizer.kt` | Pure: takes `InputHistory` samples of a 2D source and returns a gesture (direction sector, speed, path shape such as quarter-circle or half-circle). Unit-tested on synthetic sample sequences. Used by GPL-21's flick-it scheme. |
| `SkateIntent.kt` | Reads the skate actions into the `SkateContext` fields (`pushHeld`, `manualHeld`, `jumpPressed`) instead of reinterpreting `CharacterInput.sprint`/`crouch`. |

GPL-21 (control schemes) then builds on this: each scheme is a set of `ActionDefinition` defaults plus recognizer settings. The engine needs no further change.

**As built (step 5).** Three small generic pieces in `engine/gameplay` carry the table above. None of them uses skate vocabulary.
- `ActionQuery` is passed as `CharacterInput.actions`, so a behavior reads its own actions and asks for contexts with `holdContexts`. `ActionQuery.NONE` is the default for tests and replays.
- `InputContextLeases` is owned by `CharacterInputSampler`. `PlayerMotionSystem` releases every lease on scene load and when play stops, so a context never outlives editor Stop or a restart.
- `ActionPresets` lets game code add action sets without the engine importing `game/**`. `SkateGameplay.register()` adds `SkateActions`, and the engine applies presets at start and on scene load. This replaces "registered in `GameMain` and the skate prefab".

The skate defaults copy the sprint, crouch and jump defaults exactly (Shift/LT, Ctrl/LB, Space/A; axis 4 is the left trigger, not RT). `SkateIntent` is read every frame, so a key held while stepping on is not a new press.

### 8. Scripts and runtime UI

- `ScriptInput` (SCR) gains:
  - `isActionDown(name)` and `wasActionPressed(name)`;
  - `pushContext(id, priority, blocking): Int` and `popContext(handle: Int)`. Handles are scoped to the script instance and are popped automatically when the script is destroyed or hot-reloaded, so a crashing script cannot leave a context stuck.
- The runtime UI (`UiInputSystem`, GUI) keeps reading `ui.*` actions.
- The pause menu pushes `ui` with `blocking = ALL` while it is open, instead of relying on `scene.isPaused` checks in `InputSystem`. `InputSystem` keeps the `isPaused` early-out until GUI has switched.

### 9. Editor (EDT)

- `ActionBindingsModel` groups by `context.id`. The label key `lbl.keybindings.context.<id>` falls back to the raw id for game-defined contexts.
- The binding UI shows a *shadowing* hint when a binding is consumed by a higher-priority context in the default stack. This is informational only; conflicts stay per context.
- The input-testing window lists `contexts.entries()`.

## Migration steps

Each step builds, passes `gradlew test`, and leaves the skate demo playable. Steps 1, 2, 3 and 6 are mine (INP). The others are requests to their owners.

1. **INP: contexts.**
   - Turn `InputContext` into a value class.
   - Add `InputContextStack` with the base stack.
   - Resolve the reader through the stack.
   - Keep `activeContexts` as a deprecated shim.
   - Tests: `InputContextStackTest` (ordering, ties, double push/pop, blocking modes, consumed sources, edge suppression on push/pop) and the existing `InputReaderTest`/`ActionMapTest` updated.
2. **INP: history.**
   - Add `InputHistory` and delete `InputBuffer`/`InputState`. `InputProvider.endFrame` stops sampling; `InputSystem` calls `history.sample`.
   - Tests: `InputHistoryTest`.
3. **INP: action split and events.**
   - Add `UiNavigationActions` in the engine.
   - Have `InputSystem` stop registering character actions and stop filling `InputStateComponent`.
   - Add `ActionEvent` and remove the game events.
   - Done in the same merge as step 4, because `PlayerMotionSystem` must start sampling in the same commit.
4. **GPL: character sampler.**
   - Add `CharacterActions` and `CharacterInputSampler` in `engine/gameplay`, called by `PlayerMotionSystem`.
   - Tests: the sampler on a fake `InputReader` (jump edge per entity, look sensitivity from config).
5. **GPL: skate layer** (done, see *As built* in §7). Add `game/input/*` from §7. `SkateBehavior` reads skate actions. Mount/dismount pushes and pops the `skate` context. Tests: `FlickRecognizerTest` and `SkateInputContextTest`.
6. **INP + QA: guard.**
   - Add the architecture guard (below).
   - Remove the `activeContexts` shim.
   - Remove `toggle_mount`'s "board" wording.
7. **SCR / GUI / EDT follow-ups** from §8–9. They are independent of each other and can land after step 6.

Rollback: steps 1–2 are additive behind the same public reader API. Steps 3–4 are one atomic merge.

## Architecture guard

New `src/test/kotlin/com/pafoid/skate/architecture/InputGameAgnosticGuardTest.kt`. It scans these files:
- `engine/input/**`
- `engine/ecs/systems/InputSystem.kt`
- `engine/ecs/components/InputStateComponent.kt`
- `engine/events/InputEvents.kt`

| Test | Rule |
|---|---|
| `InputLayering_DoesNotImportGame` | No `import com.pafoid.skate.game.` and no fully qualified `com.pafoid.skate.game.` reference. |
| `InputLayering_HasNoSkateVocabulary` | Ignores `package`/`import` lines (the root package is `com.pafoid.skate`) and comments. Splits identifiers and string literals on camelCase, `_`, `.` and `-`. Fails on any word in the deny list: `skate`, `skater`, `skateboard`, `board`, `flick`, `trick`, `ollie`, `kickflip`, `heelflip`, `grind`, `manual`, `stance`, `goofy`, `mongo`, `deck`, `truck`, `grab`, `revert`, `bail`. Word splitting keeps `keyboard` legal. |
| `InputLayering_ContextsAreOpen` | `InputContext` is not an `enum class` (prevents regressing C1). |

`PureLogicIsolationGuardTest` gains `InputContextStack.kt` and `InputHistory.kt` (no LWJGL/editor/ImGui imports).

## Decisions and assumptions to confirm

- **A1.** The character preset (`jump`, `sprint`, ...) stays in the engine, under `engine/gameplay`, because the generic `CharacterBehavior`/`LocomotionBehavior` use it. Only skate semantics move to `game/**`. The alternative, moving the preset into the game, would make every new game re-declare walking.
- **A2.** Action ids stay stable, so user binding profiles survive without a `BindingProfile` version bump.
- **A3.** Default blocking is `CONSUME_BOUND`. This matches common engine practice (Unreal Enhanced Input mapping priority, Unity action-map enable/disable) and gives vehicles and boards a clean override.
- **A4.** Multiple local players and per-device action maps are out of scope. The stack is global. A per-player `InputUser` can wrap a stack later without API breaks.
- **A5.** No new bindings or control schemes here. Those are GPL-21.

## Out of scope

- Composite 2D action types with radial dead zones per action (today: per-stick zones in `DeadZoneSettings`).
- Rumble API changes.
- Text input.
- The ImGui capture rules from INP-09.
