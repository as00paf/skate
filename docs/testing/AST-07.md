# AST-07: Game strings in exported builds, manual test

1. `.\gradlew.bat gameJar`, then `.\gradlew.bat run`. Create a **new project** with the default settings, keep the default scene, and save it.
2. Run **File > Export Project**. The log reports `Packed N assets`, and the export succeeds.
3. Open `<project>/builds/manifest.json` (or `atlas_report.txt`). It lists `strings/game/hud.properties`, `hud_fr.properties`, `showcase.properties` and `showcase_fr.properties`.
4. Copy the `builds` folder somewhere outside the repo (e.g. `C:\temp\skate-build`), so no `assets/strings` folder sits next to it. Double-click `run-game.bat` there.
5. The HUD (score, timer, trick name) shows English text, with no `!!key!!` placeholders.
6. Press **Esc**. The pause menu reads **Paused / Resume / Restart / Language: English / Quit**.
7. Click **Language**. The menu switches to **Pause / Reprendre / Recommencer / Langue : Français / Quitter**, and the HUD is French too. Accents display correctly.
8. Click **Language** again to return to English, then **Quit**.

Automated: `.\gradlew.bat :test --tests "*ProjectExporterTest" --tests "*PackedStringsTest" --tests "*StringManagerTest"`. `Export_ShipsGameStringsAndTheGameReadsThemInEnglishAndFrench` exports a project, then reads `game.bin` the way `GameMain` does and checks every game key in EN and FR.
