# EDT-34: Frame-rate independent editor camera, manual test

1. Start the editor, create a **new project** and open its default scene (Feature Showcase included). Keep the status bar FPS readout visible.
2. With **V-Sync on** (Settings > V-Sync), hold **W** in the viewport for about 3 seconds while timing it. The camera covers about 18 units (6 units/s).
3. Turn **V-Sync off** (FPS rises a lot) and repeat step 2: the camera covers the same distance in the same time. Repeat with **A/D** and the up/down keys.
4. Spin the mouse wheel quickly a few notches in each V-Sync mode: each notch zooms the same amount, and no notch is lost at low FPS.
5. Move far away, rotate the camera, then press **Home**. The camera glides back to the start view in about one second in both V-Sync modes and stops exactly there.
6. Middle-mouse drag (look/orbit): the rotation follows the mouse distance, not the frame rate.
7. Select an object and use **Focus Selected** from the viewport context menu: the camera jumps to it at any FPS.
8. Trigger a hitch (e.g. import a large model) while holding **W**: the camera does not jump forward when the frame recovers.
