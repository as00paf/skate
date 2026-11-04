# TLG-08 – Feature Showcase in every new project: test steps

Start from a NEW project (`.\gradlew.bat run` → File > New Project → name, location, Create Project).

1. The scene tabs show `MainScene` (active) and `FeatureShowcase`; `Scenes/FeatureShowcase.scene` and `Assets/tone.wav` exist in the project folder.
2. Switch to `FeatureShowcase`: a grey floor with ten coloured pads, the overlay legend top-left and the widget panel bottom-right. No `!!key!!` text. Every Hierarchy entry except `Camera` and `Floor` starts with `[Area]`.
3. Without playing: in `Lights`, red/blue/green lighting is visible. In `Shadows`, the casters throw sun shadows and `NoCaster` throws none; the spot light and point light there also cast shadows. In `Materials`, the metal sweep, the glowing cube, the textured cube and the transparent cube are visible.
4. Press Play: the `Particles` emitters run (fire, smoke, spark bursts, fountain); the `Physics` boxes settle and the ball rolls; the `Joints` pendulum and lamp swing and the slider block drops 2 m and stops.
5. Still playing: the `Vehicle` car settles level on its wheels. Moving the camera toward `Audio` makes the tone louder, and Stop silences it. The `Animation` character walks in place.
6. In the overlay, hover and press the button: solid background that changes per state. Drag the slider. The legend and panel stay in their corners when the window is resized.
7. In `Prefabs`, all 16 library pieces are present and textured. Enable debug draw on the scene's `ScenePhysicsComponent` to see their colliders.
8. CLI: `.\gradlew.bat :tools:cli:run --args="sample --dir <tmp>"`, then `... --args="run <tmp>/FeatureShowcase --frames 60"` → prints a run summary with no crash.
