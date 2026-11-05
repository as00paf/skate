package com.pafoid.skate.engine.render.graph

import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.render.renderer.passes.RenderPass
import com.pafoid.skate.engine.render.rhi.opengl.GLBindings

/**
 * Ordered list of render passes executed once per frame. Each pass gets its CPU time
 * ([RenderPass.executionTimeNs]) and, when the context has timer queries, its GPU time ([RenderPass.gpuTimeNs], read
 * a few frames late without stalling).
 *
 * @param gpuQueries Creates the timer-query API on first use (needs a current GL context); null disables GPU timing.
 */
class RenderGraph(private val gpuQueries: () -> GpuQueryApi? = { GpuQueryApi.openGl() }) {
    private val passes = mutableListOf<RenderPass>()

    fun addPass(pass: RenderPass) {
        passes.add(pass)
    }

    fun getAllPasses(): List<RenderPass> = passes.toList()

    fun getPassByName(name: String): RenderPass? {
        return passes.find { it.name == name }
    }

    private val gpuTimers = java.util.IdentityHashMap<RenderPass, GpuTimerRing>()
    private var gpuApi: GpuQueryApi? = null
    private var gpuApiResolved = false
    private var frame = 0L

    fun execute(scene: Scene) {
        if (!gpuApiResolved) {
            gpuApi = gpuQueries()
            gpuApiResolved = true
        }
        val api = gpuApi

        // 1. Prepare all enabled nodes
        for (pass in passes) if (pass.isEnabled) pass.prepare()

        // 2. Execute all enabled nodes with CPU and GPU timing
        for (pass in passes) {
            if (!pass.isEnabled) {
                pass.gpuTimeNs = -1
                continue
            }
            val timer = api?.let { a -> gpuTimers.getOrPut(pass) { GpuTimerRing(a) } }
            // Passes may use raw GL, so cached bindings are not trusted across pass boundaries
            GLBindings.cache.reset()
            timer?.begin(frame)
            try {
                pass.executeWithTiming(scene)
            } finally {
                timer?.end(frame)
            }
            GLBindings.cache.reset()
        }

        // 3. Cleanup all nodes (even disabled ones may need cleanup)
        passes.forEach { it.cleanup() }

        for ((pass, timer) in gpuTimers) {
            timer.collect()
            if (pass.isEnabled) pass.gpuTimeNs = timer.latestNs
        }
        frame++
    }

    fun clear() {
        gpuTimers.values.forEach { it.destroy() }
        gpuTimers.clear()
        passes.clear()
    }
}
