# Recovery Execution Queue

Single-threaded execution order, one active task at a time.

## Current State

- Active task: `REG-001`
- Execution mode: sequential
- Source of truth: `docs/RECOVERY_PLAN.md` + `docs/REGRESSION_LOG.md`

## Queue

1. `REG-001` - Build/startup regression inventory - owner: `qa-engineer` - status: in_progress
2. `REG-002` - Koin wiring regression sweep - owner: `software-engineer` - status: open
3. `REG-003` - Scene lifecycle regression fixes - owner: `software-engineer` - status: open
4. `REG-004` - Event/command pipeline restoration - owner: `software-engineer` - status: open
5. `REG-005` - Edit/Play transition correctness - owner: `qa-engineer` - status: open
6. `REG-006` - ECS ordering/cache audit and resolution plan - owner: `tech-lead` - status: open
7. `REG-007` - Physics fixed-step and sync recovery - owner: `physics-engineer` - status: open
8. `REG-008` - Serialization round-trip verification - owner: `qa-engineer` - status: open
9. `REG-009` - UI localization + window registry consistency - owner: `documentation-engineer` - status: open
10. `REG-010` - Recovery doc synchronization and closure - owner: `documentation-engineer` - status: open

## Advancement Rule

Do not start `REG-(N+1)` until `REG-N` is marked `resolved` in `docs/REGRESSION_LOG.md`.
## 2026-05-03 Execution Queue: Skater Runtime Recovery

### Active (Only Task In Progress)

#### REG-018 - Reproduce and isolate skater runtime failures
- **Owner:** `qa-engineer`
- **Status:** `in_progress`
- **Objective:** Produce deterministic repro for Play mode failures:
  1. no gravity influence on skater,
  2. no gamepad movement response,
  3. no runtime animation response.
- **Deliverable:** Expected-vs-actual matrix + minimal repro steps.
- **Exit criteria:** Repro is stable and directly maps failure points to subsystem boundaries.

### Queued (Do Not Start Until Prior Task Resolves)

#### REG-019 - Restore gravity influence
- **Owner:** `physics-engineer`
- **Status:** `pending`
- **Objective:** Ensure runtime body receives gravity and syncs correctly through update order.
- **Exit criteria:** Skater falls under gravity in Play mode with stable stepping.

#### REG-020 - Restore gamepad movement flow
- **Owner:** `software-engineer`
- **Status:** `pending`
- **Objective:** Ensure mapped gamepad input reaches runtime skater control path.
- **Exit criteria:** Skater responds to configured gamepad movement controls in Play mode.

#### REG-021 - Restore runtime animation transitions
- **Owner:** `software-engineer`
- **Status:** `pending`
- **Objective:** Reconnect runtime animation state transitions to motion/physics state.
- **Exit criteria:** Idle/move/airborne transitions occur correctly during runtime control.

#### REG-022 - End-to-end validation
- **Owner:** `qa-engineer`
- **Status:** `pending`
- **Objective:** Validate gravity + movement + animation integration as one flow.
- **Exit criteria:** Focused runtime validation pass with evidence recorded in regression log.
### REG-018 Execution Packet Issued

- Execution brief: `docs/REG-018_QA_EXECUTION.md`
- Owner remains: `qa-engineer`
- Status remains: `in_progress`
- Gate: do not start REG-019 until REG-018 evidence is complete.
### Remaining Task Packets Issued

- `REG-019`: `docs/REG-019_PHYSICS_EXECUTION.md`
- `REG-020`: `docs/REG-020_INPUT_EXECUTION.md`
- `REG-021`: `docs/REG-021_ANIMATION_EXECUTION.md`
- `REG-022`: `docs/REG-022_QA_VALIDATION_EXECUTION.md`

All remaining tasks are fully specified and queued in strict sequence after REG-018 resolution.
### REG-018 Blocker State

- Current status: `blocked`
- Reason: runtime repro cannot be executed in current tool-constrained session.
- Resume action: re-enable execution/delegation tools and continue with `docs/REG-018_QA_EXECUTION.md`.
## Delegation Mode

Project-manager may dispatch each queued recovery item to its predefined owner agent for execution, while preserving strict sequential order and one-task-at-a-time policy.

Dispatch mapping for current queue:
- REG-018 -> `qa-engineer`
- REG-019 -> `physics-engineer`
- REG-020 -> `software-engineer`
- REG-021 -> `software-engineer`
- REG-022 -> `qa-engineer`

## 2026-05-04 Completion Update

- REG-018: resolved
- REG-019: resolved
- REG-020: resolved
- REG-021: resolved
- REG-022: resolved
