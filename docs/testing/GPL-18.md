# GPL-18: Exported game finds game.bin wherever it is started from, manual test

The game looks for `game.bin` in this order: `-Dskate.bin=<path>` (then only there), next to the running `skate-game.jar`, then in the working directory. If it finds none, it logs every path it searched. `game.settings.json` is kept next to the pack that was loaded.

1. `.\gradlew.bat gameJar`, then `.\gradlew.bat run`. Create a **new project**, then **File > Export Project** with the Windows target. Copy the `builds` folder to `C:\temp\MyGame`.
2. In Explorer, double-click `C:\temp\MyGame\skate-game.jar` (not `run-game.bat`). The game opens on MainScene. Change the language in the pause menu and quit: `C:\temp\MyGame\game.settings.json` now exists.
3. Open `cmd` in another folder (e.g. `cd %USERPROFILE%\Desktop`) and run `java -jar C:\temp\MyGame\skate-game.jar`. The game starts with the language you chose in step 2. No `game.settings.json` appears on the Desktop.
4. Rename `C:\temp\MyGame\game.bin` to `data.bin` and repeat step 3. The game exits and the console lists both searched paths: `C:\temp\MyGame\game.bin` and `...\Desktop\game.bin`, with the `-Dskate.bin` hint.
5. Run `java -Dskate.bin=C:\temp\MyGame\data.bin -jar C:\temp\MyGame\skate-game.jar`. The game starts.
6. In the repo, run `.\gradlew.bat runGame`. It fails at once with "runGame needs an exported pack…". Then run `.\gradlew.bat runGame -PskateBin=C:\temp\MyGame\data.bin`. The game starts from the compiled classes on that pack. In IntelliJ, run the Gradle task `runGame` with `-PskateBin=<path>` in its arguments.

Automated: `.\gradlew.bat :test --tests "*GameBinTest" --tests "*ProjectLifecycleEndToEndTest"`. Lifecycle step 7 now loads the exported pack through `GameBin` as if the jar sat in the export folder and the game were started from another directory.
