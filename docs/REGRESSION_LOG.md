# Regression Log

This log tracks active regressions and audit findings requiring verification and closure.

## Status Legend

- `open` — verified issue, no fix merged
- `in_progress` — fix in progress
- `ready_for_qa` — implementation done, awaiting verification
- `verified` — QA verified
- `resolved` — closed

## Active Regressions

| ID | Title | Severity | Area | Status | Owner | Evidence |
|---|---|---|---|---|---|---|
| AUD-001 | Screenshot action not wired from viewport toolbar | Critical | Editor UI / Screenshot | ready_for_qa | software-engineer | `src/main/kotlin/com/pafoid/skate/editor/ui/windows/viewport/ViewportToolbar.kt` |
| AUD-002 | Scene open event from ProjectWindow has no handling subscriber | Critical | Filesystem / Scene flow | ready_for_qa | software-engineer | `src/main/kotlin/com/pafoid/skate/editor/ui/windows/ProjectWindow.kt`, `src/main/kotlin/com/pafoid/skate/editor/events/FileSystemEvents.kt` |
| AUD-003 | Null-unsafe `!!` in DeleteFileCommand | Critical | Filesystem commands | ready_for_qa | software-engineer | `src/main/kotlin/com/pafoid/skate/editor/commands/project/DeleteFileCommand.kt` |
| AUD-004 | File operations use weak success handling (`renameTo/create/delete`) | High | Filesystem commands | ready_for_qa | software-engineer | `src/main/kotlin/com/pafoid/skate/editor/commands/project/{CreateFileCommand,RenameFileCommand,DeleteFileCommand}.kt` |
| AUD-005 | Undo of created directory may remove user-added files | High | Undo/Redo safety | ready_for_qa | software-engineer | `src/main/kotlin/com/pafoid/skate/editor/commands/project/CreateFileCommand.kt` |
| AUD-006 | Screenshot filenames may collide under rapid capture | Medium | Screenshot utility | open | software-engineer | `src/main/kotlin/com/pafoid/skate/engine/utils/ScreenshotUtils.kt` |
| AUD-007 | Project switch/open lifecycle may leave stale scene/resource state | High | Project lifecycle | ready_for_qa | software-engineer | `src/main/kotlin/com/pafoid/skate/editor/imgui/ImGuiLayer.kt`, `src/test/kotlin/com/pafoid/skate/editor/imgui/ImGuiLayerStartupFlowTest.kt`, `src/main/kotlin/com/pafoid/skate/editor/systems/ProjectManager.kt`, `src/test/kotlin/com/pafoid/skate/editor/systems/ProjectManagerLifecycleTest.kt`, `src/main/kotlin/com/pafoid/skate/engine/ecs/SceneManager.kt`, `src/main/kotlin/com/pafoid/skate/engine/core/Engine.kt`, `src/test/kotlin/com/pafoid/skate/engine/EngineFixedTimestepTest.kt` |
| AUD-008 | Direct mutation paths bypass event->handler->command contract | High | Editor mutation pipeline | in_progress | software-engineer | `src/main/kotlin/com/pafoid/skate/editor/ui/handlers/EditorInputHandler.kt`, `src/main/kotlin/com/pafoid/skate/editor/ui/handlers/ProjectActionHandler.kt`, `src/main/kotlin/com/pafoid/skate/editor/ui/windows/ProjectSwitcherDialog.kt`, `src/main/kotlin/com/pafoid/skate/editor/ui/windows/ProjectWizardWindow.kt` |
| AUD-009 | Environment tooling mixes direct state writes with command flow | Medium | Environment tooling | open | software-engineer | `src/main/kotlin/com/pafoid/skate/editor/ui/windows/EnvironmentWindow.kt` |
| AUD-010 | Audio master volume reset each update loop, overriding UI state | High | Runtime audio behavior | open | software-engineer | `src/main/kotlin/com/pafoid/skate/engine/ecs/systems/AudioSystem.kt` |
| AUD-011 | Mouse-look polling path appears unused in runtime update flow | Medium | Runtime input behavior | open | software-engineer | `src/main/kotlin/com/pafoid/skate/engine/ecs/systems/InputSystem.kt` |
| AUD-012 | Hardcoded user-facing strings remain in multiple UI paths | Medium | Localization contract | open | software-engineer | `src/main/kotlin/com/pafoid/skate/editor/ui/windows/{AudioInspectorWindow,ProjectSettingsWindow,EditorSettingsWindow}.kt` |
| AUD-013 | Engine/editor layering breach in AnimationSystem dependency | High | Architecture layering | open | software-engineer | `src/main/kotlin/com/pafoid/skate/engine/ecs/systems/AnimationSystem.kt` |
| AUD-014 | Async clear lifecycle risk in UndoRedoManager | High | Command lifecycle | open | software-engineer | `src/main/kotlin/com/pafoid/skate/editor/systems/UndoRedoManager.kt` |
| AUD-015 | Missing targeted tests for screenshot/filesystem critical paths | Medium | Test coverage | open | qa-engineer | `src/test/kotlin` (no focused screenshot/filesystem flow tests) |
| AUD-016 | Project settings save path appears non-persistent in current flow | High | Settings persistence | open | software-engineer | `src/main/kotlin/com/pafoid/skate/editor/ui/windows/ProjectSettingsWindow.kt` |

## Verification Notes

- Audit pass 1 findings source: `docs/AUDIT_PASS1_REPORT.md`
- Master audit execution plan: `docs/FEATURE_AND_CODE_AUDIT_PLAN.md`
- Full audit consolidation: `docs/AUDIT_FULL_REPORT.md`
- Project startup lifecycle hardening is actively in progress (latest sequence through `f37af28c`) with expanded transition coverage in `ImGuiLayerStartupFlowTest`.
- AUD-007 implementation update: project open now closes active project first; project close now closes all scenes through `SceneManager.closeAllScenes()` and resets system caches; lifecycle regression tests added in `ProjectManagerLifecycleTest`.
- AUD-007 follow-up: engine runtime loop now continues `ImGuiLayer.update()` when there is no active scene, preventing editor UI lock after project close; coverage added in `EngineFixedTimestepTest`.
- AUD-008 incremental hardening: `CloseProjectRequested` and `LoadLastProjectRequested` are now routed through execute-only commands in `ProjectActionHandler` instead of direct `ProjectManager` mutations.
- AUD-007 UI follow-up: `EditorMenuBar` now invalidates and reloads the app icon texture after `ProjectEvent.Closed`, preventing stale GL texture IDs after `ResourceManager.clear()`.
- Next remediation focus remains `AUD-008` to finish eliminating remaining direct-mutation bypass risks in input-driven paths.
