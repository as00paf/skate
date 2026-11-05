# INP-09: Pointer contract

This note reviews the pointer model that EDT-30 introduced. It is the reference for every mouse consumer.

## One pointer source, one view rect

`MouseListener` owns three values that must always share one space:

| Value | Game (`GameMain`) | Editor (`Editor`, `GameViewWindow`) |
|---|---|---|
| `pointerSource` | GLFW cursor, window coordinates (default) | `ImGui.getMousePos`, desktop coordinates (also multi-viewport) |
| `viewRect` | `(0, 0, windowW, windowH)`, set every frame, because the framebuffer is blitted over the window | viewport image rect, in ImGui desktop coordinates |
| `targetWidth/Height` | framebuffer size (`Engine.resizeFrameBuffer`) | same |

All view queries map through the pure `ViewportPointerMapping`, which is unit-tested in `ViewportPointerMappingTest`:
- `isInsideView`
- `viewLocal`
- `viewNormalized` (picking)
- `viewNdc` (rays)
- `viewTarget`

Never compare a value from one space with a rect from the other. The raw GLFW window position (formerly `getMouseX/Y/Pos`) is no longer public, because comparing it with the editor rect was the original EDT-30 bug.

## What the running game sees (`RawInput`, so also scripts, actions and runtime UI)

- **Position:** `mouseX/mouseY` are in render-target pixels, with a top-left origin. They are not clamped, so they can be outside `[0, target)` when the pointer is outside the view. This is the same pixel that picking reads and runtime UI hit-tests (`UiInputSystem`). `InputProvider.getMouseTargetPos()` and the `InputBuffer` samples use the same space.
- **Deltas:** `mouseDeltaX/Y` are GLFW cursor motion in window (screen) units. They are not scaled by the view or target size, so look sensitivity doesn't change with the editor viewport size or the render resolution. They stay valid with a captured cursor (`CursorMode.CAPTURED`), when the position has no meaning. To get a drag in target pixels, take the difference of `mouseX/Y`, or use `ViewportPointerMapping.deltaToTarget`.
- **Buttons:** `isMouseButtonDown`/`wasMouseButtonPressed` report a press that **started inside the view rect**. The button stays held when the pointer leaves the view mid-drag. A press that started elsewhere, such as an editor panel, never reaches the game.
  - The decision is made the first time the press is queried, or at `endFrame` of the press frame. It never happens in a GLFW callback, so the ImGui source is never read outside a frame.
  - ImGui `wantCaptureMouse` is **not** consulted. Before this task it was, and it is always true over the editor's game-view window. So in editor Play, scripts and `MouseButton` action bindings never saw a click.
  - In the game the rect is the whole window, so this is the plain button state.
- Editor tools keep using `InputProvider.isMouseButtonDown(button, ignoreImGui)`, which respects ImGui capture.

## Callers reviewed

| Caller | Reads | Status |
|---|---|---|
| `SelectionGizmo` (picking) | `getNormalizedMousePos` + `isInsideViewport` | consistent |
| `InputProvider.screenToRay` (gizmo rays) | `viewNdc` | consistent |
| `Engine.uiSystems` (runtime UI pointer) | `reader.mouseX/Y` + `reader.isMouseButtonDown(0)` | now the default `ReaderUiInputSource` pointer (EDT-35) |
| `CameraControlSystem` / `ReaderFlyInputSource` (TLG-09 free-fly) | `reader.mouseDeltaX/Y` for look, `reader.isMouseButtonDown(1)` to hold look (EDT-35) | deltas are cursor units, so sensitivity is the same in the game and the editor. Look holds until release, even outside the view |
| `InputSystem.pollMouseInput` | `getMouseDx/Dy`, only while captured | consistent (deltas) |
| `ScaleGizmo`, `EditorInputHandler` | `getMouseDx/Dy` | consistent (deltas) |
| `InputTestingWindow` | `getMouseViewPos` (view-local pointer units) | consistent; the label shows view-local, not target pixels |
| `UiRectGizmo`, `MeasureTool`, `ViewportContextMenu` | `ImGui.getMousePos` against the viewport image rect | the same space as the editor source; local ImGui overlays |
| Scripts (`ReaderScriptInput`, Lua host) | `RawInput` buttons | **fixed:** buttons now work over the editor game view |

## EDT-35 follow-up (done)

`Engine` no longer works around ImGui capture.
- Fly-cam look uses the default `ReaderFlyInputSource` look button, `reader.isMouseButtonDown(1)`.
- The runtime UI uses the default `ReaderUiInputSource` pointer, `reader.mouseX/Y` plus `isMouseButtonDown(0)`.

Both follow the button rule above. A right-drag look or a UI slider drag continues when the cursor leaves the view mid-drag. A press that started on an editor panel reaches neither. `PointerConsumersTest` covers both.
