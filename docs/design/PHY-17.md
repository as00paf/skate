# PHY-17 – Jolt physics backend (design note)

Status: **approved** (skate-lead and the human, 2026-11-06). Slices a to e are implemented (f waits for EDT-59); see
[Decisions](#decisions-after-review) and [Implementation notes](#implementation-notes-slices-a-to-e).

## Goal

Add a third `IPhysics3D` backend on top of [Jolt Physics](https://github.com/jrouwe/JoltPhysics). It is built only by
`Physics3DFactory` and selected per project (EDT-59). It must match the Bullet backend on everything the engine uses
today: shapes, bodies, layers, triggers and contact events, joints, ragdolls, the raycast vehicle, and ray and shape
queries.

Done when (from the backlog): the physics test suite passes on Jolt as well as on Bullet, and the Feature Showcase
runs on Jolt.

Out of scope here (follow-ups filed as PHY-19): Jolt's own `Ragdoll`,
`VehicleConstraint` and `CharacterVirtual`, the heightfield shape (PHY-15), buoyancy (PHY-16), and making Jolt the
default backend.

## Decision summary

| Topic | Decision |
|---|---|
| Bindings | `jolt-jni` (Stephen Gold, JNI, Maven Central). It is the same author and release model as our libbulletjme. |
| Native loading | Reuse snaploader. Generalise `physics3d/native/NativeLibraryLoader` to a per-library spec. Jolt loads lazily, only when the Jolt backend is created. |
| Native flavour | Single-precision Release build (`ReleaseSp`-style classifier) for the editor and for exports. |
| Exports | Phase A: every game jar carries both natives (simple, about +5 MB). Phase B (AST-15): the export drops the unused backend's natives. |
| Code layout | New package `physics3d/jolt/`. It is the only package allowed to import `com.github.stephengold.joltjni`. |
| Shared mapping | Extract the RigidBody3D/Collider3D → body description mapping out of `BulletPhysics3D.add` first (PHY-05 recommendation), so the two backends cannot drift. |
| Selection | `PhysicsBackend.JOLT`. The project setting is saved in `Project` and the pack manifest. The game runtime sets `Physics3DFactory.defaultBackend` before it creates scenes. If the Jolt natives fail to load, it falls back to Bullet with a warning. |
| Fixed step | Unchanged. `PhysicsSystem` still owns the accumulator and interpolation; `JoltPhysics3D.step(dt)` runs exactly one `PhysicsSystem.update(dt, 1, …)`. |
| Default backend | Stays Bullet until the parity suite and the showcase pass on Jolt. |

## 1. Java bindings

### Options considered

| Option | Verdict |
|---|---|
| **jolt-jni** (`com.github.stephengold:jolt-jni-Windows64`, plus other OS artifacts) | **Chosen.** Actively maintained and tracks recent Jolt releases. Covers bodies, every shape we need, constraints, the narrow-phase queries, the contact listener (through Java upcalls), the Ragdoll, Vehicle and CharacterVirtual APIs, and the debug renderer hooks. Works on JDK 11+. Same packaging and snaploader loading pattern as libbulletjme, which we already ship. |
| jolt-java (Panama/FFM) | Rejected. It needs a newer JDK than our JVM 17 target, and its maintenance has stalled. |
| Our own JNI/LWJGL-style binding | Rejected. A large C++ build to maintain on every platform. |
| Wait for an LWJGL Jolt module | None exists. |

Licences: Jolt and jolt-jni are both MIT (checked on the jolt-jni 6.2.0 POM). Both go into the third-party notices
that the export copies, next to libbulletjme's.

### Version pinning

Pinned: **jolt-jni 6.2.0** (released 2026-10-14; 7.0.0 from 2026-10-26 was too new under the two-week rule), as
`com.github.stephengold:jolt-jni-Windows64:6.2.0` for the classes plus its `ReleaseSp` classifier for
`windows/x86-64/com/github/stephengold/joltjni.dll`. jolt-jni is still pre-1.0, so its API can change between minor versions. Upgrades are explicit tasks that
re-run the parity suite.

### Process and world lifecycle

jolt-jni needs one-time global setup and explicit native memory ownership:

- **Once per process** (a `JoltRuntime` object, guarded like `NativeLibraryLoader`): load the native, call
  `Jolt.registerDefaultAllocator()`, install the assert and trace callbacks (routed to our logger), then
  `Jolt.newFactory()` and `Jolt.registerTypes()`.
- **Per `JoltPhysics3D` world**: a `TempAllocatorImpl` (about 10 MB), a `JobSystemThreadPool`, the three layer
  interfaces (see [Layers](#layers-and-masks)), and a `PhysicsSystem`.
- **Ownership**: jolt-jni objects wrap native memory. Shapes are reference-counted (`ShapeRefC`). Settings objects are
  freed right after the body or constraint is created. `destroy()` removes and destroys every body and constraint,
  then frees the system, the job system and the allocator, in reverse order of creation. A leak test covers this (see
  [Test plan](#test-plan)).

### Threading

Jolt runs its solver on its own `JobSystemThreadPool`. We do not plug in our `JobSystem`: Jolt's barrier and job
model does not map onto it, and the pool is private to the step. Thread count: `min(availableProcessors - 1, 4)`,
overridable through a system property. Determinism tests use one thread. Every Jolt call still happens on the
thread that calls `step`. Only the contact listener runs on Jolt workers, and it only writes to a queue (see
[Contacts](#contacts-and-triggers)).

## 2. Native packaging for editor and exported games

Today `NativeLibraryLoader` hard-codes `bulletjme` and the libbulletjme jar paths. Bullet runs from the `SpDebug`
runtime classifier (since PHY-20: `SpRelease`).

Plan:

1. Turn the loader into `NativeLibraryLoader(spec: NativeLibrarySpec)`, where the spec holds the library base name
   and the per-platform jar paths. Ship two specs, `BULLET` and `JOLT`. `INativeLibraryLoaderService` stays as it is,
   so the current mocked tests keep working. Each spec keeps its own "loaded" flag.
2. `build.gradle.kts`: add `implementation(libs.joltjni)` with the Windows64 Release single-precision classifier.
   Natives stay Windows-only, as with LWJGL today. Adding other OS classifiers later is one line per OS.
3. `gameJar` already merges the whole runtime classpath, so the Jolt native reaches exports without any change to
   the task. `tools/scripts/verify-game-jar.sh` gets one extra check that the Jolt DLL entry is present.
4. Loading is **lazy**. The Bullet native loads only when a Bullet world is created, and the Jolt native only when a
   Jolt world is created. A Bullet project never extracts the Jolt DLL.
5. Extraction directory: both loaders extract to `USER_DIR`, as Bullet does today. A game installed in a read-only
   folder (Program Files) would fail to extract. That is an existing problem for Bullet as well, so it is tracked as a
   separate follow-up: extract to a per-user data folder instead. It is not a blocker for PHY-17.
6. Phase B (needs AST, as part of the AST-15 export settings): the export leaves out the natives of the unused
   backend. `SIMPLE` needs neither native.

## 3. Code structure

```
engine/physics3d/
  Physics3DFactory.kt        PhysicsBackend { BULLET, SIMPLE, JOLT }
  BodyDescription.kt         NEW, backend-neutral: mass, motion type, shapes + offsets, scale, layers,
                             trigger, CCD radius, friction/restitution/damping (built from a GameObject)
  bullet/…                   switched to BodyDescription (no behaviour change)
  jolt/
    JoltRuntime.kt           one-time native load and global registration
    JoltPhysics3D.kt         IPhysics3D
    JoltRigidBodyHandle.kt   IRigidBodyHandle over (BodyInterface, BodyId)
    JoltShapes.kt            ColliderShape → ShapeRefC
    JoltLayers.kt            (layer, mask) → ObjectLayer table
    JoltConstraints.kt       IPhysicsConstraint descriptors → TwoBodyConstraint
    JoltContacts.kt          worker-thread queue → main-thread PhysicsContactListener events
    JoltDebugDraw.kt         wireframes through the existing neutral ShapeWireframe
```

`ShapeWireframe` already imports nothing from Bullet. It moves from `bullet/` up to `physics3d/` so both backends can
share it.

Architecture guard: extend `EngineLayeringGuardTest` (or add a sibling) so that `com.github.stephengold.joltjni` may
only be imported under `physics3d/jolt/`, and only `Physics3DFactory` may construct `JoltPhysics3D`, the same rule we
already enforce for Bullet.

## 4. Feature parity mapping

### Bodies

| Engine concept | Bullet today | Jolt |
|---|---|---|
| Static / Dynamic / Kinematic | mass 0 / mass > 0 / `isKinematic` | `EMotionType.Static/Dynamic/Kinematic`. Mass goes through `MassProperties` override (`EOverrideMassProperties.CalculateInertia`). |
| Mass or motion type changed | rebuild body | Rebuild the body (same path). We avoid `allowDynamicOrKinematic` so static bodies stay cheap. |
| Kinematic driven by Transform | `setPhysicsLocation` | `BodyInterface.moveKinematic(pos, rot, dt)` during fixed steps, so the body gets a velocity and pushes dynamic bodies correctly. `teleport` uses `setPositionAndRotation`. |
| Mesh collider forces static | yes | Same rule (`staticOnly`). |
| Friction / restitution | combine a·b / a·b | Jolt's defaults are √(a·b) and max(a,b). We set Bullet's combine rules on the `PhysicsSystem` so `RigidBody3D` values mean the same thing on both backends. If jolt-jni does not expose the combine function, we override them in `onContactAdded`/`onContactPersisted` through `ContactSettings`. |
| Damping | v·(1−c)^dt | Jolt: v·(1−c·dt). The mapper converts c_jolt = −ln(1−c) (clamped) so the decay per second matches. Unit test for the conversion. |
| `setAngularFactor` | per-axis factor | `EAllowedDofs`. Engine code only uses 0/1 per axis (`RigidBody3D` locks to Y). A factor of 0 locks the axis, anything else frees it. Fractional values log a one-time warning. |
| CCD (`useCCD`) | swept sphere | `EMotionQuality.LinearCast`. The threshold and radius are ignored, since Jolt sizes the cast itself. |
| Sleeping | `activate(true)` | Every setter that changes pose, velocity or forces activates the body. |
| Centre of mass | body origin (compounds are **not** recentred) | Jolt puts the COM at the shape's true centre of mass. **Decided:** use the physical COM. Either way, `getLocation` returns the body **origin** (`getPosition`, not `getCenterOfMassPosition`), so `Transform` sync does not change. |
| User data | `userObject = go` | `body.userData = handleIndex`, an index into a Kotlin-side table of `GameObject`/handle entries. The index is resolved on the main thread. |
| `rigidBodyCount` | `countRigidBodies()` | `PhysicsSystem.getNumBodies()` |

### Shapes (`ColliderShape` → Jolt)

| ColliderShape | Jolt shape | Notes |
|---|---|---|
| `Box(halfExtents, margin)` | `BoxShape(halfExtents, convexRadius)` | `margin` maps to `convexRadius`, clamped to the smallest half extent. Both engines keep the outer size, so the extents stay the same. |
| `Capsule(radius, height, axis)` | `CapsuleShape(height/2, radius)` | Jolt capsules run along Y. Axis X or Z wraps the shape in `RotatedTranslatedShape` (±90° about Z or X). |
| `Cylinder(radius, height, axis, margin)` | `CylinderShape(height/2, radius, convexRadius)` | Same axis wrapping as the capsule. |
| `ConvexHull(points)` | `ConvexHullShapeSettings(points)` | |
| `CompoundHull(hulls)` | `StaticCompoundShape` with one hull per child | Jolt can nest compounds, but we flatten anyway so both backends see the same tree. |
| `TriangleMesh(v, i)` | `MeshShapeSettings` | Static only, same rule as Bullet. |
| Several colliders with offsets | `StaticCompoundShape`, one child per collider at its `offset` | Same flattening as `BulletPhysics3D.bodyShape`, done in `BodyDescription`. |
| No collider | unit box | Same fallback. |
| Transform scale | `ScaledShape` | Non-uniform scale on capsules and cylinders is not supported (Bullet already only scales capsules uniformly). We log once and use the largest axis. |
| Heightfield (PHY-15) | `HeightFieldShape` | Designed in PHY-15, not here. |

Shape caching: identical `ColliderShape` values (data classes) map to one shared `ShapeRefC` per world, so repeated
prefabs do not rebuild hulls.

### Layers and masks

Our rule: two bodies collide when `(a.layer & b.mask) != 0 && (b.layer & a.mask) != 0`. Layer and mask are both
32-bit. Jolt filters through a 16-bit `ObjectLayer` and a broad-phase layer table.

- `JoltLayers` gives each distinct `(layer, mask)` pair in the world its own compact `ObjectLayer` index (allocated
  on first use, up to 1024 slots).
- It uses a mutable `ObjectLayerPairFilterTable`: when a new slot is created, it enables collision against each
  existing slot where our rule passes. This is O(slots) per new combination and costs nothing per step: no JNI
  upcall in the broad phase.
- Broad-phase layers: `NON_MOVING` (static) and `MOVING` (dynamic and kinematic), through
  `BroadPhaseLayerInterfaceTable` and `ObjectVsBroadPhaseLayerFilterTable`.
- Changing `collisionLayer` or `collisionMask` on a handle reassigns the body's `ObjectLayer`
  (`BodyInterface.setObjectLayer`).
- Fallback if the table approach hits a limit: a `CustomObjectLayerPairFilter` upcall that evaluates the rule
  directly. Slower; we measure before using it.

### Contacts and triggers

The contract stays: `onCollisionStarted` fires once per GameObject pair when contact starts, and
`onTriggerEnter`/`onTriggerExit` fire on the main thread after `step`. Listeners may add or remove bodies.

- `CustomContactListener.onContactAdded` and `onContactRemoved` run on Jolt workers. They push
  `(kind, bodyIdA, bodyIdB, point, normal, impulseEstimate)` into a preallocated, lock-protected ring buffer, with no
  allocation per event.
- After `update`, the main thread drains the buffer. It resolves body IDs to GameObjects through the user-data table,
  removes duplicate sub-shape contacts with the same `LongSet` pair keys Bullet uses, and dispatches events last, as
  Bullet does.
- **Triggers** are Jolt sensors (`isSensor = true`). By default Jolt sensors ignore static bodies, but Bullet triggers
  see them. We set `collideKinematicVsNonDynamic` (or the jolt-jni equivalent `sensorDetectsStatic`) so parity holds.
  Covered by a test.
- **Impulse**: Jolt only knows the applied impulse after solving, while `onContactAdded` runs before. We report an
  estimate: approach speed along the normal × effective mass. It is only used for things like impact-sound volume.
  The KDoc documents it as approximate on Jolt, and tests use a tolerance.
- Removing a body reports pending trigger exits (same `forgetContacts` behaviour).

### Queries

| API | Jolt |
|---|---|
| `raycastAll` | `NarrowPhaseQuery.castRay` with `AllHitCastRayCollector`, sorted by fraction. Sensors, the excluded body and bodies outside `layerMask` are filtered out in Kotlin, the same filter Bullet uses (`isQueryable`). |
| `raycast` / `raycastClosest` | Same filter. `ClosestHitCastRayCollector` plus an `ObjectLayerFilter` built from the layer table, with a fallback to all hits + min. |
| Hit normal | `Body.getWorldSpaceSurfaceNormal(subShapeId, point)` under a body read lock |
| `shapeCast` | `NarrowPhaseQuery.castShape` (`RShapeCast` built from `JoltShapes`). The returned point is `from + (to − from)·fraction`, which keeps the contract that the hit point is the shape's centre at impact. |
| Queries right after `add`/`teleport` | Jolt's broad phase picks up new bodies immediately (after `optimizeBroadPhase` on bulk load), so the Bullet re-insert workaround is not needed. |

### Joints (`constraints/*` descriptors → Jolt)

Every Jolt constraint uses `EConstraintSpace.LocalToBodyCom`. The pivots in our descriptors are relative to the body
**origin**, so the converter subtracts each body's `shape.getCenterOfMass()`. With the physical-COM decision this
matters for hulls and compounds. A unit test covers it.

| Descriptor | Jolt | Mapping notes |
|---|---|---|
| `FixedConstraint3D` | `FixedConstraintSettings` | `autoDetectPoint = false`, `point1/point2` = pivots |
| `HingeConstraint3D` | `HingeConstraintSettings` | hinge axis = `axisA/axisB`, normal axis = any perpendicular. Limits in radians, clamped to [−π, π]. |
| `SliderConstraint3D` | `SliderConstraintSettings` | slider axis = `axisA`. Jolt measures limits on body 2 relative to body 1, so the converter swaps the bodies or negates the limits to keep the documented "A relative to B" sign. Pinned by the existing slider test. |
| `ConeTwistConstraint3D` | `SwingTwistConstraintSettings` | twist axis = column 0 of `rotA/rotB` (Bullet's cone-twist X axis), plane axis = column 1. `normalHalfConeAngle = swingSpan1`, `planeHalfConeAngle = swingSpan2`, twist range ±`twistSpan`. |
| `SixDofConstraint3D` | `SixDofConstraintSettings` | Per axis: lower == upper → `makeFixedAxis`, lower > upper → `makeFreeAxis`, else `setLimitedAxis`. Swing type `Pyramid`, which is closest to Bullet's per-axis Euler limits. |
| `createStaticAnchor` | static body with a 1 cm box | Sensor, so it never pushes the jointed body, which matches `isContactResponse = false` |
| Linked-body collision | Bullet joints let linked bodies collide | Same in Jolt (no group filter). A later change could disable it for ragdolls on both backends. |

`rawJoint` holds the Jolt `TwoBodyConstraintRef`. `removeConstraint` removes it from the system and frees it.

### Ragdolls

`RagdollBuilder` and `RagdollSystem` only use `createRigidBody`, `ConeTwistConstraint3D`, kinematic/dynamic switching
and `addConstraint`. So once bodies and swing-twist joints work, ragdolls reach parity with no ECS change.
`PhysicsSystemRagdollTest` runs on both backends. Jolt's own `Ragdoll`/`RagdollSettings` (pose motors, stabilisation,
parent-child collision filtering) gives better ragdolls, but that is a follow-up, not part of parity.

### Vehicles

`RaycastVehicleSolver` (PHY-09) only uses `raycastClosest` and `applyImpulse`, so it works on Jolt unchanged.
`RaycastVehicleTest` runs on both backends. Jolt's `VehicleConstraint` (wheeled, motorcycle and tracked controllers)
could be an optional `VehicleComponent` mode later.

### Debug draw and stats

- Debug draw uses the neutral `ShapeWireframe` and walks the same `BodyDescription` shapes at each body's
  interpolated pose. That avoids binding Jolt's `DebugRenderer` upcalls, and the visuals are identical across
  backends.
- `EngineStats.physicsStepTime` is measured around `update` as it is today. Optional: body count and active-body
  count.

## 5. Per-project selection

| Piece | Owner | Change |
|---|---|---|
| `PhysicsBackend.JOLT` + factory branch | PHY | This task |
| Native fallback | PHY | If `JoltRuntime` fails to load, `Physics3DFactory` logs an error, publishes a `PhysicsEvents` warning event and returns a Bullet world. |
| `Project.physicsBackend` (serialised, default `BULLET`), settings field, `SetPhysicsBackendCommand` | EDT | Mirrors `graphicsBackend` / `SetGraphicsBackendCommand` |
| New Project wizard field | EDT (EDT-59) | Bullet / Simple / Jolt |
| Pack manifest `physicsBackend` (schema bump, older manifests default to Bullet) | AST | Mirrors `graphicsBackend` in `PackBuilder` |
| Game runtime startup | AST / app | Read the manifest and set `Physics3DFactory.defaultBackend` before the first scene |
| Editor | EDT | Set `defaultBackend` on project open. Changing the setting reloads open scenes, because physics worlds are not migrated live. |
| CLI / MCP | TLG | Optional `--physics <backend>` override for the headless runner |

Scene files are not affected: components are backend-neutral and `rawBody` is never serialised.

## 6. Test plan

Unit tests only, run with Windows Gradle (natives are Windows-only).

1. **Parameterised backend contract suite.** Add a test helper `PhysicsBackends.all()` (BULLET, JOLT; SIMPLE where it
   applies) and turn the physics tests that call `BulletPhysics3D(...)` directly into
   `@ParameterizedTest @MethodSource` over `Physics3DFactory.create(debugRenderer, backend)`:
   `PhysicsFeaturesTest`, `JointConstraintsTest`, `CompoundAndMeshColliderTest`, `FrictionPropagationTest`,
   `PhysicsCalibrationTest`, `RaycastVehicleTest`, and in `engine/ecs/systems/`: `PhysicsSystemRagdollTest`,
   `PhysicsTransformSyncTest`, `PhysicsCollidersFromDiskTest`. Bullet-only tests stay as they are
   (`BulletJointCreationTest`, `ShapeWireframeTest`, `LongSetTest`). Tests owned by other domains
   (`FollowCameraSystemTest`, `SkateAnimationFromDiskTest`, `ProjectLifecycleEndToEndTest`) stay on Bullet.
2. **Outcome assertions with tolerances.** Assert outcomes, not engine internals: rest height ±1 cm, bounce height
   ratio ±10 %, slide distance on a slope ±15 %, joint angle within its limits ±0.02 rad, ray hit point ±1 mm and
   fraction ±1e-4. Where a tolerance has to be per-backend, it lives in the test with a comment saying why.
3. **Jolt mapping unit tests** (pure Kotlin where possible, no world needed): capsule and cylinder axis rotation and
   half-height, convex-radius clamping, damping conversion, layer-slot allocation and pair-table contents against the
   rule, constraint pivot COM offset, contact queue deduplication and enter/exit pairing, `BodyDescription` built from
   GameObjects (shared by both backends).
4. **Events.** Collision start fires once per pair. Trigger enter and exit fire, including a trigger against a static
   body. Removing a body during a listener callback is safe. Removing a body reports trigger exit.
5. **Lifecycle and leaks.** Create, populate and destroy 50 Jolt worlds in a loop. Native allocations return to
   baseline (jolt-jni's live-object count, if exposed, otherwise process RSS within a bound). Destroying a world with
   live constraints does not crash.
6. **Determinism.** The same scene stepped twice for 300 steps on a single-thread Jolt world gives identical poses.
7. **Native loader.** `NativeLibraryLoaderTest` is extended for the per-spec loader (mocked service). The spec paths
   match the resources inside the pinned jolt-jni jar.
8. **Factory and fallback.** `JOLT` creates a `JoltPhysics3D`. A failing Jolt load returns Bullet and emits the warning
   event.
9. **Architecture guard.** Jolt imports appear only under `physics3d/jolt/`, and `JoltPhysics3D` is only constructed
   in `Physics3DFactory`.
10. **Showcase.** The QA/TLG-owned `FeatureShowcaseSmokeTest` gets a Jolt run. Ask the lead to route it to them.
11. **Benchmark (manual, not CI).** A 1000-box pile, 50 ragdolls and 20 vehicles on both backends, comparing
    `physicsStepTime`. The results go into this note.

## Implementation slices

Each slice is one task with its own tests and keeps `master` green.

1. **PHY-17a – Preparation (no behaviour change):** add `BodyDescription` and switch Bullet to it, add the per-spec
   `NativeLibraryLoader`, move `ShapeWireframe` to `physics3d/`, and parameterise the test helper (Bullet only for
   now).
2. **PHY-17b – Jolt core:** dependency and pinning, `JoltRuntime`, world lifecycle, shapes, bodies, layers, `step`,
   `teleport`, `remove`, ray and shape queries, factory branch and fallback. The contract suite runs on Jolt for
   bodies and queries.
3. **PHY-17c – Contacts and triggers.**
4. **PHY-17d – Joints, ragdoll and vehicle parity.**
5. **PHY-17e – Debug draw, stats, leak and determinism tests, benchmark numbers.**
6. **PHY-17f – Selection plumbing** with EDT (project setting, EDT-59 wizard), AST (manifest, runtime startup) and
   TLG/QA (showcase on Jolt).

Rough effort: about 8–10 agent days in total. Slices b and d are the largest.

## Risks

- **jolt-jni API churn (pre-1.0):** pin the version, upgrade only as its own task, and keep all Jolt calls in one
  package.
- **JNI upcall overhead:** only the contact listener uses upcalls (no upcalls for the broad-phase filter, thanks to
  the layer table). The benchmark will show the cost. The fallback is to drop `onContactPersisted`, so only added and
  removed contacts upcall.
- **Native memory leaks:** explicit ownership rules plus the leak test.
- **Behaviour differences** (COM, friction combining, impulse estimate, solver feel): documented above. Game tuning
  values may need a second look when a project switches backend. That is why Bullet stays the default.
- **Read-only install folders:** extraction to `USER_DIR` (both backends). Separate follow-up.

## Decisions after review

1. **Centre of mass:** use Jolt's physical COM. `getLocation` still returns the body origin.
2. **Exports:** ship both natives now; AST-15 strips the unused one.
3. **Default backend:** Bullet stays the default.
4. **Follow-ups:** Jolt-native ragdoll, vehicle and character controller are filed as PHY-19 (P3, design note later).
5. **Extraction folder:** filed as PHY-18.

## Implementation notes (slices a to e)

- **Slice a:** `BodyDescription` (shared body mapping, used by Bullet), `NativeLibrarySpec` (per-library loader,
  `loadNativeLibrary()` returns success), `ShapeWireframe` moved to `physics3d/`, and the `PhysicsBackends` test helper.
  No behaviour change.
- **Slice b:** `physics3d/jolt/` (`JoltRuntime`, `JoltPhysics3D`, `JoltRigidBodyHandle`, `JoltShapes`, `JoltLayers`,
  `JoltConversions`) behind `PhysicsBackend.JOLT`. Found while testing:
  - Jolt mesh triangles only collide on their front face during simulation. Each `TriangleMesh` is built as a static
    compound of the mesh and its reversed copy (two meshes, so each keeps its internal-edge detection), which matches
    Bullet's two-sided meshes.
  - Changing a body's layer, mask or trigger flag wakes it, otherwise a sleeping body ignores the new filter.
  - jolt-jni exposes `CombineFunction.product`, so friction and restitution combine like Bullet without a contact
    callback.
  - Standalone bodies (`createRigidBody`, anchors) use Bullet's defaults: layer 1, mask 1.
  - Not implemented in slice b: contact and trigger events (done in 17c), joints (17d), debug draw (17e).
  - Fallback: `Physics3DFactory.create(..., onFallback)` returns Bullet when Jolt cannot load; `PhysicsSystem`
    publishes `PhysicsBackendFellBack`.
- **Slice c (contacts and triggers):** `JoltContacts`. Callbacks on Jolt worker threads only append records to a
  locked buffer; the stepping thread works out starts and ends after each step and dispatches last. Found while
  testing:
  - Jolt reports a contact as removed when its bodies fall asleep; Bullet keeps reporting it. The touching set is
    rebuilt each step from the added and persisted callbacks, and a pair whose two bodies are both asleep stays
    touching. A body sleeping in a trigger does not exit, and a resting body that wakes does not start again.
  - The manifold has no contact points, so the point comes from the validate callback (point on body b). Without
    one (a woken pair) it falls back to the midpoint of the two bodies.
  - The impulse is an estimate (approach speed × effective mass). Bullet reports 0 at contact start because its
    impulse is solved after the event, so the contract only requires impulse ≥ 0.
  - As in Bullet, a dynamic trigger is held kinematic (also when toggled at runtime), and kinematic bodies collide
    with static ones (`collideKinematicVsNonDynamic` on every moving body), so kinematic-vs-ground contacts and
    triggers detecting statics behave the same.
  - A mass-change rebuild carries the body's contact pairs over (Bullet keys pairs by GameObject).
  - `LongSet` moved to `physics3d/` so both backends share it.
- **Slice d (joints, ragdoll, vehicle):** `JoltJoints`. Descriptor pivots and axes (local to each body's origin)
  are converted to world space through the bodies' poses when the joint is created and given to Jolt in
  `EConstraintSpace.WorldSpace`, so the physical-COM decision needs no pivot correction. Bullet conventions kept:
  - Fixed: identity frames on both bodies (the bodies keep the same orientation), like Bullet's 6-DOF fixed joint.
  - Hinge: zero angle from Bullet's `btPlaneSpace1` normal, rotated onto axis B. Jolt needs min ≤ 0 ≤ max within
    [-π, π], so limits are clamped; lower > upper means free.
  - Slider: limits negated and swapped (Jolt measures B relative to A); clamped to contain 0; the frame's Y axis
    locks rotation, as Bullet's slider frame does.
  - Cone twist → swing-twist: twist axis = frame X, `swingSpan1` about Z (normal half-cone), `swingSpan2` about Y
    (plane half-cone), twist ±`twistSpan`.
  - 6-DOF: Bullet's per-axis rule (equal = fixed, lower > upper = free, else limited), pyramid swing limits.
  - Removing a body first removes the joints that use it (Jolt would keep a dangling body); a removed descriptor can
    be added again. Joints wake their bodies when added or removed.
  - Ragdolls (`RagdollBuilder`, `RagdollSystem`) and the raycast vehicle needed no Jolt-specific code.
- **Slice e (debug draw, stats, leaks, determinism, benchmark):**
  - `JoltDebugDraw` draws every body from its `ShapePart`s, so the result looks like the Bullet view: boxes and
    cylinders through the renderer's primitives (a capsule as a cylinder spanning its caps), hulls and meshes as
    feature edges from the shared `ShapeWireframe`, cached per body. Hulls above `ShapeWireframe.MAX_HULL_POINTS` use
    Jolt's debug mesh, aligned to the input points' bounds.
  - `IPhysics3D.activeBodyCount` (awake, non-static bodies) is implemented by Bullet and Jolt; it defaults to the body
    count. `EngineStats.physicsStepTime` still times the native step only (event dispatch excluded) on both.
  - Leaks: `JoltLifecycleTest` creates, fills (67 bodies, a joint, a contact listener), steps and destroys 50 worlds.
    Every native object a world owns is freed, and committed memory and thread count stay flat after warm-up. Each
    world holds a 16 MB temp allocator and a thread pool, so a leak would show as hundreds of MB and 100+ threads.
  - Determinism: two single-thread runs of the same 300-step scene give bit-identical poses.

### Benchmark (manual, 2026-11-06; Bullet release native since PHY-20)

`PhysicsBenchmarkTest`, skipped unless `SKATE_BENCHMARK=1`:
`cmd.exe /c "set SKATE_BENCHMARK=1&& gradlew.bat :test --tests com.pafoid.skate.engine.physics3d.PhysicsBenchmarkTest --rerun"`.
It writes build/reports/physics-benchmark.md. The table shows `EngineStats.physicsStepTime` per fixed step (60 Hz)
over 600 measured steps after a 60-step warm-up, on a 16-core Windows machine. Both backends use release natives
(Bullet `SpRelease`, Jolt `ReleaseSp`); Jolt used 4 solver threads, Bullet one. The vehicle solver's own time is
excluded on both.

| Scene | Backend | Mean ms | p50 ms | p95 ms | Max ms | Active / bodies at end |
|---|---|---|---|---|---|---|
| 1000-box pile | Bullet | 0.53 | 0.18 | 2.62 | 4.42 | 0 / 1001 |
| 1000-box pile | Jolt | 0.92 | 0.83 | 1.66 | 3.48 | 800 / 1001 |
| 50 ragdolls (650 bones, 600 joints) | Bullet | 1.86 | 1.85 | 1.97 | 2.52 | 650 / 651 |
| 50 ragdolls (650 bones, 600 joints) | Jolt | 1.00 | 0.86 | 2.02 | 3.78 | 247 / 651 |
| 20 raycast vehicles | Bullet | 0.01 | 0.01 | 0.02 | 0.04 | 20 / 21 |
| 20 raycast vehicles | Jolt | 0.04 | 0.03 | 0.06 | 0.15 | 20 / 21 |


Reading:
- **Box pile:** Bullet's mean and median are lower because its pile falls asleep (0 active at the end) while Jolt's
  stays mostly awake (one large island has to be still as a whole before it sleeps). During the collapse, Bullet's
  spikes are ~2× Jolt's (p95 2.6 vs 1.7 ms, max 4.4 vs 3.5 ms), which matters more for frame pacing.
- **Ragdolls:** Jolt is about 1.8× faster, and lets most bones sleep where Bullet keeps all 650 awake.
- **Vehicles:** both are negligible; Bullet's single-threaded step has less fixed overhead on tiny scenes.
- Ragdoll bones start stacked along a line and are pulled into shape by their joints. This is a load test, not a
  realistic ragdoll.

Before PHY-20, the build shipped libbulletjme's **SpDebug** native. The same benchmark then gave Bullet mean
3.80 ms (box pile), 12.53 ms (ragdolls) and 0.10 ms (vehicles), 4–5× slower than the release build.

- Tests on Bullet and Jolt: `PhysicsFeaturesTest` (core and contact cases), `PhysicsCalibrationTest`,
  `FrictionPropagationTest`, `JointConstraintsTest`, `RagdollBuilderTest`, `PhysicsSystemRagdollTest`,
  `RaycastVehicleTest` and `PhysicsDebugDrawTest` (backend cases). Jolt only: `JoltPhysics3DTest`, `JoltContactsTest`,
  `JoltJointsTest`, `JoltDebugDrawTest`, `JoltLifecycleTest`, `JoltConversionsTest`, `LayerSlotsTest` and
  `JoltBackendGuardTest`. `tools/scripts/verify-game-jar.sh` checks that both natives ship in the game jar.

## Open questions (answered above)


For the human or the lead:

1. **Centre of mass:** use Jolt's physical COM (recommended) or force COM = origin for exact Bullet parity?
2. **Exports:** ship both natives now and strip unused ones in AST-15 (recommended), or strip from the start?
3. **Default backend:** keep Bullet as the default after PHY-17 (recommended), and revisit once the benchmark and the
   showcase are in?
4. **Follow-ups:** should I file tasks for Jolt-native ragdolls (pose motors), `VehicleConstraint`, and
   `CharacterVirtual` (that one with GPL)?
5. **Extraction directory:** file the per-user native extraction folder fix (both backends) as a separate PHY task?
