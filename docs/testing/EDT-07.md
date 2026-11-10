# EDT-07 test steps: editor themes

Themes are JSON files in `src/main/resources/editor/themes/` (`dark`, `light`, `high_contrast`), each with colors (`#RRGGBB` or
`#RRGGBBAA`), sizes (rounding, padding, spacing, border sizes) and a font scale. The format is documented on
`ThemeParser`. Picking a theme in the settings previews it at once. OK or Apply saves it to `editor.config`, and
Cancel or closing the window goes back to the saved theme. The default is Dark, the editor's previous look.

Setup: run `.\gradlew.bat run` and create a NEW project (File > New Project).

1. The editor looks as before (Dark). Open Settings, then Interface. The Theme list shows Dark, Light and
   High contrast.
2. Pick Light -> the whole editor (menus, panels, viewport toolbar, this window) turns light right away, with no
   restart.
3. Pick High contrast -> black and white with yellow accents, square corners, thicker borders and slightly larger
   text. Click Cancel -> back to Dark.
4. Pick Light, then click OK. Close the editor and start it again -> it opens in Light.
5. Switch the editor language to French (Settings > General, then OK), and open Interface again -> the themes read
   Sombre, Clair and Contraste élevé.
6. Optional: copy `src/main/resources/editor/themes/light.theme.json` to `mine.theme.json`. Set `"id"` to `mine`, `"nameKey"` to
   `settings.theme.mine` and `"accent"` to `#FF00FF`, then restart -> "!!settings.theme.mine!!" is listed (add the
   string to name it). A file with a misspelled color name is skipped, with a warning in the console.
7. Run `.\gradlew.bat test` -> BUILD SUCCESSFUL (`ThemeParserTest`, `ThemeApplyTest`, `ThemeSettingTest`).
