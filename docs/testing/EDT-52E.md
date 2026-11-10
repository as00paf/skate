# EDT-52E manual test steps: settings dialogs

Editor Settings, Project Settings, Key Bindings and Export are now dialogs that follow the window standard (icon title, `?` help, standard footer). They no longer appear in View > Windows.

1. Run `.\gradlew.bat run` and open a project. Open **View > Windows**. Expected: Editor Settings, Project Settings and Key Bindings are not listed.
2. Open **Editor Settings** from the gear. Expected: a floating dialog (it cannot be docked) with a gear icon in its title, a `?` at the right of the header that opens help placeholders, and a right-aligned footer **Cancel · Apply · OK**. Apply is grayed out until you change something.
3. Change the theme, press **Esc**. Expected: the dialog closes and the theme goes back. Reopen it, change the theme, click **OK**. Expected: the theme is kept.
4. Open **Project Settings**. Expected: a cube icon, footer **Cancel · Apply · OK**, and a new **Input** category on the left showing "Gamepad tuning is moving here". Close the project (**File > Close Project**) and reopen Project Settings. Expected: the shared "No project open" panel with New/Open Project buttons, and a lone **Close**.
5. Open **Key Bindings** (keyboard icon). Pick the **Editor** tab. Expected: footer **Reset to defaults · Close**. Pick **Test**. Expected: "Input testing is moving here", and only **Close**.
6. On the **Editor** tab, click a shortcut's key button, then press **Esc**. Expected: the capture is cancelled and the dialog stays open. Press **Esc** again. Expected: the dialog closes.
7. Open **File > Export…**. Expected: an export icon, the options without an in-page Export button, and footer **Cancel · Export** (Export grayed out while the game jar is missing or the folder is invalid). Click **Export**. Expected: progress shows with a lone **Close**, then **Open Folder · Back to Options · Close** when done.
8. Close and reopen each dialog. Expected: it reopens at the size and position you left it.
