# AST-12: Graphics backend in the project and the export

## Manual test

1. `.\gradlew.bat run`, create a **new project**, then save it. `<project>\<name>.skateproject` contains `"graphicsBackend": "OPENGL"`.
2. **File > Export Project**. Under the target, the dialog shows "Graphics backend: OpenGL". Export with **Asset pack only**. `builds\game.settings.json` contains `"graphicsBackend": "OPENGL"`, and `builds\manifest.json` contains `"schema": 2` and `"graphicsBackend": "OPENGL"`.
3. Close the editor and change the project file to `"graphicsBackend": "VULKAN"` (the UI comes in EDT-43). Reopen it and save: the value is kept.
4. Export again. The dialog shows "Graphics backend: Vulkan (experimental)" with a warning, and both JSON files now say `VULKAN`. The locale in `game.settings.json` is kept.
5. Switch the editor to French: the line reads "Backend graphique : Vulkan (expérimental)". The editor itself still renders with OpenGL.

Automated: `.\gradlew.bat :test --tests "*GraphicsBackendTest" --tests "*ProjectExporterTest" --tests "*ProjectLifecycleEndToEndTest"`.

## Where the backend lives (decision)

| Place | Written by | Read by | Role |
|---|---|---|---|
| `.skateproject` → `Project.graphicsBackend` | editor (EDT-43 UI) | export; game, from the packed project in `game.bin` | the project's choice, the source of truth |
| `game.settings.json` → `GameSettings.graphicsBackend` (next to `game.bin`) | export (merged; the player's locale is kept) | game startup | what this build starts with; a player can edit it to fall back to `OPENGL` |
| `manifest.json` → `PackManifest.graphicsBackend` (schema 2) | export | nothing at runtime | build metadata, for support and QA |

No migration: project files without the field open as `OPENGL` (rule 8), and `game.settings.json` without it falls back to the project.

## API for EDT-43 (editor UI) and GPL-19 (game startup)

- Enum: `com.pafoid.skate.engine.render.rhi.GraphicsBackend` (`OPENGL`, `VULKAN`), with `DEFAULT`, `experimental`, `labelKey` and `parse(String?)`.
- **EDT-43**: edit `project.graphicsBackend` through a Command (undo/redo), then save the project. For labels, use `strings.getString(backend.labelKey)`: `lbl.graphics_backend.opengl` and `lbl.graphics_backend.vulkan` are already in `src/main/resources/editor/strings/assets*.properties`, so reuse them and don't duplicate the keys. Show a warning when `backend.experimental`. The editor must keep rendering with OpenGL.
- **GPL-19**: in `GameMain`, after `loadProjectAndIcon` and before creating the `Window`, call:
  ```kotlin
  val settings = GameSettingsStore(GameSettings.fileIn(packDir), engine.logger).settings
  val backend = GraphicsBackend.resolve(project.graphicsBackend, settings.graphicsBackend) // -Dskate.graphicsBackend wins
  ```
  If `VULKAN` fails to initialise, log it and fall back to `OPENGL`.
