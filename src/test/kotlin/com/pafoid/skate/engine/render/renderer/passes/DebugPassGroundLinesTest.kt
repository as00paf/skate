package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.GridLines
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.CameraManager
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.engine.render.renderer.GroundDepthPull
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** REN-19: the editor grid is drawn as ground lines (grazing-angle depth pull), sized for the presented target. */
class DebugPassGroundLinesTest {

    @Test
    fun DebugPass_GridGoesToGroundLinesWithThePixelAngleOfTheTarget() {
        val renderer = mockk<DebugRenderer>(relaxed = true)
        val cameras = mockk<CameraManager>(relaxed = true)
        every { cameras.camera } returns CameraComponent()
        every { cameras.transform } returns Transform(Vector3f(0f, 5f, 20f)).also { it.worldMatrix.translation(0f, 5f, 20f) }
        val scene = Scene().also { it.addComponent(GridLines()) }

        DebugPass(renderer, cameras, targetHeight = { 720 }).execute(scene)

        verify(atLeast = 10) { renderer.addGroundLine3D(any(), any(), any(), any()) }
        verify(exactly = 0) { renderer.addLine3D(any(), any(), any(), any()) }
        verify { renderer.pixelAngle = GroundDepthPull.pixelAngle(Math.toRadians(45.0).toFloat(), 720) }
    }
}
