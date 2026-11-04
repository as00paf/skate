# EDT-30: Unified editor mouse coordinates, manual test

1. Start the editor, create a **new project** and open its default scene. Restore the window (not maximized) and drag it away from the top-left corner of the screen, onto a second monitor if you have one.
2. Select tool (Q): hover the skateboard, the skater and the floor. Each one highlights exactly under the cursor tip, including near the viewport edges.
3. Click the skateboard: it is selected. Click the floor next to it: the selection switches to the floor in one click.
4. Translate (W), Rotate (E) and Scale (R) tools: hover each gizmo handle. It lights up only when the cursor is on it, and dragging it moves the object with the cursor.
5. Drag a model from the Asset Browser into the viewport: it is dropped where the cursor is.
6. Pull the viewport panel out of the main window into its own floating window (multi-viewport) and move it around the screen. Repeat steps 2 and 4.
7. Add a UI canvas with a button, then press Play: the button's hover and pressed colours follow the cursor exactly, also in the floating viewport.
8. Measure tool: click two floor points. The distance tooltip appears next to the cursor, and View > Windows > Input > Input Testing shows the mouse position relative to the viewport (0, 0 at its top-left).
