# Viewport picking (EDT-31)

## Pipeline

1. `PickingPass` draws every visible, unlocked object **at any depth** in the hierarchy into the picking
   texture, writing `EntityIdEncoder.encode(go.uId)` as the colour. A hidden or locked object prunes its subtree.
   An object with `NonPickable` is skipped, but its children are still drawn. Meshes use `Transform.worldMatrix`,
   so scaled or offset children are drawn where they render, with a normal depth test.
2. The pass runs every frame, including while something is selected, so clicking another object re-targets the
   selection directly.
3. `SelectionGizmo` reads the pixel under the cursor and `PickResolver` maps the id to an object. Cameras and
   lights, which have no geometry, fall back to `SceneVisualsGizmo.pick`.

## Which object gets selected

- The first click selects the **outermost prefab-instance root** (`PrefabInstance`) above the object that was
  hit, so a prefab such as the skateboard is selected and moved as a whole.
- If no prefab root sits above the hit, and the hit object has no `RigidBody3D` but an ancestor does, the
  first click selects the **nearest such ancestor** (the physics root). The default skater and skateboard are
  plain objects with this split: a physics root plus a scaled visual child. Selecting the root keeps the model
  on its body when you move it.
- Otherwise the object that owns the clicked mesh is selected, even when it is a child.
- Clicking again while an ancestor of the hit is selected drills **one level down** toward the hit, until the
  hit itself is selected. This follows the Scene Hierarchy tree.
- Hover uses the same rule, so the hovered object is always the one a click would select.
