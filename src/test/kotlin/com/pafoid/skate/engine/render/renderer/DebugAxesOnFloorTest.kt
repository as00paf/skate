package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.GridLines
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.CameraManager
import com.pafoid.skate.engine.render.renderer.passes.DebugPass
import io.mockk.every
import io.mockk.mockk
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.pafoid.skate.engine.render.rhi.FakeRhiDevice
import com.pafoid.skate.engine.render.rhi.RhiContext

/**
 * REN-19 follow-up (REN-25: the X/Z axes moved into the grid shader): the origin axes lie on the floor and the debug shader never moves vertices. The blue Z axis
 * (and red X) drew far above the floor and over the skater because the depth pull shifted vertex positions.
 */
class DebugAxesOnFloorTest {

    private val cameras = mockk<CameraManager>(relaxed = true).also {
        every { it.camera } returns CameraComponent()
        every { it.transform } returns Transform(Vector3f(0f, 5f, 20f)).also { t -> t.worldMatrix.translation(0f, 5f, 20f) }
    }
    private val renderer = DebugRenderer(mockk<Shader>(relaxed = true), cameras)

    @BeforeEach
    fun setUp() { RhiContext.device = FakeRhiDevice() }

    @AfterEach
    fun tearDown() { RhiContext.device = null }

    @Test
    fun OriginAxes_OnlyTheYAxisIsARibbon_XAndZAreDrawnByTheGridShader() {
        val scene = Scene().also { it.addComponent(GridLines()) }

        DebugPass(renderer, cameras, targetHeight = { 720 }).execute(scene)

        // REN-25: the red X and blue Z axes are part of the infinite grid shader, so no ground ribbons are queued
        assertTrue(renderer.groundTriangleVertices().isEmpty())
    }

    @Test
    fun GroundRibbon_IsFlatEvenWhenViewedAtAGrazingAngle() {
        every { cameras.transform } returns Transform(Vector3f(0f, 0.2f, 10f)).also { t -> t.worldMatrix.translation(0f, 0.2f, 10f) }
        renderer.addThickLineQuad3D(Vector3f(0f, 0f, -50f), Vector3f(0f, 0f, 50f), Vector3f(0.2f, 0.2f, 1f), 0.04f, ground = true)
        assertTrue(renderer.groundTriangleVertices().all { it.y == 0f })
    }

    @Test
    fun DebugShader_PullsDepthPerFragmentAndNeverMovesVertices() {
        val src = javaClass.getResourceAsStream("/shaders/debugLine2D.glsl")!!.readBytes().toString(Charsets.UTF_8)
        val vertex = src.substringAfter("#type vertex").substringBefore("#type fragment")
        val fragment = src.substringAfter("#type fragment")
        assertTrue(vertex.contains("gl_Position = uProjection * viewPos;"))
        assertFalse(Regex("""viewPos\.z\s*(\+=|-=|\*=|=)""").containsMatchIn(vertex), "vertex shader must not change the view z")
        assertTrue(fragment.contains("gl_FragDepth"), "the pull is written as the fragment depth")
    }
}
