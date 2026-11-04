# AUD-10 manual test: MP3 music in the default skate scene

Before you start, put the track at `assets/sounds/music/aquarela do brasil.mp3` in the engine checkout. `git status` must not list it (it is ignored).

1. Start the editor and create a **new** project. Check that `Assets/EngineDefaults/Sounds/Music/aquarela do brasil.mp3` exists in the project folder.
2. `MainScene` opens. Select `Music` in the hierarchy: it has an `AudioComponent` (2D, looping, bus Music). While editing, no music plays.
3. Press **Play**. The music starts within a moment and does not stutter while you skate around.
4. Move the camera or the skater far away. The volume and stereo image do not change (non-positional).
5. Press **Stop**. The music stops at once. Press **Play** again: it restarts from the beginning.
6. Optional, loop: let it play to the end of the track (or test with a short MP3). It restarts without stopping.
7. Missing-file check: delete the project's copy of the MP3, reopen the project and press **Play**. The console shows a single `Sound file not found ... aquarela do brasil.mp3` warning, and the scene runs normally without music.
