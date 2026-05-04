# REG-019 Physics Execution - Restore Skater Gravity Influence

## Owner

`physics-engineer`

## Objective

Restore skater gravity behavior in Play mode by ensuring runtime physics stepping and body sync apply gravity as expected.

## Scope

- In scope: gravity application, fixed-step update order, runtime body sync.
- Out of scope: input mapping and animation logic.

## Acceptance Criteria

1. In Play mode, skater falls from rest under gravity.
2. Gravity behavior is consistent across repeated runs.
3. No regression to fixed-step stability.

## Verification Output

- Focused physics validation notes with expected-vs-actual behavior.
- Regression log evidence for gravity restoration.

