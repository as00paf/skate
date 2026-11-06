# EDT-52I test steps: Input Testing moves to Key Bindings and Project Settings

The Input Testing window is gone (EDT-51 §3.1):
- Its devices view and event log are on the **Key Bindings › Test** tab. The log has a new **Action** column showing which bound action each press fired.
- The INP-10 context list is also on that tab.
- Dead-zone tuning is in **Project Settings › Input**.

Test Input (in Search Anywhere) and **Shift+I** open Key Bindings on its Test tab. The hamburger menu no longer has an Input entry.

1. `.\gradlew.bat run` with a project open. Open the hamburger menu.
   Expected: there is no Input submenu, and View › Windows does not list Input Testing.
2. Press **Shift+I** (outside a text field).
   Expected: Key Bindings opens on the **Test** tab, showing Devices, Input Contexts and the Event Log.
   Open Search Anywhere (Ctrl+P), type `test input` (in French, `manette`) and press Enter.
   Expected: the same Test tab opens.
3. On the Test tab, hold **W** and **Space**, move the mouse, and press a mouse button.
   Expected: Devices lists the keys held, the mouse position and delta, and the buttons. Input Contexts shows `UI` and `Gameplay`.
4. In the Event Log, press **Space**, **E** and **Q**.
   Expected:
   - `Keyboard: Space pressed` shows Action **Jump, Ollie (inactive)**. Ollie is greyed because the skate context is only active while riding.
   - `Keyboard: e pressed` shows **Mount / Dismount**, and `q` shows `-`.
   - Release rows show `-`.
   - With a gamepad, **A** shows **Confirm, Jump, Ollie (inactive)**.
5. Tick **Pause**, press keys, untick it, then click **Copy log** and paste into a text editor.
   Expected: nothing was logged while paused. The pasted lines read like `Keyboard: Space pressed -> Jump, Ollie (inactive)`.
6. Open **Project Settings › Input**.
   Expected:
   - The left and right stick drawings, the trigger bars, and the mode, inner, outer, curve, sensitivity and press-threshold controls are all there, even with no gamepad connected ("No gamepad detected" is shown in grey).
   - With a gamepad, moving a stick moves the orange (raw) and green (processed) dots.
7. Drag **Left Stick › Inner** to a new value and release. Press **Ctrl+Z**, then **Ctrl+Y**.
   Expected: Command History shows one "Edit Left stick dead zone" entry. Undo restores the old value and redo applies it again.
8. Change a value, close the project and reopen it, then reopen Project Settings › Input.
   Expected: the value is still there, because each edit saves the project.
