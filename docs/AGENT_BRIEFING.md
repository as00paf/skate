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
