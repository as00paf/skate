# PHY-11 manual test steps

Bodies follow Transform edits made in the editor (inspector, gizmos, undo/redo) while the scene is not playing.

1. Start the editor and create a **new project**. The `MainScene` opens with the skater, skateboard and floor.
2. Turn on physics debug in the viewport toolbar. Green collider boxes appear on the skater, skateboard and floor.
3. Select the skateboard and drag it with the translate gizmo. Its green box moves with it on every frame of the drag, with no leftover box at the old spot.
4. Rotate it 45° with the rotate gizmo, then type `2` in each Scale field of the inspector. The box rotates, then grows to twice its size.
5. Undo (**Ctrl+Z**) twice. The box first shrinks back, then rotates back, matching the model each time.
6. Move the skateboard about 3 m up with the gizmo, then press **Play**. The board falls from the edited height, not from where it was when the project opened.
7. Press **Stop**, then move the floor down 1 m in the inspector and press **Play**. The skater falls onto the floor at its new height and can still ride with the gamepad.

Known issue (skate-editor): after a gizmo drag, undo then **redo** may not reapply the move. `TransformCommand` stores a shallow `Transform.copy()` that shares vectors with the live object. Inspector edits and undo alone are not affected.
