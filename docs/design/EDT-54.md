# EDT-54: Render Graph window overhaul (design)

Status: **proposal, awaiting approval.** Owner of the render graph: `skate-render` (reviews). Window: editor domain.
Depends on REN-24 (real pass and effect names; on `agents/render` as `9b548657`, not yet on master).

## 1. What is wrong today (master `a0dff11e`)

`editor/ui/windows/RenderGraphWindow.kt`:

| # | Problem | Where |
|---|---|---|
| P1 | Not a graph. Each pass is an ImGui collapsing header placed at `index * 160px` with a text "↓" between them. Open headers overlap the next pass, and the 400 px child area has no pan or fit. "Zoom" only changes the spacing. | `renderGraphNodes`, `drawConnectionArrow` |
| P2 | Names are generic. Every post effect checkbox reads "Effect", fixed by REN-24 (`PostEffect.labelKey`). Pass names come from `BaseRenderPass.displayName`, which splits the class name and is not translated. The icon switch knows 4 of the 7 passes. | `renderPassNode`, `getPassIcon` |
| P3 | Inputs and outputs are incomplete. Only Shadow (`ShadowMap` out), Geometry (`ShadowMap` in) and Particles (`SceneColor` in/out) declare any, so the window cannot draw data edges. | passes in `engine/render/renderer/passes` |
| P4 | Timings are hard to read. CPU ms is coloured by absolute thresholds (1 ms / 2 ms), and GPU ms is plain text inside the collapsed body. Nothing compares passes or shows trends. The status bar sums CPU time only. | `renderPassNode`, `renderStatusBar` |
| P5 | Enable/disable is risky. Every pass is disableable (`canDisable` defaults to true), including Geometry, which leaves the viewport black. Toggles mutate the pass directly from the UI. | `pass.toggleEnable()` |
| P6 | Not visible by default. `isDefault` is false, so the window is closed on first run, although `DefaultDockLayout` already gives it the BOTTOM zone. | `WindowRegistry`, `RenderGraphWindow` |
| P7 | Coupling. The window imports the concrete `PostProcessPass` and `ShadowPass` and calls their methods. Every new pass setting means more editor code against renderer internals. | imports |

## 2. Goals and non-goals

Goals:
1. **Readable node layout:** passes are boxes in execution order, left to right. Data edges are labelled with the resource and do not overlap. The layout fits the docked bottom panel, with pan, zoom and *Fit*.
2. **Real names:** each pass and post effect shows a translated name (en/fr) with a distinct icon.
3. **Inputs and outputs:** every pass declares what it reads and writes. Pins show the resource names, and edges run from the last writer to each reader.
4. **Per-pass GPU timings:** GPU and CPU ms on each node, a bar giving each pass's share of the frame, a short history graph for the selected pass, and frame totals.
5. **Enable/disable a pass:** a toggle on each node. Required passes are locked. The effect of a toggle is clear (dimmed node, status bar warning, *Enable all*).
6. **Docked and shown by default** in the bottom dock on first run and after *Reset Layout*.

Non-goals:
- Editing the graph: adding, removing or reordering passes, or rewiring resources. The graph is still built in code (`RenderResources.inExecutionOrder`).
- Persisting pass toggles. They are a debug view of the editor's renderer, not scene or project data (§6).
- A general render-graph compiler with transient resource aliasing. Resource names are descriptive only.

## 3. How the window reads the graph (layering)

Layering rules that apply here:
- The engine never imports the editor (`EngineLayering_EngineDoesNotImportEditorExceptExportTools`).
- No GL outside `render/rhi/opengl` (`RhiGlIsolationGuardTest`).
- Pure logic stays free of ImGui, LWJGL and editor imports (`PureLogicIsolationGuardTest`).

The editor already depends on the engine. This design narrows that dependency to **one read-only data contract and one control call**, both in `engine/render/graph`:

```kotlin
// engine/render/graph/RenderGraphInfo.kt: pure data, no GL, no editor import
data class RenderGraphInfo(
    val version: Int,                       // bumps when passes, resources or enabled flags change
    val passes: List<PassInfo>,             // execution order
)
data class PassInfo(
    val id: String,                         // stable: RenderPass.name, e.g. "GeometryPass"
    val labelKey: String,                   // "render.pass.geometry", translated by the editor
    val descriptionKey: String,             // "render.pass.geometry.desc"
    val reads: List<String>,                // resource ids, e.g. "SceneColor"
    val writes: List<String>,
    val required: Boolean,                  // !canDisable
    val enabled: Boolean,
    val stages: List<StageInfo>,            // sub-steps, e.g. post effects in run order; empty for most passes
)
data class StageInfo(val id: String, val labelKey: String, val enabled: Boolean, val canToggle: Boolean)

class PassTimings(capacity: Int) {          // filled in place, no per-frame allocation
    val cpuNs: LongArray; val gpuNs: LongArray   // index = pass index in RenderGraphInfo.passes; -1 = unknown
    var frameGpuNs: Long; var frameCpuNs: Long
    var gpuTimersAvailable: Boolean
}

// RenderGraph additions
fun info(): RenderGraphInfo                 // cached; rebuilt only when `version` changes
fun readTimings(into: PassTimings)          // copies the latest values; no allocation
fun setEnabled(passId: String, enabled: Boolean): Boolean        // false if unknown or required
fun setStageEnabled(passId: String, stageId: String, enabled: Boolean): Boolean
```

- **Stages.** `PostProcessPass` reports its post effects as stages, using `PostEffect.labelKey` from REN-24 and the run order from `PostChain`/`resolvedEffects()`. `setStageEnabled` maps to `toggleEffect`. Other passes report no stages until they need them.
- **Shadow quality** stays an editor setting. The window still shows it in the details panel for `id == "ShadowPass"` through `EditorSettingsManager.updateShadowQuality`, without importing `ShadowPass`.
- **What the window imports.** Afterwards it imports only `engine.render.graph.*` and editor code: no `engine.render.renderer.passes.*` and no RHI. A new guard (`RenderGraphWindowIsolationGuardTest`) fails if a file under `editor/ui/windows/rendergraph/` imports `engine.render.renderer.passes` or `engine.render.rhi`.
- **Timings source.** `RenderGraph.execute` already records CPU time per pass and reads GPU time from `RhiTimer` a few frames late, without stalling (REN-22). `readTimings` only copies those values. This works the same for OpenGL and Vulkan (VK timestamp queries behind `RhiTimer`). In practice the window only ever sees OpenGL, because the editor is GL-only.
- **Threading.** The window reads on the main thread after `renderer.render`, which is the thread that writes these fields, so no locking is needed.

### 3.1 Inputs and outputs to declare (`NEEDS REN`, confirmed by skate-render)

Execution order (`RenderResources.inExecutionOrder`): Shadow, Picking, Geometry, Particles, PostProcess, Debug, UI.

| Pass | reads | writes | required |
|---|---|---|---|
| ShadowPass | — | `ShadowMap`, `LocalShadowMap` | no |
| PickingPass | — | `PickingIds` | no (click-select stops; tooltip says so) |
| GeometryPass | `ShadowMap`, `LocalShadowMap` | `SceneColor`, `SceneDepth` | **yes** |
| ParticlePass | `SceneColor`, `SceneDepth` | `SceneColor` | no |
| PostProcessPass | `SceneColor`, `SceneDepth` (SSAO) | `FinalColor` | no (when off, the unprocessed scene colour is shown, as today) |
| DebugPass | `SceneDepth` | `FinalColor` | no |
| UiPass | — | `FinalColor` | no |

Skate-render checks each entry against the real targets (for example, whether Debug depth-tests against `SceneDepth`). It also decides what a reader sees when its writer is off: Geometry should sample a cleared, fully lit shadow map, not stale data, when Shadow is disabled. Resource names are translated with `render.resource.<id>`.

## 4. Layout (pure, tested)

`editor/ui/windows/rendergraph/RenderGraphLayout.kt` has no ImGui import and is added to the `PureLogicIsolationGuardTest` list. Text widths come from a `measure: (String) -> Float` function, which is `ImGui.calcTextSize` in the window and a fixed-width stub in tests.

1. **Columns.** Pass *i* goes in column *i*. Columns are as wide as their widest node and separated by a constant gap. Execution order reads left to right, which suits the wide, short bottom dock.
2. **Node size.** Width is the largest of the title, the timing row and the widest pin label, plus padding. Height comes from the number of rows: header, timing bar, one row per input/output pin (the larger of the two counts), and stage rows when expanded.
3. **Edges.** For every read of resource *r* by pass *j*, the edge comes from the latest earlier pass that writes *r*. A read with no writer gets an "external" stub on the left.
   - Edges between neighbouring columns are straight pin-to-pin curves.
   - Longer edges go over or under the nodes in **lanes**. Lanes are assigned greedily to edges sorted by span (interval colouring), so two edges never share a lane while their spans overlap.
   - All edges into one input pin share a colour per resource.
4. **Orientation.** If the available width is less than `0.6 × the laid-out width` and the height is larger than the width, the window switches to top-to-bottom. That is the same algorithm with the axes swapped, used when the window is docked left or right.
5. **View transform.** `pan` and `zoom` (0.25 to 2) are pure state:
   - *Fit* computes the zoom and pan that frame the graph with a margin.
   - Mouse-wheel zoom is centred on the cursor.
   - Middle- or right-drag pans.
   - Hit testing (node, pin, toggle, stage row) runs on the same rectangles, so drawing and clicking cannot disagree.
6. **Stability.** The layout is recomputed only when `RenderGraphInfo.version`, the font scale or the orientation changes. A pan or zoom only changes the transform.

Planned tests (`RenderGraphLayoutTest`, no GPU):
- Columns follow execution order.
- No two nodes overlap.
- Every edge links a writer column to a later reader column.
- No two edges with overlapping spans share a lane.
- An external read gets a stub.
- Nodes grow with label width.
- *Fit* contains every node at the expected zoom.
- Zoom keeps the point under the cursor fixed.
- Hit-testing returns the right node or pin.
- The vertical orientation is the horizontal one transposed.
- A disabled pass keeps its position, so toggling does not reflow the graph.

## 5. Window content

```
┌ Render Graph ───────────────────────────────────────────────────────────────────────────┐
│ [Fit] [1:1]  [ ] Auto-refresh timings  | CPU 6.1 ms  GPU 4.8 ms  | 7/7 passes | ⚠ timers │
├─────────────────────────────────────────────────────────────────────┬───────────────────┤
│  ┌☀ Shadows   [✓]┐   ┌⊕ Picking  [✓]┐   ┌■ Geometry   🔒┐   ┌✦ Partic…│ Geometry          │
│  │GPU 0.9 CPU 0.2│   │GPU 0.3 CPU 0.1│  │GPU 2.1 CPU 1.4│   │…        │ Draws opaque and… │
│  │██░░░░░░░ 19 % │   │█░░░░░░░░  6 % │  │████░░░░░ 44 % │   │         │ Reads: Shadow map │
│  │ ShadowMap   ●─┼───┼───────────────┼──●ShadowMap    │   │         │ Writes: Scene col…│
│  │ LocalShadow ●─┼───┼───────────────┼──●LocalShadow  │   │         │ GPU ▁▂▂▃▂▂▅▂ 2.1ms│
│  └───────────────┘   └───────────────┘  │ SceneColor ●─┼───┼●        │                   │
│                                          └──────────────┘   └─────────│                   │
├─────────────────────────────────────────────────────────────────────┴───────────────────┤
│ Middle-drag to pan, wheel to zoom. Click a node for details.                            │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

- **Node.**
  - Header: pass icon (a distinct one per pass id, with a fallback), translated name, then an enable checkbox, or a lock icon with a "required" tooltip.
  - Timing row: GPU ms, then CPU ms. Then a bar showing the pass's share of the frame's GPU time, or of CPU time when there are no timer queries.
  - Colours compare against the frame budget, not absolute ms: the default budget is 16.6 ms, and colours change when a pass passes 25 % or 50 % of it.
  - Pins: inputs on the left, outputs on the right, labelled with translated resource names.
- **Disabled pass.** The node is dimmed with an "Off" badge, timings show "—", and its outgoing edges are dashed. The status bar shows "N passes off" and an *Enable all* button.
- **Post-processing node.** It expands to show its effects in run order (Bloom, Tonemapping, Gamma, FXAA, SSAO), each with its own checkbox. Effect GPU timings appear only if skate-render adds per-stage timers (optional, §8). Until then the pass total is shown.
- **Details panel.** On the right, behind a splitter; only when a node is selected:
  - Translated description.
  - Reads and writes. Clicking one highlights its edges.
  - A sparkline of the last 120 frames of GPU and CPU ms, from a ring buffer allocated once.
  - Average and maximum.
  - Pass-specific settings (the shadow quality combo).
- **Timings refresh.** Timings refresh every frame. Displayed numbers are smoothed over about 0.5 s so they can be read. *Auto-refresh* off freezes the numbers but keeps the graph interactive. The old behaviour hid the whole graph.
- **Empty or no-timers states.** With no scene or no passes: the empty-state helper with a hint. Without GPU timer queries: a "⚠ timers" chip with a tooltip, and CPU-only bars.
- **ImGui ids** are stable (`##rg.<passId>`, `##rg.<passId>.<stageId>`). Every label and tooltip is a key in `assets/strings/editor/render*.properties` (en and fr). This keeps `ImGuiStableIdGuardTest`, `EditorHardcodedLabelGuardTest` and `TooltipGuardTest` green.

## 6. Enable/disable semantics

- Toggles call `renderGraph.setEnabled(...)` / `setStageEnabled(...)` through an execute-only `SetRenderPassEnabledCommand`. It is listed in `UndoCoverageGuardTest` as "editor-only debug view of the renderer, not scene data", next to `TogglePhysicsDebugCommand`.
- Toggles last for the editor session only. They are not saved to the scene, project or export, so an exported game always runs every pass. On project open, all passes are enabled again.
- They apply in Edit and in Play mode; it is the same renderer.
- Required passes (`canDisable = false`) cannot be toggled, and `setEnabled` returns false for them. This needs `GeometryPass.canDisable = false` (`NEEDS REN`).
- Disabling Picking shows a tooltip and a status-bar hint: "Click-select in the viewport is off".

## 7. Docked and shown by default

- `RenderGraphWindow` is registered with `isDefault = true`. `DefaultDockLayout` already puts `window.render_graph` in the BOTTOM zone, as a tab after Console. Console stays the active tab, so the new tab does not take focus on first run.
- Saved layouts (`imgui.ini`) are left alone: users who closed the window keep it closed. *View → Reset Layout* shows it again.
- Test: extend `DefaultDockLayoutTest` so `window.render_graph` is a default window in the BOTTOM zone.

## 8. Phasing and ownership (after approval)

| Step | Who | Content | Tests |
|---|---|---|---|
| 2a | skate-render | `RenderGraphInfo`/`PassTimings`/`info()`/`readTimings()`/`setEnabled()`/`setStageEnabled()`, `version` bump; pass `labelKey`s; I/O table §3.1; `GeometryPass.canDisable = false`; neutral shadow map when Shadow is off | `RenderGraphInfoTest` (order, I/O, version bumps on toggle, required pass refuses, no allocation in `readTimings`) |
| 2b | editor (assignee) | `RenderGraphLayout` + view transform; new window under `editor/ui/windows/rendergraph/`; strings en/fr; default-open; command and undo exception; isolation guard | `RenderGraphLayoutTest`, `RenderGraphViewTest`, layout default test, guard test |
| 2c (optional) | skate-render | per-stage GPU timers inside `PostProcessPass` (one `RhiTimer` per effect) | timer bookkeeping unit test |

OpenGL rendering is unchanged by 2a and 2b, apart from the opt-in toggles. REN-12 pixel parity and REN-22 timing guarantees still hold. `RenderGraph.execute` gains no work, and `info()` is cached.

## 9. Open questions for the human

1. **Required passes.** Proposed: only Geometry. Should UI or Picking also be locked?
2. **Toggle persistence.** Proposed: session-only, all on at project open. Should toggles persist per project in editor settings?
3. **Per-effect GPU timings (step 2c):** now or later? They cost one timer query per effect per frame.
4. **Default tab.** The window joins the bottom dock as a background tab. Should it be the active tab on first run instead?
