package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.GridLines
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.CameraManager
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.engine.render.renderer.GridRenderer
import com.pafoid.skate.engine.render.renderer.GroundDepthPull
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * REN-25: the editor grid is the infinite shader grid (one [GridRenderer] draw per plane), no longer hundreds of thick
 * debug lines; it gets the pixel angle of the presented target for its ground depth pull (REN-19).
 */
class DebugPassGroundLinesTest {
    private val renderer = mockk<DebugRenderer>(relaxed = true)
    private val grid = mockk<GridRenderer>(relaxed = true)
    private val cameras = mockk<CameraManager>(relaxed = true).also {
        every { it.camera } returns CameraComponent()
        every { it.transform } returns Transform(Vector3f(0f, 5f, 20f)).also { t -> t.worldMatrix.translation(0f, 5f, 20f) }
    }
    private val pixelAngle = GroundDepthPull.pixelAngle(Math.toRadians(45.0).toFloat(), 720)

    private fun run(grid: GridLines) {
        val scene = Scene().also { it.addComponent(grid) }
        DebugPass(renderer, cameras, targetHeight = { 720 }, grid = this.grid).execute(scene)
    }

    @Test
    fun DebugPass_DrawsTheGridWithTheShaderNotWithLines() {
        val component = GridLines()
        run(component)

        verify(exactly = 1) { grid.draw(DebugPass.primaryPlane(component), pixelAngle) }
        verify(exactly = 0) { renderer.addGroundLine3D(any(), any(), any(), any()) }
        verify { renderer.pixelAngle = pixelAngle }
    }

    @Test
    fun PrimaryPlane_TakesTheComponentSettingsAndShowsAxes() {
        val component = GridLines(majorStep = 5f, minorStep = 0.5f, gridYOffset = 1f, minExtent = 20f, maxExtent = 300f)
        val plane = DebugPass.primaryPlane(component)
        assertEquals(1f, plane.y)
        assertEquals(0.5f, plane.minorStep)
        assertEquals(5f, plane.majorStep)
        assertEquals(20f, plane.minExtent)
        assertEquals(300f, plane.maxExtent)
        assertTrue(plane.showAxes)
        assertFalse(DebugPass.primaryPlane(GridLines(showOriginAxes = false)).showAxes)
    }

    @Test
    fun SecondaryGrid_IsASecondPlaneWithoutAxes() {
        val component = GridLines(secondaryGridEnabled = true, secondaryGridY = 3f)
        run(component)

        verify(exactly = 1) { grid.draw(DebugPass.primaryPlane(component), any()) }
        verify(exactly = 1) { grid.draw(match { it.y == 3f && !it.showAxes }, any()) }
    }

    @Test
    fun HiddenGrid_DrawsNothing() {
        run(GridLines(showGrid = false))
        verify(exactly = 0) { grid.draw(any(), any()) }
    }
}
