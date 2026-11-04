# GPL-05 manual test steps: Follow camera

Run from the repo root on Windows (from WSL, prefix with `cmd.exe /c`).

1. Run `.\gradlew.bat :test --tests "*FollowCameraRigTest" --tests "*FollowCameraSystemTest" --tests "*FollowCameraFromDiskTest"`.
   Expected: `BUILD SUCCESSFUL`.
2. Run `.\gradlew.bat run` and create a **new** project; stay on `MainScene`. Select `Camera`. Expected:
   - Properties shows a **FollowCameraComponent** section with Distance, Pivot Height, Pitch, Spring Arm Collision,
     Probe Radius, Shake settings and so on. There is no `!!key!!` text.
   - A wooden quarter pipe stands behind the skater and a concrete funbox to the side.
3. Press **Play**. Expected: the view snaps behind the skater, looking the way it faces. Walk around: the camera
   follows smoothly. When you walk in a new direction, it swings back behind the skater after about 1.5 s.
4. Turn the camera with the right stick (or the mouse if it is captured). Expected: it orbits the skater, and the
   pitch stops before looking straight down or up.
5. Walk backward toward the quarter pipe, or orbit the camera toward it. Expected:
   - The camera pulls in in front of the ramp instead of passing through it.
   - Once the way is clear, it eases back out.
   - Do the same at the funbox.
6. Mount the board (E / Y) and ride. Expected: the camera eases to the wider, lower riding framing. On dismount it
   eases back.
7. Stop Play. Set **Distance** to 8 and untick **Use Player Presets**, then untick **Spring Arm Collision**. Press
   Play again. Expected:
   - The camera now sits 8 m back.
   - It passes through the quarter pipe.
   - On stop, it returns to where it was placed.
8. Tick **Spring Arm Collision** again and save the scene, then close and reopen the project. Press Play. Expected:
   - The Follow Camera values you set are kept.
   - The spring arm stops in front of the quarter pipe again.
