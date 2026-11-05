package com.pafoid.skate.engine.render.graph

import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.render.renderer.passes.RenderPass
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.RhiDevice
import com.pafoid.skate.engine.render.rhi.RhiTimer

/**
 * Ordered list of render passes executed once per frame. Each pass gets its CPU time
 * ([RenderPass.executionTimeNs]) and its GPU time ([RenderPass.gpuTimeNs]) from an [RhiTimer], read a few frames late
 * without stalling (-1 while unknown or when the device has no timer queries).
 *
 * @param device The device timing the passes; null (no device yet, unit tests) skips GPU timing.
 */
class RenderGraph(private val device: () -> RhiDevice? = { RhiContext.device }) {
    private val passes = mutableListOf<RenderPass>()

    fun addPass(pass: RenderPass) {
        passes.add(pass)
    }

    fun getAllPasses(): List<RenderPass> = passes.toList()

    fun getPassByName(name: String): RenderPass? {
        return passes.find { it.name == name }
    }

    private val gpuTimers = java.util.IdentityHashMap<RenderPass, RhiTimer>()

    fun execute(scene: Scene) {
        val d = device()
        val cmd = d?.commandList()

        // 1. Prepare all enabled nodes
        for (pass in passes) if (pass.isEnabled) pass.prepare()

        // 2. Execute all enabled nodes with CPU and GPU timing
        for (pass in passes) {
            if (!pass.isEnabled) {
                pass.gpuTimeNs = -1
                continue
            }
            val timer = d?.let { gpuTimers.getOrPut(pass) { it.createTimer() } }
            timer?.let { cmd?.beginTimer(it) }
            try {
                pass.executeWithTiming(scene)
            } finally {
                timer?.let { cmd?.endTimer(it) }
            }
        }

        // 3. Cleanup all nodes (even disabled ones may need cleanup)
        passes.forEach { it.cleanup() }

        // 4. Read finished timings once, after the frame's commands
        for ((pass, timer) in gpuTimers) if (pass.isEnabled) pass.gpuTimeNs = timer.latestNs
    }

    fun clear() {
        gpuTimers.values.forEach { it.close() }
        gpuTimers.clear()
        passes.clear()
    }
}
