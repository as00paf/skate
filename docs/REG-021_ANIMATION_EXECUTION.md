# REG-021 Software Execution - Restore Runtime Animation Transitions

## Owner

`software-engineer`

## Objective

Restore runtime animation transitions so skater animation reflects runtime motion/airborne state in Play mode.

## Scope

- In scope: runtime animation state transition triggers and update flow.
- Out of scope: base physics integration and controller input mappings.

## Acceptance Criteria

1. Idle -> move transition occurs with movement input.
2. Move -> idle transition occurs when motion/input stops.
3. Airborne-related runtime transition occurs when skater leaves ground.

## Verification Output

- Animation transition validation notes with expected-vs-actual behavior.
- Regression log evidence for animation restoration.

