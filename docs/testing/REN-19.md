# REN-19 test steps: grid without z-fighting, transparency with post-processing off

Setup: `.\gradlew.bat run`, then create a NEW project (File > New Project). `MainScene` opens in the default editor
view.

1. Look at the far half of the floor, near its back edge → grid lines are solid and continuous, with no dashes or
   crawling pixels. Hold right mouse and nudge the camera slightly → the far lines stay steady.
2. Lower the camera close to the floor (grazing view) and look across it → the grid still reads cleanly to the floor
   edge. The red and blue origin axes on the floor are not broken up either.
3. Walk the camera behind the skater → the grid is hidden behind the skater's body and does not draw through it.
4. Open the Render Graph window and untick **Enabled** on `PostProcessPass`.
5. Open the `FeatureShowcase` scene, click the **Materials** legend button in Play (or fly there), and look at
   `[Materials] Transparent` → the floor shows through the blue cube, but the editor's dark viewport background
   never does, from any angle.
6. Stop, then untick the GameObject **Enabled** box on `[UI] Canvas`, so the scene has no runtime UI. Look at
   `[Materials] Transparent` again → still no viewport background through it. Before this fix, the UI pass hid the
   bug.
7. Tick `PostProcessPass` **Enabled** again → the transparent cube looks the same as with post-processing off, apart
   from bloom and FXAA.
