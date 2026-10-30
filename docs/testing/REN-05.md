# REN-05 — Shader includes, variants and hot reload: human test steps

Prerequisite: run from the repository root (the editor looks for `src/main/resources/shaders` relative to the working directory; that is what enables live shader reload).

1. Run `.\gradlew.bat run` to start the editor.
2. Create a new project (File > New Project), name it `ShaderTest`, and open it. A default scene opens.
3. Add a 3D object (e.g. a cube/model from the Assets panel or GameObject menu) so something is lit by `shader_3d_default.glsl`. Confirm it renders normally in the viewport.
4. Leave the editor running. In a text editor open `src/main/resources/shaders/shader_3d_default.glsl`.
5. In the fragment stage, find the final output assignment (the `FragColor`/`fragColor` write) and temporarily multiply the colour by `vec4(1.0, 0.2, 0.2, 1.0)`. Save the file.
6. Switch to the editor window. Within about one second the object turns reddish **without restarting**. The console/log shows `Shader sources changed: shader_3d_default.glsl`.
7. Introduce a deliberate syntax error (delete a `;`). Save. Expected: the log shows `Shader reload failed for ... keeping previous version`, the viewport keeps rendering with the last good (red) shader, and the editor does not crash.
8. Fix the error and revert the colour edit. Save. The object returns to its original look.
9. Include check: create `src/main/resources/shaders/test_include.glsl` containing `vec4 testTint() { return vec4(1.0, 0.2, 0.2, 1.0); }`. In `shader_3d_default.glsl`'s fragment stage, after the `#version` line add `#include "test_include.glsl"` and multiply the output by `testTint()`. Save. The object turns reddish again; remove the include and helper, save, and confirm it reverts. Delete `test_include.glsl`.
10. Variants (automated): run `.\gradlew.bat :test --tests "com.pafoid.skate.engine.render.shader.*"`; all tests pass (variant keys, `#define` injection after `#version`, include expansion, cycle/missing detection, reload-failure behaviour).
11. Close the editor. Run `git status`; there must be no leftover edits in `src/main/resources/shaders`.
