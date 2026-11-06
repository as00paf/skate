# GPL-20: barrel rests on the ground

1. Open a project in the editor and switch to the **FeatureShowcase** scene (or create a new project to regenerate it).
2. Find the barrel in the **Physics** area. Turn on physics debug in the viewport toolbar: the cylinder wireframe should wrap the whole barrel mesh.
3. Press **Play**. The barrel stays upright where it spawned: no rolling, no sinking. Its bottom sits flush with the floor.
4. Stop. In the **Prefabs** tab, drag a **Barrel** into the viewport, then press **Play** again. It rests on the floor the same way.
5. Skate into the barrel: it gets pushed over and only then rolls.
6. Spawn a **Handrail** and press **Play**. Skate into one of its posts: the skater is blocked and doesn't pass through.
7. Grinding the handrail bar still works as before.

Note: barrels saved in scenes before this fix keep their old (too small) collider. Delete them and spawn them again.
