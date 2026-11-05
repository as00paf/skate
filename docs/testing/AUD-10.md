# AUD-10 manual test: MP3 song in the Feature Showcase

The song is bundled in the engine at `assets/sounds/music/aquarela do brasil.mp3`.

1. Start the editor and create a **new** project. Check that `Assets/EngineDefaults/Sounds/Music/aquarela do brasil.mp3` exists in the project folder.
2. Open `MainScene` and press **Play**. There is no music. Press **Stop**.
3. Open `FeatureShowcase` and select `[Audio] Emitter3D` (the cyan sphere in the `Audio` area). Its `AudioComponent` points at that MP3 (3D, looping, bus Music). It is the only audio source in the scene.
4. Press **Play** and fly to the sphere. The song plays without stutter, gets louder as you approach and pans as you turn.
5. Leave it running past the end of the song (about 3 min 43 s). It loops back to the start without stopping.
6. Press **Stop**. The song stops at once.
7. Missing-file check: delete the project's copy of the MP3 and press **Play**. The console shows a single `Sound file not found ... aquarela do brasil.mp3` warning, and the scene runs normally without sound.
