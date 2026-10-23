# INP-01 — Input review

Scope: `engine/input/**` (incl. `listeners/`), `InputSystem`, `InputStateComponent`, `InputEvents`.

## Fixed in this task (quick fixes)

- `MouseListener`: `dx`/`dy` were overwritten per cursor callback and never cleared, so a stationary mouse kept reporting the last delta. Now accumulated per frame and reset in `endFrame()`/`clear()`. Button-down count clamped at 0.
- `KeyListener`: negative/out-of-range key codes could throw. Callback and accessors use `key in indices`.
- `GamepadListener`: out-of-range `jid` threw; `isGamepadPresent` bounds-checks and the others delegate to it.
- `InputMappings.reset()` did not reset `cameraLookX/Y`.
- `InputSystem`: `jumpHeld` stayed true if the controller was unplugged while jump was held. Cleared every frame before polling.

## Open issues (for later INP tasks)

- I1. Hardcoded dead zones (0.15 move, 0.1 look, 0.5 trigger) and look sensitivities in `InputSystem` → INP-02.
- I2. Scalar per-axis dead zone, not radial; no outer dead zone or response curve → INP-02.
- I3. Only `GLFW_JOYSTICK_1` is read; raw joystick API (not gamepad mapping) so indices only match Xbox-like pads → INP-04.
- I4. `InputBinding.keyboardKey` is ignored by `InputSystem`; `moveUp/moveDown` share one axis (negative binding dead) → INP-03.
- I5. No action-map/context concept; listeners read ImGui `wantCapture*` directly (engine → editor UI library coupling).
- I6. `pausePressed`, `resetPressed`, `TrickInput`, `CameraLook` never produced; `InputEvents.kt` imports `game.skateboard.TrickType` (engine → game).
- I7. Edge state (`jumpButtonWasPressed`, `movementWasActive`) is shared across all `InputStateComponent`s.
- I8. Dead code: `checkButtonBindingBeginPress`, `previousButtons`; stale TODO on `InputBinding.isValid`.
- I9. `GamepadListener` hardcodes 15 buttons, ignores hats, `getAxes` allocates per call. `InputBuffer` has unused `jid` param.
- I10. `InputMappings` has no version field.
- I11. `ApplyMappings` subscription in `init` is never removed; re-init duplicates handlers.
- I12. No unit tests for the input package; listeners coupled to GLFW/ImGui statics.
