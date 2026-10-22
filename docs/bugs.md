# Bugs

Owner prefix = domain agent that should fix. Found by QA-01.

Method note: the QA agent runs headless from WSL, so the editor and sample game
were NOT launched. Findings come from `compileKotlin`/`compileTestKotlin` and a
scan of `TODO`/`FIXME` markers in `src/main`. Runtime repro steps are marked
"to verify" and need a manual run on a Windows desktop.

## Confirmed

### BUG-001 — Test source set does not compile (P0, owner: QA)
- Repro: `cmd.exe /c gradlew.bat test` -> `:compileTestKotlin` FAILED (~400 errors); `compileKotlin` passes.
- Cause: tests drifted from production APIs (renamed/removed members, changed constructors).
  Errors per file (count): `PointLightComponentsTest` 57, `GridLinesTest` 52, `DirectionalLightSystemTest` 35,
  `ProjectActionHandlerTest` 33, `PhysicsComponentTest` 28, `EnvironmentActionHandlerTest` 20,
  `AudioSystemTest` 18, `SceneActionHandlerTest` 15, `RenderPipelineTest` 12 (`createProjectionMatrix`, `projectionSize` missing),
  `ViewportActionHandlerDuplicateFlowTest` 12, `PlayerStateManagerTest` 12, `InputSystemTest` 12,
  `EditorMenuBarLifecycleTest` 10, `PlayerControllerTest` 8 (`scene.camera`, `gameObjects`), `SceneActionHandlerTargetingTest` 7,
  `PrefabsGeneratorTest` 6, `EditorInputHandlerEventRoutingTest` 6, `RagdollSystemTest` 6, `FrictionPropagationTest` 6,
  `OpenSceneFileCommandTest` 5, `SyncCommandSemanticsTest` 5, `TransformTest` 5, `GamepadOverlayLifecycleTest` 4,
  `ActionSearchProviderDuplicateFlowTest` 3, `GameObjectManagerTest` 3, `PhysicsCalibrationTest` 2, `CameraTest` 2
  (`applyPreset`, `lerpToPreset`), `CapsuleCollider3DTest` 1 (`val` reassigned), `StringManagerTest` 1 (constructor takes `(logger, currentLocale)`).
- Impact: no test (including architecture guards) can run; CI (QA-06) and coverage (QA-02) are blocked.
- Plan: QA fixes in tests only; any case where the test is right and production regressed gets reported separately.

## Suspected (from source markers, to verify at runtime)

| ID | Pri | Owner | File | Symptom / repro (to verify) |
|----|-----|-------|------|------------------------------|
| BUG-002 | P1 | EDT | `editor/gizmos/TranslateGizmo.kt:42` | Translate gizmo snapping broken (TODO "fix snapping"). Repro: enable snap, drag translate handle. |
| BUG-003 | P1 | EDT | `editor/search/providers/ActionSearchProvider.kt:297` | `createPrimitive()` marked "fix". Repro: search palette -> create primitive. |
| BUG-004 | P1 | EDT | `editor/ui/windows/SceneHierarchyWindow.kt:122` | Marked "TODO: fix" in hierarchy window. Repro: interact with hierarchy near that code path. |
| BUG-005 | P2 | EDT | `app/EditorScreen.kt:21` | Default window layout published instead of using `imgui.ini`; layout not restored on start. |
| BUG-006 | P2 | EDT | `editor/imgui/MImGui.kt:366` | Table helper marked "fix table". |
| BUG-007 | P2 | EDT | `editor/imgui/components/*` (DirectionalLight, PointLight, SpotLight, SpriteRenderer, Skeleton, RenderComponent) | Inspector edits mutate components directly (no Command), so no undo/redo; violates the UI mutation pipeline convention. |
| BUG-008 | P2 | ANI | `engine/assets/AssetsManager.kt:72` | Only the first animation of a file is loaded. Repro: import FBX/glTF with several clips. |
| BUG-009 | P2 | PHY | `engine/ecs/components/RagdollComponent.kt:9`, `physics3d/RagdollBuilder.kt` | Ragdoll builder flagged "check". Repro: enable ragdoll on a skeleton. |
| BUG-010 | P2 | EDT | `engine/core/WindowController.kt:126` | "check if still works" (window behavior path). |
| BUG-011 | P2 | GPL | `game/GameMain.kt:57` | Hard-coded `C:\workspace\skate_workspace\test\Builds` path; standalone game fails on other machines. |
| BUG-012 | P2 | EDT | `editor/search/history/SearchHistory.kt:10` | `search_history.json` written to the CWD. |
| BUG-013 | P2 | EDT | `editor/ui/handlers/EditorInputHandler.kt:124` | Rename shortcut does not focus the name field in the property window. |
