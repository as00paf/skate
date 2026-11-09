# GUI-13 test steps: map view in the Feature Showcase

The showcase has a minimap (bottom left) and a full-screen map. Both are drawn from the world: the floor, one coloured
pad per demo area, and a yellow square for "you". The showcase has no player character, so "you" is the scene camera.
North (-Z, the back row) is at the top. Only a newly created showcase has the map: existing showcase scenes don't
change.

1. `.\gradlew.bat run`, create a NEW project and open the Feature Showcase scene. The minimap and a **Map** button
   are in the bottom-left corner.
2. Press Play. The yellow square stays in the centre of the minimap, and the area pads around it scroll as you fly
   (RMB + WASD) or click an area in the legend. Pads at the minimap's edge are cut, not drawn outside it.
3. Click **Map** → a dimmed full-screen map with every area pad and its name. The yellow square is where the camera
   is. The game behind keeps running.
4. Press Esc (or click **Close**) → the map closes, and the pause menu does NOT open. Press Esc again → the pause menu opens.
5. Fly to the back-left (Lights) area, then open the map → the yellow square is on the Lights pad, at the top left.
6. Pause menu → Language → the map labels, title and button switch to French.
7. Export the project and run the game: the minimap, Map and Esc behave the same.
