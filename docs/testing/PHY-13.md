# PHY-13 manual test steps

Concave obstacles use one compound-hull collider. Static geometry can use a triangle-mesh collider.

1. Start the editor and create a **new project**. Open the `FeatureShowcase` scene and click **Physics** in the legend.
2. Turn on physics debug in the viewport toolbar. Two banks sit at the front of the Physics pad. The left bank (`[Physics] Compound hull bank`) shows the outlines of its convex pieces. The right bank (`[Physics] Triangle mesh bank`) shows its surface edges. There are no small green crosses.
3. Select each bank. The inspector lists one collider: `CompoundHullCollider3D` on the left bank, `MeshCollider3D` on the right one.
4. Press **Play**. Each blue ball lands on its bank's slope and rolls down onto the floor. Neither falls through. Press **Stop**.
5. Open `MainScene`, place a **Bowl** and a **Half Pipe** from the prefab library, and select each one. The inspector shows a single `CompoundHullCollider3D`, and the debug view outlines all its pieces.
6. Press **Play** and ride the skater into the half pipe and the bowl. The skater rides the transitions and doesn't fall through, as before. Press **Stop**.
7. Drag the triangle-mesh bank with the move gizmo in the `FeatureShowcase` scene. Its debug outline follows it. Save, close and reopen the project, then press **Play**. Both banks still catch their balls.
