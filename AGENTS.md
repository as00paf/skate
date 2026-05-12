# Agent Operating Contract

This file defines mandatory behavior for all agents in this repository.

## Mandatory Read Sequence

Before executing any task, read:

1. `QWEN.md`
2. `docs/TODO.md`
3. `docs/AGENT_BRIEFING.md`

## Execution Constraints

- Keep tasks small, focused, and single-owner.
- Avoid broad multi-purpose refactors unless explicitly requested.
- Record important findings in the relevant project documentation.

## Ownership Mapping

- software-engineer -> implementation/integration fixes
- physics-engineer -> physics/simulation fixes
- documentation-engineer -> documentation synchronization
- qa-engineer -> issue discovery and verification design
- tech-lead -> architecture-level conflict resolution
- reviewer -> final risk-focused validation

## Completion Requirements

A task is complete only when:

1. Task status is reflected in roadmap/TODO tracking
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

- Exactly one active task at a time.
- Exactly one primary owner per task.
- The project-manager remains responsible for roadmap status transitions and handoff integrity.
