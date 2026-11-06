# AST-13: Asset Browser lists every asset type; prefab thumbnails show materials

1. Launch the editor and create a new project (File > New Project).
2. Open the Asset Browser > **Animations** tab: the bundled clips (`idle_0.fbx`, `jump.fbx`, ...) are listed with a film icon. Drag one onto an Animator field: it is accepted.
3. **Textures** tab: the bundled textures (asphalt, concrete, metal, wood, sky...) appear with image thumbnails.
4. **Sounds** tab: `aquarela do brasil.mp3` is listed with its duration; the play button plays and stops it.
5. Copy a `.JPG` or `.wav` file into the project's `Assets/` folder and press the refresh button: it appears in the matching tab.
6. **Prefabs** tab: street and transition obstacles show a concrete surface, rails/barrel show metal, the crate and bench show wood (not plain grey).
7. Change the **Material** combo to a wood: park obstacle thumbnails re-render in that wood; rails stay metal.
8. Close the project and open another one: the tabs list that project's assets.
