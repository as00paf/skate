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
    val name: String get() = this::class.simpleName ?: "UnknownPass"

    val displayName: String get() = name
    val description: String get() = ""
    val inputs: Set<String> get() = emptySet()
    val outputs: Set<String> get() = emptySet()

    val canDisable: Boolean get() = true
    var executionTimeNs: Long
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
