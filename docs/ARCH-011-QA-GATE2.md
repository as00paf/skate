# ARCH-011 QA Gate 2 Report (Undo + Async Lifecycle Correctness)

- **Task:** ARCH-011
- **Owner:** qa-engineer
- **Status:** done
- **Date:** 2026-05-16

## 1) Gate Result

- **Result:** **done**
- **Summary:** compile and scoped ARCH-011 targeted tests executed successfully.

## 2) Evidence Summary (Commands + Outcomes)

Required commands for this gate:

1. `cmd.exe /c gradlew.bat compileKotlin --no-daemon`  
   - **Outcome:** passed
2. `cmd.exe /c gradlew.bat test --no-daemon --tests com.pafoid.skate.engine.editor.UndoRedoManagerTest --tests com.pafoid.skate.editor.commands.CreateSceneCommandTest --tests com.pafoid.skate.editor.commands.OpenSceneCommandTest --tests com.pafoid.skate.editor.ui.handlers.SceneActionHandlerTest --tests com.pafoid.skate.editor.ui.handlers.SceneActionHandlerTargetingTest`  
   - **Outcome:** passed
   - **Result summary:** targeted classes passed, including async lifecycle and handler targeting tests.

## 3) In-Scope Pass/Fail Matrix

| Scope Item | Result | Evidence |
|---|---|---|
| execute-only commands not in undo history | passed | `UndoRedoManagerTest` |
| async success pushes only on completion when configured | passed | `UndoRedoManagerTest` |
| async failure/cancel does not pollute history | passed | `UndoRedoManagerTest`, `OpenSceneCommandTest` |
| async redo success/failure behavior correctness | passed | `UndoRedoManagerTest` |

## 4) Notes / Residual Risk

- ARCH-011 gate is closed for scoped targets.
- Separate non-scoped failures may still exist in full-suite runs and should be handled in their own task scope.

## Static Readiness Notes

Scoped test classes used for gate validation:

- `UndoRedoManagerTest`
- `CreateSceneCommandTest`
- `OpenSceneCommandTest`
- `SceneActionHandlerTest`
- `SceneActionHandlerTargetingTest`
