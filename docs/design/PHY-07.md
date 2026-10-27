# PHY-07 – Per-frame allocations and degree/radian audit

## Hot-path allocation review

Hoisted or removed in steady state (no Kotlin-side allocation per fixed step / frame):

- `PhysicsSystem`: body state map + list (`BodyState` caches the `Transform`), stamp-based stale detection,
  gravity compared against a cached field, joint sync counts instead of rebuilding sets, ground check uses scratch vectors.
- `BulletPhysics3D`: contact dispatch reuses one collector listener, pooled pending events, swapped primitive
  `LongSet`s for collision/trigger pair keys, `raycastClosest` uses scratch vectors and result list.
- `RagdollSystem`: bone name -> index bindings cached on `RagdollComponent` (rebuilt when the skeleton or body count
  changes), hoisted matrices, direct writes into `pose.globalTransforms`, index loops.
- `RigidBody3D.applyImpulse`: reuses a transient origin vector.

Residual allocations (reviewed, accepted):

- `IRigidBodyHandle.linearVelocity/angularVelocity` getters and `RigidBody3D.getWorldPosition/getRotation/getVelocityInPoint` return copies (API contract).
- `raycast`, `raycastAll`, `shapeCast` allocate their result lists/hits.
- Bullet's native `rayTest`/`contactTest` allocate internally.
- Trigger enter/exit (rare) touch a boxed map entry; `debugDraw` allocates only when enabled.

## Degree/radian audit (QA-04)

- `Transform.rotation` is Euler XYZ in **degrees**.
- `BulletPhysics3D.syncBodyProperties`: degrees -> radians (`toRadiansF`) into `rotationXYZ`.
- `PhysicsSystem.onFrame`: `getEulerAnglesXYZ` -> degrees (`toDegreesF`). Consistent with the above.
- `JointComponent` limits/angles are **radians** (documented on the class).
- Bug fixed: `RigidBody3D.reset()` built `Quaternionf(euler.x, euler.y, euler.z, 1f)` from degrees; it now uses
  `rotationXYZ(toRadiansF())`. It also zeroed velocity on a copy (no-op); it now assigns fresh zero vectors.
- `RagdollSystem` and the rest of `physics3d` contain no other angle conversions.
