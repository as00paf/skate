# REN-25: Industry-standard editor grid

1. Open the editor on the default project's main scene (the grid shows when the scene is not playing).
2. Close to the floor: thin (about 1 px), smooth grid lines every 1 m, slightly stronger lines every 10 m. No thick or jagged lines.
3. The X axis is a red line and the Z axis a blue line on the floor through the origin; the Y axis is green and points up.
4. Fly the camera straight up (tens of metres): the 1 m lines fade out smoothly and the grid steps to 10 m / 100 m lines. No popping, no shimmering.
5. Lower the camera to just above the floor and look toward the horizon: far lines stay thin (no thickening), and the grid fades out with distance instead of ending at an edge.
6. Move the camera sideways: the grid has no edge and no lines jump.
7. Press Play: the grid disappears; stop: it comes back.
