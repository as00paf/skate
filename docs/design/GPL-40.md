# GPL-40: ECS-standard player and skater control (design note)

Status: **approved** by the human on 2026-11-25 (decisions in §11).
Origin: human, 2026-11-25 (stabilization phase, GameEngine.md step 3). Folds in **GPL-34** (PlayerController
inspector) and **ANI-34** (foot IK editable, walk fix). Paths are relative to `src/main/kotlin/com/pafoid/skate/`;
new files are marked **(new)**.

## 1. Problem (what the code does today)

| Piece | Where | Smell |
|---|---|---|
| `PlayerController` | `engine/ecs/components/PlayerController.kt` | Serialized fields are only `behaviorId`, `configPath`, `waterResponse`. Everything else is `@Transient` runtime state: `ground`, `groundTag`, `stepAhead`, `wasGrounded`, `footIk`, `footTargets`, `state`, and a lazily created `behavior` object. Config, ground state, foot IK and a behavior factory live in one component. |
| `CharacterBehavior` / `SkateBehavior` / `LocomotionBehavior` | `engine/gameplay/`, `game/player/SkateBehavior.kt` (500 lines) | A stateful object with hidden state (`SkateContext`, `StateMachine`, `SkateTrickInput`, `landedTricks`, `lastLanded`, `mounted`). It is reached through `controller.behavior` by `PlayerMotionSystem`, `PhysicsSystem`, `FollowCameraSystem`, `MountFollowSystem`, `SpotCameraSystem`, `SkateHudSystem`, `CharacterAnimationDriver`, and by `as? Mountable` / `as? SkateBehavior` casts. None of its state is serialized, shown in the inspector, snapshotted by Play, or predictable by `SkaterPredictionModel` without re-running the behavior. |
| Tuning | `*.character.json` through `CharacterConfigs`, path in `PlayerController.configPath` | Not in the component, so GPL-34 needs a path picker plus a second editor for the file. |
| `PlayerMotionSystem` | `engine/ecs/systems/PlayerMotionSystem.kt` (265 lines) | Does input sampling, intent building, behavior update, velocity/yaw apply, ground snapping, mount placement, respawn, foot IK driving and animation driving in one `update`. |
| `PhysicsSystem.probeGround` | `engine/ecs/systems/PhysicsSystem.kt:457` | A physics system writes into `PlayerController` (ground, tag, step, foot targets). |
| `InputStateComponent` | `engine/ecs/components/InputStateComponent.kt` | Already a component, but it is `@Serializable` only "for scene compatibility", is written by `CharacterInputSampler` called from `PlayerMotionSystem.update` (not by an input system), and has only on-foot character fields. The skate-specific intent (`SkateIntent`, `SkateTrickInput`) is a separate path that reads `ActionQuery` inside the behavior. |
| Foot IK | `engine/gameplay/ik/FootIk.kt`, `FootIkConfig` is `@Serializable` but held in a `@Transient` var | Not editable, not saved, and off by default (`enabled = false`) since round 41a because the walk looks distorted. |

Why it matters: the human wants the standard ECS shape (data components, systems that read and write them,
everything serialized and visible in the inspector), and "every engine feature must be represented and
configurable in the editor" (GameEngine.md). Today a designer cannot see why the skater is in `BAIL`.

## 2. Target shape

Rules for every new piece:
- A **component is plain serializable data** (`@Serializable`, no logic beyond trivial derived getters, no
  references to behavior objects). Runtime-only fields are `@Transient` but still shown read-only in the
  inspector (debug) so state is visible while playing.
- A **system** owns the logic; it reads and writes components through cached `query(...)`. Pure math stays in
  `engine/gameplay/**` classes that take and return plain values (unit-testable without a window or GL).
- **No `controller.behavior`.** State machines run inside systems and keep their state in components
  (`state` enum or id string + `stateTime`).
- Engine/game split (`EngineGameLayeringGuardTest`): generic locomotion, ground, mount, foot IK and input state
  in `engine/`; skateboarding (tricks, grinds, manuals, combo, stance) in `game/`.

### 2.1 Components

All in `engine/ecs/components/` unless marked **game**. Each needs a `Serializer.kt` `subclass(...)`, a
`ComponentType` entry, a `Component.type` branch, an inspector layout and `component.<Type>.*` strings (+ `.tooltip`,
EN+FR, in `editor/components`), as `ComponentRegistrationGuardTest` and the tooltip tests require.

| Component | Serialized (tuning) | Runtime (`@Transient`, shown read-only) | Replaces |
|---|---|---|---|
| `PlayerController` (kept, slimmed) | `enabled`, `waterResponse`, `respawnDelay` | none | the marker role; all runtime fields move out |
| `InputStateComponent` (reworked, see §3) | `source` (`LOCAL` / `NONE` / `NETWORK`, for NET-01), `actionBindings` | move, look, held/pressed edges, `actions` map | `InputStateComponent` + `CharacterInputSampler.sample` in the motion system |
| `LocomotionComponent` | walk/run/sprint speeds, accel, turn rate, jump impulse, coyote time, air control, crouch, look tuning (`LookTuning` folds in) | `state`, `stateTime`, `velocityTarget` | `LocomotionBehavior` + `LocomotionConfig` + `configPath` |
| `GroundStateComponent` | `GroundConfig` (probe heights, snap distance, slope limit, step up, `footOffset`, `sampleRadius`) | `ground` (`GroundInfo`), `groundTag`, `stepAhead`, `wasGrounded`, `isGrounded`, `airTime` | `PlayerController.ground*`, `behavior.ground`, `PhysicsSystem.probeGround` writes |
| `MountRiderComponent` | `mountTargetName` (object ref, `GameObjectRef`), `mountOffset`, `rideHeight`, `cameraPreset` | `mounted`, `mountDistance` | `Mountable`, `MountMath` data, `findMountObject` by name |
| `FootIkComponent` | `enabled`, `weight`, `legs` (bones, lateral), `ankleHeight`, `probeUp/Down`, `maxReach`, `rampSeconds`, `activeBelowSpeed`, `hipDrop` | `targets: List<FootTarget>`, `appliedWeight`, resolved bones | `PlayerController.footIk/footTargets`, `FootIkBinder` cache in `PlayerMotionSystem` |
| `CharacterAnimationComponent` | state-to-clip map, `blendTimes`, controller parameter names written | `state`, last request | `behavior.animationBlendTimes`, `animationParameters`, `CharacterAnimationDriver` state |
| **game** `SkateStateComponent` | `SkateControllerConfig` sections (push, brake, turn, ollie, grind, manual, tricks, input tuning, `preferredStance`) as nested `@Serializable` data | `state` (`SkateState`), `speed`, `airborneTime`, `fallSpeed`, `landingImpact`, `manualAngle`, `grindType`, `manualType`, `fakie`, `spinTurn`, `trick`, `trickTime`, `nearGrindable` | `SkateBehavior`, `SkateContext`, `StateMachine` instance state |
| **game** `SkateComboComponent` | `comboWindow`, scoring tuning | `chain`, `landedTricks`, `lastLanded`, `timeLeft` | fields of `SkateBehavior` and `Combo.kt` state |
| **game** `SkateTrickInputComponent` | flick thresholds, scheme id | flick recognizer state, `frame` | `SkateTrickInput` instance |
| **game** `SpotCameraComponent` (or fold into `FollowCameraComponent` settings) | `enabled`, spot radius, blend | active spot | state held in `SpotCameraSystem` |

Standalone `*.character.json` files stay as **optional presets** (a button "Load preset…" / "Save as preset…" copies
values into/out of the components); they are no longer referenced at runtime. This keeps the existing
`assets/Data/*.character.json` content useful and keeps `SkaterPredictionModel` loading a config for NET (see §8).

### 2.2 Systems and order

Phases follow `SystemPhase`; ordering through `runsAfter` / `runsBefore` (`SystemOrdering.resolve`).

| # | System | Phase | Reads | Writes |
|---|---|---|---|---|
| 1 | `InputStateSystem` **(new, engine)** | PRE_UPDATE | `InputStateComponent`, `InputReader`/`ActionMap` | `InputStateComponent` (all fields). Leases input contexts for `LOCAL` sources (replaces `inputSampler.leases`). |
| 2 | `SkateIntentSystem` **(new, game)** | PRE_UPDATE after 1 | `InputStateComponent.actions`, `SkateTrickInputComponent`, `SkateStateComponent.config.input` | `SkateTrickInputComponent.frame`, `SkateStateComponent.intent` |
| 3 | `GroundProbeSystem` **(new, engine)** | FIXED_UPDATE before physics step | `GroundStateComponent`, body, collision mask | `GroundStateComponent` (runtime fields). Moved out of `PhysicsSystem`; uses its `sphereCast`/`raycast` query surface, so `PhysicsSystem` no longer imports `PlayerController`. |
| 4 | `LocomotionSystem` **(new, engine)** | UPDATE | input, ground, `LocomotionComponent`, camera basis | `LocomotionComponent` runtime, a `MotionIntent` on the body (below) |
| 5 | `SkateStateSystem` **(new, game)** | UPDATE, replaces 4 for objects that have `SkateStateComponent` | intent, ground, mount, `SkateStateComponent` | `SkateStateComponent` runtime, `MotionIntent`, mount request |
| 6 | `CharacterMotionSystem` **(new, engine)** | UPDATE after 4/5 | `MotionIntent`, `GroundStateComponent` | body velocity, yaw, impulse (the `apply`/`applyGround` code of `PlayerMotionSystem`) |
| 7 | `MountSystem` **(new, engine)** | UPDATE after 6 | `MountRiderComponent`, body, mount body | mount placement and physics state (`snapRiderOnto`, `syncMount`); `MountFollowSystem` keeps its visual role |
| 8 | `RespawnSystem` **(new, engine)** | UPDATE | `PlayerController`, `WaterEntered`, start pose | body, state reset event `CharacterRespawned` |
| 9 | `FootIkSystem` **(new, engine)** | LATE_UPDATE before animation solve | `FootIkComponent`, `GroundStateComponent`, `SkeletonComponent`, body speed | `FootIkComponent.targets` (raycasts), `Animator.ik` |
| 10 | `CharacterAnimationSystem` **(new, engine)** | UPDATE after 4/5 | state components, `CharacterAnimationComponent` | `Animator` requests and parameters |
| 11 | `FollowCameraSystem`, `SpotCameraSystem`, `SkateHudSystem`, `InteractionSystem`, `BuoyancySystem` (existing) | unchanged phases | read the new components instead of `controller.behavior` | unchanged |

`MotionIntent` is a small `@Transient` data component (`desiredVelocity`, `targetYaw`, `yawRate`, `snapYaw`,
`impulse`) that replaces `CharacterMotion`. Behaviors become *the system that writes it*; the body applier is a
separate system. This is the seam that lets a non-skate game add a driving or swimming system without touching
the skater.

State machines: `StateMachine<S, C>` (`engine/gameplay/StateMachine.kt`) stays as a pure helper, but is created
per update from the component's `state` and `stateTime`, so no machine instance outlives a frame. The transition
table is a static value (like `SkateBehavior.buildMachine()` today, parameterized by the component's config).

## 3. InputStateComponent

### 3.1 Contract

| | Today | Proposed |
|---|---|---|
| Writer | `CharacterInputSampler.sample`, called by `PlayerMotionSystem.update` | **`InputStateSystem` only** (PRE_UPDATE). A network driver (NET-01) or a script may write it when `source != LOCAL`. No other system writes it. |
| Readers | `PlayerMotionSystem.buildInput`, `FollowCameraSystem` (`cameraLook`) | `LocomotionSystem`, `SkateIntentSystem`, `FollowCameraSystem`, `FirstPersonCameraSystem`, `InteractionSystem`, `SkateHudSystem` (hints only). |
| Generic actions | fixed fields (`jump`, `sprint`, ...) plus `ActionQuery` handed to the behavior | fixed fields for the built-in `CharacterActions` **plus** a generic `actions: Map<String, ActionValue>` (down, pressed, released, value) filled for every action id the entity declares in `actionBindings`. Game systems read `state.action("skate.ollie")`, never the `InputReader`. |
| Persistence | serialized "for compatibility" | tuning fields serialized (`source`, `actionBindings`, `deadZone` override); per-frame values are `@Transient`. |
| Contexts | leases held by behavior through `ActionQuery` wishes | `contexts: List<String>` serialized on the component (the contexts this entity needs active), leased by `InputStateSystem` while the object is enabled. This is exactly the "InputContext component" INP-11 Q4 deferred, and SCR/INP follow-ups (`pushContext`) keep working. |

Fields: `moveDirection`, `cameraLook`, `jumpPressed/Held`, `sprintPressed`, `crouchPressed`, `mountPressed` stay
(consumers and tests keep working); new: `actions`, `contexts`, `source`.

### 3.2 INP-11 project input actions

INP-11 (in progress, `docs/design/INP-11.md`) defines project actions in `Assets/Data/input.actions.json`, loaded
into the `ActionMap`. The component feeds from the same map: `actionBindings` lists action ids; `InputStateSystem`
resolves each id through `InputReader.isActionDown/wasActionPressed/actionValue`. So a project-defined action
("fire") reaches any system through `InputStateComponent.actions` with no Kotlin. Action fields on components use
the INP-12 `@InputActionRef` picker once it lands. INP-11 itself is unchanged; GPL-40 depends only on the
`ActionMap` and `InputReader` API that exists today. Ordering risk: INP-11 and GPL-40 touch
`CharacterInputSampler`/`ActionPresets`; GPL-40 slice 1 must land after INP-11's loader merges, or rebase on it
(see Q4).

`CharacterInputSampler` is deleted; its leases class `InputContextLeases` moves into `InputStateSystem`.
`InputGameAgnosticGuardTest` is kept: the engine input system still does not name skate actions.

## 4. Foot IK as an inspector-editable component (ANI-34)

- `FootIkComponent` replaces `PlayerController.footIk`. `FootIkConfig`/`LegIk` become the component's fields
  (`legs` is a list edited with add/remove, each bone a `HumanoidBone` dropdown or a raw bone name picked from the
  `SkeletonComponent`).
- Inspector sections: **Enable** (toggle, global `weight` 0..1, `rampSeconds`), **When to apply** (`activeBelowSpeed`
  m/s, "only grounded", "not while mounted"), **Legs** (bones, `lateral`, `probeUp/Down`, `maxReach`,
  `ankleHeight`), **Pelvis** (`hipDrop` follow of the lowest foot, `hipSmoothing`), **Debug** (draws probe rays,
  `FootTarget` positions, resolved bones; read-only `appliedWeight`, per-leg `valid`). Strings and tooltips EN+FR
  in a new `component.FootIkComponent.*` set; the debug overlay uses the editor gizmo/debug draw path.
- **Walk distortion (to confirm by reproduction in slice 6, hypotheses from reading `FootIk.kt` and
  `PhysicsSystem.probeGround`):**
  1. Targets are computed in FIXED_UPDATE from the body position but applied after the animation pose of a
     different frame, so while moving the target lags the foot (stale target against a moving pose). Fix: compute
     targets in LATE_UPDATE from the current body pose, after the animator evaluates.
  2. The IK weight stays 1 while walking, so the planted foot fights the walk cycle's foot lift. Fix: weight
     scaled by `1 - smoothstep(activeBelowSpeed)`; full IK when standing, fading out while walking and running.
  3. No pelvis offset, so the two-bone solve over-stretches the leg on uneven ground. Fix: `hipDrop`.
  Default: `enabled = true` for the Skater once the walk is verified, with `activeBelowSpeed` ~ 0.5 m/s (human to
  confirm, Q5).
- The solve (`TwoBoneIkConstraint`, `FootPlacement`, `FootIkBinder`) is reused; only ownership and the call site
  change.

## 5. GPL-34 folded in

GPL-34 asked for a behavior dropdown, a `configPath` asset ref and a water response section on
`PlayerController`. With the split, there is no behavior id and no config path to pick: each component is edited
directly (every tuning field is a normal inspector field) and `PlayerController` keeps only `waterResponse` and
`respawnDelay`. GPL-34 is closed by slice 2 (`PlayerController` + `LocomotionComponent` inspector). The
"behavior dropdown" becomes the **Add Component** menu (Locomotion, Skate State, Mount Rider, ...), which is how
a game picks its character type. `CharacterBehaviors` registry is removed.

## 6. Migration of saved scenes and prefabs

GameEngine.md rule 8: **no backward compatibility and no migrations/upgrade-on-load while the engine is a work in
progress; the human recreates content.** So the proposal does not add migration code:

- Bump `FileKind.SCENE` and `FileKind.PREFAB` `currentSchema` (`engine/assets/serialization/SchemaMigrations.kt`).
  Older files then fail with `OutdatedSchemaException` and the existing "recreate it" dialog (no silent
  half-load of a `PlayerController` with `behaviorId`/`configPath` that no longer exist).
- Regenerate the **default content** in the same slice that changes each component (rule 8: keep default projects
  current): `PrefabsGenerator.spawnSkater` and the `Skater` prefab class, the default scenes, the CLI/MCP
  templates (`templates/SkateGameTemplate.kt`, `EmptyTemplate.kt`), and any `.prefab`/`.scene` bundled in resources.
- `CharacterConfigs`/`*.character.json` presets keep loading (they are data presets, §2.1), so the skater's tuned
  values are not lost: the generator fills the components from `Assets.Configs.SKATER_CHARACTER`.
- Fallback if the human wants user content preserved (Q1): a one-shot offline converter (a CLI command, not
  load-time code) that rewrites old `PlayerController` JSON into the new components. Not proposed by default.

## 7. MainScene and Feature Showcase impact

- **MainScene** (`PrefabsGenerator` default scene): the Skater entity changes from
  `Transform, RenderComponent, Animator, SkeletonComponent, RigidBody3D, BoxCollider3D, InputStateComponent,
  PlayerController, InventoryComponent, EquipmentComponent` to the same plus `LocomotionComponent`,
  `GroundStateComponent`, `MountRiderComponent`, `SkateStateComponent`, `SkateComboComponent`,
  `SkateTrickInputComponent`, `FootIkComponent`, `CharacterAnimationComponent`. The Skateboard keeps its own
  components; the mount link is the `MountRiderComponent.mountTargetName` ref. Camera entities
  (`FollowCameraComponent(targetName = "Skater")`, `FirstPersonCameraComponent`) are unchanged.
- **Feature Showcase** (`game/samples/FeatureShowcase.kt`): uses `Skater.DEFAULT_ANIMATIONS` and
  `Skater.CONTROLLER_PATH` (animation upper-body mask demo, netcode demo with `SkaterPredictionModel`). Both keep
  working; the demos that spawn a controlled skater are updated to the new components, and each new user-visible
  piece gets a labeled demo (rule 8): **[Gameplay] Foot IK** (a stair/ramp patch with the IK toggle and debug on),
  **[Gameplay] Input state** (a cube driven by `InputStateComponent` with a project action, no script), and
  **[Gameplay] Locomotion without skate** (a second character with only the engine components, proving the
  engine/game split).
- **Play snapshot** gains correctness: runtime state is `@Transient`, so a Play restart resets it exactly as
  today; with components the editor can show it while playing.
- HUD, spot camera and pause menu keep their behavior, reading `SkateStateComponent` and `SkateComboComponent`.

## 8. Cross-domain impact (reported, not done by GPL-40 slices)

| Domain | Need |
|---|---|
| EDT | Inspector layouts for the new components (list editors for `legs`, `actionBindings`, `contexts`; object ref pickers; debug overlay for foot IK targets). Depends on EDT-107/108 in progress. |
| INP | INP-11 loader merged first (Q4). `InputStateSystem` replaces the sampler (INP owns `CharacterInputSampler`, `InputContextLeases`, `ActionQuery`; GPL-40 slice 1 needs INP's review). |
| ANI | `Animator` parameter wiring: `CharacterAnimationSystem` writes the same parameter names as `writeAnimationParameters` today. ANI-34 is folded into slice 6. |
| ECS | `System` ordering for the nine new systems; component add/remove at runtime in the inspector. |
| NET | `SkaterPredictionModel` currently builds a `SkateBehavior`-like model from `SkateControllerConfig`; it must read `SkateStateComponent` config instead. NET-01 `source = NETWORK`. |
| SCR | Script API reads `InputStateComponent` fields and writes `actions`; no change expected. |
| QA | Guards: no system outside `InputStateSystem` writes `InputStateComponent`; no `engine/` import of `game/`; no `controller.behavior`. |

## 9. Slice plan

Each slice is one task, ends green (`test`), and leaves the game playable. IDs are proposals; GameEngine.md is
edited only by the lead.

| Slice | Content | Tests (no window / GL) | Owner |
|---|---|---|---|
| **GPL-40a** | `GroundStateComponent` + `GroundProbeSystem` (moved from `PhysicsSystem`); `PlayerController` stops holding ground fields; the camera and other systems read the new component. | Ground probe with a fake raycast writes the component; `PhysicsSystem` has no `PlayerController` import (guard). | GPL |
| **GPL-40b** | `InputStateComponent` rework + `InputStateSystem` (actions map, contexts, source, `actionBindings`); delete `CharacterInputSampler`. | Fake `InputReader`: fields, edges, project action id, context lease on enable/disable, `NETWORK` source untouched; guard "only `InputStateSystem` writes". | GPL, INP review |
| **GPL-40c** | `LocomotionComponent` + `LocomotionSystem` + `CharacterMotionSystem` + `MotionIntent`; remove `LocomotionBehavior`/`CharacterBehaviors` for on-foot; preset load/save of `*.character.json`. Inspector for `PlayerController` + `LocomotionComponent` closes **GPL-34**. | Pure `LocomotionMath` cases (speed, accel, turn, jump, coyote) moved from existing tests; component serialization round-trip; inspector registration guards. | GPL, EDT |
| **GPL-40d** | `MountRiderComponent` + `MountSystem` + `RespawnSystem` (water bail, spawn tag, start pose). | Existing `MountPoseTest`, `WaterRespawnsTest`, `SkateWaterBailTest` ported to the components. | GPL |
| **GPL-40e** | **game:** `SkateStateComponent`, `SkateComboComponent`, `SkateTrickInputComponent`, `SkateIntentSystem`, `SkateStateSystem` replacing `SkateBehavior`; HUD, spot camera, follow camera, `SkaterPredictionModel` read components. | Every state transition and motion value test of `SkateBehavior` ported 1:1 (same inputs, same outputs) before deleting it; combo and HUD tests (`SkateHudSystemTest`, `PauseMenuParityTest`) stay green. | GPL, NET review |
| **GPL-40f** | `CharacterAnimationComponent` + `CharacterAnimationSystem`; animator parameter parity. | `SkateAnimationParametersTest`, `AnimatorCharacterParametersTest` unchanged in behavior. | GPL, ANI review |
| **GPL-40g** (= **ANI-34**) | `FootIkComponent` + `FootIkSystem` (LATE_UPDATE targets, speed-scaled weight, hip drop), inspector + debug view, strings EN+FR, default on for the Skater once the walk is verified. | `FootPlacement` and weight-ramp math tests; `SkaterFootIkTest` ported; a "walk does not move targets while speed > threshold" case. | ANI + GPL, EDT |
| **GPL-40h** | Schema bump, regenerate default scenes/prefabs/templates, Feature Showcase demos, docs (`docs/animation/skater-controller.md`, `docs/design` index), delete `PlayerController` transient fields and `CharacterBehaviors`. | `StringTablesTest`, `EditorStringKeysTest`, `ComponentRegistrationGuardTest`, CLI `CreateTemplatesTest`/`SampleTemplatesTest`, a MainScene "loads and the skater moves on Play" headless test. | GPL, TLG |

Order: a → b → c → d → e → f → g → h. a and b are independent of each other and can run in parallel. Until e lands,
`SkateBehavior` keeps working through a temporary adapter (`SkateStateSystem` is the target; the adapter is
deleted in e, not kept). Total is large; the human can stop after c and keep the skater on the old path, since
a to c leave the skate behavior untouched.

## 10. Risks

- **Behavior parity** of the skater (500 lines of state logic) is the main risk. Mitigation: port the existing
  tests first, run old and new side by side in a test on recorded input sequences (golden: same states and
  velocities frame by frame) before deleting `SkateBehavior`.
- **Netcode prediction** (`SkaterPredictionModel`) shares the config; keep the pure step function shared by the
  system and the model.
- **Many small components** raises the cost of spawning a character: provide a "Character (Skater)" prefab and an
  "Add Character" menu entry so no one adds nine components by hand.
- **Inspector work** depends on EDT-107/108, which the stabilization order finishes first.

## 11. Decisions (human, 2026-11-25)

1. **Saved scenes and prefabs:** rule 8, no migration and no converter. MainScene, the Skater prefab and the Feature
   Showcase are updated by hand in the same PR as the rewrite.
2. **Input:** one `InputSampleSystem` reads the INP-11 project actions and writes `InputStateComponent`; all other
   systems only read it.
3. **Component split:** approved as proposed (§2.1); keep the skate components separate.
4. **Foot IK default:** off until the walk is verified; the distortion is fixed inside the rewrite (ANI-34), then the
   default is revisited.
5. **Not asked, recommended option applies:** components own the tuning and `*.character.json` become optional
   presets; INP-11's loader lands before GPL-40b; slices a to c, then human test, then d to h.
