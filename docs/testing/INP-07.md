# INP-07 manual test steps: text input

Delivers `InputProvider.consumeTypedText()` and `InputProvider.isKeyRepeated(key)`, fed by a GLFW char callback
registered in `Engine.initCallbacks`. No editor or game UI consumes it yet (GUI text fields will), so the manual check
uses a temporary log probe. Nothing here needs a project, but step 5 uses the editor.

Run all commands from the repo root on Windows (PowerShell/cmd), or from WSL with `cmd.exe /c`.
Branch: `agents/input`, commit "INP-07".

## A. Automated check (start here)

1. Run: `.\gradlew.bat test --tests "com.pafoid.skate.engine.input.TextInputBufferTest"`
   Expected: `BUILD SUCCESSFUL`, 4 tests passed
   (`TextInput_CollectsTypedTextAndClearsOnConsume`, `..._SupportsSupplementaryCodePointsAndIgnoresControlChars`,
   `..._IsCappedAtMaxPending`, `..._RepeatLastsOneFrame`).
2. Run the full suite: `.\gradlew.bat test`
   Expected: `BUILD SUCCESSFUL`, no failed tests (including the architecture guards).

## B. Manual check with a temporary probe (real keyboard)

3. Open `src/main/kotlin/com/pafoid/skate/engine/ecs/systems/InputSystem.kt`. At the top of `override fun update(dt: Float) {`
   (line ~70) add these two lines (do NOT commit them):
   ```kotlin
   val typed = inputProvider.consumeTypedText()
   if (typed.isNotEmpty()) inputProvider.logger.log("TYPED: $typed")
   if (inputProvider.isKeyRepeated(org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE)) inputProvider.logger.log("BACKSPACE REPEAT")
   ```
4. Launch the editor: `.\gradlew.bat run`
   Expected: the Skate editor window opens; the console shows engine start-up logs.
5. In the editor, create a NEW project if it shows a start screen: use the File menu's New Project entry, enter any name
   and a folder, confirm, then open the (empty) scene so the engine is updating. If a project/scene is already open
   you can skip this. The play/runtime state does not matter; `InputSystem.update` runs in the editor loop.
6. Click on the **Scene viewport** (empty area, not a text field) so no ImGui text box has focus. Type `abc XYZ`.
   Expected: console lines containing `TYPED: a`, `b`, `c`, ` `, `X`, `Y`, `Z` (possibly merged per frame, e.g. `TYPED: abc`).
7. Type accented/non-ASCII characters (layout permitting), e.g. `é` or `ñ`.
   Expected: they appear in the log unchanged (Unicode, layout aware).
8. Press Enter, Tab, Escape, arrow keys, F-keys.
   Expected: NO `TYPED:` lines (control characters are ignored).
9. Hold Backspace for about one second.
   Expected: repeated `BACKSPACE REPEAT` lines after the OS key-repeat delay; a single tap logs none.
10. Click into an ImGui text field (for example the hierarchy search box) and type.
    Expected: the text appears in the field as normal (the ImGui chained char callback still works) and the probe also
    logs it, since the engine reads raw typed text regardless of ImGui focus.
11. Close the editor, then revert the probe: `git checkout src/main/kotlin/com/pafoid/skate/engine/ecs/systems/InputSystem.kt`
    Expected: `git status` clean.

## Known limits
- Pending text is capped at 256 characters per frame; extra input in one frame is dropped.
- `consumeTypedText()` is not gated by ImGui focus; a future in-game text widget should only consume it while focused.
