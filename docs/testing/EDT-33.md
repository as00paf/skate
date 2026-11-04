# EDT-33: One top-level rule in the hierarchy, manual test

1. Start the editor and create a **new project**. Save the default scene (**Ctrl+S**), close it and reopen it from the Asset Browser, so it is loaded from disk.
2. **Scene Hierarchy:** every top-level object appears once at the root, and the skateboard expands to show its visual child. The Feature Showcase scene shows its sections the same way.
3. **Drag** a box onto the skateboard row: the box moves under the skateboard and is no longer listed at the root. **Ctrl+Z** puts it back at its original root position; **Ctrl+Y** moves it under the skateboard again.
4. **Drag** the box (now a child) onto the **scene name header** at the top of the hierarchy: it becomes top-level again. Dragging the skateboard onto its own visual child does nothing.
5. Right-click the skateboard and choose **Create Empty Child**: the new object appears only under the skateboard. Delete it, then **Ctrl+Z**: it comes back under the skateboard, in the same position.
6. Use **Duplicate** on the skateboard's visual child: the copy appears next to it, under the skateboard.
7. Save, close and reopen the scene: the parenting from steps 3–6 is unchanged, and clicking the skateboard in the viewport still selects it.
8. Press **Play**, then **Stop**: the hierarchy looks the same as before Play, and drag/drop still works.
