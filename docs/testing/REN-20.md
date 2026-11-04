# REN-20 test steps: shadows and picking cover the whole hierarchy

Setup: `.\gradlew.bat run`, then create a NEW project (File > New Project). `MainScene` opens.

1. Select `Skateboard` (the physics root, no mesh of its own) and set its Transform Y to 0.6 → the board floats, and
   its visual child `Skateboard Model` casts a shadow on the floor.
2. Move `Skateboard` with the gizmo → the board's shadow follows it every frame, and stops updating when you stop.
3. Expand `Skateboard`, select `Skateboard Model`, and untick `castShadow` on its RenderComponent → the board's
   shadow disappears. Tick it again → the shadow comes back.
4. Hide `Skateboard` with the eye icon in the Scene Hierarchy → the board, its shadow, its hover highlight and its
   click target all disappear. Clicking where it was selects the floor. Show it again → everything comes back.
5. Untick the GameObject **Enabled** box on `Skateboard` → same result as step 4 (no drawing, no shadow, no picking).
   Tick it again.
6. Untick the RenderComponent `enabled` box on `Skateboard Model` → the board is not drawn, and clicking where it was
   does not select it.
7. Open the `FeatureShowcase` scene and click the "Shadows" focus button → `[Shadows] GroupChild` (a cube) and
   `[Shadows] GroupGrandchild` (a sphere above it) cast shadows. Move `[Shadows] Group` → both shadows move with it.
8. Press Play in either scene → the shadows still follow the skater and the board. Press Stop.
