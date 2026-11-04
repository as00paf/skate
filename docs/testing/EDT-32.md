# EDT-32: Undo/redo of transforms, manual test

1. Start the editor and create a **new project**. Its default scene opens.
2. Select the skateboard. With the **Translate** tool (W), drag it about 2 m along X. Press **Ctrl+Z**: it returns to its start. Press **Ctrl+Y**: it moves back to where you dropped it. Repeat undo and redo twice; each press toggles between the two positions.
3. Do the same with the **Rotate** (E) and **Scale** (R) tools on a box.
4. In the **Properties** panel, drag the Transform **Y** value of the box for a while, then release. A single Ctrl+Z restores the value from before the drag, and Ctrl+Y re-applies the final value.
5. Ctrl+click the box's Translation **X** field, type `5`, press Enter, then undo and redo. Click the **X** reset button next to Rotation, then undo and redo.
6. Open **View > Windows > Debug > Command History**: each gizmo drag and each inspector edit is one entry named "Transform" with the object's name.
7. Save the scene (**Ctrl+S**), close and reopen it (or restart the editor), then repeat steps 2 and 4 on the loaded objects.
8. Press **Play**, then **Stop**: the history is cleared and the objects are back at their edit-time transforms. Undo and redo work again for new edits.
