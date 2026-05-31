# 🛹 Skate Engine Roadmap (Future Backlog)

## Purpose

This roadmap is the full **future development backlog** for SkateSim. It tracks planned milestones, sequencing, and scope at planning level.

### Boundary

- **Roadmap (`docs/roadmap.md`)** = all planned future development (backlog + sequencing).
- **TODO (`docs/TODO.md`)** = near-term execution plans with subtasks and implementation approach.
- **Changelog (`docs/CHANGELOG.md`)** = completed work history.

## Status Legend

- `Planned` — scoped backlog item, not started
- `In Progress` — currently being implemented
- `Blocked` — cannot proceed until dependency/decision is resolved

## Phase Objectives

| Phase | Objective | Exit Criteria |
|---|---|---|
| Phase 2 — Core Systems | Complete core editor/runtime systems required before gameplay-depth and polish. | Tooling, rendering, and runtime foundations are feature-complete and integrated. |
| Phase 3 — Polish & Tooling | Deliver gameplay depth, performance hardening, scripting, and production workflows. | Core gameplay loop, optimization, scripting, docs, and multiplayer foundations are in place. |

## Backlog (All Planned Future Development)

| ID      | Title                                            | Phase | Priority | Dependencies                                  | Status      | Planned Outcome                                                                                                                                                                                       |
|---------|--------------------------------------------------|-------|----------|-----------------------------------------------|-------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| A48.0.2 | Editor/Engine Separation Refactor (Main.kt-down) | 2     | Critical | A48.0.1, tech-lead architecture approval gate | Blocked     | Re-establish hard engine/editor boundaries, starting explicitly with `Main.kt`, `Engine.kt`, `Window.kt`, `ImGuiLayer.kt`, `EditorWorkspace.kt`, then widen to package-level cleanup and guard tests. |
| A46.0.1 | Comprehensive Engine UI & Editor Tooling Revamp  | 2     | High     | Render Graph baseline                         | In Progress | All 4 subtasks complete. Pending user review before closing. |
| A46.0.2 | Implement Advanced Lighting Models               | 2     | High     | Render Graph baseline                         | Planned     | Add point/spot lighting and environment-based lighting support.                                                                                                                                       |
| A46.0.3 | Develop Post-Processing Stack                    | 2     | High     | Render Graph baseline                         | Planned     | Enable effect pipeline (bloom, depth of field, color grading/tone mapping).                                                                                                                           |
| A46.0.4 | Create Advanced Material System                  | 2     | High     | Render Graph baseline                         | Planned     | Introduce extensible shader-driven material workflow (PBR-oriented).                                                                                                                                  |
| A46.0.5 | Implement In-Game UI System                      | 2     | High     | None                                          | Planned     | Add runtime UI framework for HUD/menus separate from editor ImGui.                                                                                                                                    |
| A46.0.6 | Develop VFX/Particle System                      | 2     | High     | None                                          | Planned     | Add emitter-driven particle/VFX pipeline for gameplay and environment effects.                                                                                                                        |
| A46.0.7 | Enhance Animation System (Retargeting)           | 2     | Medium   | None                                          | Planned     | Support retargeting across skeletons and richer animation reuse.                                                                                                                                      |
| A46.0.8 | Implement Advanced Physics Constraints           | 2     | Medium   | None                                          | Planned     | Add additional physics joint/constraint capabilities for complex interactions.                                                                                                                        |
| A47.0.1 | Develop Skateboarding Physics Mechanics          | 3     | High     | A46.0.8                                       | Planned     | Deliver gameplay-grade skateboard movement and interaction mechanics.                                                                                                                                 |
| A47.0.2 | Implement Character Controller & State Machine   | 3     | High     | A46.0.7                                       | Planned     | Integrate character control/state logic with animation + physics systems.                                                                                                                             |
| A47.0.3 | Optimize Rendering Performance                   | 3     | Medium   | A46.0.2, A46.0.3                              | Planned     | Improve frame stability and throughput under advanced rendering load.                                                                                                                                 |
| A47.0.4 | Optimize Physics Performance                     | 3     | Medium   | A46.0.8                                       | Planned     | Improve simulation performance and stability under gameplay complexity.                                                                                                                               |
| A47.0.5 | Integrate Scripting Language (TypeScript)        | 3     | High     | Render Graph baseline                         | Planned     | Add script runtime and engine API boundary for gameplay scripting.                                                                                                                                    |
| A47.0.6 | Develop Sample Skate Game Project                | 3     | Medium   | A47.0.5                                       | Planned     | Build sample project that validates core engine workflows end-to-end.                                                                                                                                 |
| A47.0.7 | Refine Editor Workflow & UX                      | 3     | Low      | A46.0.1                                       | Planned     | Polish usability, discoverability, and workflow efficiency in editor tooling.                                                                                                                         |
| A47.0.8 | Comprehensive Documentation & Tutorials          | 3     | Low      | All previous milestones                       | Planned     | Publish practical onboarding and feature tutorials aligned with shipped systems.                                                                                                                      |
| A47.0.9 | Integrate Networking for Multiplayer             | 3     | Medium   | A47.0.5                                       | Planned     | Establish multiplayer/networking foundation and initial synchronization model.                                                                                                                        |
| A48.0.1 | Comprehensive Feature & Code Audit               | 3     | High     | None                                          | In Progress | QA pass complete (2026-05-31). All findings resolved or verified except AUD-002, which is deferred to A48.0.2. Functionally complete. |

## Near-Term Focus (next backlog wave)

1. A48.0.2 — Editor/Engine Separation Refactor (approval-gated before implementation)
2. A48.0.1 — Comprehensive Feature & Code Audit
3. A46.0.1 — Editor tooling revamp remaining scope
4. A46.0.2 — Advanced Lighting Models
5. A46.0.3 — Post-Processing Stack
6. A46.0.4 — Advanced Material System

## Sequencing Risks

- Boundary refactor (`A48.0.2`) is a prerequisite quality gate for safe progress on editor/runtime features; delaying it
  increases regression risk in all Phase 2 items.
- Render-stack items (`A46.0.2`/`A46.0.3`/`A46.0.4`) may serialize if shared render abstractions grow unexpectedly.
- Phase 3 gameplay depth depends on completion quality of animation/physics foundations (`A46.0.7`/`A46.0.8`).
- Optimization milestones (`A47.0.3`/`A47.0.4`) are gated by feature maturity and representative workload availability.

## References

- `docs/TODO.md`
- `docs/CHANGELOG.md`
- `docs/DEVELOPMENT_GUARDRAILS.md`
- `docs/ECS_ARCHITECTURE.md`
- `docs/DOCS_INDEX.md`
