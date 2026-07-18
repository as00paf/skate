package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.ecs.Scene

/**
 * Represents a single rendering pass in the rendering pipeline.
 *
 * Render passes are executed in order each frame, with each pass
 * responsible for a specific aspect of rendering (e.g., picking,
 * geometry, shadows, post-processing, debug visualization).
 */
interface RenderPass {
    /**
     * Unique name of the render pass for identification in the graph.
     */
    val name: String get() = this::class.simpleName ?: "UnknownPass"

    /**
     * Human-readable display name for UI visualization.
     */
    val displayName: String get() = name

    /**
     * Optional description of what this pass does.
     */
    val description: String get() = ""

    /**
     * Set of input resource names that this pass depends on.
     */
    val inputs: Set<String> get() = emptySet()

    /**
     * Set of output resource names that this pass provides.
     */
    val outputs: Set<String> get() = emptySet()

    /**
     * Whether this pass can be disabled via UI.
     * Critical passes should return false.
     */
    val canDisable: Boolean get() = true

    /**
     * Execution time in nanoseconds for the last frame.
     * Implementations must provide storage for this property.
     */
    var executionTimeNs: Long

    /**
     * Whether this pass is enabled for execution.
     * Implementations must provide storage for this property.
     */
    var isEnabled: Boolean

    /**
     * Called before any passes are executed to prepare resources for the frame.
     */
    fun prepare() {}

    /**
     * Executes this render pass with a contextual data set.
     *
     */
    fun execute(scene: Scene) {}

    /**
     * Called after the pass has finished execution for cleanup or unbinding.
     */
    fun cleanup() {}

    /**
     * Executes this render pass with performance timing.
     * Wraps execute() to measure execution time.
     *
     */
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

    /**
     * Toggles the enabled state of this pass.
     * Only works if canDisable returns true.
     */
    fun toggleEnable() {
        if (canDisable) {
            isEnabled = !isEnabled
        }
    }
}
