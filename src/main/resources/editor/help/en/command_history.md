# Command History

The Command History is the list of changes you made in the editor, in order. It is the long version of **Undo**: instead of pressing Ctrl+Z ten times, you can click the point you want to go back to.

## What it is

Every change you make, such as moving an object, renaming it or adding a component, is saved as a **command**. The window has two parts:

- **Executed Commands**: the changes that are currently applied, oldest first.
- **Undone Commands**: the changes you undid. They stay here so you can redo them, until you make a new change.

At the top are the **Undo**, **Redo** and **Clear History** buttons.

## How to use it

1. Make a few changes in the scene and watch them appear in the list.
2. Click a command, or right-click it and choose **Undo to here**, to undo every change made after it.
3. Right-click an undone command and choose **Redo to here** to apply the changes again up to that one.
4. Use **Ctrl+Z** and **Ctrl+Y** for one step at a time.
5. Click **Clear History** to empty the list. You cannot undo the cleared changes afterwards, so do this only when you are sure.

Making a new change after undoing removes the undone commands: the history follows a single path.

## Related windows

- [Scene Hierarchy](hierarchy.md) and [Properties](properties.md): where most changes are made.
- [Console](console.md): the Action lines record the same changes.
