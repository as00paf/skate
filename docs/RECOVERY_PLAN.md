# Regression Recovery Plan

## Purpose

Stabilize the codebase after recent architectural changes, identify all regressions, and resolve them through small, single-owner tasks with explicit verification.

This document governs recovery execution and is the controlling plan for regression work.

## Current Recovery Status (Synchronized)

- Resolved regressions: `REG-001` through `REG-017`
- Remaining unresolved regressions: none
- No open `P0`/`P1` regressions remain in the recovery track.

## Recovery Principles

1. One task in progress at a time.
2. Every task has exactly one owner agent.
3. No fix is "done" without a verifiable outcome.
4. Roadmap and regression log are always updated immediately after task completion.

## Current Recovery Scope

- Boot/startup failures
- DI wiring and runtime service resolution issues
- Scene lifecycle regressions (open/close/switch/create)
- Event-driven pipeline integrity (`UI -> Event -> ActionHandler -> Command -> UndoRedoManager`)
- Edit/Play mode regressions
- ECS system ordering and cache invalidation regressions
- Physics stepping and sync regressions
- Serialization/persistence regressions
- Editor UI/Window registry and localization regressions

## Phases

### Phase A - Discovery and Triage

Goal: identify all regressions and classify severity.

Outputs:
- Updated `docs/REGRESSION_LOG.md` with reproducible issues
- Updated `docs/ARCH_CHANGE_IMPACT.md` with affected subsystems
- Ordered queue of P0/P1 tasks

### Phase B - P0 Stabilization

Goal: remove build/startup/crash/data-loss blockers.

Outputs:
- Build compiles
- Editor boots
- No critical crash in smoke flow

### Phase C - P1 Core Behavior Recovery

Goal: restore correctness in core runtime/editor flows.

Outputs:
- Scene lifecycle stable
- Event/command pipeline stable
- Edit/Play transitions stable
- ECS update ordering/caching stable

### Phase D - P2/P3 Hardening

Goal: close medium/low defects and remove dead code introduced by architecture migration.

Outputs:
- Remaining regressions closed or explicitly deferred
- Documentation synchronized with behavior

## Exit Criteria

Recovery is complete when:

1. No open P0/P1 issues remain in `docs/REGRESSION_LOG.md`
2. Recovery tasks are marked complete in roadmap files
3. All modified subsystems have owner-confirmed verification evidence
4. Follow-up non-blocking improvements are clearly recorded as normal roadmap work

## Delegation Policy

- `software-engineer`: general Kotlin implementation and integration fixes
- `physics-engineer`: physics/simulation/fixed-step/collision regressions
- `documentation-engineer`: recovery docs, changelog, user-facing migration notes
- `qa-engineer`: reproducibility, regression test design, failure mapping
- `tech-lead`: architecture-level conflict resolution, system-ordering decisions
- `reviewer`: final quality gate and risk-focused review

## Recovery Backlog and Status

1. REG-001 - Build/startup regression inventory (owner: `qa-engineer`) - `resolved`
2. REG-002 - Koin wiring regression sweep (owner: `software-engineer`) - `resolved`
3. REG-003 - Scene lifecycle regression fixes (owner: `software-engineer`) - `resolved`
4. REG-004 - Event/command pipeline restoration (owner: `software-engineer`) - `resolved`
5. REG-005 - Edit/Play mode transition correctness (owner: `qa-engineer`) - `resolved`
6. REG-006 - ECS system ordering + cache invalidation audit (owner: `tech-lead`) - `resolved`
7. REG-007 - Physics fixed-step and sync recovery (owner: `physics-engineer`) - `resolved`
8. REG-008 - Serialization round-trip and scene persistence checks (owner: `qa-engineer`) - `resolved`
9. REG-009 - UI localization + window registry consistency (owner: `documentation-engineer`) - `resolved`
10. REG-010 - Recovery documentation synchronization (owner: `documentation-engineer`) - `resolved`
11. REG-011 - Test suite compilation baseline recovery (owner: `software-engineer`) - `resolved`
12. REG-012 - Runtime test behavior recovery (owner: `qa-engineer`) - `resolved`
13. REG-013 - Environment system lifecycle fix (owner: `software-engineer`) - `resolved`
14. REG-014 - Physics test DI fixture alignment (owner: `qa-engineer`) - `resolved`
15. REG-015 - Render graph test expectation alignment (owner: `qa-engineer`) - `resolved`
16. REG-016 - StringManager test DI setup alignment (owner: `qa-engineer`) - `resolved`
17. REG-017 - Environment test internal API drift cleanup (owner: `qa-engineer`) - `resolved`
