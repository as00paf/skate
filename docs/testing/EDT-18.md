# EDT-18: Editor logging and user-data paths

Per-user editor files now live in the user-data folder instead of the working directory:
`%APPDATA%\Skate` on Windows (macOS: `~/Library/Application Support/Skate`, Linux: `~/.config/skate`). That covers
`editor.config`, `imgui.ini`, `imgui.windows` and `search_history.json`. `-Dskate.userDataDir=<folder>` overrides
it. Old files in the repo folder are not migrated (rule 8): the first start uses default settings and layout.

1. Start the editor. Open **File > New Project**: the location field shows `<Documents>\Skate Projects`, and that
   folder now exists. Type another existing folder: the text stays (it used to snap back every frame).
   **Browse...** also fills the field. Create a **new project** there.
2. Move a window, change the language or the shadow quality, then close the editor. `%APPDATA%\Skate` contains
   `editor.config`, `imgui.ini` and `imgui.windows`, and no new `editor.config` / `imgui.ini` appeared in the
   working directory.
3. Restart: the layout and settings from step 2 come back.
4. **Ctrl+Shift+F**, search `cube` and pick a result. `%APPDATA%\Skate\search_history.json` contains the query, and
   no `search_history.json` is written in the working directory.
5. Click the viewport toolbar's screenshot button: the Console (Editor tab) logs "Screenshot captured (W x H)".
6. Corrupt `search_history.json` (type random text into it) and search again: the Console shows a warning and
   search still works. Nothing is printed to the terminal.
