# ANI-10 manual test steps: real skateboard riding clip

Run from the repo root on Windows (from WSL, prefix with `cmd.exe /c`).

1. Run `.\gradlew.bat :test --tests "com.pafoid.skate.engine.animation.graph.SkateRidingClipTest"`. Expected: `BUILD SUCCESSFUL` (3 tests).
2. Run `.\gradlew.bat run` and create a **new** project with the default skate template. Expected: `Assets/EngineDefaults/Characters/animations/skate riding.fbx` exists in the project. Open the main scene and press **Play**. Expected: no `Controller` errors in the console.
3. Walk to the board and press **E** (gamepad **Y**). Expected: the skater plays the riding clip. It stands side-on with the left foot toward the board's nose, and the soles are on the deck, neither floating nor sunk into it.
4. Stay still on the board for 10 seconds. Expected: the subtle riding motion loops with no visible pop every 1.2 s.
5. Hold **W** to push, then release it. Expected: the procedural push plays, then it blends back into the riding clip once you are rolling.
6. While rolling, press **Space**. Expected: the procedural ollie and land poses play, then the riding clip returns.
7. Press **E** to get off. Expected: the normal idle/walk clips return.

Known limits: push, ollie, land, grind and manual are still procedural until their clips arrive (see `docs/animation/skater-controller.md`).
