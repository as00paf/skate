# ANI-08 – Animator controller persistence: test steps

Start from a NEW project (`.\gradlew.bat run` → File > New Project → name, location, Create Project).

1. Run `.\gradlew.bat :test --tests "com.pafoid.skate.engine.animation.graph.ControllerPersistenceTest"` → 3 tests pass.
2. Run `.\gradlew.bat :test` → BUILD SUCCESSFUL.
3. In the new project, add any model with an Animator, then File > Save Project and reopen it → scene loads with no errors in the console (old scenes without `controllerPath` still load).
4. Edit the saved scene JSON: add `"controllerPath": "<abs path to controller .json>"` to the Animator (controller from `AnimationControllerAsset.toJson()`, clips must exist on the Animator) → reopen: no console errors, state machine active.
5. Point `controllerPath` at a missing file or a controller naming an unknown clip → console shows an ERROR naming the path/clip; the scene still loads and plain clip playback works.

There is no editor UI to pick a controller yet (EDT-16), hence step 4 is manual.
