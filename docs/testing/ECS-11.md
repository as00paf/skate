# ECS-11: Find child objects by id (manual test)

1. Launch the editor and create a **new** project; open its default scene.
2. In the Scene Hierarchy, create an empty object `Parent`, then use "Create child" on it twice so it has `Child`, and `Child` has `GrandChild`.
3. Drag `GrandChild` onto another top-level object: it reparents (before ECS-11 nested objects could not be dragged).
4. Drag it back onto `Child`: it reparents again at depth 3.
5. Open the search (global search) and type `GrandChild`: the result appears; select it and check the object gets selected in the hierarchy/inspector.
6. Search a component name that only `GrandChild` has (e.g. add a Light to it first): the component result resolves and selects `GrandChild`.
7. Rename `GrandChild` from the hierarchy (F2 / rename): the rename applies to the nested object.
8. Delete `GrandChild`, then undo: search finds it again after undo and not while deleted.
