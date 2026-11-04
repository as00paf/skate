# REN-09 test steps: shared fog (linear, exp, exp², height)

Setup: `.\gradlew.bat run`, create a NEW project (File > New Project).

1. Open the `FeatureShowcase` scene → there is no fog by default, and the sky is clear.
2. Press Play and click the **Fog** legend button → the camera flies to the Fog area, and orange posts recede along
   the right edge of the floor.
3. Click **Cycle fog** (top right) once → linear fog: nearby posts are clear and the far ones fade into grey. The
   floor fades smoothly, without bands along its big triangles, and the sky turns the same grey as the far floor.
4. Click again (exp), then again (exp²) → exp fogs nearby posts a little. Exp² keeps them clear and then thickens
   quickly. In both, the floor at the horizon and the sky meet without a visible seam.
5. Click again (height fog) → fog pools near the ground, the top of the tall `[Fog] Tower` stays clearer than its
   base, and the sky overhead is blue again. Click once more → the fog is off.
6. Stop. Select the scene and, in its EnvironmentComponent, set `fogMode` = EXP, `fogDensity` = 0.03 and tick
   `renderFog` → the editor viewport shows the fog without playing. Untick `renderFog` → the fog is gone.
7. Set `fogHeightFalloff` = 0.3 → the fog thins with height. Orbit the camera up high → the scene looks clearer from
   above.
8. In the Particles area with fog on, the Fire, Smoke, Sparks and Fountain particles fade with distance like the models. Save the
   scene, reopen it, then press Play → the fog settings are kept.
