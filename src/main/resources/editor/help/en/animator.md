# Animator

The Animator window builds and changes **animator controllers**: the files that decide which animation a character plays and when it switches to another one. You never need to edit the controller file by hand.

## What it is

A **controller** (a `.controller.json` file) is a graph:

- A **state** is something the character can be doing, such as `idle`, `walk` or `jump`. Each state plays a **clip** (an animation). The **default state** (marked ▶) is where the character starts.
- A **transition** is an arrow from one state to another. It happens when its **conditions** are all true, or when the clip has played to the end if **Wait for the end of the clip** is on. The **blend time** mixes the two clips so the change is smooth.
- A **parameter** is a value that gameplay or scripts set and conditions read. A **Float** is a number (`speed`), a **Bool** is on or off, a **Trigger** is set once and used up by the transition it fires.
- **Any State** is a special box: its transitions are checked whatever state the character is in.

Calling `play("jump")` from gameplay or a script turns on the Bool parameter `jump` (a **request**). Right-click a state > **Go here when requested** adds the matching transition for you.

## How to use it

1. Open it from **View > Windows > Assets**, or click **Edit** next to the controller of an **Animator** in Properties. Click **New** to start a controller in `Assets/Animations`.
2. Built-in controllers, such as the skater's, are read-only: click **Make a project copy to edit**, then **Use on …** to give the copy to the character.
3. Right-click the graph > **Add state**, or drag an animation from the Project window onto the graph. Drag boxes to move them; drag on empty space to select several. Double-click a state to rename it.
4. Select a state to choose its **Clip**, **Speed**, **Loop** and **Events** on the right. A state without a clip holds the rest pose.
5. Right-click a state > **Make transition**, then click the state to go to. Select the arrow to add **Conditions** ("speed is greater than 2", "jump is set"), the blend time and the wait for the end of the clip. Use the arrows next to a state's transitions to set which one is checked first.
6. Add parameters with **+ Float**, **+ Bool** and **+ Trigger**. Renaming a parameter updates every condition.
7. **Ctrl+D** duplicates, **Ctrl+C** / **Ctrl+V** copy and paste states, **Delete** deletes, **F** frames the whole graph. Every change can be undone with **Ctrl+Z**.
8. Click **Save**: the characters that use the controller are updated, even while the game is playing.

While the game is playing, the graph follows the **Preview on** object: the active state is green, a running blend shows on its arrow, events flash, and you can change parameter values to try transitions. Those live values are not saved.

## Related windows

- [Animation Editor](animation_editor.md): change a clip a state plays.
- [Properties](properties.md): the Animator that uses the controller.
- [Project](project.md): the controllers and clips of the project.
