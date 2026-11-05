# EDT-41: Reopen a project from Recent Projects

Fix: the editor reopened projects from a relative `projectPath` read from the `.skateproject` file. It now uses the absolute path kept in the editor settings.

1. Start the editor and create a NEW project, for example `RecentTest` in any folder. The default scene opens.
2. Open the hamburger menu and choose **Close Project**. Then choose **Recent Projects > RecentTest**.
   **Expected:** the project opens with its scenes. The console shows no "Project file does not exist or invalid extension" error.
3. Close the editor and start it again.
   **Expected:** `RecentTest` reopens at startup (last project).
4. Close the editor. Move the `RecentTest` folder somewhere else, start the editor, and open the moved `RecentTest.skateproject` with **Open Project...**.
   **Expected:** the project and its default scene open from the new folder.
5. Close the project and reopen it from **Recent Projects**.
   **Expected:** it opens from the new folder, and the old location no longer appears in the list. **Project Settings > Recent projects** shows the new path.
