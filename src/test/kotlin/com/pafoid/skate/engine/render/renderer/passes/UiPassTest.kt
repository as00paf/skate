package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.render.RenderPasses
import com.pafoid.skate.engine.render.renderer.Renderer2D
import com.pafoid.skate.engine.render.ui.UiDrawList
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * REN-21: the runtime UI is drawn by its own pass at the very end of the frame, into the presented target, so
 * post effects (bloom, FXAA) and debug lines (grid, gizmos) never affect it.
 */
class UiPassTest {

    private val events = mutableListOf<String>()
    private val drawList = UiDrawList()
    private val batchShader = mockk<Shader>(relaxed = true)
    private val renderer2D = mockk<Renderer2D>(relaxed = true).also { r ->
        every { r.ui } returns drawList
        every { r.bindShader(any()) } answers { events += "bindShader" }
        every { r.renderUi(any(), any()) } answers { events += "renderUi ${arg<Int>(0)}x${arg<Int>(1)}" }
    }

    private fun pass() = UiPass(
        renderer2D = renderer2D,
        batchShader = batchShader,
        bindTarget = { events += "bindTarget" },
        targetSize = { 640 to 360 },
        endTarget = { events += "endTarget" },
    )

    @Test
    fun execute_DrawsRecordedUiIntoThePresentedTargetInsideUiState() {
        drawList.drawQuad(0f, 0f, 10f, 10f, 1f, 1f, 1f, 1f)

        pass().execute(Scene())

        assertEquals(listOf("bindTarget", "bindShader", "renderUi 640x360", "endTarget"), events)
        verify { renderer2D.bindShader(batchShader) }
    }

    @Test
    fun execute_WithNothingRecorded_OnlyResetsTheList() {
        pass().execute(Scene())

        assertEquals(listOf("renderUi 640x360"), events)
    }

    @Test
    fun executionOrder_UiIsLastAfterPostProcessAndDebug() {
        val passes = RenderPasses(
            picking = mockk(relaxed = true),
            geometry = mockk(relaxed = true),
            debug = mockk(relaxed = true),
            shadow = mockk(relaxed = true),
            postProcess = mockk(relaxed = true),
            ui = mockk(relaxed = true),
            particles = mockk(relaxed = true),
        )

        val order = passes.inExecutionOrder()

        assertEquals(
            listOf(passes.shadow, passes.picking, passes.geometry, passes.particles, passes.postProcess, passes.debug, passes.ui),
            order,
        )
    }
}
