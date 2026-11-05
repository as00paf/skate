# EDT-09: Export wizard, manual test

1. Run `.\gradlew.bat gameJar`, then `.\gradlew.bat run`. Create a **new project** with the default settings.
2. **File > Export Project**. The *Export Project* dialog opens with the Windows target selected, "Game jar: ...\build\libs\...-game.jar" shown, every scene ticked, the default scene greyed out "(default scene, always included)", Compress and Only referenced assets on, and the output folder set to `<project>\builds`.
3. Untick one non-default scene (e.g. a showcase scene), then click **Export**. The dialog shows "Saving the project...", then a progress bar with the step (Collecting / Packing / Verifying / Copying the game jar / Writing run-game.bat) and the file being packed, ending at 100% with "Exported N assets (… KB) to …\builds".
4. Click **Open Folder**. `builds` contains `game.bin`, `manifest.json`, `skate-game.jar`, `run-game.bat` and `atlas_report.txt`. The unticked scene is not listed in `manifest.json`.
5. Double-click `run-game.bat`. The game starts on the default scene, and the HUD shows English text with no `!!key!!`. Press **Esc**: the pause menu shows Paused / Resume / Restart / Language: English / Quit. **Language** switches it to French. **Quit** closes the game.
6. Back in the editor, click **Back to Options**. Choose **Asset pack only**, set the output folder with **Browse...** to a new folder, and export. Only `game.bin`, `manifest.json` and `atlas_report.txt` are written there.
7. Type the project folder itself as the output folder. A red message says it can't be used, and **Export** is disabled. Click **Default** to restore `<project>\builds`.
8. Switch the editor to French (**Settings > Language > Français**) and reopen the dialog: every label is French, and the dialog keeps its position and size.

## Game jar location (AST-11)

1. Delete `build\libs\skate-game.jar` (or don't run `gameJar`), start the editor and open **File > Export Project**. A red message reads "No game jar found. Searched: …\build\libs. Run gradlew gameJar, or choose a game jar below.", and **Export** is disabled.
2. Start the editor from another working directory, e.g. `cd C:\temp` then `java -jar <repo>\build\libs\skate.jar`, or a run configuration with another working directory. With `skate-game.jar` built next to `skate.jar`, the dialog still shows "Game jar: <repo>\build\libs\skate-game.jar".
3. Under **Game jar (optional …)**, click **Choose...** and pick a game jar elsewhere. With no jar next to the editor, the dialog uses it, and it is still set after an editor restart. **Clear** removes it. `-Dskate.gameJar=<path>` works the same way.
4. Rebuild the editor (`.\gradlew.bat build`) without `gameJar`. The dialog warns that the game jar is older than the editor build.
