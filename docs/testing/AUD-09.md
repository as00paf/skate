# AUD-09 manual test: Feature Showcase audio

The emitter now plays the bundled song (AUD-10) instead of a generated tone.

1. Start the editor and create a **new** project (old projects are not migrated).
2. Open the `FeatureShowcase` scene. While editing, you should hear nothing.
3. Press **Play**. From the overview camera the song is faint: the emitter is far away.
4. Fly the camera (right mouse + WASD) towards the cyan sphere in the `Audio` area (middle row, far right). The song gets clearly louder as you get closer.
5. Stand next to the sphere and turn the camera left and right. The song pans between your left and right speakers.
6. Press **Stop**. All sound stops at once. Press **Play** again: the song starts again.
7. Optional: select `[Audio] Emitter3D` while editing and use the inspector's Play/Stop preview. It still works outside Play.
