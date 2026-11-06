# AST-16 slice a: Asset Store window (Poly Haven), manual test

Needs an internet connection. Poly Haven is CC0 and needs no login.

1. Run `.\gradlew.bat run` and open (or create) a project.
2. **View > Windows > Asset Store**. The window shows Source **Poly Haven**, Type **Models**, License **Commercial use allowed**, and the line "Powered by Poly Haven (polyhaven.com). Assets are CC0.". A grid of models fills in, most-downloaded first; thumbnails appear after a moment (blank tiles until then).
3. Type `table`, press **Enter**. The results are ranked tables. Hover a tile: its title shows. Scroll down and click **Load more** if present.
4. Click **Wooden Table 01**. The details pane shows the preview, "By Ethan Place", a green star with **CC0 1.0 Universal** and its explanation, the description, a **Variant** list (1k, 2k, 4k with sizes), **Import**, **Open on website** (opens polyhaven.com/a/WoodenTable_01) and **License text**.
5. Pick **1k**, click **Import**. A progress bar shows "Downloading Wooden Table 01", then "Imported Wooden Table 01 into Assets/Store".
6. In Explorer: `<project>\Assets\Store\polyhaven\woodentable_01\` holds `WoodenTable_01_1k.gltf`, `WoodenTable_01.bin`, `textures\*.jpg`, each with a `.meta` whose `settings` has `store.provider = polyhaven`, `store.sourceId`, `store.license = CC0-1.0`. `<project>\.skate\attribution.json` has one entry (title, URL, author, `CC0-1.0`, the GUIDs). Staging files are under `<project>\.skate\cache\store\`.
7. In the Project window the `.gltf` shows as a 3D model. Drag it into the viewport the way you add any project model: the textured table loads.
8. **Attribution** tab: one row, Wooden Table 01 / polyhaven / CC0-1.0 / Ethan Place.
9. **Edit > Undo** (Command History shows "Import from Asset Store  Wooden Table 01"): the `woodentable_01` folder and its `.meta` files are gone, and `attribution.json` has no entry. **Redo** brings them back.
10. Type **Textures**, search `rock wall`, open **Rock Wall 08**, import **1k**. `Assets\Store\polyhaven\rock_wall_08\` holds `rock_wall_08_diff_1k.jpg`, `rock_wall_08_nor_gl_1k.jpg`, `rock_wall_08_arm_1k.jpg` and `rock_wall_08.skmat`. Assign the `.skmat` to a cube: it shows the stone wall.
11. Import Wooden Table 01 again at **2k**: the same folder is updated in place, and the `.meta` GUIDs of `WoodenTable_01.bin` are unchanged. Undo restores the 1k version.
12. Start an import and click **Cancel download**: nothing is added under `Assets\Store`.
13. Switch the editor to French: every label in the window is translated.

Automated (recorded Poly Haven fixtures in `src/test/resources/assetstore/polyhaven/`, no sockets):
`.\gradlew.bat :test --tests "com.pafoid.skate.editor.assetstore.*"`.
