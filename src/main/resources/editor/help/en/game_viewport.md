# Game Viewport

The Game Viewport is the main 3D view of the editor. This is where your scene becomes a place you can walk around, look at and change.

## What it is

The viewport draws the current scene through the editor camera. It is not the game camera: you can fly anywhere without moving the player. Above the 3D image is a toolbar with the tools you need to select, move, rotate, scale and measure objects.

The bar also has Play controls. **Play** starts the scene, **Pause** freezes it while keeping its state, **Stop** ends Play and restores the scene as it was before Play, and **Reset** puts every open scene back to its pre-Play state while keeping Play running. A physics-debug button draws colliders as wireframes, and a camera button takes a screenshot.

The tabs above the 3D view are the open scenes. Each scene remembers the editor camera position and mode, so switching back to a scene puts you where you left it.

## How to use it

1. Click an object to select it. The same object is selected in the [Scene Hierarchy](hierarchy.md).
2. Hold the **middle mouse button** and drag to look around. Use **W**, **A**, **S** and **D** to fly, **Space** to go up, **Left Shift** to go down and the **mouse wheel** to zoom. Press **Home** to return to the starting view and **F** to focus the selected object.
3. Choose the tool you need: **1** select, **2** move, **3** rotate, **4** scale, **M** measure. The toolbar shows the same tools.
4. Use **Local/World** to choose the axes of the gizmo, and **Snap** to move in fixed steps.
5. Right-click in the viewport to create an empty object, a cube, a sphere, a cylinder, a plane, a light or a camera, or to spawn a prefab. The menu also has **Duplicate**, **Delete**, **Focus Selected** and **Reset Camera**.
6. Drag files from the [Asset Browser](asset_browser.md) into the viewport to place models, prefabs and other assets.
7. Press **F5** to play. While playing, the viewport shows the game as the player sees it. Press **F5** again to stop and get the editor view back.

If the viewport is empty, check that a project and a scene are open. The [Console](console.md) shows load errors, and the [Profiler](profiler.md) tells you whether the frame rate is the problem.

## Related windows

- [Scene Hierarchy](hierarchy.md): the list of objects in the scene.
- [Properties](properties.md): change the selected object.
- [Console](console.md): read errors and warnings.
- [Profiler](profiler.md): measure the frame rate and the time each system takes.
- [Render Graph](render_graph.md): see and tune the render passes behind the image.
