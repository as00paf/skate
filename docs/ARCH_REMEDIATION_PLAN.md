# Architecture Remediation Plan (ARCH Program)

## Purpose

This plan operationalizes `docs/CODEBASE_ASSESSMENT_REPORT.md` into an execution-ready, agent-assigned program.

## Program Goals

1. Restore consistent architecture patterns after refactor drift.
2. Eliminate high-risk behavioral inconsistency across UI/editor/runtime flows.
3. Add guardrails so drift does not recur.

## ADR References

- `ARCH-002`: `docs/ADR-ARCH-002-core-contracts.md`

---

## Canonical Architecture Contracts (Must Hold)

1. **Mutation pipeline:** `UI -> Event -> Handler -> CommandExecutor -> UndoRedoManager`
2. **Play-mode policy:** editor mutations are blocked while runtime play is active (except explicit allowlisted runtime controls).
3. **Command taxonomy:**
   - `UndoableCommand` (history-tracked)
   - `ExecuteOnlyCommand` (not in undo history)
   - `AsyncCommand` (history only after successful completion)
4. **ECS invalidation:** cache invalidation responds to both object-set changes and component-composition changes.
5. **Layering:** `engine/**` must not depend on `editor/**`.
6. **Localization:** no hardcoded user-facing strings in UI paths.

---

## Ordered Workstreams

| Order | Workstream | Owner lead | Outcome |
|---|---|---|---|
| WS0 | Program bootstrap + guard scaffolding | documentation-engineer + qa-engineer | Backlog live in docs, baseline architecture tests in place |
| WS1 | Command contract + UndoRedo core | software-engineer | Canonical undo/execute-only/async semantics implemented |
| WS2 | Edit/Play mutation gate | software-engineer | Centralized mutation gate enforced |
| WS3 | UI event-pipeline convergence | software-engineer | UI mutation entrypoints become event-only |
| WS4 | ECS component invalidation model | physics-engineer | Component add/remove invalidates caches deterministically |
| WS5 | DI/layering decoupling | tech-lead + software-engineer | Engine/editor boundary and DI consistency restored |
| WS6 | Duplication consolidation | software-engineer | Unified duplicate-object and scene traversal behavior |
| WS7 | Localization + consistency hardening | software-engineer | Remaining literals removed; consistency defects closed |
| WS8 | Hardening, gates, closure docs | qa-engineer + reviewer + documentation-engineer | Full regression gates pass; docs synchronized |

---

## Task Backlog (ARCH-001+)

| ID | Title | Owner | Depends on | Scope (key files) | Acceptance criteria |
|---|---|---|---|---|---|
| ARCH-001 | Bootstrap ARCH tracking in docs | documentation-engineer | none | `docs/TODO.md`, `docs/roadmap.md`, `docs/AGENT_BRIEFING.md` | ARCH tasks/status/dependencies visible and consistent |
| ARCH-002 | ADR pack for core contracts | tech-lead | ARCH-001 | `docs/` ADR/update files | Command model, mutation gate, invalidation model, layering rules explicitly defined |
| ARCH-003 | UI conformance pass A | software-engineer | ARCH-002 | `PropertiesWindow.kt`, `SceneHierarchyWindow.kt` | No direct mutation/direct command execution from these windows |
| ARCH-004 | UI conformance pass B | software-engineer | ARCH-002 | `EditMenuBuilder.kt`, `ActionSearchProvider.kt`, `ViewportToolbar.kt` | Event-only publishing in these entrypoints |
| ARCH-005 | Play-mode mutation gate integration | software-engineer | ARCH-003, ARCH-004 | handlers + command executor surfaces | Mutating editor actions blocked in play mode |
| ARCH-006 | Quick consistency fixes | software-engineer | ARCH-002 | `SystemsWindow.kt`, `MouseListener.kt` | Systems sections correct; scroll capture behavior unified |
| ARCH-007 | QA Gate 1 (UI + play boundary) | qa-engineer | ARCH-005, ARCH-006 | targeted tests + smoke matrix | P0 flow/boundary scenarios pass |
| ARCH-008 | UndoRedo core refactor | software-engineer | ARCH-007 | `Command.kt`, `UndoRedoManager.kt` | Undoable/execute-only/async command handling implemented |
| ARCH-009 | Retrofit sync command semantics | software-engineer | ARCH-008 | selected commands with no-op undo | Non-undoable commands no longer pollute history |
| ARCH-010 | Async command lifecycle hardening | software-engineer | ARCH-008 | `CreateSceneCommand.kt`, `OpenSceneCommand.kt` + manager | Async ops enter history only on success |
| ARCH-011 | QA Gate 2 (undo + async) | qa-engineer | ARCH-009, ARCH-010 | stack/lifecycle scenario tests | History correctness validated under success/failure timing |
| ARCH-012 | Reviewer architecture gate | reviewer | ARCH-011 | all diffs ARCH-003..010 | No unresolved high-risk contract violations |
| ARCH-013 | ECS invalidation implementation | physics-engineer | ARCH-012 | `GameObject.kt`, `Scene.kt`, `SystemManager.kt`, affected systems | Component mutations reliably invalidate caches |
| ARCH-014 | QA Gate 3 (ECS invalidation) | qa-engineer | ARCH-013 | ECS mutation tests | No stale-cache behavior in covered systems |
| ARCH-015 | Duplicate-object flow consolidation | software-engineer | ARCH-012 | duplicate flow callsites/services | Duplicate behavior unified across entrypoints |
| ARCH-016 | Scene traversal consolidation | software-engineer | ARCH-012 | `SceneSerializer.kt`, `ProjectManager.kt` | Shared recursive traversal utility adopted |
| ARCH-017 | DI/layering decision checkpoint | tech-lead | ARCH-014, ARCH-016 | `docs/` + migration map | Final DI + boundary migration decisions approved |
| ARCH-018 | DI/layering implementation | software-engineer | ARCH-017 | `Scene.kt`, `BulletPhysics3D.kt`, `ViewportAction.kt`, handlers, `KoinModule.kt` | Engine/editor decoupling and constructor DI conformance improved |
| ARCH-019 | Localization completion sweep | software-engineer | ARCH-017 | `GameViewWindow.kt`, `ProjectWindow.kt`, `AudioSystem.kt`, `InputSystem.kt`, `strings*.properties` | No hardcoded UI strings in scoped modules |
| ARCH-020 | Guard tests + async test fixtures | qa-engineer | ARCH-018, ARCH-019 | `src/test/kotlin/**` | Architecture regression checks prevent recurrence |
| ARCH-021 | QA Gate 4 full checkpoint | qa-engineer | ARCH-020 | full regression pass | No P0/P1 regressions; P2 documented |
| ARCH-022 | Final reviewer gate | reviewer | ARCH-021 | full program diff | Go/no-go with no high-risk unresolved items |
| ARCH-023 | Documentation closure | documentation-engineer | ARCH-022 | `docs/AGENT_BRIEFING.md`, `docs/TODO.md`, `docs/roadmap.md`, `docs/ECS_ARCHITECTURE.md`, this file + assessment addendum | Docs reflect implemented architecture and remaining follow-ups |

---

## Parallelization Map

### Sequential (must not overlap)

1. ARCH-001 -> ARCH-002
2. ARCH-003 + ARCH-004 -> ARCH-005 -> ARCH-007
3. ARCH-008 -> (ARCH-009 + ARCH-010) -> ARCH-011 -> ARCH-012
4. ARCH-020 -> ARCH-021 -> ARCH-022 -> ARCH-023

### Parallel-safe lanes (after prerequisites)

- ARCH-003 and ARCH-004 (separate UI slices)
- ARCH-009 and ARCH-010
- ARCH-013 with ARCH-015/ARCH-016 (if file ownership does not conflict)
- ARCH-018 and ARCH-019

### Must be completed before broad parallelization

1. ARCH-002 (contracts finalized)
2. ARCH-008 (command semantics stabilized)
3. Baseline architecture tests from ARCH-020 scaffolding pattern introduced early as possible

---

## Milestones

1. **M1 Stabilization:** through ARCH-007
2. **M2 Undo/Async correctness:** through ARCH-012
3. **M3 ECS + consolidation:** through ARCH-016 and ARCH-014
4. **M4 DI/layer + localization + guardrails:** through ARCH-020
5. **M5 Release readiness + closure:** ARCH-021 to ARCH-023

---

## Verification Strategy

- Build check: `cmd.exe /c gradlew.bat compileKotlin --no-daemon`
- Targeted tests per task scope
- Gate milestones with broader test runs:
  - `cmd.exe /c gradlew.bat test --no-daemon`
- ARCH-007 QA report path: `docs/ARCH-007-QA-GATE1.md`
- Manual smoke matrix for:
  - UI actions across menu/search/windows/toolbar
  - play-mode mutation blocking
  - undo/redo behavior under sync + async flows
  - ECS add/remove component mutation cases

---

## Risks and Mitigations

1. **High merge conflict risk in UI files**
   - Mitigation: strict file ownership per task; vertical-slice migration.
2. **Undo regressions during command split**
   - Mitigation: unit test state-machine coverage before UI migration.
3. **Overblocking legitimate play controls**
   - Mitigation: explicit allowlist in mutation gate + QA scenario coverage.
4. **DI refactor startup instability**
   - Mitigation: staged migration + targeted boot smoke after each slice.

---

## Immediate Next 3 Tasks

1. **ARCH-001** — bootstrap docs/backlog tracking.
2. **ARCH-002** — finalize architecture decision pack.
3. **ARCH-003** — begin UI conformance pass A (after ARCH-002 signoff).
