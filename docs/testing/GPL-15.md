# GPL-15 manual test steps: riding the skateboard

Run from the repo root on Windows (from WSL, prefix with `cmd.exe /c`).

1. Run `.\gradlew.bat :test --tests "*MountPoseTest" --tests "*MountTest"`. Expected: `BUILD SUCCESSFUL`.
2. Run `.\gradlew.bat run` and create a **new** project with the default skate template. Open its main scene. It should contain the Skater and the Skateboard 1 m in front of it.
3. Press **Play**. Walk the skater (WASD) next to the board, within 1.5 m, and press **E** (gamepad **Y**) to get on. Expected: the skater jumps onto the board. Its feet are on the deck, it faces the board's nose, and the camera pulls back.
4. Stand still on the board for 5 seconds. Expected: neither the skater nor the board rises. The wheels stay on the ground.
5. Hold **W** (or **Shift**) to push, release it, then steer with **A**/**D**. Expected: the push clip (the walking clip) plays while pushing, and the riding idle (`idle_1`) plays while rolling or stopped. The board stays under the feet and points where the skater faces, with no lag and no drift at speed.
6. Turn on physics debug with the "Toggle Physics Debug" command (command palette or Edit/Debug menu). Expected: the board's collider box is under the skater's feet and touches the ground. The skater's box stands on top of it.
7. Press **E** again to get off. Expected: the walk/idle animation comes back, the camera moves closer, and the board stays where it was left. Walking into it pushes it.
8. Ride, then press **Space** to ollie. Expected: the skater and board go up and come back down to riding height. They do not keep floating.

Known limits: no skateboarding clips ship with the engine yet. Riding uses `idle_1` and pushing uses `walking`, so the pose is upright and facing forward rather than a sideways skate stance.
