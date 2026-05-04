# Agent Operating Contract

This file defines mandatory behavior for all agents in this repository.

## Mandatory Read Sequence

Before executing any task, read:

1. `QWEN.md`
2. `docs/RECOVERY_PLAN.md`
3. `docs/REGRESSION_LOG.md`
4. `docs/AGENT_BRIEFING.md`

## Recovery Constraints

- Only perform work tied to a regression/task ID.
- Keep tasks small, focused, and single-owner.
- Do not run multi-purpose broad refactors during recovery.
- If new issues are found, log them immediately in `docs/REGRESSION_LOG.md`.

## Ownership Mapping

- software-engineer -> implementation/integration fixes
- physics-engineer -> physics/simulation regressions
- documentation-engineer -> documentation and recovery synchronization
- qa-engineer -> issue discovery and verification design
- tech-lead -> architecture-level conflict resolution
- reviewer -> final risk-focused validation

## Completion Requirements

A task is complete only when:

1. The assigned regression status is updated to `resolved` in `docs/REGRESSION_LOG.md`
2. Verifiable outcome is documented (expected vs actual)
3. Any follow-up issues are added as new entries
## Project-Manager Delegation Configuration

The `project-manager` agent is authorized to invoke specialist agents to execute active roadmap tasks, using strict single-task sequencing:

1. `qa-engineer` for reproduction and verification tasks
2. `physics-engineer` for physics/simulation fixes
3. `software-engineer` for implementation/integration fixes
4. `documentation-engineer` for roadmap and documentation updates
5. `reviewer` for final risk-focused validation

Delegation constraints:
- Exactly one active regression task at a time.
- Exactly one primary owner per task.
- The project-manager remains responsible for roadmap status transitions and handoff integrity.

