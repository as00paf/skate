# EDT-14: Audio bus and mute UI — manual test

From a **new project** (File > New Project); the default scene doesn't matter.

1. Open the **FeatureShowcase** scene, select **[Audio] Emitter3D** and open **View > Windows > Audio > Audio Inspector**. Under *Selected object* you see the source fields: sound file (the bundled MP3), random clips, Bus = MUSIC, volume 0.6, Loop and Play on awake checked, Streaming = AUTO, Priority, 3D with attenuation, min/max distance, rolloff and Doppler 0. The Properties window shows the same inspector. Switch the editor language to French and back: labels change and nothing collapses or jumps.
2. Press **Play** and walk the camera to the sphere: the song plays.
3. Open the **Mixer** header in the Audio Inspector and drag **Music > Volume** down slowly. The song gets quieter *while you drag*. Release, then press Ctrl+Z: the volume returns to its previous value, audibly. Ctrl+Y redoes it.
4. Tick **Music > Mute**: the song goes silent immediately and keeps its place; untick it and it continues from there. Mute **Master** too: everything is silent. Untick it.
5. In the source inspector, change **Volume** to 0.2 and release: quieter at once. Ctrl+Z restores 0.6. Set **Bus** to SFX with Doppler at 0: still no pitch wobble during camera glides. Ctrl+Z puts it back on MUSIC. A source on the MUSIC bus with Doppler above 0 shows "Music bus: Doppler is ignored."
6. Drop a sound from the asset browser onto **Sound file**, and another onto **Random clips > Drop sounds here**. Both changes are undoable. Remove the clip with **X**.
7. Stop, set **Music > Volume** to about 0.3 and mute **SFX**, then close and reopen the editor (the last project reopens). The mixer shows the same values and the song plays at the lower volume in Play.
8. Optional: export the project and run it. The song plays at the mixer volume set in the editor.
