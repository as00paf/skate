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

    @Test
    @DisplayName("should execute post process pass after geometry pass and before debug pass")
    fun shouldExecutePostProcessPassAfterGeometryAndBeforeDebug() {
        val scene = mockk<Scene>(relaxed = true)

        val geometryPass = mockk<RenderPass>(relaxed = true) {
            io.mockk.every { name } returns "GeometryPass"
            io.mockk.every { isEnabled } returns true
        }
        val postProcessPass = mockk<RenderPass>(relaxed = true) {
            io.mockk.every { name } returns "PostProcessPass"
            io.mockk.every { isEnabled } returns true
        }
        val debugPass = mockk<RenderPass>(relaxed = true) {
            io.mockk.every { name } returns "DebugPass"
            io.mockk.every { isEnabled } returns true
        }

        val graph = RenderGraphBuilder()
            .addPass(geometryPass)
            .addPass(postProcessPass)
            .addPass(debugPass)
            .build()

        graph.execute(scene)

        verifyOrder {
            geometryPass.executeWithTiming(any())
            postProcessPass.executeWithTiming(any())
            debugPass.executeWithTiming(any())
        }
    }

    @Test
    @DisplayName("should skip disabled passes during execution")
    fun shouldSkipDisabledPassesDuringExecution() {
        val scene = mockk<Scene>(relaxed = true)

        val enabledPass = mockk<RenderPass>(relaxed = true) {
            io.mockk.every { name } returns "EnabledPass"
            io.mockk.every { isEnabled } returns true
        }
        val disabledPass = mockk<RenderPass>(relaxed = true) {
            io.mockk.every { name } returns "DisabledPass"
            io.mockk.every { isEnabled } returns false
        }

        val graph = RenderGraphBuilder()
            .addPass(enabledPass)
            .addPass(disabledPass)
            .build()

        graph.execute(scene)

        io.mockk.verify(exactly = 1) { enabledPass.executeWithTiming(any()) }
        io.mockk.verify(exactly = 0) { disabledPass.executeWithTiming(any()) }
    }
}
