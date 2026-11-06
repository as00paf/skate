package com.pafoid.skate.editor.ui.windows.rendergraph

import com.pafoid.skate.engine.render.graph.PassTimings
import com.pafoid.skate.engine.render.graph.RenderGraph
import com.pafoid.skate.engine.render.graph.RenderGraphInfo
import com.pafoid.skate.editor.ui.windows.rendergraph.PassTimingStats.BudgetLevel
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.render.renderer.passes.BaseRenderPass
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** EDT-54 §5: readable timings for the Render Graph window. */
class PassTimingStatsTest {
    private class Pass(override val name: String, private val cpuNs: Long, override val onDemand: Boolean = false) : BaseRenderPass() {
        override fun execute(scene: Scene) {}
        fun setTimes(gpu: Long) { gpuTimeNs = gpu; executionTimeNs = cpuNs }
    }

    /** Graph with fake GPU times: A 4 ms, B 12 ms, on-demand picking 3 ms, disabled C. */
    private fun frame(): Triple<RenderGraphInfo, PassTimings, List<Pass>> {
        val passes = listOf(Pass("A", 1_000_000), Pass("B", 2_000_000), Pass("Picking", 500_000, onDemand = true), Pass("C", 1))
        val graph = RenderGraph { null }.apply { passes.forEach { addPass(it) } }
        passes[0].setTimes(4_000_000); passes[1].setTimes(12_000_000); passes[2].setTimes(3_000_000)
        passes[3].isEnabled = false
        val timings = PassTimings()
        graph.readTimings(timings)
        return Triple(graph.info(), timings, passes)
    }

    @Test
    fun Shares_ComeFromTheGpuTotal_WithoutOnDemandOrDisabledPasses() {
        val (info, timings) = frame()
        val stats = PassTimingStats().apply { update(info, timings, 1f / 60f) }
        assertEquals(16f, stats.frameGpuMs, 0.001f)
        assertEquals(0.25f, stats.share(info, 0), 0.001f)
        assertEquals(0.75f, stats.share(info, 1), 0.001f)
        assertEquals(-1f, stats.share(info, 2), "on demand")
        assertEquals(-1f, stats.share(info, 3), "disabled")
        assertEquals(-1f, stats.gpuMs(3))
    }

    @Test
    fun Values_AreSmoothed_AndReachTheNewValueWithinHalfASecond() {
        val (info, timings) = frame()
        val stats = PassTimingStats()
        stats.update(info, timings, 1f / 60f)
        assertEquals(4f, stats.gpuMs(0), 0.001f, "first sample is taken as is")
        timings.gpuNs[0] = 8_000_000
        stats.update(info, timings, 1f / 60f)
        assertTrue(stats.gpuMs(0) in 4.01f..5f, "one frame later: barely moved (${stats.gpuMs(0)})")
        repeat(40) { stats.update(info, timings, 1f / 60f) }
        assertTrue(stats.gpuMs(0) > 7.8f, "after ~0.7 s: ${stats.gpuMs(0)}")
    }

    @Test
    fun History_IsARingOfTheLast120Frames() {
        val (info, timings) = frame()
        val stats = PassTimingStats()
        repeat(150) { k ->
            timings.gpuNs[1] = (k + 1) * 1_000_000L
            stats.update(info, timings, 1f / 60f)
        }
        assertEquals(PassTimingStats.HISTORY, stats.historyCount)
        val history = stats.gpuHistory(1)
        assertEquals(31f, history[stats.historyOffset], "oldest kept sample")
        assertEquals(150f, stats.max(history))
        assertEquals((31 + 150) / 2f, stats.average(history), 0.001f)
    }

    @Test
    fun BudgetLevels_AreRelativeToTheFrameBudget() {
        assertEquals(BudgetLevel.LOW, PassTimingStats.budgetLevel(3f))
        assertEquals(BudgetLevel.MEDIUM, PassTimingStats.budgetLevel(5f))
        assertEquals(BudgetLevel.HIGH, PassTimingStats.budgetLevel(9f))
        assertEquals(BudgetLevel.LOW, PassTimingStats.budgetLevel(9f, budgetMs = 40f))
    }

    @Test
    fun UnknownValues_ShowAsADash() {
        assertEquals(PassTimingStats.DASH, PassTimingStats.format(-1f))
        assertTrue(PassTimingStats.format(1.234f).startsWith("1"))
    }

    @Test
    fun PassCountChange_ResetsTheStats() {
        val (info, timings) = frame()
        val stats = PassTimingStats().apply { update(info, timings, 0.1f) }
        val smaller = RenderGraphInfo(info.version + 1, info.passes.take(2))
        stats.update(smaller, timings, 0.1f)
        assertEquals(2, stats.size)
        assertEquals(1, stats.historyCount)
    }

}
