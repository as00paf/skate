# INP-09: Pointer contract test steps

Start from a NEW project (`.\gradlew.bat run`, then File > New Project, then Create Project). Move the editor window away from the top-left of the desktop, or onto a second monitor, before you start.

1. Open the `FeatureShowcase` scene. Click several objects in the viewport: the one under the cursor is selected each time, also near the viewport edges.
2. Save the scene (Ctrl+S), close the project and reopen it from disk. Open `FeatureShowcase` again and press Play.
3. Click each legend button on the left: the button under the cursor reacts and the camera glides to that area. Click **Overview**.
4. Hold the right button inside the game view and drag: the camera looks around, at the same speed whatever the viewport panel size. Make the panel smaller and repeat. Right-dragging that starts on the Hierarchy or Inspector does not turn the camera.
5. Stop. On `Floor`, add a Kotlin script with `onUpdate { ctx, _ -> if (ctx.input.isMouseButtonDown(0)) ctx.log.info("click") }`. Save, Play, and click in the game view: `click` is logged in the console Engine tab. Clicking the Inspector logs nothing.
6. Window > Input Testing: the mouse position follows the cursor inside the game view, and the delta is 0 when the mouse is still.
7. Standalone: with `FeatureShowcase` as the start scene, export the project (project export action), then double-click `run-game.bat` in the export folder. The legend buttons respond under the cursor (step 3) and right-drag look works (step 4).
