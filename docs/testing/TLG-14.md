# TLG-14: Samples use project-relative paths, manual test

From the repository root (the CLI reads the engine assets from there):

1. Run `.\gradlew.bat :tools:cli:run --args="sample tetris --dir C:\temp\tlg14"`.
2. Open `C:\temp\tlg14\Tetris\Scenes\Game.scene` and `Tetris.skateproject` in a text editor. References read like `Assets/EngineDefaults/Models/cube.obj` and `Assets/Scripts/tetris.kts`, with no `C:\...` path.
3. Move the folder: rename `C:\temp\tlg14` to `C:\temp\tlg14-moved`.
4. Run `.\gradlew.bat :tools:cli:run --args="validate C:\temp\tlg14-moved\Tetris"`. It reports the project as valid with 2 scenes.
5. Run `.\gradlew.bat :tools:cli:run --args="export C:\temp\tlg14-moved\Tetris --out C:\temp\tlg14-game"`, then `.\gradlew.bat gameJar` and copy `build\libs\skate-game.jar` into `C:\temp\tlg14-game`.
6. Delete `C:\temp\tlg14-moved`. In `C:\temp\tlg14-game`, run `java -jar skate-game.jar`. Tetris starts: cubes are drawn, pieces fall, and the lock blip plays.
7. Repeat steps 1 to 6 with `sample marble-run` and `sample feature-showcase` (no folder names change). Both start from the exported folder alone; the showcase skater animates.
8. Open the moved Tetris project (before step 6) in the editor (`.\gradlew.bat run`). The scene loads without "not found" errors in the console.

Automated: `.\gradlew.bat :tools:cli:test --tests "*SampleExportTest"`.
