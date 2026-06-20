# 🛹 SkateSim Engine TODO — Project Lifecycle Priority Plan

Goal

Make project creation/open/close rock-solid: when a user creates a project it must contain all expected default assets,
be immediately usable, and be reliably openable/closable later. Scenes, GameObjects and Components must round-trip
through serialization unchanged. This plan focuses exclusively on that lifecycle.

Principles

- Small, testable steps that increase confidence (unit + smoke tests).
- Explicit readiness signals: asset DB and bundled asset copy must be awaitable.
- Clear ownership: ProjectManager owns project lifecycle; SceneManager owns scene lifecycle.
- Keep editor-only code out of runtime engine paths.

Roadmap (JIRA-style tasks, sequential)

P1 — Project format & locations (owner: software-engineer) — 0.25d

- Description: Define canonical project layout and file formats (project metadata file, Assets/, Scenes/, Builds/).
  Document exact locations and file naming conventions.
- Deliverable: project schema doc (docs/PROJECT-FORMAT.md) and code constants for paths.
- Acceptance: ProjectManager and ProjectWizard use the same constants; no string literals elsewhere for core locations.

P2 — Asset DB readiness & bundled assets provisioning (owner: software-engineer) — 0.75d

- Description: Ensure AssetDatabase.initialize(projectDir) returns an awaitable readiness (Job/Future) and that
  bundled-engine assets are copied in a cancellable, idempotent way.
- Subtasks:
    * Make initialize return readiness future/status.
    * engineAssetCopier.copyBundledAssets returns count and completes before readiness is signaled.
- Acceptance: createProject waits on asset DB readiness before proceeding to GUID resolution or prefab spawning.

P3 — CreateProject orchestration (owner: software-engineer) — 1.0d

- Description: Implement a clear, suspendable createProject flow in ProjectManager that:
    1) creates directory structure, 2) initializes asset DB (await), 3) copies bundled assets (await), 4) sets up engine
       default roots for prefabs, 5) creates default scenes via SceneManager API, 6) saves created scene(s) to disk
       using Serializer.
- Acceptance: New project creation is deterministic: after createProject returns, default scene files exist on disk,
  prefab references resolve, and the editor can open the project without additional downloads or scans.

P4 — Default Scene + Prefab generation (owner: software-engineer) — 0.5d

- Description: Move default scene generation into a single, testable routine that uses PrefabsGenerator to spawn
  defaults into a Scene obtained from SceneManager.createScene(suspend). Do not access GameObjectManager before
  SceneManager has registered systems.
- Acceptance: Default scene is created entirely via SceneManager + PrefabsGenerator calls; scene saved via Serializer
  and loads back identically.

P5 — Scene serialization and consolidation (owner: software-engineer) — 0.75d

- Description: Consolidate serialization responsibilities: deprecate redundant SceneSerializer usage if Serializer
  covers all needs. Ensure polymorphic registration separation (Components vs Assets) and stable GUID-to-path resolution
  during load.
- Subtasks:
    * Add round-trip tests for Scene -> JSON -> Scene.
    * Ensure Serializer.resolveReferences (or equivalent) runs after asset DB readiness.
- Acceptance: Scenes and components serialize/deserialize with identical logical state (transform, component data) and
  assets resolve to the same GUIDs.

P6 — OpenProject flow & project close (owner: software-engineer) — 0.75d

- Description: Implement openProject that:
    * reads project metadata, 2) initializes asset DB (await), 3) registers engine/editor mappings, 4) loads
      default/opened scenes via SceneManager, and 5) restores editor state. Implement closeProject to save dirty scenes,
      unregister systems, and release resources.
- Acceptance: Opening a freshly created project results in the same runtime/editor state as immediately after
  createProject. Closing then re-opening preserves scenes and assets.

P7 — Prefab & asset mapping tests (owner: qa-engineer) — 0.5d

- Description: Add tests verifying prefab GUID resolution, asset path mapping, and behavior when assets are missing (
  graceful errors/logging).
- Acceptance: Tests cover happy path and missing-asset path and pass locally.

P8 — Backwards compatibility & migration (owner: software-engineer) — 0.5d

- Description: Provide a migration path for older projects or scenes (if serialization schema changed). Implement
  migration helpers that convert legacy fields into the current project/scene layout during openProject.
- Acceptance: A documented migration step that runs automatically on open and logs what was changed.

P9 — Tests & smoke verification (owner: qa-engineer) — 1.0d

- Description: Automated tests and local smoke verification for create/open/close, serialization round-trips, and
  asset-provisioning idempotency.
- Acceptance: Targeted tests run and pass locally on Windows; PR checklist includes smoke test steps.

P10 — Docs & acceptance (owner: documentation-engineer) — 0.25d

- Description: Update docs/TODO.md (this file), docs/PROJECT-FORMAT.md, and docs/ARCHITECTURE-AUDIT.md with references
  to the changes and instructions for QA verification.
- Acceptance: Docs updated and cross-referenced from PR descriptions.

Acceptance criteria (overall)

- createProject reliably produces a usable project folder with Assets/ and Scenes/ and default scenes that serialize and
  deserialize identically.
- openProject waits for asset DB readiness and loads scenes whose assets resolve.
- closeProject saves state and releases resources cleanly.
- Tests validate serialization round-trips and asset mapping.

Workflow and approval

- Each P* task is a single PR. Keep commits small and include a smoke test checklist in PR body.
- Review and approve one P* at a time. After each PR, update docs/ARCHITECTURE-AUDIT.md with findings.

Approval loop

- Review this TODO.md and reply with edits or Approve. Once approved, indicate which task to start (P1..P10). The
  assistant will then run the focused audits/tests and prepare a minimal-change PR for your review when requested.

