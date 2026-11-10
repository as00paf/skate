# AST-22: glTF textures in subfolders, Asset Browser refresh

The Asset Store (AST-16a) is not merged yet, so these steps use a manual Poly Haven download with the same layout.

1. On polyhaven.com, download a model as **glTF, 1k** (e.g. "Wooden Table 01") and unzip it into your project's `Assets/Store/test/` folder. It contains a `.gltf`, a `.bin` and a `textures/` subfolder.
2. In the Project window, create any file or folder under `Assets/` (that sends a file-system change). Without pressing refresh, the Asset Browser **Textures** tab now lists the model's `textures/*_1k.jpg`.
3. Drag the `.gltf` from the Project window into the viewport: the model renders **textured** (not plain white or grey), and the console shows no "Failed to load texture" error.
4. Save the scene and export the project (File > Export).
5. Open `Builds/manifest.json`: it lists the `.gltf`, the `.bin` and every `textures/...` file the model uses (not other files in that folder).
6. Run the exported game: the model is textured there too.
