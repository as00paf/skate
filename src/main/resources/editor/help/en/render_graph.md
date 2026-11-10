# Render Graph

The Render Graph shows how the engine draws one image of your scene. It is an advanced window: you do not need it to make a game, but it helps you understand and speed up the picture.

## What it is

The engine does not draw a frame in one go. It runs several **passes**, one after the other, and each pass adds something:

- **Shadows**: computes where the lights cast shadows.
- **Picking**: finds which object is under the mouse when you click.
- **Geometry**: draws the sky, the 3D objects and the sprites.
- **Particles**: draws smoke, sparks and other particle effects.
- **Post-Processing**: applies effects such as bloom or colour grading to the whole image.
- **Debug**: draws the grid, physics shapes, gizmos and editor icons.
- **Runtime UI**: draws the game's menus and HUD on top.

The window draws the passes as boxes linked by the **resources** (images and buffers) they read and write. Each box shows how long the pass takes on the graphics card (GPU) and on the processor (CPU).

## How to use it

1. Open a scene. The graph is empty until the renderer has built its passes.
2. Click a pass to see its details and a graph of its time over the last frames.
3. Untick the checkbox of a pass to switch it off for this editor session and see what it adds. A padlock marks passes that must always run. Exported games always run every pass. **Enable all** switches everything back on.
4. Expand **Post-Processing** to switch single effects on or off.
5. Drag with the middle or right mouse button to pan, use the wheel to zoom, and click **Fit** to see the whole graph. **1:1** shows it at its real size.
6. Click a resource to highlight the passes linked to it.

If clicking objects in the viewport stops working, check that **Picking** is on.

## Related windows

- [Profiler](profiler.md): the total time of a frame.
- [Editor Settings](editor_settings.md): the graphics quality of the editor.
- [Game Viewport](game_viewport.md): the image the passes produce.
