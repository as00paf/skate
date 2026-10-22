# ECS-01 — ECS review and target design

Scope: `GameObject`, `Scene`, `SceneManager`, `System`, `SystemManager`, `GameObjectManager`, `TransformSystem`, `Component`, `EngineExtensions.kt` and the systems that use them. Paths relative to `src/main/kotlin/com/pafoid/skate/engine/`.

## 1. Current state

- `GameObject` holds `components: MutableList<Component>` and `children: MutableList<GameObject>`, both public and mutable. `Scene` is a `GameObject` (root); its direct children are the top-level objects.
- Component access lives in `EngineExtensions.kt`: `getComponent<T>()` is `filterIsInstance().firstOrNull()` (allocates a list per call), `hasComponent`, `getAllComponents<T>()` (recursive flatMap, allocates), `addComponent`, `removeComponent`.
- `System` has `priority` (EARLY/DEFAULT/LATE), `cacheDirty`, `invalidateCache()`, `rebuildCache()`. Each system keeps its own private list.
- `SystemManager` sorts by priority ordinal, runs `update(dt)` only; pending systems are added after the frame.

## 2. Issues

### Component storage
- S1. `components` is a public mutable list: any code (editor, commands, deserialization, `SceneManager`) can add/remove without notifying anyone. No single mutation point, so no invalidation hook is possible.
- S2. `getComponent<T>()` / `hasComponent<T>()` are O(n) and allocate (`filterIsInstance` builds a list) on hot paths (`TransformSystem.updateTransform` per transform per frame, `PhysicsSystem.update` per frame).
- S3. `addComponent` replaces by `isInstance` on the *requested* class: adding a base type removes subclasses; `removeComponent(KClass)` removes all subclass instances. "One component per type" is implicit and not enforced in `components.add` callers.
- S4. `Component.type: ComponentType?` is a hand-maintained `when`, and `GameObject.getComponent(ComponentType)` another one. Missing entries: `SpotLightComponent`, `NativeScriptComponent`, `BoneOverride` (`type` returns null for them) — silent bugs when adding components. Both should derive from one registry.
- S5. `Component.ID_COUNTER` / `GameObject.ID_COUNTER` are global mutable statics (not thread safe, shared across scenes, restored via `maxId` after load). `uId == -1` as a sentinel; `init()` and `generateId()` duplicate the logic.
- S6. `Component.update(dt)` is marked `//TODO: remove` yet `GameObject.update` still drives it; two update paths (component-driven and system-driven) coexist.
- S7. `Component.gameObject` is a nullable `@Transient` var set in `init`; `init` is called repeatedly and from many places (`SceneManager.resolveSceneReferences` calls `init` and again in `resolveObjectReferences` for children; `GameObjectManager.init` calls again). Init is not idempotent by contract.
- S8. `GameObject.copy` sets uIds, but copies of children are not re-id'd (only the root and its own components); `getIdCounter` / `uId` duplicated between types.
- S9. `GameObject.addChild/removeChild` do not notify the scene: hierarchy changes also invalidate queries (`getAllComponents`) and world transforms but nothing signals it. `GameObjectManager.addGameObject/removeGameObject` bypass `addChild` (no `parent` set, `scene.children.add` directly) so `parent` is null for those objects, which breaks `TransformSystem` parent lookup.
- S10. `GameObjectManager.update` only iterates top-level children and removes dead ones with no `destroy`/queue semantic; dead children of nested objects are never removed. `GameObjectManager.destroy()` shadows `System.destroy()` (does not call `invalidateCache`). `getGameObject(id/name)` is O(n) top-level only.
- S11. `System.scene` defaults to a throw-away `Scene()` instead of being lateinit/nullable — systems touching it before `init` silently work on an empty scene.

### Queries and caches (all half implemented)
- Q1. Six independent ad-hoc caches: `TransformSystem` (`getAllComponents<Transform>`), `AnimationSystem` (`AnimatedGameObject`), `PhysicsSystem` (`PhysicsObject`), `PlayerMotionSystem` (GameObjects), `RagdollSystem` (GameObjects), `ScriptingSystem` (`NativeScriptComponent`). `EnvironmentSystem.invalidateCache` is an empty override. Others (`DirectionalLightSystem`, `AudioSystem`, `DayNightCycleSystem`, `InputSystem`, `CameraManager`) query the scene directly each frame.
- Q2. Nothing ever sets `cacheDirty = true` when a component or child is added/removed/destroyed at runtime. Only triggers: `System.start()` (some systems), `init`, and `resetSystemCaches()` called from `editor/systems/ProjectManager.kt:172`. Objects spawned or removed during play (and in the editor via add/remove component commands) are missed until a manual reset. `System.cacheDirty` is a public flag every system re-implements by hand.
- Q3. Inconsistent scope: `TransformSystem`/`ScriptingSystem` use recursive `getAllComponents`, while `AnimationSystem`/`RagdollSystem`/`PlayerMotionSystem`/`PhysicsSystem` use `scene.children` only, so nested objects are skipped.
- Q4. Bugs: `PlayerMotionSystem.rebuildCache` does not `clear()` before `addAll` (duplicates on every rebuild); `PhysicsSystem.update` recomputes `scene.children.filter { hasComponent }` for every cached entry each frame (O(n²), allocation) and removes entries before they could be re-added; `AnimationSystem` caches component refs, so replacing a component leaves stale refs; `TransformSystem` rebuilds a list order unrelated to hierarchy depth, so a child can be updated before its parent (one-frame-stale world matrices) and each update does an uncached `parent.getComponent<Transform>()` (existing TODO).
- Q5. `CustomCollider3D.cachedShape` and `Component.fieldCache` are separate unrelated caches (non-thread-safe `mutableMapOf`); `fieldCache` is out of scope for invalidation but must be made `ConcurrentHashMap` or documented as main-thread only.
- Q6. Cache of a disabled/destroyed GameObject is not filtered (`isEnabled`, `isDead` ignored by query results).

### System ordering
- O1. Three coarse buckets only (EARLY/DEFAULT/LATE). Within a bucket the order is insertion order (stable sort) and so depends on `Engine.kt` registration order — implicit. Real dependencies exist: Input → GameObjectManager → Physics (fixed step) → PlayerMotion → Animation/Ragdoll → Transform → Camera → DirectionalLight → Audio. Today Physics, Transform and Input are all EARLY and only correct by registration order; `PhysicsSystem` comment claims a "deterministic fixed-step" but it simply passes `dt`.
- O2. No phases (fixed update, late update, render). `SystemManager.update` is the only entry point; rendering is outside the ECS.
- O3. `addSystem(isRunning = true)` defers to the end of the frame; deferred add re-sorts, but `removeSystem` during iteration mutates `_systems` while it is being iterated (ConcurrentModificationException risk).
- O4. `loadScene` calls `init` on all systems every scene switch, `start()` is only called once (`hasStarted`), so systems get `init` without `start` on later scenes — each system compensates with `cacheDirty = true` in `start`, which is not called again. `SceneManager.openScenes(list)` calls `systemManager.loadScene` and then `switchScene` calls it again (double init).
- O5. `getSystem<T>()` is an inline `filterIsInstance` per call; systems reach each other via constructor wiring (`DirectionalLightSystem(cameraManager)`), unchecked.

### Scene / SceneManager
- M1. `resolveSceneReferences` is a loader, init and asset resolution step in one; swallows exceptions per object; initializes components of nested children twice. It should be split (init lifecycle vs. asset resolve).
- M2. `SceneManager` only publishes scene-level events; no `GameObjectAdded/Removed` or `ComponentAdded/Removed` events exist.
- M3. `closeScene` while closing the active scene calls `switchScene` with the previous scene without handling `activeSceneIndex = -1` correctly when the closed index is below active (index shift is not applied when `activeSceneIndex > index`... it decrements, which then clamps; acceptable but untested).
- M4. `isDirty` is set externally; nothing in the ECS sets it on mutation.

## 3. Target design

### 3.1 Component storage
- `GameObject.components` becomes read-only to the outside: `val components: List<Component>` backed by a private list. Mutations only via `addComponent(c)`, `removeComponent(c)`, `removeComponent(KClass)` members on `GameObject` (the extension functions delegate). Deserialization keeps a serializer-friendly backing field and runs `onComponentsLoaded()` which wires owners.
- Per-object type index: `Map<KClass<out Component>, Component>` (exact class → instance) plus a lazily-filled lookup for `getComponent<T>()` of supertypes/interfaces, cleared on mutation. `getComponent<T>()`/`hasComponent<T>()` become allocation-free (`T::class.java` lookup, fallback loop with no list).
- Rule: at most one component per concrete class (keep today's replace behaviour, but replace by exact class, not `isInstance`).
- `ComponentRegistry`: single place mapping class ↔ `ComponentType` (replaces both `when` blocks; a unit test asserts every `Component` subclass is registered).
- Ids: `IdGenerator` (atomic) for GameObject and Component ids; one `ensureId()`; `init(go)` contract: idempotent, sets `gameObject`, called exactly once by `GameObject.addComponent` or by the loader (`onLoaded`).
- Remove `Component.update` (S6) after systems own all per-frame logic (follow-up, coordinated with owning domains).
- Hierarchy: `addChild/removeChild/destroy` are the only mutation points; `GameObjectManager.add/remove` use them and set `parent`.

### 3.2 Change notification (basis of cache invalidation)
- Each `Scene` owns a `ComponentWorld` (index + version counters). `GameObject` finds it by walking `parent` to its `Scene` (cached `scene` reference set on attach/detach).
- Events (internal, synchronous, no editor dependency): `ComponentAdded(go, c)`, `ComponentRemoved(go, c)`, `ObjectAdded(go)`, `ObjectRemoved(go)`, `ObjectEnabledChanged`. Each bumps `scene.structureVersion` (hierarchy) and `componentVersion[KClass]` (per component class).
- Objects not attached to a scene don't notify; attaching a subtree raises `ObjectAdded` for every descendant.

### 3.3 Queries
- `Query` = set of required component classes (+ optional `exclude`, `includeDisabled=false`, scope = whole scene hierarchy). API:
  ```kotlin
  val q = scene.world.query(Transform::class, RigidBody3D::class)   // cached, shared by identical signature
  q.forEach { go -> ... }          // allocation-free iteration over a stable snapshot
  q.entities: List<GameObject>     // read-only
  ```
  Typed helpers `query2<A, B>()` return component tuples without extra lookups.
- `SystemQuery` helper: systems declare queries in `init` (`val bodies = query(RigidBody3D::class)`), the base `System` owns them and disposes in `destroy`. Replaces `cache`, `cacheDirty`, `rebuildCache`, `invalidateCache` in every system (ECS-02).
- Result order: pre-order hierarchy traversal, deterministic; parents always before children (fixes Q4 transform ordering).
- Scope is always the whole hierarchy (fixes Q3); a query can opt into `topLevelOnly`.
- Disabled/dead objects are skipped at iteration time (`isEnabled && !isDead`), not by cache rebuild, so toggling is O(1).

### 3.4 Cache invalidation
- Each query stores the `(structureVersion, componentVersion of its classes)` it was built from. On `forEach`/`entities` access it compares versions; if stale it rebuilds once (lazy, single pass). No manual `cacheDirty` flags.
- Mutation during iteration is safe: iteration runs over the snapshot; structural changes are applied at the next access. Dead objects are removed in a `SceneManager`/`GameObjectManager` end-of-frame sweep (recursive), then version bump.
- Scene switch / unload: `world.clear()` drops all queries; `SystemManager.resetSystemCaches()` becomes `world.invalidateAll()` (kept as a deprecated shim for `ProjectManager`).
- `System.cacheDirty/invalidateCache/rebuildCache` are deprecated in ECS-02 and deleted once no system overrides them. Data derived from queries (e.g. `AnimatedGameObject`) is not cached; systems read components from the entity each frame via O(1) lookup.
- Tests required (ECS-02): add/remove component, add/remove child, reparent, destroy, disable, scene switch, replace component, mutation during iteration, ordering of results, identical-signature sharing.

### 3.5 System ordering
- Replace `ExecutionPriority` with phases: `PreUpdate`, `FixedUpdate`, `Update`, `LateUpdate`, `Render` (ECS-03). `System.phase` and `System.runsAfter: List<KClass<out System>>`, `runsBefore`.
- `SystemManager` resolves order per phase by topological sort (Kahn) with ties broken by registration index, so output is deterministic; cycles or references to unregistered systems throw a descriptive error at resolve time (resolution is cached and invalidated only on add/remove).
- `FixedUpdate` uses an accumulator with a fixed step (e.g. 1/60) and max-substeps; physics moves here. `Render` runs once per frame with interpolation alpha.
- Proposed default graph: PreUpdate: Input → GameObjectManager (spawn/despawn sweep) → Scripting. FixedUpdate: Physics → PlayerMotion. Update: Animation → Ragdoll → DayNight → Transform. LateUpdate: Camera → DirectionalLight → Environment → Audio. Render: render graph.
- Lifecycle: `init(scene)` and `start()` both run on every scene load (`start` after `init`), with `start` only once per scene per system; `loadScene` called exactly once per switch. `removeSystem` is deferred while updating (same queue as pending adds). `getSystem<T>()` backed by a `KClass → System` map.
- `System.scene` becomes `lateinit` (access before init fails fast).

### 3.6 Scene / SceneManager
- Split `resolveSceneReferences` into `attach(scene)` (init components exactly once, build world index) and `resolveAssets(scene)`; log failures but do not skip `init`.
- Mark `scene.isDirty = true` from the world change events.
- Publish `ObjectAdded/Removed`, `ComponentAdded/Removed` on the existing `EventSystem` for the editor (the editor subscribes; engine never imports editor).

## 4. Migration plan (follow-up tasks)

| Step | Task | Content |
|---|---|---|
| 1 | ECS-02 | Private component list + mutation API, per-object index, `ComponentRegistry`, `ComponentWorld`, versioned `Query`, migrate the six systems, fix Q4 bugs, unit tests for invalidation |
| 2 | ECS-03 | Phases, dependency declarations, topological resolution, lifecycle fixes (O3, O4), tests |
| 3 | ECS-04 | Component gap analysis (missing `ComponentType` entries S4 included) |
| 4 | ECS-05 | Prefab links use ids from `IdGenerator` and `ComponentAdded` events for override tracking |

Cross-domain impact (report to the lead, no changes made here): `PhysicsSystem`, `AnimationSystem`, `PlayerMotionSystem`, `RagdollSystem` (PHY/ANI/GPL owners) only need mechanical adoption of the query API; `editor/systems/ProjectManager.kt:172` calls `resetSystemCaches()`; editor commands that mutate `components`/`children` directly must switch to the member API (EDT); `PhysicsSystem` constructs `BulletPhysics3D` directly, which should go through `Physics3DFactory` (PHY).

## 5. Assumptions
- Single-threaded access to the ECS (main thread). Parallel job access would need snapshots; out of scope.
- Backwards compatibility of serialized scenes: `components` and `children` property names remain unchanged.
