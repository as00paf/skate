# Agent Briefing (Project-Optimized)

This file is the execution contract for agents working on **SkateSim**.

## Required Read Order

1. `QWEN.md`
2. `docs/TODO.md`
3. Task-specific subsystem files

## Project Priorities

1. Deliver roadmap work in small, single-owner increments.
2. Preserve architecture quality while shipping features.
3. Keep editor behavior predictable and undo-safe.
4. Keep runtime behavior deterministic and testable.

## Non-Negotiable Engineering Rules

1. **Koin-first DI:** no manual singleton patterns for engine/editor services.
2. **Event-driven actions:** UI -> Event -> ActionHandler -> Command -> UndoRedoManager.
3. **Command pattern for state changes:** editor mutations must be command-driven.
4. **Edit vs Play boundary:** editor-only tools must not mutate runtime simulation.
5. **Localization required:** no hardcoded user-facing strings.
6. **Kotlin safety:** avoid `!!`; use explicit null-safe handling.

## Where to Change Code

- DI registration: `src/main/kotlin/com/pafoid/skate/app/KoinModule.kt`
- Engine lifecycle/core flow: `src/main/kotlin/com/pafoid/skate/engine/core/`
- ECS and systems: `src/main/kotlin/com/pafoid/skate/engine/ecs/`
- Editor UI/actions/commands: `src/main/kotlin/com/pafoid/skate/editor/`
- Events: `src/main/kotlin/com/pafoid/skate/engine/events/`
- Localization: `src/main/resources/values/strings*.properties`

## Execution Checklist

1. Pick one TODO item and keep scope tight.
2. Reuse existing patterns/helpers before introducing new abstractions.
3. Update related docs when behavior or workflow changes.
4. Validate compile + relevant tests before handoff.

## ARCH Program (Architecture Remediation)

Reference: `docs/ARCH_REMEDIATION_PLAN.md` (canonical ARCH contract/backlog).
Core contracts ADR: `docs/ADR-ARCH-002-core-contracts.md`.
QA Gate 1 report: `docs/ARCH-007-QA-GATE1.md`.

### Execution Policy

- Default policy: **single active ARCH task at a time**.
- Parallel work is allowed only for tasks explicitly marked parallel-safe in the remediation plan.

### Milestone Gates

- **M1 Stabilization:** through ARCH-007
- **M2 Undo/Async correctness:** through ARCH-012
- **M3 ECS + consolidation:** through ARCH-016 and ARCH-014
- **M4 DI/layer + localization + guardrails:** through ARCH-020
- **M5 Release readiness + closure:** ARCH-021 to ARCH-023

### Dependency / Order Summary

1. ARCH-001 -> ARCH-002
2. ARCH-003 + ARCH-004 -> ARCH-005 -> ARCH-007
3. ARCH-008 -> (ARCH-009 + ARCH-010) -> ARCH-011 -> ARCH-012
4. ARCH-020 -> ARCH-021 -> ARCH-022 -> ARCH-023
5. Additional prerequisite chain: ARCH-012 -> ARCH-013/015/016; ARCH-017 -> ARCH-018/019

### ARCH Task Status Registry (ARCH-001..ARCH-023)

| ID | Title | Owner | Status |
|---|---|---|---|
| ARCH-001 | Bootstrap ARCH tracking in docs | documentation-engineer | done |
| ARCH-002 | ADR pack for core contracts | tech-lead | done |
| ARCH-003 | UI conformance pass A | software-engineer | done |
| ARCH-004 | UI conformance pass B | software-engineer | done |
| ARCH-005 | Play-mode mutation gate integration | software-engineer | done |
| ARCH-006 | Quick consistency fixes | software-engineer | done |
| ARCH-007 | QA Gate 1 (UI + play boundary) | qa-engineer | blocked (compile passed; full test suite failing with 14 failures) |
| ARCH-008 | UndoRedo core refactor | software-engineer | done |
| ARCH-009 | Retrofit sync command semantics | software-engineer | done |
| ARCH-010 | Async command lifecycle hardening | software-engineer | done |
| ARCH-011 | QA Gate 2 (undo + async) | qa-engineer | done |
| ARCH-012 | Reviewer architecture gate | reviewer | done |
| ARCH-013 | ECS invalidation implementation | physics-engineer | done |
| ARCH-014 | QA Gate 3 (ECS invalidation) | qa-engineer | done |
| ARCH-015 | Duplicate-object flow consolidation | software-engineer | done |
| ARCH-016 | Scene traversal consolidation | software-engineer | done |
| ARCH-017 | DI/layering decision checkpoint | tech-lead | done |
| ARCH-018 | DI/layering implementation | software-engineer | done |
| ARCH-019 | Localization completion sweep | software-engineer | done |
| ARCH-020 | Guard tests + async test fixtures | qa-engineer | done |
| ARCH-021 | QA Gate 4 full checkpoint | qa-engineer | done |
| ARCH-022 | Final reviewer gate | reviewer | pending |
| ARCH-023 | Documentation closure | documentation-engineer | pending |

### ARCH Status Transition Log (M1 run 2026-05-12)

| ID | Owner | Transition |
|---|---|---|
| ARCH-002 | tech-lead | pending -> in_progress -> done |
| ARCH-003 | software-engineer | pending -> in_progress -> done |
| ARCH-004 | software-engineer | pending -> in_progress -> done |
| ARCH-005 | software-engineer | pending -> in_progress -> done |
| ARCH-006 | software-engineer | pending -> in_progress -> done |
| ARCH-007 | qa-engineer | pending -> in_progress -> blocked (compile passed; full test suite failed: 252 run, 14 failed) |
| ARCH-008 | software-engineer | pending -> in_progress -> done |
| ARCH-009 | software-engineer | pending -> in_progress -> done |
| ARCH-010 | software-engineer | pending -> in_progress -> blocked (pending implementation); blocked -> in_progress -> done |
| ARCH-011 | qa-engineer | pending -> in_progress -> blocked (depends on ARCH-010; ready for scoped QA execution); blocked -> in_progress -> done (2026-05-16: compile + targeted ARCH-011 tests passed) |
| ARCH-012 | reviewer | pending -> in_progress -> done (2026-05-16: reviewer gate passed; no blocking ARCH-008..011 architecture violations) |
| ARCH-013 | physics-engineer | pending -> in_progress -> done (2026-05-16: component-mutation versioning added; SystemManager cache invalidation extended to component composition changes; compile and SystemManagerInvalidationTest passed) |
| ARCH-014 | qa-engineer | pending -> in_progress -> blocked (2026-05-16: could not execute required compile/targeted-adjacent Gradle commands in current environment; existing SystemManagerInvalidationTest artifact indicates 4/4 pass from prior run); blocked -> in_progress -> done (2026-05-16: compile and targeted+adjacent ARCH-014 tests passed) |
| ARCH-015 | software-engineer | pending -> in_progress -> done (2026-05-16: duplicate-object entry points consolidated onto ViewportDuplicate -> ViewportActionHandler -> DuplicateGameObjectCommand; divergent EditorInputHandler clone path removed; targeted duplicate-flow tests added) |
| ARCH-016 | software-engineer | pending -> in_progress -> done (2026-05-17: scene graph traversal in serializer/project flows consolidated onto shared recursive utility; nested hierarchy traversal covered by SceneTraversalTest) |
| ARCH-017 | tech-lead | pending -> in_progress -> done (2026-05-17: DI/layering checkpoint approved; constructor-DI and engine/editor boundary policies finalized; ARCH-018 migration map + readiness checklist published in `docs/ARCH-017-DI-LAYERING-CHECKPOINT.md`) |
| ARCH-018 | software-engineer | pending -> in_progress -> done (2026-05-17: Scene physics creation moved to `Physics3DFactory` contract, `ViewportActionHandler` converted to constructor DI, `BulletPhysics3D` removed `KoinComponent` injection, and prefab payload boundary decoupled from UI-window package) |
| ARCH-019 | software-engineer | pending -> in_progress -> done (2026-05-17: localized remaining hardcoded UI strings in `GameViewWindow`, `ProjectWindow`, `InputSystem`, and `AudioSystem`; added required keys in `strings.properties` and `strings_fr.properties`) |
| ARCH-020 | qa-engineer | pending -> in_progress -> done (2026-05-17: added architecture guard tests for UI mutation pipeline and engine/editor layering, extracted shared async `ImmediateJobSystem` fixture for command tests, and added async stale-completion lifecycle regression coverage in `UndoRedoManagerTest`) |
| ARCH-021 | qa-engineer | pending -> in_progress -> blocked (2026-05-17: executed Gate 4 commands; `compileKotlin` passed and targeted ARCH regression tests passed, but full `test` failed with 3 failures in `EngineFixedTimestepTest` (2) and `EnvironmentSystemTest` (1); no-go for ARCH-022); blocked -> in_progress -> done (2026-05-17: fixed `EngineFixedTimestepTest` DI setup and `EnvironmentSystem.update()` component-creation path; reran targeted failing tests and full suite with all passing) |

## Build and Test Commands (WSL in this repo)

- `cmd.exe /c gradlew.bat compileKotlin --no-daemon`
- `cmd.exe /c gradlew.bat test --no-daemon`
- Targeted tests: `cmd.exe /c gradlew.bat test --tests "fully.qualified.TestClass"`

## Handoff Format (Required)

- Scope completed
- Files changed
- Behavioral outcome (expected vs actual)
- Test/build evidence summary
- Follow-up items (if any)

## Agent Role Mapping

- `software-engineer`: implementation and integration across editor/runtime systems
- `physics-engineer`: fixed-step simulation, body sync, collisions, physics correctness
- `qa-engineer`: reproduction design, edge-case validation, focused verification plans
- `documentation-engineer`: architecture and feature docs aligned with implemented behavior
- `tech-lead`: architecture decisions, system ordering, boundary enforcement
- `reviewer`: high-signal risk review before finalization
- `project-manager`: sequence roadmap tasks, assign one owner, ensure clean handoffs
