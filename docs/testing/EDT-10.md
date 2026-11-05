# EDT-10: Editor UX pass (manual test)

Start the editor and create a NEW project, for example `UxTest`.

1. **Empty states.** Close the project (hamburger menu > Close Project), then open the Hierarchy, Properties and Project windows.
   **Expected:** each shows "No project open" with New Project and Open Project buttons. Reopen `UxTest` from Recent Projects. In the Hierarchy, type `zzz` in the search box. **Expected:** "No results". Clear the search.

2. **Shortcuts in menus.** Open the hamburger menu > File and Edit.
   **Expected:**
   - New Scene shows Ctrl+N, Save Scene shows Ctrl+S and Save Scene As shows Ctrl+Shift+S.
   - Save Project and Import Scene show no shortcut.
   - Hovering Undo shows "Shortcut: Ctrl+Z". The viewport's Translate tool tooltip says "Shortcut: 2".

3. **New shortcuts.** In the viewport, right-click > Create Empty, then:
   - press F to frame the object;
   - press F2 to rename it inline in the Hierarchy;
   - press Ctrl+D to duplicate it, then Ctrl+Z to undo and Ctrl+Shift+Z to redo;
   - press F5 to start Play and F5 again to stop.

   **Expected:** each shortcut works.

4. **Undo in inspectors.** Select a light. In Properties, drag Intensity, then change Color.
   **Expected:**
   - Ctrl+Z undoes each change in one step, and the Command History shows one entry per change.
   - Right-click the component header > Reset to default, then Ctrl+Z. **Expected:** the values come back.
   - Right-click the viewport > Create Light > Point, then Ctrl+Z. **Expected:** the light is removed.

5. **Key Bindings.** Open Settings > Key Bindings > Editor. Click the Focus button and press Del.
   **Expected:**
   - Both Focus and Delete show "Same key as: ...". Press Reset to Defaults; the warnings go away.
   - The Camera tab lists Move up / Move down (Space / Left Shift).
   - The Fixed shortcuts tab lists Ctrl+Z, Ctrl+S and the others.
   - Editor Settings > Key Bindings only has a button that opens this window.

6. **Error dialogs.** Close the editor. In the `UxTest` folder, replace the contents of `UxTest.skateproject` with `{ broken`, then start the editor.
   **Expected:** a "Could not open the project" dialog shows the file path, Copy details works, and OK leads to the project wizard. Restore the file afterwards, or create a new project.

7. **Focused windows.** Open the Console, click a log line, and press Ctrl+C with an object selected in the scene.
   **Expected:** the log line is copied, and no object is copied (Edit > Paste does not add one). In the Hierarchy, select an object and press Home. **Expected:** the first object is selected and the camera does not reset.

8. **Icons.** In the Hierarchy context menu, Duplicate uses the clone icon (not the copy icon). In the hamburger menu, Quit uses the exit icon (not the trash can).
