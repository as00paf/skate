# AST-14: one assets folder per project

1. Launch the editor and create a new project.
2. In the Project window (or Explorer), check the project root holds `Assets/`, `Scenes/`, `Builds/` and the `.skateproject` file, with no other assets folder.
3. Open `Assets/`: it contains `EngineDefaults/` and `strings.properties` only (no `textures/` folder next to it).
4. Open `Assets/EngineDefaults/`: `Characters`, `Models`, `Shaders`, `Sounds`, `Textures` (one `Shaders` folder; `Textures` holds `app_icon.png`).
5. Press Play on `MainScene`: the skater, obstacles, sky and music load with no missing-asset errors in the console.
6. Asset Browser > Textures: `.jpg` files copied into `Assets/` are listed.
7. Export the project (File > Export): it succeeds into `Builds/` and the exported game starts.
8. Run `skate validate <project>` from the CLI: no "Duplicate assets folder" warning.
