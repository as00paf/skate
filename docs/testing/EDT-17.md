# EDT-17 test steps: material inspector and .skmat assets

Setup (once): `.\gradlew.bat run`, create a NEW project (File > New Project), and open `MainScene`.

1. Right-click the viewport > Create > Cube, then select it. In the inspector, the **Material** section reads "Uses the
   model's own materials". Click **Override Material** → base colour, metallic, roughness, emissive and the other
   fields appear.
2. Drag **Metallic** and **Roughness** → the cube changes in the viewport while you drag. Pick a red **Base Color** and
   an **Emissive** of about (2, 1, 0) → the cube turns red and glows.
3. Press Ctrl+Z → only the last change (emissive) is undone. Press Ctrl+Z again → the base colour returns. Ctrl+Y
   redoes both.
4. Set **Alpha Mode** to `BLEND` and the base colour alpha to 0.4 → the cube is see-through. **Alpha Cutoff** only
   shows for `MASK`.
5. Click **Save As...** → the dialog opens in `<project>/Assets/Materials/`. Save as `RedGlow.skmat` → "Asset:
   RedGlow.skmat" shows, and the file appears in the Project Browser.
6. Change roughness, then click **Save**. Save the scene (Ctrl+S), close it, and reopen it → the cube looks the same,
   and the asset row still reads `RedGlow.skmat`.
7. Create a sphere, click **Load...** and pick `RedGlow.skmat` → the sphere gets the same material. Ctrl+Z → the
   sphere is back to its own material.
8. Open the `FeatureShowcase` scene and fly to `Materials` → the jade sphere `[Materials] Asset` shows
   "Asset: ShowcaseJade.skmat".
