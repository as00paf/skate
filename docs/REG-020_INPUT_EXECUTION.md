# REG-020 Software Execution - Restore Gamepad Movement Flow

## Owner

`software-engineer`

## Objective

Restore the runtime gamepad input path so movement commands reach skater control systems in Play mode.

## Scope

- In scope: input mapping, event dispatch path, runtime movement command routing.
- Out of scope: gravity internals and animation graph internals.

## Acceptance Criteria

1. Skater responds to configured gamepad movement input in Play mode.
2. Input response is deterministic across repeated runs.
3. No editor-only input behavior leaks into runtime path.

## Verification Output

- Input path validation notes with expected-vs-actual behavior.
- Regression log evidence for movement restoration.

