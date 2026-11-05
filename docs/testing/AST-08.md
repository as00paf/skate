# AST-08: Export .skmat materials with their textures, manual test

1. Run `.\gradlew.bat gameJar`, then `.\gradlew.bat run`. Create a **new project** with the default settings.
2. In the default scene, select **[Materials] Textured** (the asphalt cube). In the Properties material inspector, change its base color tint, then **Save As...** `Assets\Materials\TexturedRock.skmat`. The component now links that file.
3. **File > Save Project**.
4. **File > Export Project**, keep the defaults and click **Export**. The export ends with "Exported N assets ...".
5. Open `<project>\builds\atlas_report.txt` (or `manifest.json`). It lists `Assets/Materials/TexturedRock.skmat`, the asphalt texture it uses, and `Materials/ShowcaseJade.skmat`.
6. Double-click `builds\run-game.bat`. The **[Materials] Textured** cube shows the asphalt texture with your tint, and **[Materials] Asset** shows the jade-green ShowcaseJade material, both looking as they did in the editor.
7. Check that the game console or log has no `Could not resolve data for path: ...skmat` or texture errors.

Automated: `.\gradlew.bat :test --tests "*MaterialExportTest"` exports a project whose scene links a `.skmat` (one absolute and one project-relative texture), then loads the material and its textures back from `game.bin`.
