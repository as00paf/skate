# ANI-09 manual test steps: skater riding poses and controller

Run from the repo root on Windows (from WSL, prefix with `cmd.exe /c`).

1. Run `.\gradlew.bat :test --tests "com.pafoid.skate.engine.animation.procedural.*"`. Expected: `BUILD SUCCESSFUL` (14 tests).
2. Run `.\gradlew.bat run` and create a **new** project with the default skate template. Open its main scene and press **Play**. Expected: no `Controller` errors in the console. On foot, the skater idles, walks (W) and runs (Shift) as before.
3. Walk up to the board and press **E** (gamepad **Y**). Expected: the skater turns side-on on the deck, left foot toward the nose. Knees are bent, arms are lowered and out along the board, the head is turned toward the nose, and the skater breathes slowly.
4. Hold **W**. Expected: the push cycle plays. The hips open toward the nose, and the back foot steps down beside the board, kicks back and returns to the tail. Release W while rolling. Expected: a lower riding stance.
5. While rolling, press **Space**. Expected: crouch, pop (back foot down, front foot slides up), then a tucked crouch in the air. On touchdown there is a short absorb crouch, then the riding stance returns.
6. Hold **Ctrl** (manual) at speed. Expected: the hips shift over the back foot and the front foot lifts.
7. Press **E** to get off. Expected: the normal idle/walk clips return, with a smooth 0.2 s blend.

Known limits: the poses are procedural stand-ins. Rolling off a ledge also shows the ollie pose, because gameplay has a single AIR state. To use real clips, see `docs/animation/skater-controller.md`.
