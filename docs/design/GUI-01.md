# GUI-01 — Runtime UI design

Status: **proposed, awaiting lead approval**. Code lands in GUI-02 under `engine/ui/`.

## Goals / non-goals
- Retained-mode in-game UI (HUD, menus) for any 3D game; no ImGui or editor dependency (guarded by architecture tests).
- Pure, unit-tested layout. Rendering via `Renderer2D`; all text via `StringManager` keys.
- Non-goals here: SDF fonts and plurals (GUI-04), editor authoring (GUI-03).

## Model
UI is ECS-native: a `UICanvasComponent` on a GameObject roots a tree of child GameObjects, each with a `UIElementComponent` (rect + widget data). Hierarchy reuses the scene's GameObject parent/child.

- `UICanvasComponent(mode = ScreenSpace | WorldSpace, referenceResolution, scaleMode = ConstantPixel | ScaleWithScreen(match) | ConstantPhysical, sortOrder)`.
- `UIElementComponent`: `anchorMin`, `anchorMax` (0..1 of parent), `pivot`, `offsetMin`, `offsetMax` (or `position`+`size` when anchors coincide), `visible`, `interactable`, `opacity`, `zOrder`.
- Layout groups on a container: `VerticalLayout`, `HorizontalLayout`, `GridLayout` (padding, spacing, alignment, fill flags), plus `SizeFitter` for content-sized elements.

## Layout (pure)
`UILayout.resolve(tree: UINodeSpec, canvasSize, dpiScale): Map<NodeId, Rect>` has no GL or ECS types. Passes: (1) measure preferred sizes bottom-up, (2) arrange top-down from anchors/offsets, with layout groups overriding child rects. Canvas scale is a function of scaleMode, window size and DPI. Tests: anchors (corner, stretch, center), pivot, nesting, groups, DPI/scale modes, pixel rounding.

## Widgets
Components on top of `UIElementComponent`:
`UIText(key, args, style, align, wrap, overflow)`, `UIImage(texture, tint, sliceBorders, fit)`, `UIPanel`, `UIButton(labelKey, states normal/hover/pressed/disabled/focused)`, `UISlider(min,max,value,step,orientation)`, `UIProgressBar(value 0..1, fillDirection)`, `UIList(items, scroll, virtualization)`.
Text stores a **StringManager key + args**, never a literal, and is resolved at render time so a locale change updates immediately. Events (click, valueChanged, focusChanged) are Kotlin lambdas / script-bindable.

## Systems
1. `UILayoutSystem` — dirty-flag driven; recomputes on tree/size/resolution change.
2. `UIInputSystem` — hit-tests resolved rects (back-to-front by sortOrder/zOrder) using `InputSystem`, sets hover/press, and flags input consumed so gameplay can skip it.
3. `UIFocusSystem` — focus graph and navigation.
4. `UIRenderSystem` — walks visible nodes in sort order, emits `Renderable2D` to `Renderer2D` with an ortho camera; batches by texture; scissor for scrolling lists.

Systems run after gameplay and before render; engine-only imports.

## Focus and gamepad navigation
- Focusable = interactable widget. One focused element per canvas; modal canvases trap focus.
- D-pad / left stick (with repeat delay) / Tab move focus. Default is spatial (nearest focusable in direction by resolved rect); optional explicit `navUp/Down/Left/Right` overrides.
- Actions come from the input action map (`ui_confirm`, `ui_cancel`, `ui_up/down/left/right`, `ui_next/prev`) so rebinding works. A focused slider consumes left/right.
- Mouse hover moves focus; the last-used device decides whether the focus highlight is shown.

## Rendering dependencies
`Renderer2D` has only add/render today. Needed (coordinate with skate-render, REN-11): tinted textured quad with 9-slice, scissor push/pop, glyph quad submission, ortho screen camera. GUI-02 starts with a minimal bitmap-font path; SDF in GUI-04.

## Localization
`UIText.key` → `StringManager.getString(key, *args)`; `getQuantityString` for counts. Text is re-measured on locale change (change notification added in GUI-04).

## Serialization
Canvases are ordinary scene/prefab components and serialize with the existing scene format; assets referenced by handle.

## Testing
Pure unit tests: layout, spatial focus navigation, slider/progress math, hit-test ordering. No GL tests.

## Open questions for lead
1. Confirm the ECS-component approach (vs. a separate non-GameObject UI tree).
2. World-space canvases in GUI-02 or deferred? (Proposed: deferred.)
3. REN-11 must expose scissor and 9-slice in `Renderer2D` before GUI-02.
