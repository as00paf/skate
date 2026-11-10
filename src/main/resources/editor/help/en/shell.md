# Shell

The Shell runs a command line program from inside the editor and shows what it prints. It saves you from opening a separate terminal for common tasks such as building or checking the project.

## What it is

A **command** is a line of text that starts a program, for example `gradlew build`. The Shell runs one command at a time in the project folder and streams its output live. The status line tells you whether the command is running, how long it took and its **exit code** (0 usually means success).

The **Presets** menu holds ready-made commands:

- build the engine with Gradle;
- run all the tests;
- show the Skate CLI help;
- validate, run headless or pack this project with the Skate CLI.

## How to use it

1. Type a command in the box at the bottom, or pick one from **Presets**.
2. Press **Enter** or click **Run**.
3. Read the output as it arrives. Errors from the program appear here, not in the Console.
4. Click **Cancel** to stop a command that takes too long.
5. Press **Up** and **Down** in the box to recall earlier commands.
6. Click **Clear** to empty the output.

Only one command runs at a time. Wait for it to finish, or cancel it, before you start the next one.

## Related windows

- [Console](console.md): the editor's own messages.
- [Export Project](export_wizard.md): build a playable copy of the game without typing commands.
