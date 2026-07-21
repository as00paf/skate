package com.pafoid.skate.engine.render.graph

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.render.renderer.passes.RenderPass
import io.mockk.mockk
import io.mockk.verifyOrder
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Render Graph Logic Tests")
class RenderGraphTest {

    @Test
    @DisplayName("should execute passes in correct order")
    fun `should execute passes in correct order`() {
        val scene = mockk<Scene>(relaxed = true)
        val go = mockk<GameObject>(relaxed = true)
        
        val pass1 = mockk<RenderPass>(relaxed = true) {
            io.mockk.every { name } returns "Pass1"
            io.mockk.every { isEnabled } returns true
        }
        val pass2 = mockk<RenderPass>(relaxed = true) {
            io.mockk.every { name } returns "Pass2"
            io.mockk.every { isEnabled } returns true
        }
        
        val graph = RenderGraphBuilder()
            .addPass(pass1)
            .addPass(pass2)
            .build()

        graph.execute(scene)
        
        verifyOrder {
            pass1.executeWithTiming(any())
            pass2.executeWithTiming(any())
        }
    }
}
