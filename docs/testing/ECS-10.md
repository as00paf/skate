# ECS-10 test steps (Tag and Layer on GameObject)

Engine-level feature; there is no editor inspector field yet (EDT follow-up), so verification is via tests and scripts. Start from a clean checkout of `agents/ecs` (editor: `.\gradlew.bat run`, File > New Project, to confirm nothing broke).

1. `.\gradlew.bat :test --tests "*TagLayerTest"` -> 5 tests PASSED.
2. `.\gradlew.bat :test --tests "*ComponentQueryTest"` -> all PASSED (query caching unchanged).
3. `.\gradlew.bat test` -> BUILD SUCCESSFUL.
4. `.\gradlew.bat run`, File > New Project: the editor opens an empty scene and a saved/reloaded scene still loads (old scenes have no tag/layer fields and default to tag "" / layer 0).
5. API check: `scene.findByTag("x")`, `scene.findByLayer(GameObject.layerBit(2))`, `GameObject.layer = 32` throws `IllegalArgumentException`.
