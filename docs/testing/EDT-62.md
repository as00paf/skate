# EDT-62 — Confirm before deleting objects

1. In the Scene Hierarchy, right-click an object that has no children ▸ **Delete**. A centred modal titled "Delete objects" asks: Delete "<name>"?, with a hint that Ctrl+Z undoes it, and **Cancel** / a red **Delete** on the right.
2. Press **Cancel**: nothing is deleted. Do it again and press **Esc**: nothing is deleted.
3. Right-click a parent object (for example one with nested children) ▸ **Delete**: the message names it and gives the total number of children at every depth (e.g. "Delete "Rail" and its 3 child object(s)?"). Press **Delete**: the object and its children disappear.
4. Press **Ctrl+Z**: the object comes back with all its children, at the same place in the hierarchy.
5. Select an object in the viewport, right-click in the viewport ▸ **Delete**: the same modal appears. Press **Enter**: it is deleted.
6. Select an object and press the **Delete** key (in the viewport or the hierarchy): the same modal appears.
7. Open Search Anywhere and run the **Delete** action on a selected object: the same modal appears.
8. **Cut** (Ctrl+X, Edit ▸ Cut, or Hierarchy ▸ Cut) does not ask: the object goes to the clipboard and is removed, and Paste brings it back.
