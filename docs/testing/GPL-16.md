# GPL-16 manual test steps: Game-ready skateboard mesh

Run from the repo root on Windows (from WSL, prefix with `cmd.exe /c`).

1. Run `.\gradlew.bat :test --tests "*SkateboardModelTest" --tests "*SkateparkShapesTest" --tests "*MeshBuilderTest"`.
   Expected: `BUILD SUCCESSFUL`. The board OBJ loads with fewer than 20K triangles (about 2.7K) and the collider
   fits it.
2. Run `.\gradlew.bat run` and create a **new** project. Open the default scene. Expected:
   - A skateboard rests on the floor 1 m in front of the skater, nose pointing away from the skater (+Z).
   - It has black grip tape, a wood edge, kicked-up nose and tail, metal trucks and cream wheels.
   - The wheels touch the floor; nothing floats or sinks.
3. In the Asset Browser's **Prefabs** tab, check the **Skateboard** tile. Expected: its thumbnail shows the same
   board. Drag one onto the floor and it lands upright.
4. Select the board and enable the physics debug draw. Expected: the box collider wraps the board from the floor to
   the deck top, between the kicktails.
5. Open the **Render Graph** window and orbit the camera around the skater and board for a few seconds. Expected:
   - The Shadow and Picking passes are no longer dominated by the board: together they are about 2.5 ms lower than
     in the REN-22 measurement.
   - The frame time is below the pre-round-11 baseline.
6. Press **Play**, walk to the board and mount it (E / Y), then push and turn. Expected: the skater stands on the
   grip tape (feet neither floating above it nor sunk into it) and the board rolls and turns as before.
7. Stop, save the scene, close and reopen the project. Expected: the board reloads with the same look and position.
