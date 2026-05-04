# Skater Runtime Recovery Checklist

Use this checklist while executing `docs/SKATER_RUNTIME_RECOVERY_PLAN.md`.

## Reproduction Baseline

- Enter Play mode from editor scene.
- Spawn/load skater at a non-zero height.
- Confirm expected behavior baseline:
  - skater drops under gravity,
  - gamepad input moves skater,
  - animation responds to movement/airborne states.

## Verification Matrix

| Capability | Expected | Actual | Pass/Fail |
| --- | --- | --- | --- |
| Gravity | Skater accelerates downward in Play mode |  |  |
| Gamepad Movement | Skater responds to mapped gamepad controls |  |  |
| Animation | Animation transitions match skater motion state |  |  |
| Integrated Flow | Physics + input + animation remain synchronized |  |  |

## Regression Logging Discipline

- Update `docs/REGRESSION_LOG.md` status transition for each task:
  - `open` -> `in_progress` -> `resolved` (or `blocked`/`deferred` when applicable).
- Add expected-vs-actual evidence for each resolved item.
- Create new regression IDs for newly discovered defects before implementation.

