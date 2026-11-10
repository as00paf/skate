# Scene Hierarchy

The Scene Hierarchy is the list of everything that exists in the current scene. Think of it as the table of contents of your level: if an object is not in this list, the game does not know about it.

## What it is

The window shows a tree. The top of the tree is the scene itself, and below it are the objects. An object can contain other objects: a skateboard can contain its wheels, a character can contain a hat, and so on. Moving a parent moves its children too.

Each row has three parts:

- the **name** of the object;
- an **eye** icon to show or hide the object in the viewport;
- a **padlock** icon to lock the object so you do not move it by accident.

At the top you also see the scene name with a small pencil button to rename the scene, a search field, a **+** button to create an empty object and a **link** button that keeps the hierarchy selection and the viewport selection in sync.

## How to use it

1. Click an object to select it. Its components appear in [Properties](properties.md).
2. Type in the search field to keep only the objects whose name contains that text.
3. Press **+** or **Insert** to create an empty object. Rename it with **F2**.
4. Drag an object onto another one to make it a child. Drop it on the scene name to make it a top-level object again.
5. Right-click an object for **Duplicate**, **Copy**, **Cut**, **Paste as child**, **Delete**, **Rename**, **Focus in viewport**, **Lock** and **Visible**. Right-click empty space to create an empty object.
6. Use **Up** and **Down** to move through the list, **Home** and **End** to jump to the first or last object, **Delete** to remove the selection and **Ctrl+D** to duplicate it.
7. The row icons at the top of the list can also expand or collapse the whole tree.

Deleting an object is undoable: press **Ctrl+Z** if you removed the wrong one. If an object is hidden or locked, the eye and padlock icons show it at a glance.

## Related windows

- [Properties](properties.md): edit the selected object.
- [Game Viewport](game_viewport.md): see and move the object in 3D.
- [Project](project.md): the files and prefabs that can be placed in the scene.
