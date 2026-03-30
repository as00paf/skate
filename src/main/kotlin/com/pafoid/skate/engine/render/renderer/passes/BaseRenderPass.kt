package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.render.graph.RenderContext

/**
 * Base class for render passes providing default metadata and timing implementations.
 *
 * Extend this class instead of implementing RenderPass directly to get:
 * - Default metadata properties (displayName, description, canDisable)
 * - Performance tracking (executionTimeNs)
 * - Enable/disable functionality (isEnabled, toggleEnable)
 * - Timed execution wrapper (executeWithTiming)
 */
abstract class BaseRenderPass : RenderPass {

    /**
     * Human-readable display name for UI visualization.
     * Defaults to the class simple name.
     */
    override val displayName: String
        get() = name.replace("Pass", "").replace(Regex("(\\p{Lu})")) { match ->
            " ${match.value}"
        }.trim()

    /**
     * Optional description of what this pass does.
     * Override to provide meaningful description.
     */
    override val description: String = ""

    /**
     * Whether this pass can be disabled via UI.
     * Override to false for critical passes that must always run.
     */
    override val canDisable: Boolean = true

    /**
     * Execution time in nanoseconds for the last frame.
     */
    override var executionTimeNs: Long = 0

    /**
     * Whether this pass is enabled for execution.
     */
    override var isEnabled: Boolean = true

    /**
     * Executes this render pass with performance timing.
     * Wraps execute() to measure execution time.
     *
     * @param context Data and resource context for execution
     */
    final override fun executeWithTiming(context: RenderContext) {
        val start = System.nanoTime()
        try {
            if (isEnabled) {
                execute(context)
            }
        } finally {
            executionTimeNs = System.nanoTime() - start
        }
    }

    /**
     * Toggles the enabled state of this pass.
     * Only works if canDisable returns true.
     */
    final override fun toggleEnable() {
        if (canDisable) {
            isEnabled = !isEnabled
        }
    }
}
