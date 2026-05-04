# Agent Briefing (Mandatory Read)

All agents must read this file before starting any recovery task.

## Current Situation

Recovery execution is complete through the current plan scope.  
`REG-001` through `REG-017` are resolved, including test compilation/runtime recovery (`REG-011` and `REG-012`).

## Required Context Read Order

1. `QWEN.md`
2. `docs/RECOVERY_PLAN.md`
3. `docs/REGRESSION_LOG.md`
4. `docs/ARCH_CHANGE_IMPACT.md`
5. Relevant subsystem files for assigned task

## Operating Rules During Recovery

1. Do one assigned task only.
2. Do not fix outside assigned scope unless it is a direct blocker; log new findings.
3. Every fix must map to a regression ID.
4. Update `docs/REGRESSION_LOG.md` status immediately after work.
5. Preserve event-driven and command-based editor architecture.
6. Preserve edit/play boundaries.
7. Preserve Koin-based DI patterns.

## Task Handoff Requirements

When handing off a completed task, include:

- Regression ID
- Scope completed
- Files changed
- Outcome summary (expected vs actual after fix)
- Any new regressions discovered
- Suggested next task (if dependency unlocked)

## Agent-Specific Focus

- `software-engineer`: implementation and integration regressions outside deep physics.
- `physics-engineer`: deterministic stepping, sync, collisions, runtime simulation correctness.
- `documentation-engineer`: synchronize docs with real behavior and recovery status.
- `qa-engineer`: reproducibility, edge-case design, failure classification.
- `tech-lead`: ordering, architectural boundaries, high-risk design conflicts.
- `reviewer`: risk-focused final pass and regression leak detection.
## Delegation Enablement (Project-Manager)

The project-manager role is configured to coordinate execution by invoking specialized agents per roadmap task ownership.

Execution policy:
- Single active task at a time
- Single owner per task
- Explicit handoff packet before each execution step
- Status updates recorded in roadmap/TODO/regression log after each handoff completion
