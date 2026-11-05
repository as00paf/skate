# EDT-42: Screenshot folder

1. Start the editor and create a NEW project, for example `ShotTest`. Click the camera (screenshot) button in the viewport toolbar.
   **Expected:**
   - No native message box opens.
   - The status bar shows "Screenshot saved to `<path>`" for a few seconds, and the Console (Editor tab) logs the same path.
2. Open the folder in File Explorer.
   **Expected:** the PNG is in `ShotTest/Screenshots/`, not in a `screenshots/` folder next to the editor or wherever it was started.
3. Close the project (hamburger menu > Close Project), close the wizard, and click the screenshot button in the viewport toolbar again.
   **Expected:** the path shown is in the editor's user-data folder (`%APPDATA%\Skate\Screenshots` on Windows).
4. Take two screenshots quickly.
   **Expected:** two separate files with different names, both listed in the Console.
