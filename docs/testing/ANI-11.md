# ANI-11 manual test steps: exported skater keeps its clips (BUG-021)

Run from the repo root on Windows (from WSL, prefix with `cmd.exe /c`).

1. Run `.\gradlew.bat :test --tests "*AnimatorClipResolutionTest" --tests "*ProjectLifecycleEndToEndTest"`. Expected: `BUILD SUCCESSFUL`, `Lifecycle_8` included.
2. Run `.\gradlew.bat run` and create a **new** project. Press **Play**. Expected: the skater idles and walks as before (editor path unchanged).
3. Export the project (Windows build, or Pack only plus the game runner), then run the exported game.
4. Expected: the skater idles, walks, and rides with its clips (the `skate riding` clip on the board). The game log has no `Controller ... uses unknown motion` or `Animation clip not found` lines.
5. Close the editor, move the project folder, reopen it and press **Play**. Expected: the clips still load (AST-09 relative paths).
