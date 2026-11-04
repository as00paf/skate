# REN-21 test steps: 2D picking, UI after post-processing, highlight

Setup (once): `.\gradlew.bat run`, create a NEW project (File > New Project), open its scene. The default scene has
the `HUD` canvas (score text top-left, restart button top-right).

1. Look at the HUD in the viewport → the text and the button edges are sharp, not blurred by bloom or FXAA. The text
   has no box around it, and the viewport background never shows through it.
2. Orbit the camera so that grid lines and the camera/light visuals cross the HUD → they pass **under** the HUD.
3. Open the Render Graph window → `UiPass` is the last pass, after `PostProcessPass` and `DebugPass`. Untick its
   **Enabled** box → the HUD disappears. Tick it again → the HUD is back.
4. Right-click the viewport > Create Empty. Add `SPRITE_RENDERER` in the inspector, then set its position to (0, 1, 0)
   → a white square appears. Click elsewhere to deselect it, then click the square in the viewport → the sprite is
   selected (Scene Hierarchy and inspector). Before REN-21, clicking a sprite selected nothing.
5. Hover the skater, then click it → hovering gives a light green tint. Selecting gives a stronger green, and the
   skater stays opaque.
6. Select the skateboard root in the Scene Hierarchy → the whole board (deck, trucks, wheels) is tinted. Click again
   to drill down to one part → only that part is tinted.
7. Press Play with the skater still selected → there is no green tint in the game view, and the HUD still shows.
   Stop → the tint comes back.
