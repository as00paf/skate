package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.render.graph.RenderLabelKeys

/**
 * Represents a single rendering pass in the rendering pipeline.
 *
 * Render passes are executed in order each frame, with each pass
 * responsible for a specific aspect of rendering (e.g., picking,
 * geometry, shadows, post-processing, debug visualization).
 */
interface RenderPass {
    val name: String get() = this::class.simpleName ?: "UnknownPass"

    val displayName: String get() = name
    val description: String get() = ""
    /** Translation key of the pass name shown in tools (`lbl.render_pass.<id>`, EDT-54). */
    val labelKey: String get() = RenderLabelKeys.passLabelKey(name)
    val descriptionKey: String get() = RenderLabelKeys.passDescriptionKey(name)

    /** Resource ids read, in declaration order (see [com.pafoid.skate.engine.render.graph.RenderResourceIds]). */
    val inputs: Set<String> get() = emptySet()

    /** Resource ids written, in declaration order; may be empty. */
    val outputs: Set<String> get() = emptySet()

    /** True when the pass renders only on request (picking), so most frames have no timing. */
    val onDemand: Boolean get() = false

    /** Whether the last graph frame actually rendered this pass; an on-demand pass is false on idle frames. */
    val ranThisFrame: Boolean get() = isEnabled

    val canDisable: Boolean get() = true
    /** CPU time of the last [executeWithTiming], in nanoseconds. */
    var executionTimeNs: Long

    /** GPU time of a recent frame in nanoseconds (timer queries, a few frames late); -1 when unknown or disabled. */
    var gpuTimeNs: Long
    var isEnabled: Boolean

    fun prepare() {}
    fun execute(scene: Scene) {}
    fun cleanup() {}

    fun executeWithTiming(scene: Scene) {
        val start = System.nanoTime()
        try {
            if (isEnabled) {
                execute(scene)
            }
        } finally {
            executionTimeNs = System.nanoTime() - start
        }
    }

    fun toggleEnable() {
        if (canDisable) {
            isEnabled = !isEnabled
        }
    }
}
