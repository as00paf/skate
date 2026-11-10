# Key Bindings

Key Bindings lists every keyboard shortcut of the editor and every control of your game, and lets you change them.

## What it is

A **binding** links an input (a key, a mouse button, a gamepad button or stick) to an **action** (jump, move forward, open search...). The window has tabs:

- **Game Actions**: the controls of your game, saved with the project. Each action can have several bindings, for example a key and a gamepad button. Actions are grouped by context, such as **Gameplay**, **UI** or **Skateboard**.
- **Editor Shortcuts**: the keys you use in the editor, such as the gizmo tools or Play.
- **Fixed shortcuts**: standard shortcuts such as Ctrl+C. They cannot be changed.
- **Test**: press keys or move a gamepad stick and see which action fires.

## How to use it

1. Open it from **Settings > Key Bindings...**, or from the **Key Bindings** button in [Editor Settings](editor_settings.md).
2. Type in the search box to find an action by name or by its key.
3. Click a binding, then press the new key. Press **Esc** or **Cancel** to stop waiting.
4. Use **+** to add another binding: a keyboard key, a mouse button or a gamepad input.
5. If the input is already used, the window says by which action. Choose **Swap** to exchange the two bindings, or **Unbind other** to take the input away from the other action.
6. **Reset** puts one action back to its default, the group reset puts a group back, and **Reset All** restores everything. **Clear** removes all bindings of an action.
7. Save a set of bindings as a **Profile** to load it later, or use **Copy JSON** and **Paste JSON** to share it.

Changes to game actions can be undone with **Ctrl+Z**.

## Related windows

- [Keyboard shortcuts](shortcuts.md): the default keys on one page.
- [Editor Settings](editor_settings.md): your other preferences.
- [Project Settings](project_settings.md): input settings saved with the game.
