package com.pafoid.skate.engine.render.graph

/**
 * Read-only description of the render graph for tools such as the editor's Render Graph window (EDT-54). Pure data:
 * readers never touch passes, GL or the RHI. Obtained from [RenderGraph.info]; a new instance with a higher [version]
 * is built whenever the pass list or any pass/stage enabled flag changes.
 *
 * @param version Changes whenever the pass list or an enabled flag changes; [PassTimings.version] refers to it.
 * @param passes Passes in execution order.
 */
data class RenderGraphInfo(
    val version: Int,
    val passes: List<PassInfo>,
)

/**
 * One pass of [RenderGraphInfo].
 *
 * @param id Stable id ([com.pafoid.skate.engine.render.renderer.passes.RenderPass.name]), e.g. "GeometryPass".
 * @param labelKey Translation key of the pass name, e.g. "lbl.render_pass.geometry".
 * @param descriptionKey Translation key of the pass description, e.g. "lbl.render_pass.geometry.desc".
 * @param reads Resource ids the pass reads, e.g. "SceneColor" (see [RenderResourceIds]).
 * @param writes Resource ids the pass writes; may be empty (e.g. no shadow maps on the device).
 * @param required True when the pass cannot be disabled.
 * @param enabled Whether the pass runs.
 * @param onDemand True when the pass renders only when requested (picking), so most frames have no timing.
 * @param stages Toggleable sub-steps in run order (post effects); empty for most passes.
 */
data class PassInfo(
    val id: String,
    val labelKey: String,
    val descriptionKey: String,
    val reads: List<String>,
    val writes: List<String>,
    val required: Boolean,
    val enabled: Boolean,
    val onDemand: Boolean,
    val stages: List<StageInfo>,
)

/**
 * A toggleable sub-step of a pass.
 *
 * @param id Stable id within the pass, e.g. "FXAA".
 * @param labelKey Translation key, e.g. "lbl.post_process.fxaa".
 * @param enabled Whether the stage is switched on.
 * @param canToggle Whether the stage can be switched from tools.
 * @param active False when the stage cannot run at all in this renderer (e.g. post-processing without the scene
 *   framebuffer in an exported game), whatever [enabled] says.
 */
data class StageInfo(
    val id: String,
    val labelKey: String,
    val enabled: Boolean,
    val canToggle: Boolean,
    val active: Boolean,
)

/**
 * Per-pass timings, filled in place by [RenderGraph.readTimings] (no allocation once sized). Index *i* is pass *i* of
 * the [RenderGraphInfo] whose version is [version]; when they differ, call [RenderGraph.info] again first.
 * All values are nanoseconds; -1 means unknown or disabled. GPU times arrive a few frames late.
 */
class PassTimings(capacity: Int = 0) {
    var version: Int = -1
    var size: Int = 0
        private set
    var cpuNs = LongArray(capacity)
        private set
    var gpuNs = LongArray(capacity)
        private set

    /**
     * Last non-negative time of each pass: GPU, else CPU. For on-demand passes, the CPU time of the last frame they
     * actually rendered (their GPU results arrive frames late and mostly time idle frames).
     */
    var lastMeasuredNs = LongArray(capacity)
        private set

    /** Sum of the per-pass GPU timers of enabled, per-frame passes (there is no whole-frame timer); -1 if none. */
    var frameGpuNs: Long = -1

    /** Sum of the per-pass CPU times of enabled, per-frame passes. */
    var frameCpuNs: Long = 0
    var gpuTimersAvailable: Boolean = false

    internal fun resize(count: Int) {
        if (count > cpuNs.size) {
            cpuNs = LongArray(count)
            gpuNs = LongArray(count)
            lastMeasuredNs = LongArray(count).also { it.fill(-1) }
        }
        if (count != size) lastMeasuredNs.fill(-1)
        size = count
    }
}

/** Resource ids used in pass reads/writes. Translation keys are `lbl.render_resource.<snake_case id>`. */
object RenderResourceIds {
    const val SHADOW_MAP = "ShadowMap"
    const val LOCAL_SHADOW_MAP = "LocalShadowMap"
    const val PICKING_IDS = "PickingIds"

    /** The scene colour target: the presented image in the editor. Post-processing ping-pong targets are internal. */
    const val SCENE_COLOR = "SceneColor"
    const val SCENE_DEPTH = "SceneDepth"

    val ALL = listOf(SHADOW_MAP, LOCAL_SHADOW_MAP, PICKING_IDS, SCENE_COLOR, SCENE_DEPTH)

    fun labelKey(id: String): String = "lbl.render_resource.${RenderLabelKeys.snake(id)}"
}

/** Translation-key conventions for render passes (`lbl.render_pass.*`, EDT-54). */
object RenderLabelKeys {
    /** "GeometryPass" -> "lbl.render_pass.geometry", "PostProcessPass" -> "lbl.render_pass.post_process". */
    fun passLabelKey(passId: String): String = "lbl.render_pass.${snake(passId.removeSuffix("Pass"))}"

    fun passDescriptionKey(passId: String): String = passLabelKey(passId) + ".desc"

    /** "SceneColor" -> "scene_color". */
    fun snake(id: String): String = id.replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), "_").lowercase()
}
