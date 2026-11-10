# Script Editor

The Script Editor is a text editor for your game's scripts. A script is a small program that gives an object its own behaviour, for example "spin slowly" or "open the door when the player is near".

## What it is

Skate scripts are Kotlin script files (`.kts`) stored in the project, usually in `Assets/Scripts`. The editor opens each script in a tab, colours the code, suggests names as you type and checks the script for errors.

Below the text is the error list. **Compile** errors are mistakes found before the script runs, such as a typo. **Runtime** errors happen while the game plays. Each error shows its line; click it to jump there.

## How to use it

1. Double-click a `.kts` file in the [Project](project.md) window to open it here.
2. To create a script, type a name in the box at the top and click **New**. The script starts from a template that already compiles.
3. Write your code. Press **Ctrl+Space** for suggestions.
4. Click **Check** to look for errors without saving.
5. Press **Ctrl+S** or click **Save** to save. If the game is playing, the script reloads right away, so you can see the change without stopping.
6. Use **Ctrl+F** to find text, **Ctrl+H** to replace it and **Ctrl+G** to go to a line.
7. Closing a tab with unsaved changes asks first. **Discard Changes** drops them.

To make a script run, add a **Script** component to an object in [Properties](properties.md) and choose the script.

## Related windows

- [Project](project.md): find and organise your scripts.
- [Properties](properties.md): attach a script to an object.
- [Console](console.md): messages and errors printed by scripts.
