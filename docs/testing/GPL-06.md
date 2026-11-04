# GPL-06 manual test steps: Skate animation states

Run from the repo root on Windows (from WSL, prefix with `cmd.exe /c`). Keyboard bindings are listed; the gamepad
equivalents are A (jump), right trigger (push), LB (crouch / manual) and Y (mount).

1. Run `.\gradlew.bat :test --tests "*SkateBehaviorTest" --tests "*SkateAnimationPipelineTest" --tests "*SkateAnimationFromDiskTest"`.
   Expected: `BUILD SUCCESSFUL`.
2. Run `.\gradlew.bat run` and create a **new** project. In `MainScene`, press **Play**, walk to the board and mount
   it with **E**. Expected: the skater stands on the deck in the riding idle pose.
3. Hold **Shift**. Expected: the push pose plays while speed builds. Release it: the skater blends into the rolling
   pose and coasts. Below walking pace it returns to riding idle.
4. While rolling, press **Space**. Expected:
   - The skater pops an ollie (about knee height), holds the air pose, then plays a short landing.
   - It goes back to rolling.
5. While rolling at speed, hold **Left Ctrl**. Expected: the manual pose plays. Release it to go back to rolling.
   Hold it while letting the balance drift: the skater bails.
6. Roll toward the concrete ledge at speed and ollie onto it. Expected:
   - The skater locks into the grind pose along the ledge.
   - Riding off the end (or pressing Space) leaves the grind into the air.
7. Push up the quarter pipe's deck (or anything high) and drop off more than about 3.7 m, or land sideways.
   Expected: the skater bails and recovers to riding idle after about 1.2 s.
8. Close the editor and set `blendTimes.push` to `1.0` in `src/main/resources/configs/skater.character.json`. A
   `configs/skater.character.json` in the working directory overrides it. Run `.\gradlew.bat run` again, ride and
   push. Expected: the change into the push pose now fades over one second. Revert the change afterwards.
