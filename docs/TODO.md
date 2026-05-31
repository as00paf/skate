# 🛹 SkateSim Engine TODO (Near-Term Execution Plan)

## Scope

This file contains **near-future tasks** that are ready for execution and already have a concrete plan (description, subtasks, and implementation approach).

> Roadmap boundary: `docs/roadmap.md` tracks the full future backlog (what/when).  
> TODO boundary: `docs/TODO.md` tracks near-term execution plans (how).

## Status Legend

- `Planned`
- `In Progress`
- `Blocked`

## Near-Term Queue

| ID      | Title                                               | Status      | Why now                                                                                                            |
|---------|-----------------------------------------------------|-------------|--------------------------------------------------------------------------------------------------------------------|
| A48.0.2 | Editor/Engine Separation Refactor (Main.kt-down)    | In Progress | Critical layering and lifecycle coupling violations block safe feature work and invalidate architecture contracts. |
| A48.0.1 | Comprehensive Feature & Code Audit                  | In Progress | AUD-002 remains deferred pending A48.0.2; all other findings resolved or verified.                                |
| A46.0.1 | Engine UI & Editor Tooling Revamp (remaining scope) | ✅ Closed   | All subtasks complete and reviewed.                                                                               |
| A46.0.2 | Advanced Lighting Models                            | Planned     | Next major rendering capability on Phase 2 path.                                                                   |
| A46.0.3 | Post-Processing Stack                               | Planned     | Depends on render foundations and follows lighting work.                                                           |
| A46.0.4 | Advanced Material System                            | Planned     | Needed for scalable shading workflows and content authoring consistency.                                           |

---

## Execution Plans

### A48.0.2 — Editor/Engine Separation Refactor (Main.kt-down)

**Description**  
Plan and execute a phased refactor that restores engine/editor boundaries from application entry (`Main.kt`) through
engine lifecycle, DI composition, ECS/systems/components, and UI mutation flow.

**Approval gate (mandatory before implementation)**

- This initiative remains `Blocked` until the user reviews and explicitly approves the phased plan and sequencing below.

**Phased implementation plan**

| Phase | Scope                                                                                                | Dependencies     | Owner                  | Acceptance criteria                                                                                                                                                             |
|-------|------------------------------------------------------------------------------------------------------|------------------|------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| P0    | Baseline + architecture decisions (violation inventory, boundary target map, migration order)        | A48.0.1 findings | tech-lead              | Approved boundary map exists for `Main.kt -> app modules -> engine/core -> engine/render -> engine/ecs`; decision log resolves startup mode strategy and adapter boundaries.    |
| P1    | Bootstrap/runtime split at entrypoint and engine loop                                                | P0               | software-engineer      | Engine runtime starts without direct editor construct wiring (`EditorWorkspace`, `ImGuiLayer`) in runtime lifecycle path; startup mode selection moved to composition boundary. |
| P1a   | **Kickoff file tranche**: `Main.kt`, `Engine.kt`, `Window.kt`, `ImGuiLayer.kt`, `EditorWorkspace.kt` | P1               | software-engineer      | Initial decoupling lands first in these five files, with boundary ownership made explicit before widening to package-level cleanup.                                             |
| P2    | DI decomposition (`KoinModule` split into runtime/editor composition roots)                          | P1               | software-engineer      | Monolithic module replaced by separated runtime/editor modules; engine module graph resolves without importing editor packages.                                                 |
| P3    | Engine package decontamination (`engine/core`, `engine/render`, `engine/ecs`)                        | P2               | software-engineer      | No direct `editor/**` imports remain in targeted engine packages; editor-dependent behavior consumed via engine-owned interfaces/adapters.                                      |
| P4    | ECS/editor concern extraction + mutation pipeline closure                                            | P3               | software-engineer      | ECS components/systems no longer contain editor ImGui/string/settings logic; remaining direct state mutations in windows/handlers replaced by `Event -> Handler -> Command`.    |
| P5    | Guardrail verification hardening                                                                     | P4               | qa-engineer            | Layering and mutation guard tests catch known violation classes (imports, lifecycle coupling, direct mutation paths); CI gate definition updated to require new tests.          |
| P6    | Documentation reconciliation + closure report                                                        | P5               | documentation-engineer | `ECS_ARCHITECTURE`, guardrails, roadmap, and TODO reflect actual status and closure evidence; no document claims boundary cleanliness before verification sign-off.             |

**Execution tasks (single-agent, dependency-ordered)**

1. Author approved boundary target map and migration sequence (`P0`) — **Owner: tech-lead**.
2. Define runtime/editor bootstrap contract from `Main.kt` downward (`P1`) — **Owner: tech-lead**.
3. Implement bootstrap split and lifecycle decoupling (`P1`) — **Owner: software-engineer**.
4. Execute kickoff tranche in `Main.kt`, `Engine.kt`, `Window.kt`, `ImGuiLayer.kt`, `EditorWorkspace.kt` (`P1a`) — *
   *Owner: software-engineer**.
5. Split Koin composition roots and dependency graph checks (`P2`) — **Owner: software-engineer**.
6. Remove editor imports from engine core/render/ecs using adapter interfaces (`P3`) — **Owner: software-engineer**.
7. Extract editor-only ECS logic and complete mutation pipeline refactors (`P4`) — **Owner: software-engineer**.
8. Expand layering/mutation guardrail tests and add regression fixtures (`P5`) — **Owner: qa-engineer**.
9. Publish closure docs and status synchronization across planning/architecture docs (`P6`) — **Owner:
   documentation-engineer**.

**P0 output (completed 2026-05-29)**

- Boundary target map, violation inventory, and migration sequence: `docs/ADR-ARCH-003-engine-editor-boundary.md`
- **Startup mode decision:** Single binary with `--editor` mode flag. Runtime-only is the default; editor
  composition (`appModule`, `EditorScreen`, `ImGuiLayer`) is only loaded when `--editor` is passed to `main()`.
  See ADR-ARCH-003 §3 for full rationale.
- 15 concrete violations inventoried (V-01 – V-15): 4 Critical, 8 High, 3 misclassification.
- P1a kickoff tranche defined: `Main.kt` (flag + guard), `Engine.kt` (no changes needed), `Window.kt` (no
  changes needed), `ImGuiLayer.kt` (no structural changes needed), `EditorScreen.kt` (no structural changes
  needed — guarded by `Main.kt` change). Full detail in ADR-ARCH-003 §5.
- P0 approval gate is cleared. P1 may proceed.
- **P1 complete (2026-05-31):** `Main.kt` guarded — `EditorScreen` only instantiated when `--editor` flag passed. `runtimeAdapterModule` extracted from `engineModule` in `KoinModule.kt`; three contract bindings (`EngineLogger`, `IStringManager`, `InputMappingsProvider`) no longer couple `engineModule` to `appModule`. `Engine.kt` KDoc added. Acceptance criteria met.
- **Next: P2** — Full `KoinModule` split; `engineModule` must resolve standalone without `appModule`.

**Known blockers/risks**

- ~~Startup mode ownership decision~~ — resolved: single binary with `--editor` flag (ADR-ARCH-003 §3).
- DI split (P2) may expose hidden circular dependencies between editor windows and engine services.
- Guard tests must be broadened before claiming contract compliance.

---

### A48.0.1 — Comprehensive Feature & Code Audit

**Description**  
Audit implemented features and associated code paths to identify regressions, contract violations, and maintainability risks before further feature expansion.

**Subtasks**
1. Build feature inventory and owner mapping across editor/runtime systems.
2. Execute critical-path QA verification first (project/scene lifecycle, undo/redo, filesystem, screenshot).
3. Run reviewer high-signal code audit on architecture/logic risks.
4. Record findings with severity, owner, repro confidence, and evidence.
5. Define remediation backlog and re-verification gates.

**Current phase status**
- Pass 1 completed: critical-path audit produced initial findings.
- Full audit completed: expanded QA + reviewer findings consolidated and prioritized.
- Remediation complete: all 16 original findings (AUD-001 to AUD-016) implemented.
- QA pass completed 2026-05-31. AUD-001, AUD-003, AUD-006 verified. AUD-004, AUD-017 resolved. AUD-002 deferred.
- Gate: **Functionally complete.** Only AUD-002 remains open — deferred to A48.0.2 by design.

**Deferred to A48.0.2**

| ID | Title | Severity | Decision |
|---|---|---|---|
| AUD-002 | Scene open from ProjectWindow not working end-to-end | Critical | Deferred — root cause requires architectural work; expected to resolve during A48.0.2 |

**Resolved (2026-05-30)**
- ~~AUD-004~~: Filesystem command errors now surface via `FileSystemOperationFailed` event → `ProjectWindow` inline error display.
- ~~AUD-017~~: `DeleteFileCommand` now uses system temp dir; no `.trash_*` artifacts in project folder.

**Verified (QA confirmed 2026-05-30)**
- ~~AUD-001~~: Screenshot trigger wired and working end-to-end.
- ~~AUD-003~~: `DeleteFileCommand` null-safe; no crash on delete.
- ~~AUD-006~~: Screenshot filenames unique under rapid capture.

**Deferred**
- AUD-005: Undo of directory creation safety — low priority, moved to backlog.

**Tracking artifacts**
- `docs/FEATURE_AND_CODE_AUDIT_PLAN.md`
- `docs/AUDIT_PASS1_REPORT.md`
- `docs/AUDIT_FULL_REPORT.md`
- `docs/REGRESSION_LOG.md`

---

### A46.0.1 — Engine UI & Editor Tooling Revamp (remaining scope)

**Description**  
Finish remaining editor UX scope: diagnostics maturity, interaction polish, search/history workflows, and viewport tooling parity.

**Subtasks**
1. ~~Complete diagnostics UX in Console and Profiler surfaces.~~ ✅ Done
2. ~~Finalize viewport toolbar ergonomics and discoverability.~~ ✅ Done
3. ~~Improve context menus and drag/drop workflows.~~ ✅ Done
4. ~~Improve global search and command/history discoverability.~~ ✅ Done

**Implementation notes**
- Enforce `UI -> Event -> Handler -> Command -> UndoRedoManager`.
- Avoid direct command execution in UI entrypoints.
- Do not add UI tests — test event handlers and commands instead.
- Keep changes incremental and command-safe.

### A46.0.2 — Implement Advanced Lighting Models

**Description**  
Add support for additional light models and environment lighting to improve scene realism and authoring flexibility.

**Subtasks**
1. Add data model/runtime support for point and spot lights.
2. Integrate light evaluation path into renderer/shader pipeline.
3. Add editor controls for light authoring and validation.
4. Add targeted rendering tests/fixtures for correctness regression checks.

**Implementation notes**
- Keep render path modular to reduce coupling with post-processing/material milestones.

---

### A46.0.3 — Develop Post-Processing Stack

**Description**  
Introduce chained post-processing effects with configurable passes and runtime/editor toggles.

**Subtasks**
1. Define pass graph and configuration model for post-processing.
2. Implement first-pass effects (bloom, tone mapping; depth-of-field where feasible).
3. Add editor controls for enabling and tuning effects.
4. Add targeted validation for pass ordering and performance constraints.

**Implementation notes**
- Sequence after/alongside lighting work to avoid duplicated render integration effort.

---

### A46.0.4 — Create Advanced Material System

**Description**  
Build a scalable material authoring/runtime model for modern shading workflows and long-term renderer extensibility.

**Subtasks**
1. Define material data model (core properties, texture slots, defaults).
2. Implement material runtime binding path and renderer integration.
3. Add editor-side material inspection/editing controls.
4. Add validation coverage for default/fallback and shader/material compatibility.

**Implementation notes**
- Keep model compatible with planned lighting/post-processing expansions.
- Reuse existing resource/asset loading patterns to avoid duplicated pipelines.

---

## Deferred Backlog (tracked in roadmap only)

All remaining milestones beyond this near-term execution set stay in `docs/roadmap.md` until they are ready for concrete breakdown.

## References

- `docs/roadmap.md`
- `docs/CHANGELOG.md`
- `docs/DEVELOPMENT_GUARDRAILS.md`
- `docs/ECS_ARCHITECTURE.md`
