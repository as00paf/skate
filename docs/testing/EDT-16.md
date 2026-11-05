# EDT-16 manual test steps: Animator controller and mask in the inspector

Run from the repo root on Windows (from WSL, prefix with `cmd.exe /c`).

1. Run `.\gradlew.bat :test --tests "com.pafoid.skate.editor.animation.*" --tests "*AnimatorMaskTest"`. Expected: `BUILD SUCCESSFUL`.
2. Run `.\gradlew.bat run` and create a **new** project. In the main scene, select **Skater** and open its **Animator** in the Properties window. Expected: **Controller** shows `skater` and **Mask** shows `Full body`.
3. Open the Controller list and pick **None**. Expected: the skater falls back to plain clip playback. Press **Ctrl+Z**: `skater` comes back. Press **Ctrl+Y**: it is `None` again. Undo once more to restore `skater`.
4. Set **Mask** to **Bone subtree** and pick `...Spine` in **Mask root bone**. Undo and redo both steps. Expected: each step is restored one at a time.
5. Copy `configs/skater.controller.json` from the repo into the project's `Assets` folder as `my.controller.json`, click **Refresh**, and open the list. Expected: `my` is listed. Dragging the file from the Project window onto the Controller box also assigns it.
6. Press **Play**, get on the board (E) and push (W). Expected: **State** reads `ride_idle -> push`, then `push`, the progress bar moves, and **Requested** shows `push`.
7. Stop, save the project (File > Save), close and reopen it. Expected: the Skater keeps the chosen controller and mask.
8. Open the **Feature Showcase** scene and press **Play**. Expected: in the Animation area, `[Animation] Upper-body mask` walks with its legs while its arms stay in the idle pose. Next to it, `[Animation] Character` swings its arms.

Switch the editor language to French. Expected: the Animator labels are translated, and the docked layout and open combos keep working.
