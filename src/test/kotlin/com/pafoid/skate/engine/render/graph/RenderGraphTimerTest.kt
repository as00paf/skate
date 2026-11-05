package com.pafoid.skate.engine.render.graph

import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.render.renderer.passes.BaseRenderPass
import com.pafoid.skate.engine.render.rhi.FakeRhiDevice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** REN-22 / REN-12 step 7: the graph times each enabled pass with an RhiTimer. */
class RenderGraphTimerTest {
    private class Pass(override val name: String, private val log: MutableList<String>) : BaseRenderPass() {
        override fun execute(scene: Scene) { log += "execute $name" }
    }

    @Test
    fun Execute_TimesEachEnabledPass_WithItsOwnTimer() {
        val device = FakeRhiDevice().also { it.timerResultNs = 750_000L }
        val graph = RenderGraph { device }
        val a = Pass("A", device.events)
        val b = Pass("B", device.events).also { it.isEnabled = false }
        graph.addPass(a); graph.addPass(b)

        graph.execute(Scene())
        graph.execute(Scene())

        assertEquals(750_000L, a.gpuTimeNs)
        assertEquals(-1L, b.gpuTimeNs)
        assertEquals(1, device.timers.size, "one timer per pass, reused every frame")
        assertEquals(listOf("beginTimer", "execute A", "endTimer"), device.events.take(3))

        graph.clear()
        assertTrue(device.timers.single().closed)
    }

    @Test
    fun Execute_WithoutADevice_RunsPassesUntimed() {
        val log = ArrayList<String>()
        val pass = Pass("A", log)
        RenderGraph { null }.apply { addPass(pass) }.execute(Scene())
        assertEquals(listOf("execute A"), log)
        assertEquals(-1L, pass.gpuTimeNs)
    }
}
