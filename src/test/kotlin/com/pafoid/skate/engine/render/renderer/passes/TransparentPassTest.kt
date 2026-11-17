package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.assets.data.models.AlphaMode
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.assets.data.models.MeshPart
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.render.FrameBuffer
import com.pafoid.skate.engine.render.renderer.LightingUniformsLoader
import com.pafoid.skate.engine.render.renderer.ModelRenderer
import com.pafoid.skate.engine.render.rhi.FakeRhiDevice
import com.pafoid.skate.engine.render.rhi.RhiContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** REN-29 task 1: blended meshes leave the geometry pass and draw in their own pass, in scene order. */
class TransparentPassTest {
    private val device = FakeRhiDevice()
    private val modelRenderer = mockk<ModelRenderer>(relaxed = true)
    private val frame = SceneFrame(LightingUniformsLoader())

    @BeforeEach fun setUp() { RhiContext.device = device }
    @AfterEach fun tearDown() { RhiContext.device = null }

    private fun pass() = TransparentPass(modelRenderer, mockk<FrameBuffer>(relaxed = true), frame, useFbo = false)

    private fun renderComponent(alphaMode: AlphaMode, override: Material? = null): RenderComponent {
        val part = mockk<MeshPart>(relaxed = true) { every { material } returns Material(alphaMode = alphaMode) }
        val model = mockk<`3dModel`>(relaxed = true) { every { mesh } returns listOf(part) }
        return mockk(relaxed = true) {
            every { this@mockk.model } returns model
            every { material } returns override
        }
    }

    private fun item(name: String, program: Shader) =
        SceneFrame.DrawItem(GameObject(name), renderComponent(AlphaMode.BLEND), Transform(), program)

    @Test
    fun Transparent_IsBlended_FollowsTheEffectiveMaterial() {
        assertTrue(GeometryPass.isBlended(renderComponent(AlphaMode.BLEND)))
        assertFalse(GeometryPass.isBlended(renderComponent(AlphaMode.OPAQUE)))
        assertFalse(GeometryPass.isBlended(renderComponent(AlphaMode.MASK)))
        // A material override wins over the mesh's own material
        assertTrue(GeometryPass.isBlended(renderComponent(AlphaMode.OPAQUE, override = Material(alphaMode = AlphaMode.BLEND))))
        assertFalse(GeometryPass.isBlended(renderComponent(AlphaMode.BLEND, override = Material(alphaMode = AlphaMode.OPAQUE))))
    }

    @Test
    fun Transparent_WithNothingQueued_DoesNotTouchTheTarget() {
        frame.begin(Scene(), CameraComponent(), Vector3f(), null)
        pass().execute(Scene())

        assertTrue(device.events.none { it.startsWith("begin") }, device.events.toString())
    }

    @Test
    fun Transparent_DrawsQueuedItemsInSceneOrder_ThenEmptiesTheQueue() {
        frame.begin(Scene(), CameraComponent(), Vector3f(), null)
        val program = mockk<Shader>(relaxed = true)
        val first = item("first", program)
        val second = item("second", program)
        frame.transparent.add(0L, first)
        frame.transparent.add(0L, second)

        pass().execute(Scene())

        verify(ordering = io.mockk.Ordering.ORDERED) {
            modelRenderer.render(first.transform, first.renderComponent, program, any(), any(), any())
            modelRenderer.render(second.transform, second.renderComponent, program, any(), any(), any())
        }
        verify(exactly = 1) { program.start() } // one program switch for both draws
        assertEquals(listOf("begin backbuffer layer=0 clear=false viewport=null", "end"), device.events.filter { it.startsWith("begin") || it == "end" }.map { it.replace(Regex("viewport=.*"), "viewport=null") })
        assertEquals(0, frame.transparent.size)
    }

    @Test
    fun Transparent_BeforeTheFrameBegan_DrawsNothing() {
        frame.transparent.add(0L, item("early", mockk(relaxed = true)))

        pass().execute(Scene())

        verify(exactly = 0) { modelRenderer.render(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun Transparent_Cleanup_DropsItemsSoTheyNeverLeakIntoTheNextFrame() {
        frame.transparent.add(0L, item("left over", mockk(relaxed = true)))

        pass().cleanup()

        assertEquals(0, frame.transparent.size)
    }

    @Test
    fun SceneFrame_Begin_EmptiesLastFramesTransparentDraws() {
        frame.transparent.add(0L, item("stale", mockk(relaxed = true)))

        frame.begin(Scene(), CameraComponent(), Vector3f(1f, 2f, 3f), null)

        assertEquals(0, frame.transparent.size)
        assertEquals(Vector3f(1f, 2f, 3f), frame.cameraPosition)
    }
}
