package com.pafoid.skate.editor.ui.windows.rendergraph

import com.pafoid.skate.engine.render.graph.PassTimings
import com.pafoid.skate.engine.render.graph.RenderGraphInfo
import kotlin.math.exp

/**
 * Readable timings for the Render Graph window (EDT-54 §5), pure: values smoothed over about [SMOOTHING_SECONDS] so
 * they can be read, a [HISTORY] frame ring buffer per pass for the sparkline, shares of the frame and budget levels.
 * Arrays are reallocated only when the pass count changes.
 */
class PassTimingStats {
    var size = 0
        private set
    private var gpuMs = FloatArray(0)
    private var cpuMs = FloatArray(0)
    private var gpuHistory = Array(0) { FloatArray(HISTORY) }
    private var cpuHistory = Array(0) { FloatArray(HISTORY) }

    /** Next write position in every history ring; the oldest sample is here too once the ring is full. */
    var historyOffset = 0
        private set
    var historyCount = 0
        private set

    var frameGpuMs = -1f
        private set
    var frameCpuMs = 0f
        private set

    /** Feeds one frame of [timings] (read for [info]) after [dtSeconds]. */
    fun update(info: RenderGraphInfo, timings: PassTimings, dtSeconds: Float) {
        val n = info.passes.size
        if (n != size) reset(n)
        val a = (1f - exp(-dtSeconds / SMOOTHING_SECONDS)).coerceIn(0f, 1f)
        for (i in 0 until minOf(n, timings.size)) {
            gpuMs[i] = smooth(gpuMs[i], timings.gpuNs[i], a)
            cpuMs[i] = smooth(cpuMs[i], timings.cpuNs[i], a)
            gpuHistory[i][historyOffset] = toMs(timings.gpuNs[i]).coerceAtLeast(0f)
            cpuHistory[i][historyOffset] = toMs(timings.cpuNs[i]).coerceAtLeast(0f)
        }
        historyOffset = (historyOffset + 1) % HISTORY
        historyCount = minOf(historyCount + 1, HISTORY)
        frameGpuMs = if (timings.frameGpuNs < 0) -1f else smoothValue(frameGpuMs, toMs(timings.frameGpuNs), a)
        frameCpuMs = smoothValue(frameCpuMs, toMs(timings.frameCpuNs), a)
    }

    fun reset(passCount: Int) {
        size = passCount
        gpuMs = FloatArray(passCount) { -1f }
        cpuMs = FloatArray(passCount) { -1f }
        gpuHistory = Array(passCount) { FloatArray(HISTORY) }
        cpuHistory = Array(passCount) { FloatArray(HISTORY) }
        historyOffset = 0
        historyCount = 0
        frameGpuMs = -1f
        frameCpuMs = 0f
    }

    /** Smoothed GPU ms of pass [i]; -1 when unknown or disabled ("—"). */
    fun gpuMs(i: Int): Float = gpuMs.getOrElse(i) { -1f }
    fun cpuMs(i: Int): Float = cpuMs.getOrElse(i) { -1f }

    /** Ring buffers for `ImGui.plotLines(values, count, offset)`, oldest at [historyOffset]. */
    fun gpuHistory(i: Int): FloatArray = gpuHistory[i]
    fun cpuHistory(i: Int): FloatArray = cpuHistory[i]

    fun average(history: FloatArray): Float =
        if (historyCount == 0) 0f else samples(history).sum() / historyCount

    fun max(history: FloatArray): Float = samples(history).maxOrNull() ?: 0f

    private fun samples(history: FloatArray): List<Float> =
        if (historyCount < HISTORY) history.take(historyCount) else history.toList()

    /**
     * Share of pass [i] in the frame, 0..1: of the GPU total when GPU timers exist, else of the CPU total. On-demand
     * and disabled passes have no share (-1).
     */
    fun share(info: RenderGraphInfo, i: Int): Float {
        val pass = info.passes.getOrNull(i) ?: return -1f
        if (!pass.enabled || pass.onDemand) return -1f
        val useGpu = frameGpuMs > 0f && gpuMs(i) >= 0f
        val value = if (useGpu) gpuMs(i) else cpuMs(i)
        val total = if (useGpu) frameGpuMs else frameCpuMs
        return if (value < 0f || total <= 0f) -1f else (value / total).coerceIn(0f, 1f)
    }

    private fun smooth(current: Float, ns: Long, a: Float): Float =
        if (ns < 0) -1f else smoothValue(current, toMs(ns), a)

    private fun smoothValue(current: Float, target: Float, a: Float): Float =
        if (current < 0f) target else current + (target - current) * a

    enum class BudgetLevel { LOW, MEDIUM, HIGH }

    companion object {
        const val HISTORY = 120
        const val SMOOTHING_SECONDS = 0.5f / 3f // ~95 % of a change shows within 0.5 s
        const val FRAME_BUDGET_MS = 1000f / 60f

        fun toMs(ns: Long): Float = ns / 1_000_000f

        /** Colour level of a pass time against the frame budget: over 25 % is MEDIUM, over 50 % HIGH. */
        fun budgetLevel(ms: Float, budgetMs: Float = FRAME_BUDGET_MS): BudgetLevel = when {
            ms > budgetMs * 0.5f -> BudgetLevel.HIGH
            ms > budgetMs * 0.25f -> BudgetLevel.MEDIUM
            else -> BudgetLevel.LOW
        }

        /** "1.23 ms", or "—" for an unknown/disabled value. */
        fun format(ms: Float): String = if (ms < 0f) DASH else "%.2f ms".format(ms)

        const val DASH = "—"
    }
}
