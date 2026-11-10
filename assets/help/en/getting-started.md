# Getting started with Skate

Skate is a 3D game engine with its own editor. You build a game by placing objects in a scene, giving them components and pressing Play. You do not need to know how a 3D program works to begin: this page explains the words and the first steps.

## The editor at a glance

The editor is made of dockable windows. The default layout shows:

- the **Scene Hierarchy** on the left: the list of objects in the current scene;
- the **Game Viewport** in the middle: the 3D view where you place and move objects;
- **Properties** on the right: the settings of the selected object;
- the **Project** window and the **Asset Browser** for files and assets;
- the **Console**, **Profiler**, **Command History**, **Render Graph** and **Script Editor** at the bottom.

Every window has a tab. Drag a tab to move a window, close it with the X, and reopen it from **View > Windows**. The **?** button in a window header opens the help article for that window.

## Words you will see

- **Project**: the folder that holds your game, its scenes, scripts and assets.
- **Scene**: one level or stage of your game. A scene contains objects. You can have several scenes and switch between them with the tabs above the viewport.
- **GameObject**: an object in the scene. It can be an empty marker, a 3D model, a light, a camera or a sound source.
- **Component**: a capability added to a GameObject, such as its position (**Transform**), a **Mesh**, a **Light**, a **Collider** or a **Script**.
- **Asset**: a file used by the game, such as a texture, model, sound, animation or prefab.
- **Prefab**: a saved GameObject, with its components, that you can reuse in other scenes or several times in the same scene.
- **Gizmo**: the coloured handles that appear on the selected object to move, rotate or scale it.

## Your first five minutes

1. Start the editor. If no project is open, the **Switch Project** dialog appears. Choose **New Project** and follow the [Project Wizard](project_wizard.md): give the project a name and a folder.
2. Look at the [Scene Hierarchy](hierarchy.md). It lists the objects of the scene. Click an object to select it.
3. Look at the [Properties](properties.md) panel. It shows the components of the selected object. Change a value and watch the viewport.
4. Use the [Game Viewport](game_viewport.md) to look around: hold the middle mouse button and drag, move with **W**, **A**, **S** and **D**, and press **Home** to return to the starting view.
5. Add an object with the **+** button in the Hierarchy. Move it with the translate gizmo (**2**), rotate it with **3** and scale it with **4**.
6. Press **F5** to play the scene. Press **F5** again to stop. Play mode is temporary: changes made while playing are not kept when you stop.
7. Save with **Ctrl+S**. The current scene is saved inside the project.

## When something goes wrong

Open the [Console](console.md) and read the red **Error** lines first. The [Profiler](profiler.md) shows whether the editor is running slowly. If you cannot find a window, use [Search Everywhere](search.md) (**Ctrl+P**) or the **View > Windows** menu.

## Where to go next

- [Keyboard shortcuts](shortcuts.md)
- [Scene Hierarchy](hierarchy.md)
- [Game Viewport](game_viewport.md)
- [Project](project.md)
