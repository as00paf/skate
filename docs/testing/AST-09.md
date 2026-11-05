# AST-09: Project-relative asset references, manual test

1. Run `.\gradlew.bat gameJar`, then `.\gradlew.bat run`. Create a **new project** (e.g. `C:\temp\RelTest\Game`) and save it.
2. Open `Scenes\MainScene.scene` and `Scenes\FeatureShowcase.scene` in a text editor. Model, texture, sound, controller and `.skmat` references read like `Assets/EngineDefaults/...`, with no `C:\...` path. The `.skateproject` file is the same.
3. Close the editor. Move the whole project folder somewhere else, e.g. `D:\elsewhere\Game`, or rename `RelTest`.
4. Start the editor and **Open Project** from the new location. Both scenes load without "not found" errors in the console. The skater animates (controller), **[Materials] Asset** is jade green (`.skmat`), **[Materials] Textured** shows asphalt, and the sky is textured.
5. Press **Play** in the Feature Showcase scene: the showcase song plays.
6. **File > Export Project** with the defaults, then run `builds\run-game.bat`. Textures, the skater's animations and the linked materials match the editor, and the song plays in the showcase.
7. Switch the editor to French. In the Export Project dialog, type the project folder as the output folder and click **Export**. The failure message and its detail ("… est le dossier du projet …") are in French.

Automated: `.\gradlew.bat :test --tests "*AssetPathsTest" --tests "*ExportedReferencesTest" --tests "*ProjectRelativePathsGuardTest"`.

---

## Earlier AST-09 (prefab templates)


Status: engine API only (`PrefabStore`). Nothing in the editor calls it yet, so the check is automated.
The only editor change is that `.prefab` files are now classified as `FileType.PREFAB`.

1. Open a terminal in the repo root on Windows (natives are Windows-only). From WSL use `cmd.exe /c` in front.
2. Run: `.\gradlew.bat :test --tests "*PrefabStoreTest"`
   Expected: `BUILD SUCCESSFUL` with 3 PASSED:
   - `PrefabStore_TemplateRoundTripsThroughFile`: saved file contains `"schema"`, template equals after load.
   - `PrefabStore_InstancesWithOverridesSurviveSceneAndPrefabReload`: a scene with an instance and overrides is saved, reloaded with fresh serializer, prefab edited on disk, instance updated; override kept (translation x=5), prefab edit applied (scale x=3).
   - `PrefabStore_LoadAllReportsBadFilesAndKeepsGoodOnes`: a corrupt `.prefab` is reported, good ones load.
3. Run `.\gradlew.bat :test` for the whole suite. Expected: `BUILD SUCCESSFUL`.
4. Manual (optional): in the editor asset browser, a file named `x.prefab` placed in a project folder should show as a Prefab type. Not verified in the UI.
