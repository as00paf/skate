# AST-05 test steps: hot reload of textures, models, sounds

Hot reload runs every frame from `Engine.update` (`AssetsManager.pollHotReload`, polls every 500 ms).
It only watches assets already loaded through `AssetsManager` (cached by absolute path).
The texture object is kept and its GL payload is swapped, so anything rendering it updates.
`AssetEvent.Reloaded` is published; the editor does not yet listen to it (NEEDS EDT).

## Automated
1. `.\gradlew.bat :test --tests "*AssetReloaderTest"`
   Expected: 4 PASSED (reload after file is stable, no reload while still changing, failed reload reported and retried, deleted/unwatched ignored).
2. `.\gradlew.bat :test` Expected: `BUILD SUCCESSFUL`.

## Manual (editor, needs a GPU; exact menu labels not verified by me)
1. Launch: `.\gradlew.bat run` Expected: the Skate editor window opens.
2. Create a new project via the File menu (New Project), choose an empty folder, confirm. Expected: project opens with an empty scene.
3. Copy a PNG (e.g. `a.png`) into the project folder (Explorer). Open the asset browser Textures tab. Expected: `a.png` thumbnail visible (this loads it into the cache).
4. Add a cube to the scene and apply `a.png` as its texture (Textures tab, drag/select onto the cube). Expected: cube shows the image in the viewport.
5. Edit `a.png` in an image editor (change colours) and save over the same path. Wait ~1-2 seconds.
6. Expected: the cube and the thumbnail show the new image without restarting or touching the editor. A broken/half-written file is ignored until stable; a corrupt file logs `Hot reload failed for ...` and keeps the old image.
7. Same check for a model (.glb) used by an object: replace the file; the mesh updates. For a .wav, replace the file; the next playback uses the new sound.
Known limits: if the thumbnail does not refresh, that is the missing EDT hook; models reload meshes only if already cached via `loadModel`/`requestModel` keys.
