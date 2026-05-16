# ARCH-007 QA Gate 1 Report (UI + Play Boundary)

- **Task:** ARCH-007
- **Owner:** qa-engineer
- **Status:** blocked
- **Reason:** verification commands ran, but gate failed due test failures (including scene action handler regressions).
- **Date:** 2026-05-12

## Verification Prepared

1. Added/updated unit coverage for play-mode command gate:
   - `src/test/kotlin/com/pafoid/skate/engine/editor/UndoRedoManagerTest.kt`
   - verifies blocked command execution in play mode
   - verifies allowlisted command execution in play mode

2. Static conformance checks completed for M1 targets:
   - UI entrypoints now publish events rather than executing commands directly in scoped files.
   - Play-mode mutation gate integrated in `UndoRedoManager` and action handlers.
   - Mouse scroll capture behavior unified (`getScrollX`, `getScrollY`).
   - `SystemsWindow` now splits editor vs gameplay systems instead of duplicating the same list.

## Verification Executed

1. `cmd.exe /c gradlew.bat compileKotlin --no-daemon` -> **passed**
2. `cmd.exe /c gradlew.bat test --tests "com.pafoid.skate.engine.editor.UndoRedoManagerTest" --no-daemon` -> **passed**
3. `cmd.exe /c gradlew.bat test --no-daemon` -> **failed** (`252 tests completed, 14 failed`)

## Blocking Failures (Gate Open)

- `SceneActionHandlerTargetingTest`
  - `tab select and close others target provided scene reference()`
  - `close request targets provided scene reference after scene order churn()`
- `SceneActionHandlerTest`
  - `handleOpenRequested_logsCancellationFromCompletionEvent()`
  - `handleCreateRequested_generatesUniquePaths()`
  - `handleCreateRequested_publishesSceneCreatedEvent()`
  - `handleCreateRequested_createsSceneFileOnDisk()`
  - `handleCreateRequested_usesExistingFileForUniquenessCheck()`
  - `handleCreateRequested_publishesSceneOpenedAndChangedEvents()`
  - `handleCreateRequested_opensSceneInSceneManager()`
  - `handleCreateRequested_failsGracefully_whenNoProjectDirectory()`
  - `handleCreateRequested_logsSuccessMessage()`
- `EngineFixedTimestepTest`
  - `update should pass accumulated time to scene()`
  - `update should call scene update with actual delta time()`
- `EnvironmentSystemTest`
  - `update when Scene has no environment component creates component on Scene()`

## Pending to Clear Gate

1. Triage failures into:
   - ARCH-002..006 regressions vs pre-existing failures
   - related vs unrelated to M1 scope
2. Fix/contain M1-related failures and re-run:
   - `cmd.exe /c gradlew.bat test --no-daemon`
