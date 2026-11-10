# GPL-22: MainScene Camera button

Use a **new** project: existing projects keep their saved HUD with the old Restart button.

1. Create a new project. MainScene opens. Press **Play**.
2. The HUD's top-right button now reads **Camera** (French: **Caméra**). Click it: the view switches to first person, at the skater's eyes, facing where the skater faces.
3. Click it again: fixed camera, high up in front of the start area, looking at the spawn. Click again: back to the third-person follow camera.
4. Press **C** (or the gamepad **Back/View** button): the cameras cycle in the same order. Ride around in first person; the view follows smoothly.
5. Press **Esc** and choose **Restart**: play restarts on the third-person camera, and the next press goes to first person again.
6. Stop Play: the editor viewport is back on the editor camera. Open the key bindings: **Next Camera** is listed under Gameplay > Camera and can be rebound.
7. Export the game, run it and repeat steps 2 to 5 in the exported build.
