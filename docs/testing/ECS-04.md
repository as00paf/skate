# ECS-04 manual test steps (component gap analysis)

ECS-04 is a documentation deliverable; it adds no editor-visible behaviour. Verify the doc and its claims as follows. Run all commands from the repository root (Windows PowerShell shown; from WSL prefix with `cmd.exe /c`).

1. **Clean state.** `git status` -> expect `nothing to commit, working tree clean` on branch `agents/ecs`. Confirm `git log --oneline -1` shows `ECS-04: component gap analysis` (or a later commit that contains it).
2. **Doc exists.** Open `docs/design/ECS-04.md`. Expected: sections "Baseline", "Already covered", a "Gaps" table with 14 rows (columns: Component, Why, Owner, Priority, Notes), and "Recommended follow-up tasks".
3. **Verify the "already covered" claims.**
   1. `rg "isTrigger" src/main/kotlin/com/pafoid/skate/engine/ecs/components/RigidBody3D.kt` -> expected: a `var isTrigger: Boolean` line.
   2. `rg "collisionLayer|collisionMask" src/main/kotlin/com/pafoid/skate/engine/ecs/components/RigidBody3D.kt` -> expected: both properties.
   3. `dir src\main\kotlin\com\pafoid\skate\engine\ecs\components\AudioListener.kt` -> expected: file exists.
   4. `dir src\main\kotlin\com\pafoid\skate\engine\ecs\prefab\PrefabInstance.kt` -> expected: file exists.
4. **Verify the gaps are really missing.** Run each; expected: **no** matches for a component class with that name.
   - `rg -l "class (Tag|Lifetime|Billboard|LODGroup|Decal|Spline|ParticleEmitter)\b" src/main/kotlin` -> empty output.
   - `rg "tag|layer" -i src/main/kotlin/com/pafoid/skate/engine/ecs/GameObject.kt` -> no `tag`/`layer` property.
5. **Follow-ups listed.** In the doc's "Recommended follow-up tasks" confirm ECS-10 (Tag/Layer, P1) and ECS-11 (Lifetime, P2) plus REN, PHY and GPL items are present.
6. **No regressions (optional).** Run `.\gradlew.bat test`. Expected: `BUILD SUCCESSFUL` (docs-only change, nothing to fail that was not already failing).

Editor smoke test is not applicable: there is no new menu, key or button for this task. If you still want to confirm the editor starts: `.\gradlew.bat run`, expected: the Skate editor window opens (use File > New Project to create a fresh project, expected: an empty scene with a default camera).
