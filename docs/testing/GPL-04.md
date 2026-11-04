# GPL-04 manual test steps: Prefab library

Run from the repo root on Windows (from WSL, prefix with `cmd.exe /c`).

1. Run `.\gradlew.bat :test --tests "*SkateparkShapesTest" --tests "*MeshBuilderTest" --tests "*PrefabsGeneratorTest"`.
   Expected: `BUILD SUCCESSFUL`.
2. Run `.\gradlew.bat run` and create a **new** project. Open the Asset Browser's **Prefabs** tab.
   Expected:
   - Four sections: Player, Street, Transition and Generic.
   - Each tile shows a rendered thumbnail and a name.
   - The tiles include Half Pipe, Bowl, 3-Stair, Handrail, Point Light and Spawn Point.
   - No `!!key!!` text.
3. Set **Material** to `Wood (Dark)`, then drag **Quarter Pipe**, **Half Pipe** and **Bowl** onto the floor.
   Expected: each one lands where you drop it, sits flat on the floor and has a dark-wood texture. Ctrl+Z removes
   the last one.
4. Drag a **5-Stair**, then a **Handrail** and a **Hubba**. Set the handrail's and hubba's Transform to the
   stairs' Z, with X = ±1.05. Expected: the rail and the hubba follow the step noses. The rail is metal whatever
   material is selected.
5. Right-click the viewport and open **Create Prefab → Generic**. Spawn a **Point Light** and a **Crate**. Expected:
   - The light appears 3 m up and lights the obstacles.
   - The crate sits on the floor.
6. Press **Play** and push the skater into the crate, then ride into the quarter pipe and the stairs.
   Expected:
   - The crate gets knocked around.
   - The skater collides with the ramp surface and the steps, not an invisible box.
7. Spawn a **Trigger Volume** and a **Spawn Point**, then check them in the Hierarchy/Properties.
   Expected:
   - The trigger has a static Rigid Body with Is Trigger ticked and a Box Collider, but no mesh.
   - The spawn point is an empty object tagged `SpawnPoint`.
8. Save the scene and reopen it. Expected: every prefab comes back with the same position, material and colliders.
   The default scene's ledge now sits on the floor.

Known limits:
- Concave obstacles carry one hull collider per convex piece, so the bowl lists about 80 colliders in Properties.
- Point Light and Spot Light share the sphere thumbnail, and Crate and Trigger Volume share the cube thumbnail.
- Projects created before this change keep their old kicker, bank and quarter pipe models (no migration, rule 8).
