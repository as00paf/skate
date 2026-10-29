# INP-08 test steps: cursor capture API

New: `CursorMode` (NORMAL/HIDDEN/CAPTURED), `InputProvider.setCursorMode/getCursorMode`; the first mouse move after a
mode change gives no delta (no camera jump). No UI uses it yet, so manual checks use a temporary probe.

Setup (once): `.\gradlew.bat run`, create a NEW project (File menu > New Project), open its scene.

1. `.\gradlew.bat test --tests "com.pafoid.skate.engine.input.CursorModeTest"` -> BUILD SUCCESSFUL, 2 tests passed.
2. `.\gradlew.bat test` -> BUILD SUCCESSFUL.
3. Temporary probe (do not commit): at the top of `InputSystem.update` add
   `if (inputProvider.keyBeginPress(org.lwjgl.glfw.GLFW.GLFW_KEY_F9)) inputProvider.setCursorMode(if (inputProvider.getCursorMode() == com.pafoid.skate.engine.input.CursorMode.CAPTURED) com.pafoid.skate.engine.input.CursorMode.NORMAL else com.pafoid.skate.engine.input.CursorMode.CAPTURED)`
   then relaunch with `.\gradlew.bat run` and open the scene (click the viewport first).
4. Press F9 with the mouse over the viewport -> the cursor disappears and stays locked while you move the mouse.
5. Move the mouse in the captured state -> `getMouseDx()/Dy()` keep changing without limit (no screen-edge stop).
6. Press F9 again -> the cursor reappears and moves normally.
7. Right after step 4/6 the first mouse movement must not produce a large jump (check with a `logger.log` of `getMouseDx()`).
8. Revert the probe: `git checkout src/main/kotlin/com/pafoid/skate/engine/ecs/systems/InputSystem.kt`.
