# AST-06 test steps
1. `.\gradlew.bat :test --tests "*PackBuilderTest" --tests "*ProjectExporterTest"` -> 7 PASSED.
2. `.\gradlew.bat gameJar` -> `build/libs/*-game.jar` exists.
3. `.\gradlew.bat run`, open a project, trigger Export (project export action).
4. Open `<project>/builds`: expect `game.bin`, `manifest.json` (sha256 per entry), `skate-game.jar`, `run-game.bat`, `atlas_report.txt`; unreferenced textures absent from the manifest.
5. Double-click `run-game.bat`: the game opens standalone (not run by me; needs GPU).
