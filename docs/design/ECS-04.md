# ECS-04: Component gap analysis

Baseline: components registered in `Serializer` / `ComponentType` today are Transform, RenderComponent, SpriteRenderer, CameraComponent, the four light types, EnvironmentComponent, DayNightCycleComponent, GridLines, AudioComponent, AudioListener, ReverbZone, RigidBody3D (with `isTrigger`, `collisionLayer`, `collisionMask`), Box/Capsule/Cylinder/Custom colliders, JointComponent, SkeletonComponent, Animator, BoneOverride, RagdollComponent, ScriptComponent, NativeScriptComponent, PlayerController, InputStateComponent, NonPickable, ScenePhysicsComponent, and `PrefabInstance` (ECS-05).

Already covered (not gaps): *Trigger* (`RigidBody3D.isTrigger` + trigger events), *AudioListener*, *Prefab instance link* (ECS-05), *physics layers/mask* (bit flags on the body).

## Gaps

| # | Component | Why | Owner | Priority | Notes |
|---|---|---|---|---|---|
| 1 | `Tag` / `Layer` on GameObject | Find/filter objects by name-independent tag; shared layer for culling, picking, camera masks and queries. Physics layers exist but are per-body and unrelated to render/pick layers. | ECS | P1 | Core `GameObject.tag: String`, `GameObject.layer: Int` (0..31), `Scene.findByTag`, query filter; physics/render/editor consume. |
| 2 | `Lifetime` (auto-destroy after N seconds) | Spawned effects, projectiles, debris. Currently scripts must hand-roll timers. | ECS | P2 | Small system in LATE_UPDATE; deferred destroy through the scene. |
| 3 | `Billboard` | Camera-facing quads (markers, foliage, particles, UI in world). | REN | P2 | Needs camera data at render time; axis-locked variant. |
| 4 | `LODGroup` | Distance-based mesh/material swap and culling; needed for large scenes. | REN | P2 | Levels with screen-size thresholds and hysteresis; depends on frustum culling. |
| 5 | `ParticleEmitter` | Already REN-10; confirmed missing. | REN | P2 | No new task. |
| 6 | `TriggerEvents` helper (enter/exit/stay as component callbacks or script events) | `isTrigger` exists on bodies but gameplay needs per-object enter/exit routing. | PHY | P2 | Verify PHY collision-event tasks cover routing to scripts. |
| 7 | `Decal` / projector | Skate marks, grind sparks, graffiti on surfaces. | REN | P3 | Projected texture volume. |
| 8 | `Reflection/Light probe` | Plausible lighting for metal/glass; ties to REN-06/07. | REN | P3 | |
| 9 | `Spline` / path | Rails, camera dollies, AI paths; rails currently modelled with colliders only. | GPL | P2 | Evaluate by distance; editor gizmo (EDT). |
| 10 | `NavMesh agent` | AI/pedestrians. | GPL | P3 | Needs a navmesh build step first. |
| 11 | `CameraRig` (follow/orbit/shake as data) | Camera behaviours are in game code. | GPL | P2 | Possibly already GPL camera work. |
| 12 | `Volume` (post-process/audio/fog blend volumes) | Local overrides of environment settings. | REN | P3 | Depends on REN-08. |
| 13 | `Canvas` world-space attach (UI component) | World-space UI tied to GameObjects. | GUI | P3 | |
| 14 | `Cloth/Soft body`, `Water` | Out of scope for now. | - | - | Listed for completeness. |

## Recommended follow-up tasks (for the lead to add)

- **ECS-10 (P1)** `Tag` and `Layer` on `GameObject`: fields, serialization, `Scene.findByTag/findByLayer`, layer-mask param on `System.query`, tests. Then EDT inspector field, REN camera culling mask, PHY mapping of layer to collision groups.
- **ECS-11 (P2)** `Lifetime` component and system with deferred destroy and tests.
- **REN-xx (P2)** `Billboard` component and pass support.
- **REN-xx (P2)** `LODGroup` component, selection math unit-tested.
- **PHY-xx (P2)** Route trigger enter/exit/stay to components/scripts if not already covered.
- **GPL-xx (P2)** `Spline` component (rails/camera paths); confirm `CameraRig` overlap with existing GPL camera tasks.
- **REN-xx (P3)** Decal, reflection probe, post-process volume. **GPL (P3)** NavMesh agent.
