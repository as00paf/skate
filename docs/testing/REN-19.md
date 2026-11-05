# REN-19 test steps: grid without z-fighting, transparency with post-processing off

Setup: `.\gradlew.bat run`, then create a NEW project (File > New Project). `MainScene` opens in the default editor
view.

1. Look at the far half of the floor, then lower the camera to a grazing view → grid lines are solid to the floor
   edge, with no dashes or crawling pixels, even when you nudge the camera.
2. Look at the origin axes around the skater, who stands at the origin: the red X axis, the blue Z axis and the green
   Y axis (vertical). Red and blue lie on the floor, and no line floats above the floor or above the skater's head.
   The part of the blue Z axis behind the skater is hidden by the skater's legs. The green axis shows only between
   the legs and above the head, and is hidden by the body.
3. Lower the camera to a grazing angle in front of the skater, then to the side → the axes still run along the
   floor, pass behind the legs (not over them), and do not flicker against the floor.
4. Walk the camera behind the skater → the grid is hidden behind the skater's body and does not draw through it.
5. Open the Render Graph window and untick **Enabled** on `PostProcessPass`.
6. Open the `FeatureShowcase` scene, click the **Materials** legend button in Play (or fly there), and look at
   `[Materials] Transparent` → the floor shows through the blue cube, but the editor's dark viewport background
   never does, from any angle.
7. Stop, then untick the GameObject **Enabled** box on `[UI] Canvas`, so the scene has no runtime UI. Look at
   `[Materials] Transparent` again → still no viewport background through it. Before this fix, the UI pass hid the
   bug.
8. Tick `PostProcessPass` **Enabled** again → the transparent cube looks the same as with post-processing off, apart
   from bloom and FXAA.
