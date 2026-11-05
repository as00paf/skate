# GPL-08: Game logging and paths, manual test

The standalone game takes every file location from the engine (`game.bin` next to the jar, or `-Dskate.bin=<path>`). It reads UI text only from the pack, and it reports errors through the logger instead of printing them.

1. `.\gradlew.bat gameJar`, then `.\gradlew.bat run`. Create a **new project** with the default settings and save it.
2. Run **File > Export Project**. The log reports `Packed N assets`.
3. Copy the `builds` folder outside the repo (e.g. `C:\temp\skate-build`) and double-click `run-game.bat`. The game opens full screen on MainScene. The HUD shows English text with no `!!key!!` placeholders, and **Esc** opens the pause menu. Quit.
4. Rename `game.bin` in that folder to `data.bin` and run `run-game.bat` again from a console (`cmd`, then `run-game.bat`). The game does not start, and the console shows one line: `Game data not found: C:\temp\skate-build\game.bin (pass -Dskate.bin=<path> to use another pack)`. No stack trace or bare path is printed.
5. In the same console run `java -Dskate.bin=data.bin -jar skate-game.jar`. The game starts as in step 3.
6. Move the folder somewhere else (another drive or user folder) and repeat step 3. It still runs, because no machine-specific path is left.

Automated: `.\gradlew.bat :test --tests "*GameLoggingAndPathsGuardTest" --tests "*PlatformApiGuardTest" --tests "*GameBinTest" --tests "*ProjectExporterTest"`.
