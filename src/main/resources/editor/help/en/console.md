# Console

The Console is where the editor and the engine write down what they are doing. When something does not work, this is the first place to look.

## What it is

Every message is one line with a level:

- **Info**: normal news, such as "scene loaded".
- **Warn**: something looks wrong but the editor keeps going.
- **Error**: something failed. Read these first.
- **Action**: a change you made in the editor, such as creating or deleting an object.

The **Engine** and **Editor** tabs separate the messages of the game engine from those of the editor itself. A counter at the top shows how many messages of each level there are.

## How to use it

1. Click **All**, **Info**, **Warn**, **Error** or **Action** to show only one level.
2. Type in the search box to keep only the lines that contain that text.
3. Click a line to select it. Hold **Ctrl** to add lines one by one, or **Shift** to select a range.
4. Press **Ctrl+C**, or right-click and choose **Copy Selected**, to copy the lines. **Copy All** copies everything in the panel. Paste them into a bug report or a message.
5. Right-click and choose **Clear All Logs** to start from an empty console.
6. Turn **Auto-Scroll** on to always see the newest message, or off to read older messages while new ones arrive.

Script errors that happen while the game is playing also appear here, with the script name and line.

## Related windows

- [Command History](command_history.md): the editor actions behind the Action lines.
- [Profiler](profiler.md): check whether the editor is slow.
- [Shell](shell.md): run a command and read its own output.
- [Script Editor](script_editor.md): fix the scripts that report errors.
