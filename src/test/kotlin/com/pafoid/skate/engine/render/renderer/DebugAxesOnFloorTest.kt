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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * REN-19 follow-up: the origin axes lie on the floor and the debug shader never moves vertices. The blue Z axis
 * (and red X) drew far above the floor and over the skater because the depth pull shifted vertex positions.
 */
class DebugAxesOnFloorTest {

    private val cameras = mockk<CameraManager>(relaxed = true).also {
        every { it.camera } returns CameraComponent()
        every { it.transform } returns Transform(Vector3f(0f, 5f, 20f)).also { t -> t.worldMatrix.translation(0f, 5f, 20f) }
    }
    private val renderer = DebugRenderer(mockk<Shader>(relaxed = true), cameras)

    @Test
    fun OriginAxes_XAndZRibbonsLieFlatOnTheFloorPlane() {
        val scene = Scene().also { it.addComponent(GridLines()) }

        DebugPass(renderer, cameras, drawState = {}, targetHeight = { 720 }).execute(scene)

        val vertices = renderer.groundTriangleVertices()
        assertEquals(4 * 3, vertices.size, "two ribbons (red X, blue Z), two triangles each")
        assertTrue(vertices.all { it.y == 0f }, "every ground ribbon vertex at y = 0: ${vertices.map { it.y }.distinct()}")
        val xs = vertices.map { it.x }; val zs = vertices.map { it.z }
        assertTrue(xs.max() > 1f && zs.max() > 1f, "one ribbon runs along X, the other along Z")
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
