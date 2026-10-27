# Input Testing window (EDT-19)

`Input > Input Testing...` (Ctrl+Shift+I) opens a floating dialog (EDT-24) that is the single place to inspect input devices and tune gamepad behaviour.

- **Devices**: live keyboard (keys down), mouse (position, delta, buttons) and every connected gamepad (GLFW joysticks 1-16). "Copy device info" puts the active gamepad's name, axes and pressed buttons on the clipboard.
- **Gamepad tuning** (active gamepad selectable): per stick the raw (orange) and processed (green) value, dead-zone shape (axial/radial), inner and outer dead zone, response curve and sensitivity; per trigger the raw/processed value, inner/outer dead zone, curve and press threshold; and the axis/button map of the standard GLFW gamepad layout.
- **Event log**: recent key, mouse and gamepad button events with pause, clear and copy.

Edits change the project's live `InputMappings.deadZones` immediately, so they apply in-game at once. When a drag or choice ends, one undoable `SetDeadZonesCommand` is dispatched via `InputSettingsAction.SetDeadZones` and the project is saved (dead zones persist in the project file; see INP-02). Sensitivity is `StickDeadZone.sensitivity` (default 1.0, range 0.1-5), applied after the dead zone and curve.

The Key Bindings window no longer hosts gamepad dead-zone settings.

The dialog is an `EditorDialog`: it closes with the title-bar X, the Close button or Esc, is not listed in View > Windows and is not stored in the docked layout (ImGui keeps its size and position; stale `DockId` entries from older layouts are stripped on start).
