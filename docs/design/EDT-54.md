# EDT-54: Render Graph window overhaul (design)

Status: **revised after the skate-render review; awaiting lead approval.** Owner of the render graph: `skate-render` (reviewed and will implement step 2a). Window: editor domain.
Depends on REN-24 (real effect names, `NoShadowPass`), which is now merged through `integration/round-27`.

Revision 2 applies the 7 required changes and the agreed nits from the skate-render review. They are marked **(R1)** to **(R7)** and **(nit)** where they land.

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
    val version: Int,                       // changes whenever the pass list or any enabled bit changes (R4)
    val passes: List<PassInfo>,             // execution order
)
data class PassInfo(
    val id: String,                         // stable: RenderPass.name, e.g. "GeometryPass"
    val labelKey: String,                   // "lbl.render_pass.geometry", translated by the editor (R6)
    val descriptionKey: String,             // "lbl.render_pass.geometry.desc"
    val reads: List<String>,                // resource ids, e.g. "SceneColor"
    val writes: List<String>,               // may be empty, e.g. NoShadowPass (R3)
    val required: Boolean,                  // !canDisable
    val enabled: Boolean,
    val onDemand: Boolean,                  // runs only when requested, e.g. PickingPass (R7)
    val stages: List<StageInfo>,            // sub-steps, e.g. post effects in run order; empty for most passes
)
data class StageInfo(
    val id: String,                         // PostEffect.name
    val labelKey: String,                   // PostEffect.labelKey, e.g. "lbl.post_process.fxaa"
    val enabled: Boolean,
    val canToggle: Boolean,
    val active: Boolean,                    // false when the whole pass cannot run (nit: exported game, no FBO)
)

class PassTimings(capacity: Int) {          // filled in place, no per-frame allocation
    var version: Int                        // version of the info() the arrays index into (R4)
    val cpuNs: LongArray; val gpuNs: LongArray   // index = pass index in info().passes; -1 = unknown or disabled
    val lastMeasuredNs: LongArray           // last non-negative GPU (else CPU) time, for on-demand passes (R7)
    var frameGpuNs: Long                    // sum of the per-pass timers, not a whole-frame timer (nit)
    var frameCpuNs: Long
    var gpuTimersAvailable: Boolean
}

// RenderGraph additions
fun info(): RenderGraphInfo                 // cached; rebuilt only when the enabled signature changes (R4)
fun readTimings(into: PassTimings)          // copies the latest values, sets into.version; no allocation
fun setEnabled(passId: String, enabled: Boolean): Boolean        // false if unknown or required
fun setStageEnabled(passId: String, stageId: String, enabled: Boolean): Boolean
```

- **Change detection (R4).** `RenderPass.isEnabled` is a public `var`, and other code changes it too: `toggleEnable()`, the GPU parity harness, `PostProcessPass.updateSettings()` / `toggleEffect()`. So `version` is not bumped by `setEnabled` alone.
  - Every `info()` call computes a cheap **signature**: the pass count and one bit per pass `isEnabled` and per stage `enabled`, packed into a `Long` (plus a `BitSet` fallback above 64 bits).
  - It compares the signature with the cached one, without allocating. Only when it differs does it rebuild the info and increment `version`.
  - `readTimings(into)` writes the same `version` into `PassTimings`. If the window sees `timings.version != info.version`, it rebuilds its layout and resizes the arrays before indexing, so index *i* is always pass *i* of the current `info()`.
- **Stages (R5).** `PostProcessPass` reports **every** effect of `PostEffect.DEFAULT_PIPELINE`, in that order, with its own `enabled` flag. Disabled effects are included, so they can be switched back on from the window. `labelKey` is `PostEffect.labelKey`. `setStageEnabled` maps to `PostProcessSettings`, enabling or disabling to the requested state, not toggling. Other passes report no stages until they need them.
- **Exported games (nit).** Without the scene framebuffer (`useFbo == false`), PostProcess is skipped entirely, and `info()` marks its stages `active = false`. The editor always uses the FBO, so this never shows there. It is noted so the contract is correct for any runtime viewer.
- **Shadow quality** stays an editor setting. The window still shows it in the details panel for `id == "ShadowPass"` through `EditorSettingsManager.updateShadowQuality`, without importing `ShadowPass`.
- **What the window imports.** Afterwards it imports only `engine.render.graph.*` and editor code: no `engine.render.renderer.passes.*` and no RHI. A new guard (`RenderGraphWindowIsolationGuardTest`) fails if a file under `editor/ui/windows/rendergraph/` imports `engine.render.renderer.passes` or `engine.render.rhi`.
- **Timings source.** `RenderGraph.execute` already records CPU time per pass and reads GPU time from `RhiTimer` a few frames late, without stalling (REN-22). It sets -1 for disabled passes. `readTimings` only copies those values (and keeps `lastMeasuredNs`). The window shows -1 as "—" (nit). This works the same for OpenGL and Vulkan (VK timestamp queries behind `RhiTimer`). In practice the window only ever sees OpenGL, because the editor is GL-only.
- **Threading.** The window reads on the render (main) thread after `renderer.render`, which is the thread that writes these fields, so no locking is needed (confirmed by skate-render).

### 3.1 Inputs and outputs to declare (`NEEDS REN`, checked by skate-render against the code)

Execution order (`RenderResources.inExecutionOrder`): Shadow, Picking, Geometry, Particles, PostProcess, Debug, UI.

| Pass | reads | writes | required |
|---|---|---|---|
| ShadowPass | — | `ShadowMap`, `LocalShadowMap`; **none** for `NoShadowPass` (R3) | no |
| PickingPass (on demand, R7) | — | `PickingIds` | no (click-select stops; tooltip says so) |
| GeometryPass | `ShadowMap`, `LocalShadowMap` | `SceneColor`, `SceneDepth` | **yes** |
| ParticlePass | `SceneColor`, `SceneDepth` | `SceneColor` | no |
| PostProcessPass | `SceneColor`, `SceneDepth` (SSAO) | `SceneColor` (R1) | no (when off, the unprocessed scene colour is shown, as today) |
| DebugPass | `SceneDepth` (depth-tests against geometry) | `SceneColor` (R1) | no |
| UiPass | — | `SceneColor` (R1) | no |

- **No `FinalColor` (R1).** The renderer has no such target.
  - `PostProcessPass` alternates between two scratch textures (`PostChain`), then blits the result back into the scene colour target. The scratch textures are internal and not shown as resources.
  - Debug and UI draw into the presented target. That is the scene framebuffer in the editor and the back buffer in exported games.
  - The graph uses one resource, `SceneColor`, and its tooltip says "Scene colour target (the presented image in the editor)". Edges into later readers come from the latest writer, so the chain Geometry → Particles → PostProcess → Debug → UI reads as successive versions of `SceneColor`.
- **Shadow off (R2).** Nothing new to build. `ShadowPass` publishes its maps each frame (`ShadowFrame.publish()`), and `GeometryPass` uses them only if they were published that frame (`shadowFrame.consume()`). **Geometry renders unshadowed when the shadow pass did not publish this frame.** skate-render adds a test for this in 2a.
- **Resource names (R6)** are translated with `lbl.render_resource.<id>`.

## 4. Layout (pure, tested)

`editor/ui/windows/rendergraph/RenderGraphLayout.kt` has no ImGui import and is added to the `PureLogicIsolationGuardTest` list. Text widths come from a `measure: (String) -> Float` function, which is `ImGui.calcTextSize` in the window and a fixed-width stub in tests.

1. **Columns.** Pass *i* goes in column *i*. Columns are as wide as their widest node and separated by a constant gap. Execution order reads left to right, which suits the wide, short bottom dock.
2. **Node size.** Width is the largest of the title, the timing row and the widest pin label, plus padding. Height comes from the number of rows: header, timing bar, one row per input/output pin (the larger of the two counts), and stage rows when expanded.
3. **Edges.** For every read of resource *r* by pass *j*, the edge comes from the latest earlier pass that writes *r*. Edges ignore the enabled flags, so the graph does not reflow on toggle; edges out of a disabled pass are drawn dashed.
   - **No writer (R3).** A read with no earlier writer gets no edge. Its input pin is drawn dimmed with a "no writer" tooltip. This is the normal case for Geometry's `ShadowMap`/`LocalShadowMap` on devices without shadow maps, where the shadow slot is `NoShadowPass` with no outputs.
   - **Passes with no writes or no reads** (`NoShadowPass`, `UiPass`) are valid nodes: they simply have no pins on that side.
   - Outputs that no later pass reads (`PickingIds`, the last `SceneColor`) keep their pin with no edge.
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
- A read with no writer gets no edge and a dimmed pin, and a pass with no writes (`NoShadowPass`) lays out with no output pins (R3).
- Successive `SceneColor` writers form a chain: each reader links to the latest earlier writer (R1).
- Nodes grow with label width.
- *Fit* contains every node at the expected zoom.
- Zoom keeps the point under the cursor fixed.
- Hit-testing returns the right node or pin.
- The layout is rebuilt when `RenderGraphInfo.version` changes and not otherwise (R4).
- The vertical orientation is the horizontal one transposed.
- A disabled pass keeps its position, so toggling does not reflow the graph.

## 5. Window content

```
┌ Render Graph ───────────────────────────────────────────────────────────────────────────┐
│ [Fit] [1:1]  [ ] Auto-refresh timings  | CPU 6.1 ms  GPU 4.8 ms  | 7/7 passes | ⚠ timers │
├─────────────────────────────────────────────────────────────────────┬───────────────────┤
│  ┌☀ Shadows   [✓]┐   ┌⊕ Picking  [✓]┐   ┌■ Geometry   🔒┐   ┌✦ Partic…│ Geometry          │
│  │GPU 0.9 CPU 0.2│   │On demand      │  │GPU 2.1 CPU 1.4│   │…        │ Draws opaque and… │
│  │██░░░░░░░ 19 % │   │last 0.3 ms    │  │████░░░░░ 44 % │   │         │ Reads: Shadow map │
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
  - Timing row: GPU ms, then CPU ms; -1 shows as "—" (nit). Then a bar showing the pass's share of the frame's GPU time, or of CPU time when there are no timer queries.
  - The frame GPU total in the toolbar has a tooltip: "Sum of the per-pass GPU timers (no whole-frame timer); values arrive a few frames late" (nit).
  - Colours compare against the frame budget, not absolute ms: the default budget is 16.6 ms, and colours change when a pass passes 25 % or 50 % of it.
  - Pins: inputs on the left, outputs on the right, labelled with translated resource names.
- **Disabled pass.** The node is dimmed with an "Off" badge, timings show "—", and its outgoing edges are dashed. The status bar shows "N passes off" and an *Enable all* button.
- **On-demand pass (R7).** `PickingPass` renders only when a pick is requested (REN-22), so its timings are 0 or -1 on most frames.
  - It is not drawn as disabled. Its timing row reads "On demand" with "last *x* ms" from `PassTimings.lastMeasuredNs`, and its tooltip explains why.
  - It is left out of the per-frame share bars and the frame totals, so it does not skew the other passes' shares.
- **Post-processing node.** It expands to show **all** its effects in `PostEffect.DEFAULT_PIPELINE` order (SSAO, Bloom, Tonemapping, Gamma Correction, FXAA), enabled or not, each with its own checkbox and its REN-24 label (R5). Only the pass total time is shown; per-effect GPU timers are deferred (§8, 2c).
- **Details panel.** On the right, behind a splitter; only when a node is selected:
  - Translated description.
  - Reads and writes. Clicking one highlights its edges.
  - A sparkline of the last 120 frames of GPU and CPU ms, from a ring buffer allocated once.
  - Average and maximum.
  - Pass-specific settings (the shadow quality combo).
- **Timings refresh.** Timings refresh every frame. Displayed numbers are smoothed over about 0.5 s so they can be read. *Auto-refresh* off freezes the numbers but keeps the graph interactive. The old behaviour hid the whole graph.
- **Empty or no-timers states.** With no scene or no passes: the empty-state helper with a hint. Without GPU timer queries: a "⚠ timers" chip with a tooltip, and CPU-only bars.
- **ImGui ids** are stable (`##rg.<passId>`, `##rg.<passId>.<stageId>`).
- **String keys (R6)** follow the existing `lbl.*` convention, in `assets/strings/editor/render*.properties` (en and fr):
  - Pass names: `lbl.render_pass.<key>`, and descriptions `lbl.render_pass.<key>.desc`. `<key>` is the pass id without the `Pass` suffix, in snake case, as REN-24 does for effects (`lbl.post_process.gamma_correction`): `GeometryPass` → `lbl.render_pass.geometry`, `PostProcessPass` → `lbl.render_pass.post_process`.
  - Resources: `lbl.render_resource.<key>`, for example `lbl.render_resource.scene_color`.
  - Post effects: the existing `PostEffect.labelKey` (`lbl.post_process.<effect>`).
  - Window chrome keeps the existing `lbl.render_graph.*` and `tooltip.render_graph.*` keys.
  - A test checks that every pass id and resource id in `info()` has a non-blank label in both files.
  - With every label and tooltip a key, this keeps `ImGuiStableIdGuardTest`, `EditorHardcodedLabelGuardTest` and `TooltipGuardTest` green.

## 6. Enable/disable semantics

- Toggles call `renderGraph.setEnabled(...)` / `setStageEnabled(...)` through an execute-only `SetRenderPassEnabledCommand`. It is listed in `UndoCoverageGuardTest` as "editor-only debug view of the renderer, not scene data", next to `TogglePhysicsDebugCommand`.
- Toggles last for the editor session only. They are not saved to the scene, project or export, so an exported game always runs every pass. On project open, all passes are enabled again.
- They apply in Edit and in Play mode; it is the same renderer.
- Required passes (`canDisable = false`) cannot be toggled, and `setEnabled` returns false for them. This needs `GeometryPass.canDisable = false` (`NEEDS REN`). `BaseRenderPass.toggleEnable()` already respects it. Tests such as the GPU parity harness still set `isEnabled` directly, which stays allowed and is caught by the signature check (R4).
- Disabling Picking shows a tooltip and a status-bar hint: "Click-select in the viewport is off".

## 7. Docked and shown by default

- `RenderGraphWindow` is registered with `isDefault = true`. `DefaultDockLayout` already puts `window.render_graph` in the BOTTOM zone, as a tab after Console. Console stays the active tab, so the new tab does not take focus on first run.
- Saved layouts (`imgui.ini`) are left alone: users who closed the window keep it closed. *View → Reset Layout* shows it again.
- Test: extend `DefaultDockLayoutTest` so `window.render_graph` is a default window in the BOTTOM zone.

## 8. Phasing and ownership (after approval)

| Step | Who | Content | Tests |
|---|---|---|---|
| 2a | skate-render | `RenderGraphInfo`/`PassTimings`/`info()`/`readTimings()`/`setEnabled()`/`setStageEnabled()`; signature-based `version` (R4); `onDemand` flag (R7); pass `labelKey`s (`lbl.render_pass.*`, R6) with en/fr strings; I/O table §3.1 (R1, R3); every post effect as a stage (R5); `GeometryPass.canDisable = false` | `RenderGraphInfoTest`: order; I/O; `NoShadowPass` has no writes; version changes when `isEnabled` or an effect is set *outside* `setEnabled`, and not otherwise; all effects listed, disabled ones included; required pass refuses; no allocation in `info()` when unchanged or in `readTimings`. A test that Geometry renders unshadowed when the shadow pass did not publish (R2). |
| 2b | editor (assignee) | `RenderGraphLayout` + view transform; new window under `editor/ui/windows/rendergraph/`; strings en/fr; default-open; command and undo exception; isolation guard | `RenderGraphLayoutTest`, `RenderGraphViewTest`, layout default test, guard test |
| 2c (**deferred**) | skate-render | per-stage GPU timers inside `PostProcessPass` (one `RhiTimer` per effect). Deferred: 5 extra timer queries per frame for little gain until the effects get heavier (bloom blur, real SSAO). | timer bookkeeping unit test |

OpenGL rendering is unchanged by 2a and 2b, apart from the opt-in toggles. REN-12 pixel parity and REN-22 timing guarantees still hold. `RenderGraph.execute` gains no work, and `info()` is cached.

## 9. Decisions and open questions

Agreed with skate-render (revision 2):
1. **Required passes:** only Geometry is locked.
2. **Toggle persistence:** session-only; every pass is enabled again on project open.
3. **Per-effect GPU timings (2c):** deferred.

Still open for the lead or the human:
4. **Default tab.** The window joins the bottom dock as a background tab, with Console active. Should it be the active tab on first run instead?

## 10. Revision log

- **Revision 1** (`4e2f465c`): initial proposal.
- **Revision 2**, after the skate-render review:
  - R1: no `FinalColor`; PostProcess, Debug and UI write `SceneColor`.
  - R2: the neutral-shadow-map work is dropped (`ShadowFrame` publish/consume already gives unshadowed rendering).
  - R3: passes may have no writes; a read with no writer shows a dimmed pin, not an external stub.
  - R4: change detection uses a signature of the enabled bits; `PassTimings` carries the version.
  - R5: every post effect is a stage, disabled ones included.
  - R6: `lbl.render_pass.*` / `lbl.render_resource.*` keys.
  - R7: picking is shown as "on demand" and kept out of the share bars.
  - Nits applied: the frame GPU total is a sum (tooltip), -1 shows as "—", stages inactive without an FBO, the `toggleEnable`/harness note, the threading confirmation, 2c deferred.
- **Step 2a implementation note:** an on-demand pass is still timed on its idle frames (about 0 ms), and GPU results arrive frames late. So `RenderPass.ranThisFrame` (from `PickingSchedule.fresh` for picking) limits `lastMeasuredNs` to frames where the pass really rendered, and for on-demand passes it holds that frame's CPU time. For per-frame passes it is GPU, else CPU, as designed. Geometry's shadow handoff is `GeometryPass.shadowsThisFrame()`, which is tested.
- **Step 2b implementation notes:**
  - The window lives in `editor/ui/windows/rendergraph/`.
  - Toggles go through `RenderGraphAction` events. `RenderGraphActionHandler` runs them as `SetRenderPassEnabledCommand` (UI code never executes commands, per `UiMutationPipelineGuardTest`), and it also re-enables every pass on `ProjectEvent.Opened`.
  - Layout caching is the pure `RenderGraphLayoutCache`.
  - Console stays the front tab after a layout build through `DefaultDockLayout.ACTIVE_TABS`.
