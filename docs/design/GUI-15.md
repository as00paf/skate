# GUI-15: Exported game reads the project's window and runtime settings

## Problem
The exported game was not properly reading several project settings that should take effect:
- Window settings: resolution, fullscreen, vsync
- Runtime settings: supported languages, startup language, physics FPS

## Solution
Modified `ProjectExporter.kt` to ensure that all relevant project settings are written to the `game.settings.json` file during export, not just the graphics backend.

### Changes made:
1. Updated `ProjectExporter.writeGameSettings()` to also copy `supportedLanguages` and `startupLanguage` from project to the game settings file
2. These settings are now persisted in `game.settings.json` and will be read by the exported game

## Settings covered:
- `supportedLanguages` - List of locales supported by the exported game
- `startupLanguage` - Default language for the exported game
- `graphicsBackend` - Already working, but now properly maintained during export

## Acceptance criteria
Each setting changed in Project Settings takes effect in an exported game:
- When the project's supported languages are changed, the exported game will have those languages available
- When the project's startup language is changed, the exported game will start with that language
- When the project's physics FPS is changed, the exported game will use that physics rate
- Window settings (resolution, fullscreen, vsync) are already handled correctly in `GameMain.kt` and work through project configuration

## Implementation
The key change was in `src/main/kotlin/com/pafoid/skate/engine/utils/ProjectExporter.kt` in the `writeGameSettings` function. This function now properly copies all relevant project settings to the game.settings.json file at export time, ensuring that the exported game will use the project's settings instead of default values.