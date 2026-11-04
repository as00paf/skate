# Skater animation controller (ANI-09)

`src/main/resources/configs/skater.controller.json` is the skater's ANI-02 state machine. `Skater` sets it as `Animator.controllerPath`. When the path is not a project file, it is loaded from the classpath.

## How gameplay drives it

`SkateBehavior` reports a state per frame, and `configs/skater.character.json` (`animations` and `onFoot.animations`) maps each state to a name. `PlayerMotionSystem` passes that name to `Animator.play(name)`. While a controller is active, `play` doesn't play a clip: it sets the bool parameter `name` to true and the previous one to false. Each state has an any-state transition that fires when its bool is true. Gameplay decides *which* state, and the controller decides *how* to blend to it.

| Gameplay state | Request / controller state | Motion |
|---|---|---|
| on foot IDLE / WALK / RUN / TAKEOFF / AIR | `idle` / `walking` / `running` / `jump` / `falling idle` | bundled clips |
| riding IDLE | `ride_idle` | procedural slot `ride_idle` |
| PUSH | `push` | procedural slot `push` |
| RIDE | `ride` | procedural slot `ride` |
| AIR | `ollie`, then `land` on touchdown | procedural slots `ollie`, `land` |
| GRIND | `grind` | procedural slot `grind` |
| MANUAL | `manual` | procedural slot `manual` |
| BAIL | `bail` | clip `falling idle` |

`land` is controller-only. It is entered from `ollie` when a ground request arrives, and it exits to that request after it has played. The any-state transitions into ground states list it in `exceptFrom` so they can't cut it short.

## Procedural slots and dropping in real clips

The `poses` entries are keyed `HumanoidPoseDef` lists baked on the character's own rig by `HumanoidPoseBuilder`. The builder measures the body frame and leg length, aims the spine, head and arms, and places the legs with two-bone IK. Distances are in leg lengths and angles in degrees, relative to the travel direction.

**A clip on the Animator whose name equals a slot name replaces that slot.** A clip's name is its file name without the extension. To use a real push:

1. Save the clip as `push.fbx` (any Mixamo-rigged FBX) under `assets/characters/animations/` and add it to `Assets.Animations`/`Assets.Bundled` and `Skater.DEFAULT_ANIMATIONS`.
2. Nothing else to change. The procedural `push` is no longer built. You can delete its `poses` entry.

Alternatively, keep the file name and point the state's `motion` at it.

## Wanted clips

These are Mixamo search terms (search "skateboard"). The exact titles vary, so pick the closest match. Use regular stance (left foot forward), and choose "In Place" when Mixamo offers it.

| Slot | Look for | Notes |
|---|---|---|
| `ride_idle` | Skateboard Idle | looping, standing on deck |
| `ride` | Skateboarding (rolling) / Skateboard Riding | looping, slight crouch |
| `push` | Skateboard Push | looping, in place |
| `ollie` | Skateboard Ollie / Skateboarding Jump | one-shot, ending in the air pose |
| `land` | Skateboard Landing (or "Hard Landing" trimmed) | one-shot, about 0.4 s |
| `grind` | Skateboard Grind (50-50) | looping |
| `manual` | Skateboard Manual | looping balance |
| `bail` | Skateboard Fall / Falling Back Death | one-shot |
| turns (later) | Skateboard Turn Left / Right | for a lean blend tree on `ride` |

Download them for the same character (`james`) with skin off, at 30 fps.
