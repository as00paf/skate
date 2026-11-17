package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.components.WaterComponent
import com.pafoid.skate.engine.ecs.systems.CameraManager
import com.pafoid.skate.engine.render.FrameBuffer
import com.pafoid.skate.engine.render.renderer.LightingUniformsLoader
import com.pafoid.skate.engine.render.rhi.FakeRhiDevice
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.water.WaterRenderer
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.joml.Matrix4f
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** REN-29 §6.2: the water pass copies the scene, then draws the surfaces; without water it costs nothing. */
class WaterPassTest {
    private val device = FakeRhiDevice()
    private val camera = CameraComponent().apply {
        viewportWidth = 640; viewportHeight = 360
        projection.setPerspective(Math.toRadians(60.0).toFloat(), 16f / 9f, 0.1f, 500f)
        view.setLookAt(0f, 5f, 15f, 0f, 0f, 0f, 0f, 1f, 0f)
    }
    private val cameraManager = mockk<CameraManager>(relaxed = true) {
        every { this@mockk.camera } returns this@WaterPassTest.camera
        every { transform } returns Transform().apply { worldMatrix.set(Matrix4f().translation(0f, 5f, 15f)) }
    }
    private val frame = SceneFrame(LightingUniformsLoader())
    private val shader = mockk<Shader>(relaxed = true)
    private val frameBuffer = FrameBuffer(640, 360, msaaSamples = 0) { device }

    @BeforeEach fun setUp() { RhiContext.device = device; frameBuffer.initialize() }
    @AfterEach fun tearDown() { RhiContext.device = null }

    private fun pass(ssr: Int = 16) = WaterPass({ shader }, { shader }, WaterRenderer(), frameBuffer, frame, cameraManager, ssrSteps = { ssr })

    private fun sceneWithWater(): Scene {
        val scene = Scene("S")
        scene.addChild(GameObject("Pool").addComponent(Transform()).addComponent(WaterComponent(sizeX = 10f, sizeZ = 6f)))
        return scene
    }

    @Test
    fun Water_NoWater_NoCopyNoPassNoAllocation() {
        val pass = pass()
        frame.begin(Scene(), camera, Vector3f(), null)
        device.events.clear()
        pass.execute(Scene("Dry"))
        assertTrue(device.events.isEmpty(), device.events.toString())
        assertNull(pass.sceneCopy)
    }

    @Test
    fun Water_CopiesColourAndDepth_ThenDrawsVisibleChunksIntoTheScene() {
        val pass = pass()
        val scene = sceneWithWater()
        frame.begin(scene, camera, Vector3f(0f, 5f, 15f), null)
        device.events.clear()

        pass.execute(scene)

        val copy = pass.sceneCopy!!
        assertEquals(640, copy.width)
        val blit = device.events.indexOfFirst { it.startsWith("blit") }
        val begin = device.events.indexOfFirst { it.startsWith("begin") }
        assertTrue(blit in 0 until begin, device.events.toString())
        assertTrue(device.events[blit].endsWith("NEAREST COLOR_DEPTH"), device.events[blit])
        assertTrue(device.events.count { it.startsWith("drawIndexed") } >= 1)
        verify { shader.uploadInt(Uniforms.WATER_SSR_STEPS, 16) }
        verify { shader.uploadInt(Uniforms.WATER_SCENE_DEPTH, Uniforms.WATER_SCENE_DEPTH_UNIT) }
        assertEquals("end", device.events.last())
    }

    @Test
    fun Water_SceneCopyIsCreatedOnce_AndFollowsTheTargetSize() {
        val pass = pass()
        val scene = sceneWithWater()
        frame.begin(scene, camera, Vector3f(), null)
        pass.execute(scene)
        val first = pass.sceneCopy
        pass.execute(scene)
        assertSame(first, pass.sceneCopy)
        frameBuffer.resize(320, 200)
        pass.execute(scene)
        assertSame(first, pass.sceneCopy)
        assertEquals(320, pass.sceneCopy!!.width)
    }

    @Test
    fun Water_SsrStepsAreClampedForTheShaderLoop() {
        val scene = sceneWithWater()
        frame.begin(scene, camera, Vector3f(), null)
        pass(ssr = 1000).execute(scene)
        verify { shader.uploadInt(Uniforms.WATER_SSR_STEPS, WaterPass.MAX_SSR_STEPS) }
    }

    @Test
    fun Water_BehindTheCamera_IsCulled() {
        val renderer = WaterRenderer()
        val pass = WaterPass({ shader }, { shader }, renderer, frameBuffer, frame, cameraManager)
        val scene = Scene("S")
        scene.addChild(GameObject("Behind").addComponent(Transform().apply { worldMatrix.translation(0f, 0f, 200f) })
            .addComponent(WaterComponent(sizeX = 4f, sizeZ = 4f)))
        frame.begin(scene, camera, Vector3f(), null)
        device.events.clear()
        pass.execute(scene)
        assertEquals(0, renderer.chunksDrawn)
        assertEquals(1, renderer.chunksCulled)
    }

    @Test
    fun Water_Picking_DrawsEachPickableSurfaceWithItsEntityId() {
        val renderer = WaterRenderer()
        val pass = WaterPass({ shader }, { shader }, renderer, frameBuffer, frame, cameraManager)
        val scene = sceneWithWater()
        pass.drawPicking(scene)
        verify(exactly = 1) { shader.uploadFloat(Uniforms.ENTITY_ID, any()) }
        assertTrue(renderer.chunksDrawn >= 1)
    }

    @Test
    fun SurfaceFrame_UsesPositionAndYawOnly() {
        val t = Transform().apply { worldMatrix.translation(1f, 2f, 3f).rotateY(0.5f).rotateX(0.3f) }
        val m = WaterRenderer.modelMatrix(t, Matrix4f())
        assertEquals(0.5f, WaterRenderer.yawOf(m), 1e-5f)
        val up = m.transformDirection(Vector3f(0f, 1f, 0f))
        assertEquals(1f, up.y, 1e-5f)
        assertEquals(Vector3f(1f, 2f, 3f), m.getTranslation(Vector3f()))
    }
}
