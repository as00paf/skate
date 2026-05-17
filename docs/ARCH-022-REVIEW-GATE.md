# ARCH-022 Final Reviewer Gate

- **Task:** ARCH-022
- **Owner:** reviewer
- **Status:** blocked
- **Date:** 2026-05-17

## 1) Gate Decision

**NO-GO for ARCH-023**

## 2) Scope Reviewed

Final architecture review across ARCH-018 .. ARCH-021 outputs, focused on:

- DI/layering contract conformance
- UI mutation pipeline conformance
- guardrail test coverage effectiveness
- regression risk before ARCH-023 closure

## 3) Blocking Findings

1. **UI mutation pipeline contract breach (gating)**
   - `ProjectWindow.kt` still executes commands directly via `undoRedoManager.executeCommand(...)` for file operations.
   - This bypasses the canonical `UI -> Event -> Handler -> CommandExecutor -> UndoRedoManager` path.

2. **Engine/editor boundary breach (gating)**
   - Engine classes still import editor-layer types/services (examples observed in `InputSystem`, `AudioSystem`, `SceneManager`).
   - Violates ARCH layering rule: `engine/**` must not depend on `editor/**`.

3. **ARCH-020 guardrail coverage too narrow (gating)**
   - Current guard tests protect only a limited subset of files/entry points.
   - Coverage is insufficient to prevent recurrence of boundary/pipeline drift across broader codebase surface.

## 4) Non-Blocking Follow-ups

- Remove unused local in `ProjectWindow.reimportAsset`.
- Extend async lifecycle coverage with `clear()` while async completion is in-flight.

## 5) Recommendation

- **ARCH-022 remains blocked.**
- **ARCH-023 must not start** until the blocking contract violations are remediated and re-reviewed.
