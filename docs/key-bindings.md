# Key Bindings window (EDT-20)

The **Game Actions** tab edits the INP-03 `ActionMap` of the open project (gameplay and UI contexts).

- Actions are grouped by context and category; the search box matches name, id, category and bound input.
- Each binding is a button: click it, then press the new key or gamepad input (mouse buttons are chosen from a menu).
  `x` removes a slot, `+` adds a keyboard, mouse or gamepad binding, `Clear` removes all, `Reset` restores defaults
  (per action, per group, or all).
- Binding an input already used in the same context opens a conflict dialog: **Swap**, **Unbind other** or **Cancel**.
- Every change is a `KeyBindingsAction.Apply(BindingOperation)` handled by `KeyBindingsActionHandler`, which runs an
  undoable `EditBindingsCommand`, mirrors the result into `InputMappings.bindings` and saves the project.
- Profiles are JSON files in `<project>/InputProfiles/`. Save/Delete act on the name in the box; Load, and Paste JSON
  (clipboard import) are undoable. Copy JSON exports the current bindings.
- Gamepad dead zones and sensitivity live only in the Input Testing dialog.

The **Editor Shortcuts** tabs still edit `EditorInputMappings` (editor-only keys) directly.
