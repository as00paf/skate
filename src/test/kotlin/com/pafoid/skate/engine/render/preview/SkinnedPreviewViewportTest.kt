package com.pafoid.skate.engine.render.preview

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.render.renderer.ModelRenderer
import com.pafoid.skate.engine.render.rhi.FakeRhiDevice
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.ShaderDesc
import io.mockk.every
import io.mockk.mockk
import org.joml.Matrix4f
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** REN-33: the offscreen preview draws the posed model into its own target with its own program (fake device). */
class SkinnedPreviewViewportTest {
    private val device = FakeRhiDevice()
    private val skins = ArrayList<SkeletonComponent?>()
    private val modelRenderer = mockk<ModelRenderer>(relaxed = true).also { r ->
        every { r.render(any(), any(), any(), any(), any(), any()) } answers { skins += arg<SkeletonComponent?>(4) }
    }
    private lateinit var program: Shader
    private lateinit var viewport: SkinnedPreviewViewport

    private val root = Bone(0, "root", Matrix4f().translation(0f, 1f, 0f))
    private val skeleton = Skeleton(root, 1).also { root.inverseBindMatrix.set(root.bindLocalTransform).invert() }
    private val model = `3dModel`("james.glb", skeleton = skeleton)
    private val camera = PreviewCamera(Matrix4f().lookAt(0f, 1f, 3f, 0f, 1f, 0f, 0f, 1f, 0f), Matrix4f().perspective(0.8f, 1.5f, 0.01f, 100f), Vector3f(0f, 1f, 3f))

    @BeforeEach
    fun setUp() {
        RhiContext.device = device
        program = Shader(device.createShader(ShaderDesc("engine:Shaders/shader_3d_default.glsl?SKINNED", "v", "f")))
        viewport = SkinnedPreviewViewport({ program }, modelRenderer, { device })
    }

    @AfterEach
    fun tearDown() { RhiContext.device = null }

    @Test
    fun render_DrawsThePosedModelIntoItsOwnTarget_AndReturnsItsTexture() {
        val raised = arrayOf(Matrix4f().translation(0f, 1.5f, 0f))
        val id = viewport.render(640, 360, model, intArrayOf(-1), raised, camera)
        assertTrue(id > 0)
        assertEquals(1, device.targets.size)
        assertEquals(640, device.targets.single().width)
        assertEquals(id, device.targets.single().colorTexture(0).nativeHandle.toInt())
        // The skinning buffer holds world * inverseBind: 0.5 m up
        val palette = skins.single()!!.matrixPalette[0]
        assertTrue(palette.equals(Matrix4f().translation(0f, 0.5f, 0f), 1e-5f), "$palette")
        // Camera and studio light went to the preview's own program
        assertTrue(device.events.any { it.startsWith("engine:Shaders/shader_3d_default.glsl?SKINNED.uSunDirection=") })
        assertTrue(device.events.any { it == "engine:Shaders/shader_3d_default.glsl?SKINNED.uFogMode=0" })
    }

    @Test
    fun theTarget_IsReused_AndResizedWithThePreviewArea() {
        val pose = arrayOf(Matrix4f(root.bindLocalTransform))
        viewport.render(640, 360, model, intArrayOf(-1), pose, camera)
        viewport.render(640, 360, model, intArrayOf(-1), pose, camera)
        viewport.render(800, 400, model, intArrayOf(-1), pose, camera)
        assertEquals(1, device.targets.size)
        assertTrue(device.events.contains("resize ${device.targets.single().name} 800x400"))
        assertEquals(3, viewport.draws)
        assertSame(skins[0], skins[2], "one skeleton component per skeleton")
    }

    @Test
    fun nothingToDraw_ReturnsZero() {
        assertEquals(0, viewport.render(640, 360, `3dModel`("static.obj"), intArrayOf(-1), arrayOf(Matrix4f()), camera))
        assertEquals(0, viewport.render(0, 360, model, intArrayOf(-1), arrayOf(Matrix4f()), camera))
        assertTrue(device.targets.isEmpty())
        assertTrue(skins.isEmpty())
    }

    @Test
    fun destroy_FreesTheTarget() {
        viewport.render(64, 64, model, intArrayOf(-1), arrayOf(Matrix4f()), camera)
        viewport.destroy()
        assertTrue(device.targets.single().closed)
        assertNotEquals(0, viewport.render(64, 64, model, intArrayOf(-1), arrayOf(Matrix4f()), camera), "usable again")
    }
}
