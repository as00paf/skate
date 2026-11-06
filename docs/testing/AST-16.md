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

## Slice b: ambientCG

14. In the Asset Store, set Source to **ambientCG**. Type switches to **Textures**, the credit line reads "Materials from ambientCG.com (CC0)…", and popular materials fill the grid with thumbnails.
15. Search `brick`, open **Bricks 059**. The license is CC0, the author is ambientCG, **Open on website** opens ambientcg.com/view?id=Bricks059, and the Variant list starts `1K (… MB)` with JPG before PNG at each size.
16. Import **1K-JPG**. `Assets\Store\ambientcg\bricks059\` holds `Bricks059_1K-JPG_Color.jpg`, `Bricks059_1K-JPG_NormalGL.jpg`, `Bricks059_1K-JPG_ORM.png` and `bricks059.skmat`, each with a `.meta`. No `.usdc`, `.blend` or displacement map is imported.
17. Open `Bricks059_1K-JPG_ORM.png`: it is reddish/greenish (R = AO, G = roughness, B = 0 for a non-metal). Assign `bricks059.skmat` to a cube: red bricks with visible normal detail, not shiny. Import a metal (search `metal`, e.g. **Metal 032**): its ORM has a strong blue channel and the cube looks metallic.
18. The Attribution tab lists Bricks 059 / ambientcg / CC0-1.0 / ambientCG. Undo removes the folder and the entry.
19. Disconnect the network and search ambientCG: a red "Search failed" line appears in the grid, and switching back to Poly Haven still works once the network is back.

## Review fixes (slice a)

20. During a 4k import the editor stays responsive (the copy runs in the background). Command History shows "Import from Asset Store" (translated in French).
21. `<project>\.skate\.gitignore` contains `cache/`. In git, `attribution.json` shows as new and nothing under `.skate\cache` does.
22. After **Undo** and **Redo** of an import, the Project window and the Attribution tab update without a manual refresh. (The Asset Browser refresh is AST-22, skate-assets.)
23. Import the same model five or more times: `.skate\cache\store\polyhaven\woodentable_01\` keeps at most 5 `backup-*` folders. Close the project: all `backup-*` folders are gone, and the variant folders stay.
24. Start an import, and switch project before it finishes: nothing is written to either project, and an error says the project changed.
25. Scroll through several pages of results, then close the window: the console shows no texture errors (thumbnails are freed; at most 96 are kept on the GPU).

Automated (recorded Poly Haven and ambientCG fixtures in `src/test/resources/assetstore/`, generated zips, no sockets):
`.\gradlew.bat :test --tests "com.pafoid.skate.editor.assetstore.*"`.
