# PHY-05 – Second physics backend spike

Goal: check that `IPhysics3D` / `IRigidBodyHandle` really are backend-agnostic, and evaluate Jolt as a candidate.

## Prototype

`physics3d/simple/SimplePhysics3D.kt`, selectable with `Physics3DFactory.create(renderer, PhysicsBackend.SIMPLE)`.
It has no dependencies and imports no Bullet type. Scope:

- Supported: gravity, damping, forces/impulses, kinematic and static bodies, bounding-sphere contacts with restitution,
  layer/mask filtering, collision and trigger events, `raycast`/`raycastAll`, `shapeCast` (bounding-sphere sweep).
- Not supported: joints (`addConstraint` throws `UnsupportedOperationException`), friction, rotational dynamics from
  contacts, CCD, debug draw.
- Tests: `SimplePhysics3DTest` (falling, resting on static, masks, ray queries, joint rejection, factory selection).

## Findings on the abstraction

- The interface compiled against a non-Bullet backend with **no changes**. `PhysicsSystem`, `RigidBody3D` and the
  ragdoll code only touch `IPhysics3D` / `IRigidBodyHandle`, so swapping backends needs no ECS edits.
- Leaks / sharp edges found:
  - Constraints are already backend-neutral descriptors (`HingeConstraint3D` etc.); only `rawJoint: Any?` is
    backend-owned. A second backend just has to translate them in `addConstraint` (follow-up PHY-08 fixed the Bullet
    side: ragdoll registration, constraint frame transpose, joint tests).
  - `IPhysics3D.add(go)` makes each backend read `RigidBody3D`/`Collider3D`/`Transform` itself; the mapping code
    (mass, layers, trigger, shape selection) is duplicated per backend. A shared helper that builds a backend-neutral
    body description would remove that.
  - Collider `margin` is Bullet-specific in `ColliderShape.Box/Cylinder`.

## Jolt evaluation (not integrated)

- Candidate: `jolt-jni` (Stephen Gold; JNI over Jolt Physics, Maven Central, Windows/Linux/macOS natives).
- Pros: multithreaded, deterministic option, good CCD (linear cast), better large-world/ragdoll performance than
  Bullet, native character controller, active development; API shape (bodies, shapes, constraints, layers, ray/shape
  casts, contact listener) maps 1:1 onto `IPhysics3D`.
- Cons: JNI natives per platform (the project is Windows-only today, so it matches), body layers are fixed
  object/broadphase layer tables (our 32-bit layer/mask would need a `ObjectLayerPairFilter` callback), a contact
  listener runs on worker threads and must be queued for the main thread, and it is a second large native dependency.
- Not integrated in this spike: pulling natives into the build needs a decision from the lead. Estimated effort for a
  real `JoltPhysics3D`: shapes + bodies ~2 days, filters/events ~1 day, joints ~2 days, plus parity tests.

## Recommendation

Keep Bullet as default. Before any real second backend: add a backend-neutral constraint API (above) and a shared
body-description helper. Then implement Jolt behind `PhysicsBackend.JOLT`.

## Breaking changes

- `PhysicsBackend` gains `SIMPLE`: exhaustive `when` expressions over it must handle the new value.
