# Runtime pause semantics (GUI-09, reviewed in ECS-13)

The runtime pause is the pause menu's freeze while the scene stays in Play. It is not editor Stop.

## State
- `SystemManager.paused` is the source of truth. `Engine.setPaused` only accepts `true` while `runtimePlaying`.
- `Scene.isPaused` mirrors it **immediately** (the setter writes the loaded scene), so systems later in the same
  frame see it. It is `@Transient` and never saved.
- Cleared by: Stop (`EngineAction.SetRuntimePlaying(false)`), and any `SystemManager.loadScene` (scene switch,
  Restart through `ScenePlaySnapshot.restore`). The previous scene's `isPaused` is cleared too.

## What runs while paused
| Runs (`runsWhilePaused = true`) | Frozen |
|---|---|
| `InputSystem` (reads actions; does not feed `InputStateComponent`) | `PhysicsSystem` (no fixed steps, no time accumulated, no catch-up on resume) and `PhysicsFrameSystem` |
| `UiInputSystem`, UI layout, `PauseMenuSystem` | `AnimationSystem`, `RagdollSystem`, `ParticleSystem` |
| `AudioSystem` (holds playing voices, resumes only those) | `ScriptingSystem`, `GameObject.update` (component updates) |
| `CameraManager`, `TransformSystem` (derived state) | `CameraControlSystem` (free-fly and focus glide), `FollowCameraSystem`, `MountFollowSystem` |
| `PlatformSystem` (platform callbacks) | `DayNightCycleSystem`, `GameObjectManager` (dead-object pruning), gameplay systems, NET systems |
| All `RENDER`-phase systems | |

Simulation never reads wall-clock time, so frozen systems resume exactly where they stopped.

## Rules for new systems
- Default is frozen. Opt in with `override val runsWhilePaused get() = true` only for input, UI, audio bookkeeping,
  or state derived from other components (transforms, cameras).
- A pause-aware system that also drives gameplay must check `scene.isPaused` itself (as `InputSystem` does).
- Systems holding per-scene resources must release them in `init` when the scene changes (as `AudioSystem` does),
  because a scene switch also clears the pause.
