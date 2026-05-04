# Skater Runtime Recovery Plan

## Scope

This plan targets the active runtime regressions where, during scene run mode, the skater:

1. is not affected by gravity,
2. is not animated correctly,
3. is not moveable with the gamepad as expected.

## Execution Rules

- One task at a time.
- One primary owner per task.
- Each task must produce a verifiable expected-vs-actual outcome.
- If a new issue is discovered, log a new regression ID before fixing it.

## Recovery Tasks

### REG-018 - Reproduce and isolate skater runtime failures

- **Owner:** `qa-engineer`
- **Objective:** Produce a reliable repro for gravity, animation, and gamepad movement failure in Play mode.
- **Subsystem:** Runtime integration (input -> physics -> animation).
- **Verification Outcome:** A deterministic repro script and observed vs expected behavior table.

### REG-019 - Restore gravity influence on skater runtime body

- **Owner:** `physics-engineer`
- **Objective:** Ensure skater rigid body receives gravity and is stepped/synced in runtime update order.
- **Subsystem:** Physics stepping and body synchronization.
- **Verification Outcome:** In Play mode, skater falls from rest under gravity with stable timestep behavior.

### REG-020 - Restore gamepad-driven movement flow

- **Owner:** `software-engineer`
- **Objective:** Reconnect runtime gamepad input path so movement commands reach skater control systems.
- **Subsystem:** Input mapping/event delivery/runtime control dispatch.
- **Verification Outcome:** In Play mode, left stick / configured controls move skater as before.

### REG-021 - Restore runtime animation state transitions

- **Owner:** `software-engineer`
- **Objective:** Ensure animation graph/state updates are driven by runtime movement/physics state.
- **Subsystem:** Animation system integration with control + physics state.
- **Verification Outcome:** Idle, movement, and airborne states transition correctly while running.

### REG-022 - End-to-end runtime validation

- **Owner:** `qa-engineer`
- **Objective:** Validate that gravity, movement, and animation all work together in Play mode.
- **Subsystem:** End-to-end runtime behavior.
- **Verification Outcome:** Focused runtime test pass plus manual repro checklist pass.

## Task Order (Strict)

1. REG-018
2. REG-019
3. REG-020
4. REG-021
5. REG-022

