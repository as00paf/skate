package com.pafoid.skate.engine.render.graph

import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.render.renderer.passes.RenderPass
import com.pafoid.skate.engine.render.renderer.passes.StagedRenderPass
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
        structureChanged = true
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
        structureChanged = true
    }

    // ---- Tool view (EDT-54) ----------------------------------------------------------------------------------------

    private var info: RenderGraphInfo? = null
    private var version = 0
    private var structureChanged = true

    /** Enabled bits of every pass and stage, in order: the cached [info] is current while this matches. */
    private var signature = LongArray(1)
    private var scratch = LongArray(1)

    /**
     * Description of the graph for tools. Cached: rebuilt (with a new version) only when the pass list or any
     * pass/stage enabled flag changed, whoever changed it (`isEnabled` is a public var, post effects change through
     * the pass settings). Checking costs one bit per pass and stage and allocates nothing.
     */
    fun info(): RenderGraphInfo {
        val bits = signatureBits()
        val words = (bits + 63) / 64
        if (scratch.size < words) scratch = LongArray(words)
        scratch.fill(0L)
        var bit = 0
        for (i in passes.indices) {
            val pass = passes[i]
            if (pass.isEnabled) setBit(bit)
            bit++
            if (pass is StagedRenderPass) {
                for (s in 0 until pass.stageCount) {
                    if (pass.isStageEnabled(s)) setBit(bit)
                    bit++
                }
            }
        }
        val cached = info
        if (cached != null && !structureChanged && sameSignature(words)) return cached

        if (signature.size < scratch.size) signature = LongArray(scratch.size)
        signature.fill(0L)
        scratch.copyInto(signature, 0, 0, words)
        signatureWords = words
        structureChanged = false
        return build(++version).also { info = it }
    }

    private var signatureWords = 0

    private fun signatureBits(): Int {
        var bits = 0
        for (i in passes.indices) {
            val pass = passes[i]
            bits += 1 + ((pass as? StagedRenderPass)?.stageCount ?: 0)
        }
        return maxOf(bits, 1)
    }

    private fun setBit(bit: Int) {
        scratch[bit ushr 6] = scratch[bit ushr 6] or (1L shl (bit and 63))
    }

    private fun sameSignature(words: Int): Boolean {
        if (words != signatureWords) return false
        for (w in 0 until words) if (scratch[w] != signature[w]) return false
        return true
    }

    private fun build(version: Int) = RenderGraphInfo(version, passes.map { pass ->
        val staged = pass as? StagedRenderPass
        PassInfo(
            id = pass.name,
            labelKey = pass.labelKey,
            descriptionKey = pass.descriptionKey,
            reads = pass.inputs.toList(),
            writes = pass.outputs.toList(),
            required = !pass.canDisable,
            enabled = pass.isEnabled,
            onDemand = pass.onDemand,
            stages = if (staged == null) emptyList() else (0 until staged.stageCount).map { s ->
                StageInfo(staged.stageId(s), staged.stageLabelKey(s), staged.isStageEnabled(s), true, staged.stagesActive)
            },
        )
    })

    /**
     * Copies the latest timings into [into], indexed like the passes of [info] (whose version it records). Allocates
     * nothing once [into] is large enough. Disabled passes read -1. On-demand passes stay out of the frame totals;
     * their [PassTimings.lastMeasuredNs] is the CPU time of the last frame they actually rendered.
     */
    fun readTimings(into: PassTimings) {
        into.version = info().version
        into.resize(passes.size)
        var frameGpu = 0L
        var frameCpu = 0L
        var anyGpu = false
        for (i in passes.indices) {
            val pass = passes[i]
            val enabled = pass.isEnabled
            val gpu = if (enabled) pass.gpuTimeNs else -1L
            val cpu = if (enabled) pass.executionTimeNs else -1L
            into.cpuNs[i] = cpu
            into.gpuNs[i] = gpu
            if (pass.onDemand) {
                // GPU results arrive frames late and mostly time idle frames, so keep the CPU time of real renders.
                if (enabled && pass.ranThisFrame && cpu >= 0L) into.lastMeasuredNs[i] = cpu
                continue
            }
            if (gpu >= 0L) into.lastMeasuredNs[i] = gpu else if (cpu >= 0L) into.lastMeasuredNs[i] = cpu
            if (!enabled) continue
            if (gpu >= 0L) { frameGpu += gpu; anyGpu = true }
            if (cpu > 0L) frameCpu += cpu
        }
        into.frameGpuNs = if (anyGpu) frameGpu else -1L
        into.frameCpuNs = frameCpu
        into.gpuTimersAvailable = anyGpu
    }

    /** Switches pass [passId] on or off; false when there is no such pass or it is required. */
    fun setEnabled(passId: String, enabled: Boolean): Boolean {
        val pass = getPassByName(passId) ?: return false
        if (!pass.canDisable) return false
        pass.isEnabled = enabled
        return true
    }

    /** Switches stage [stageId] of pass [passId] on or off; false when either is unknown or cannot change. */
    fun setStageEnabled(passId: String, stageId: String, enabled: Boolean): Boolean {
        val pass = getPassByName(passId) as? StagedRenderPass ?: return false
        for (s in 0 until pass.stageCount) {
            if (pass.stageId(s) == stageId) return pass.setStageEnabled(s, enabled)
        }
        return false
    }
}
