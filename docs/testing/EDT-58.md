# EDT-58 test steps: the editor camera is remembered per scene

Each scene keeps its own editor camera: position, rotation, perspective/orthographic mode, zoom and FOV. The data is
editor-only. It is saved in `<project>/.editor/scene-cameras.json`, which export never packs (hidden folder).

1. `.\gradlew.bat run`, open a project with at least two scenes, and open both as tabs.
2. In scene A, fly the camera somewhere recognisable (RMB + WASD), and switch to orthographic in the camera settings.
3. Switch to scene B's tab and move the camera somewhere else, in perspective.
4. Switch back to A → A's camera is back where you left it, in orthographic. Switch to B → B's view is back.
5. Press Play, then Stop (or use the pause menu's Restart) → the editor camera has not moved.
6. Quit the editor and start it again (the project reopens) → the current scene's camera is where you left it.
   Open the other scene → its camera is restored too.
7. Export the project → `scene-cameras.json` is not in `atlas_report.txt`, and the exported game starts from its own scene camera.
