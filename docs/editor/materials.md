# Material inspector (EDT-17)

Every object with a `RenderComponent` has a **Material** section in the inspector.

- **No override** (the default for imported models): the model renders its own per-part materials, which the
  inspector does not edit. **Override Material** gives the object its own material, starting from the model's
  first material. **Load...** takes it from a `.skmat` asset instead.
- **Override**: base colour, base colour factor, metallic, roughness, emissive (HDR, values above 1 bloom), alpha
  mode (cutoff for `MASK`), double sided, and the five texture slots. Changes show in the viewport while you drag.
  Each finished drag or click is one undo step (Ctrl+Z / Ctrl+Y). **Remove Override** goes back to the model's
  materials.
- **Assets** (`.skmat`, REN-04 format, JSON):
  - **Save As...** writes the material (default folder `Assets/Materials/`) and links it.
  - **Save** overwrites the linked file.
  - **Load...** uses a file's material and links it.
  - **Unlink** keeps the values but stops using the file.

  Linking and unlinking can be undone. Writing a file can't: undo never deletes or rewrites files.

A linked object stores the path in `RenderComponent.materialPath`. When the scene loads, the material is read from
that file. The scene also keeps a copy of the material, used when the file is missing or invalid. Edits made in the
inspector stay in the scene only until you press **Save**: the next time the scene loads, the file wins.
