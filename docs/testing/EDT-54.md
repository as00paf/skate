# EDT-54: Render Graph window

1. Delete `imgui.ini` (or use *View → Reset Layout*) and start the editor on MainScene. The Render Graph is a tab in the bottom dock next to Console, and Console is the active tab.
2. Open the Render Graph tab. Seven passes run left to right (Shadows, Picking, Geometry, Particles, Post-Processing, Debug, Runtime UI) with translated names and icons. Coloured links are labelled by resource (Scene Colour, Scene Depth, Shadow Map…) and do not overlap; *Fit* frames the graph.
3. Each pass shows GPU and CPU ms and a share bar. Picking reads "On demand" (with the last time after a click in the viewport). The toolbar shows CPU/GPU totals, and the GPU tooltip says the GPU total is a sum of per-pass timers.
4. Untick Shadows: the node dims and reads "Off", its links turn dashed, the scene renders without shadows, and the status line shows "Passes off: 1" with *Enable all*. Geometry has a lock and cannot be switched off.
5. Expand Post-Processing (arrow): all five effects are listed, disabled ones included. Tick Tonemapping: the image changes. Untick FXAA: edges get jaggier.
6. Click Geometry: the details panel shows its description, inputs and outputs, a timing graph with average/max. Click Shadows: the panel also has the shadow quality combo. Click a resource to highlight its links.
7. Middle- or right-drag pans, the wheel zooms around the cursor, and *1:1* resets. Dock the window in a narrow, tall side panel: the graph turns top-to-bottom.
8. Click `?` in the header (or right-click the tab, then Help): the help popover explains the window. Switch to French: every label and tooltip is translated. Open the showcase project: every pass is on again.
