# REN-24: Post-processing no longer blurs the frame; real effect names

1. Open the editor with the default project and look at the main scene (asphalt ground, ramps, character).
2. Open **Window > Render Graph** and expand **Post Process**.
3. Check the effect checkboxes: they read **Bloom, Tonemapping, Gamma Correction, FXAA, SSAO** (not "Effect" five times). In French: Lueur, Tonemapping, Correction gamma, FXAA, SSAO.
4. Toggle the whole **Post Process** pass off and on while looking at the ground texture and the floor lines: the asphalt grain and the line edges look equally sharp both ways.
5. With Post Process on, toggle only **FXAA**: textures do not change; only jagged silhouette edges (ramp outlines, character) get slightly smoother.
6. The other passes show distinct names (Shadow, Picking, Geometry, Particle, Post Process, Debug, Ui); none shows "Unknown".
