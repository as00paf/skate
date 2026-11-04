# AUD-09 manual test: Feature Showcase audio

Turn the system volume up. The tone is a plain 440 Hz beep.

1. Start the editor and create a **new** project (old projects are not migrated).
2. Open the `FeatureShowcase` scene. While editing, you should hear nothing.
3. Press **Play**. You should hear a quiet, steady tone right away: that is `[Audio] Ambient2D`.
4. Fly the camera (right mouse + WASD) towards the cyan sphere in the `Audio` area (middle row, far right). The tone gets clearly louder as you get closer: that is `[Audio] Emitter3D`.
5. Stand next to the sphere and turn the camera left and right. The emitter pans between your left and right speakers.
6. Press **Stop**. All sound stops at once.
7. Press **Play** again. The ambient tone starts again.
8. Optional: select `[Audio] Emitter3D` while editing and use the inspector's Play/Stop preview. It still works outside Play.
