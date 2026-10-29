# AST-09 test steps: `.prefab` persistence

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
