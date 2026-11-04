# REN-10 manual test steps: Particle system

Run from the repo root on Windows (from WSL, prefix with `cmd.exe /c`).

1. Run `.\gradlew.bat :test --tests "*ParticleSimulatorTest" --tests "*ParticleSystemTest"`. Expected: `BUILD SUCCESSFUL`, with 21 tests passing.
2. Run `.\gradlew.bat run`, open a project and a scene, then create an empty GameObject near the camera.
3. In **Properties**, click **+** and choose `PARTICLE_EMITTER`. Expected: a Particle Emitter section shows labeled fields (no `!!key!!` text). It includes Shape, two color pickers and Texture Path.
4. Before you press Play, check the viewport. Expected: no particles. The simulation only runs while the scene runs.
5. Press **Play**. Expected: soft white dots rise from the object in a cone, shrink and fade out. The inspector shows **Live particles: N** plus **Restart** and **Stop** buttons.
6. While the scene is playing, try a few settings:
   - Set Gravity Y to `-9.8`: the dots arc back down.
   - Set Shape to `SPHERE`: they burst outward.
   - Tick Additive Blending: overlapping dots glow brighter.
   - Set Start Color to orange: the dots turn orange.
   - Move the GameObject: particles already emitted stay behind (world space).
7. Set Texture Path to a PNG (e.g. `assets/textures/camera.png`) and press **Restart**. Expected: the quads show the texture and always face the camera. For a 2×2 atlas, set Atlas Columns and Atlas Rows to `2`; the frame then changes over each particle's life.
8. Press **Stop** in the editor toolbar. Expected: all particles vanish. Save and reopen the scene. Expected: the emitter settings are kept. Disabling the component during Play also removes its particles.

Known limits: particles are not depth-sorted within an emitter, and changes made in the inspector cannot be undone (same as other reflection-edited components). Particles are not drawn in edit mode.
