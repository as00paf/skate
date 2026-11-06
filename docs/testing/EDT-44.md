# EDT-44 — Camera/light icons keep their shape; light icons use the light color

1. Open a project with a camera, a point light, a spot light and the scene's directional light.
2. Hold RMB and look around quickly so the icons sweep from the centre to the screen edges: each icon stays a flat, undistorted symbol of the same pixel size.
3. Fly with WASD/QE toward and away from an icon: its on-screen size does not change, and it does not wobble while you move.
4. Scroll to zoom and orbit: same result. The camera frustum and light arrows follow the icon size.
5. Select the point light and change its color in the Inspector: the bulb icon changes color live. An orange ring and the range sphere show that the light is selected.
6. Select the spot light and set a bright HDR color (e.g. 4, 2, 0): the bulb and arrow show orange (the hue in the 0–1 range), not white.
7. Set a light color to black: the icon stays visible in grey.
8. Click each icon: it still selects its object.
