# 🛹 SkateSim Engine TODO (Near-Term Execution Plan)

## Scope

This TODO contains the single next, actionable task: simplify and reduce the project/scene loading code. Other backlog
items are tracked in docs/roadmap.md.

## A48.0.3 — Simplify Project & Scene Loading (Next Task)

Status: Planned
Owner: software-engineer

Description
Keep orchestration simple: ProjectManager shall own project lifecycle (create/open/close) and SceneManager shall own
scene lifecycle (create/open/close/start). Avoid adding new abstraction layers. Reduce code, remove synchronous
blocking, eliminate duplicated responsibilities, and make flows straightforward to read, test, and maintain.

Audit summary (key findings)

- runBlocking found in ProjectManager.createDefaultScene when bootstrapping a new scene.
- ProjectManager.loadDefaultScene calls systemManager.loadScene before and after deserialization (duplicate).
- SceneSerializer.loadFromFile is used by ProjectManager and OpenSceneFileCommand; it performs IO + resource resolution
  and relies on GameObjectManager (via SystemManager).
- AssetDatabase init/scan happens in ProjectManager and can block or run off the UI thread; lifecycle tokens used but
  sometimes duplicated.

Subtasks (minimal, ordered)

1) Audit & quick fixes (0.5d) — done: located the above call sites and confirmed usage patterns.
2) Eliminate runBlocking (0.5d) — replace runBlocking usage in ProjectManager with jobSystem.runIO or equivalent async
   scheduling; ensure errors are logged and surfaced.
3) Consolidate default scene creation (1.0d) — ensure ProjectManager uses SceneManager.createScene (suspend) to produce
   a Scene, spawn prefabs into that Scene via PrefabsGenerator, resolve asset GUIDs, then call
   SceneSerializer.saveToFile. No separate bootstrap service.
4) Deduplicate scene-system binding (0.5d) — remove redundant systemManager.loadScene calls; define clear call-site:
   caller (ProjectManager) must call systemManager.loadScene(scene) once before deserialization if serializer needs
   systems, or after if serializer only mutates scene data.
5) Make asset DB init/scan asynchronous and cancellable (1.0d) — keep jobSystem-based scanning with lifecycle epoch
   checks; ensure ProjectManager remains orchestrator and exposes progress/failure events.
6) Simplify SceneSerializer responsibilities (1.0d) — keep it as IO + resource resolution for components; it should NOT
   start/stop scenes or register systems. Maintain current boolean return for success/failure and provide clearer
   logging/exception paths.
7) Tests & docs (1.0d) — add focused unit tests for ProjectManager.createProject/openProject/closeProject happy and
   error paths; document the simplified flow in docs/TODO.md and update CHANGELOG.md.

Acceptance criteria

- No runBlocking calls remain in ProjectManager.
- ProjectManager orchestrates project and default scene creation using SceneManager APIs; no separate loader service
  added.
- SceneManager owns scene creation/open/close/start responsibilities.
- SceneSerializer is IO-only and does not manipulate SystemManager lifecycle; ProjectManager invokes
  systemManager.loadScene exactly where needed.
- startKoin with editorModule compiles and runtime behavior unchanged for happy paths.

Estimate: ~5 workdays (including tests and docs).

Deliverables

- Updated ProjectManager with async asset initialization and simplified default scene creation flow.
- Cleaned SceneManager usage with single, well-documented entry points for scene creation/opening.
- SceneSerializer simplified to IO/resource-resolution only with clearer logging.
- Unit tests for ProjectManager scene lifecycle paths and documentation updates.

## A48.0.4 — Extract Scene Physics into ScenePhysicsComponent (Next Task)

Status: Planned
Owner: physics-engineer / software-engineer

Description

Remove runtime physics state from Scene and place it into a serializable ScenePhysicsComponent that the PhysicsSystem
owns and updates. This keeps runtime native handles transient, simplifies Scene serialization, and follows ECS
principles: scene-level systems driven by components.

Subtasks (minimal, ordered)

1) Audit & discovery (0.5d) — find all references to Scene physics runtime fields and document call sites.
2) Create ScenePhysicsComponent (1.0d) — add a serializable config (gravity, fixedTimestep, enabled) and @Transient
   runtime fields (native world handle, accumulator).
3) Remove runtime physics from Scene (0.5d) — migrate persisted settings into the component config and add compatibility
   accessors on Scene.
4) Update Physics3DFactory & PhysicsSystem (1.0d) — provide create/destroy APIs for per-scene physics worlds; ensure
   PhysicsSystem initializes/destroys and steps worlds using the component.
5) Update SceneManager lifecycle & rehydration (0.5d) — ensure systems bound and ScenePhysicsComponent attached before
   postDeserialize; migrate legacy saved settings during load.
6) Migrate callers (0.5d) — replace direct Scene.physics usages with scene.getComponent<ScenePhysicsComponent>() and
   update callers (PrefabsGenerator, ProjectManager, tests, commands).
7) Tests & smoke verification (0.5d) — manual play-mode smoke test and minimal unit tests for component lifecycle.
8) Cleanup & docs (0.25d) — remove legacy fields and update docs/TODO.md and CHANGELOG.md.

Acceptance criteria

- Scene no longer contains native physics runtime fields; those are only in ScenePhysicsComponent as @Transient.
- PhysicsSystem creates, steps, and destroys per-scene physics worlds using the component.
- SceneManager coordinates system binding and component attachment before rehydration.
- Existing saved scenes are loadable via a compatibility conversion that migrates persisted physics settings into the
  new component config.

Estimate: ~3.75–5.25 days (iterative, includes migration and verification).
