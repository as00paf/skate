# ADR-ARCH-002: Core Architecture Contracts (M1 Stabilization)

- **Status:** accepted
- **Owner:** tech-lead
- **Related backlog:** ARCH-002
- **Date:** 2026-05-12

## Context

Architecture drift introduced inconsistent mutation paths, weak edit/play boundaries, and mixed ownership of state changes.

## Decisions

1. **Mutation pipeline is mandatory:** `UI -> Event -> ActionHandler -> CommandExecutor(UndoRedoManager) -> History`.
   - UI layers must publish events only.
   - UI layers must not call `UndoRedoManager.executeCommand(...)` directly.
   - UI layers must not mutate scene/object state directly for editor mutations.

2. **Play-mode mutation gate is centralized:**
   - Editor mutation commands are blocked while runtime play is active.
   - Runtime control actions (play/pause/resume/stop) are allowlisted and remain available.
   - Gate is enforced in command execution surfaces and consumed by handlers for non-command mutation paths.

3. **Command taxonomy baseline (M1/M2):**
   - `Command` remains the shared executable abstraction.
   - Immediate M1 requirement: block editor mutation commands in play mode unless explicitly allowlisted.
   - M2 refinement (ARCH-008+): split to undoable / execute-only / async-completing semantics.

4. **ECS invalidation contract:**
   - System cache invalidation must respond to object-set and component-composition changes.
   - M1 only codifies the rule; implementation closure continues in ARCH-013/014.

5. **Layering and dependency rules:**
   - `engine/**` must not depend on `editor/**`.
   - Domain actions/events must avoid editor-only types where practical; remediation remains tracked for later ARCH tasks.

## Consequences

- UI entry points are refactored to event-only orchestration.
- Handlers become the single execution bridge for commands and runtime/editor policy checks.
- QA gates can validate flow conformance by checking event publishing + handler execution paths.

## Verification

- ARCH-003/004: targeted UI surfaces removed direct mutation/direct command execution.
- ARCH-005: mutation gate integrated in command executor and handler surfaces.
- ARCH-007: QA Gate 1 verifies M1 conformance and boundary behavior.
