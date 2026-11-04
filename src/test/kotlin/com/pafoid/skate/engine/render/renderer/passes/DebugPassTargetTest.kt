package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.systems.CameraManager
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Regression test for PHY-10: post-processing leaves framebuffer 0 bound, so DebugPass must bind the
 * presented target itself before drawing, or debug lines never reach the editor viewport.
 */
class DebugPassTargetTest {

    @Test
    fun execute_BindsPresentedTargetBeforeDrawing() {
        val events = mutableListOf<String>()
        val debugRenderer = mockk<DebugRenderer>(relaxed = true)
        every { debugRenderer.draw() } answers { events += "draw" }
        val pass = DebugPass(debugRenderer, mockk<CameraManager>(relaxed = true)) { events += "bindTarget" }

        pass.execute(Scene())

        assertEquals(listOf("bindTarget", "draw"), events)
    }
}
