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

| ID | Title | Status | Why now |
|---|---|---|---|
| A46.0.1 | Engine UI & Editor Tooling Revamp (remaining scope) | In Progress | Core usability/workflow debt still impacts daily iteration speed. |
| A46.0.2 | Advanced Lighting Models | Planned | Next major rendering capability on Phase 2 path. |
| A46.0.3 | Post-Processing Stack | Planned | Depends on render foundations and follows lighting work. |
| A46.0.4 | Advanced Material System | Planned | Needed for scalable shading workflows and content authoring consistency. |

---

## Execution Plans

### A46.0.1 — Engine UI & Editor Tooling Revamp (remaining scope)

**Description**  
Finish remaining editor UX scope: diagnostics maturity, interaction polish, search/history workflows, and viewport tooling parity.

**Subtasks**
1. Complete diagnostics UX in Console and Profiler surfaces.
2. Finalize viewport toolbar ergonomics and discoverability.
3. Improve context menus and drag/drop workflows.
4. Improve global search and command/history discoverability.
5. Add targeted UI tests/guards for mutation pipeline compliance.

**Implementation notes**
- Enforce `UI -> Event -> Handler -> Command -> UndoRedoManager`.
- Avoid direct command execution in UI entrypoints.
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
