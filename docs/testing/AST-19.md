# AST-19: .blend import with Blender

Needs Blender 4.2 LTS or newer (found automatically in Program Files or through the .blend file association).

1. Open a project. In Explorer, copy `baseball-cap.blend` into the project's `Assets/` folder (any subfolder).
2. Within a few seconds the console shows `Imported baseball-cap.blend: baseball-cap.glb`, and a `baseball-cap.blend.import/` folder appears next to the `.blend` (with `.meta` files).
3. Drag `baseball-cap.blend` from the Project window onto the viewport: a textured cap appears where you dropped it. Ctrl+Z removes it.
4. Note the GUID in `baseball-cap.blend.import/baseball-cap.glb.meta`. Open the `.blend` in Blender, change the model (move a vertex, change a color), save (Ctrl+S).
5. Within a few seconds the console shows a new import, the cap in the viewport updates without reopening the scene, and the GUID in the `.meta` file is unchanged.
6. Copy any image into `Assets/` and rename the copy to `broken.blend`: the console shows a Blender import error for `broken.blend`, nothing is created for it, and the cap is unaffected.
7. Save the scene, export the project, and check `Builds/manifest.json`: it lists `baseball-cap.glb` and no `.blend` or `.blend1` file.
