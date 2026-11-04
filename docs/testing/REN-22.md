# REN-22 test steps: editor frame rate (shadows, picking, per-pass timings)

Setup: `.\gradlew.bat run`, create a NEW project (File > New Project) and keep the default MainScene open. The shadow
quality is MEDIUM (the default). Watch `FPS:` in the status bar.

1. Leave the mouse outside the viewport and don't move the camera → the frame rate is well above the round-10 rate
   (offscreen at 1920x1080: about 340 FPS, versus about 38 FPS with PR #13 before this fix).
2. Open the Render Graph window and tick **Show Performance** → each pass shows `Execution Time` (CPU) and
   `GPU Time`. With the camera still, `ShadowPass` GPU time is about 0, and `PickingPass` is about 0 while the mouse is
   outside the viewport.
3. Orbit the editor camera → `ShadowPass` GPU time goes up while the camera moves (the cascades re-render), then
   drops back to about 0 when it stops. Shadows look the same as before: no lag, no popping, and the skater and board
   cast shadows.
4. Move the mouse over the skater → the hover highlight follows the pointer as before, and `PickingPass` shows GPU
   time while you hover. Click the skater, then click the floor → selection changes on the first click.
5. Move the mouse in from outside the viewport and click an object right away → that object is selected, not
   whatever was under the pointer earlier.
6. Select the skater and move it with the gizmo → its shadow follows every frame. Undo → the shadow goes back.
7. Untick **Enabled** on `ShadowPass` → the shadows disappear. Tick it again → they come back right away.
