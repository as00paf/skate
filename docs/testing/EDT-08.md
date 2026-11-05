# EDT-08 – Shell command panel: test steps

Start from a NEW project (`.\gradlew.bat run` → File > New Project → name, location, Create Project).

1. View > Windows > Debug > **Shell**: the panel opens and can be docked like any window. The status reads "Idle"; hovering it shows the working directory (the engine checkout).
2. Type `echo hello` and press Enter: `> echo hello` (blue), then `hello`, then "Exited with code 0" (green). Type `exit 3` and press Enter: the result line is red and the status reads "Exited with code 3".
3. **Presets** > "Gradle: run all tests" fills the input; click **Run**. Output lines stream in while the editor stays responsive: move the viewport camera and open menus while it runs. **Run** is disabled and the status counts seconds.
4. Click **Cancel** while Gradle is running: within a couple of seconds the status reads "Cancelled", and Task Manager shows no leftover `java`/`cmd` child process from that run.
5. **Presets** > "Skate CLI: validate this project", then Run: the CLI reports the new project as valid (2 scenes) and exits with code 0.
6. Press Up/Down in the input to walk back through the commands you ran. Close and restart the editor, reopen Shell, and press Up: the history is still there (`shell_history.txt` next to `editor.config`).
7. Switch the editor language to French (Editor Settings): the panel's texts are translated, and it keeps its dock position and open state. Right-click the output to Copy all or Clear.
