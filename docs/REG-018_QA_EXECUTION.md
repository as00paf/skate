# REG-018 QA Execution - Skater Runtime Repro Isolation

## Owner

`qa-engineer`

## Objective

Produce deterministic reproduction evidence for Play mode failures where the skater:

1. is not affected by gravity,
2. is not moveable via gamepad input,
3. is not animating according to runtime state.

## Scope

- In scope: runtime behavior validation and failure mapping only.
- Out of scope: implementation changes.

## Repro Procedure

1. Launch engine and open a known skater scene.
2. Enter Play mode.
3. Observe skater at spawn without input for gravity response.
4. Apply configured gamepad movement input and observe transform/velocity change.
5. Observe animation state transitions while idle and while input is applied.
6. Exit Play mode and repeat once for determinism.

## Expected vs Actual Evidence (fill during execution)

| Check | Expected | Actual | Result |
| --- | --- | --- | --- |
| Gravity at rest | Skater accelerates downward in Play mode | Blocked: Play mode runtime could not be launched in this session (no command/runtime execution tool available). | BLOCKED |
| Gamepad movement | Skater responds to movement input | Blocked: gamepad movement cannot be exercised without executable Play mode session. | BLOCKED |
| Runtime animation | Animation transitions track idle/move/airborne | Blocked: animator runtime transitions cannot be observed without Play mode execution. | BLOCKED |
| Repeatability | Same outcome across two runs | Blocked: deterministic two-run check requires runnable runtime session. | BLOCKED |

## Required Deliverables

1. Completed expected-vs-actual table.
2. Minimal repro steps (short form, 5-8 bullets).
3. Suspected subsystem mapping for each failed check:
   - input pipeline,
   - physics stepping/sync,
   - animation state update flow.

## Completion Criteria

- REG-018 can be marked `resolved` only when repro evidence is complete and deterministic.
- If additional defects are found, create new regression IDs before any implementation work.

## Execution Attempt (2026-05-04)

- Mandatory recovery docs were reviewed (`QWEN.md`, `RECOVERY_PLAN.md`, `REGRESSION_LOG.md`, `AGENT_BRIEFING.md`).
- Runtime repro execution was attempted with available tooling.
- Attempted command set for repro launch (not executable in this session due missing command runner):
  - `./gradlew run --no-daemon`
  - `./gradlew run --info --no-daemon`
  - `./gradlew test --tests "com.pafoid.skate.*" --no-daemon` (fallback runtime signal check)
- Blocker: this environment exposes file inspection/edit tools only (`view`, `glob`, `apply_patch`) and does not expose runtime command/Play-mode interaction tooling.
- REG-018 remains `blocked` until runtime command execution access is restored.

## Deterministic Repro Steps (to execute once unblocked)

1. Start engine and load the skater validation scene used for REG-018.
2. Enter Play mode and wait 3 seconds with no input.
3. Record skater Y-position/velocity change for gravity check.
4. Apply left-stick movement for 3 seconds and record transform/velocity response.
5. Observe animation state while idle, moving, and (if gravity works) airborne/landing.
6. Exit Play mode, relaunch same scene, and repeat steps 2-5 to confirm deterministic repeatability.

## Suspected Subsystem Mapping (from reported symptoms)

- Gravity failure -> `physics stepping/sync`
- Gamepad movement failure -> `input pipeline`
- Animation non-response -> `animation update flow`
