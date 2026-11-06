package com.pafoid.skate.engine.render.rhi.vulkan

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.assets.loaders.ShaderLoader
import com.pafoid.skate.engine.render.data.VertexLayouts
import com.pafoid.skate.engine.render.rhi.BufferDesc
import com.pafoid.skate.engine.render.rhi.BufferUpdate
import com.pafoid.skate.engine.render.rhi.BufferUsage
import com.pafoid.skate.engine.render.rhi.CompareFunc
import com.pafoid.skate.engine.render.rhi.Filter
import com.pafoid.skate.engine.render.rhi.PipelineState
import com.pafoid.skate.engine.render.rhi.PixelFormat
import com.pafoid.skate.engine.render.rhi.RenderTargetDesc
import com.pafoid.skate.engine.render.rhi.SamplerDesc
import com.pafoid.skate.engine.render.rhi.TextureDesc
import com.pafoid.skate.engine.render.rhi.TextureType
import com.pafoid.skate.engine.render.rhi.Wrap
import com.pafoid.skate.testing.GpuTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * VK-02 on a real device: every resource kind the passes create, uploaded and read back. Opt-in (`SKATE_RHI=vulkan`)
 * and GPU-only (@GpuTest).
 */
@GpuTest
class VulkanResourcesGpuTest {
    private lateinit var device: VulkanDevice
    private val resources get() = device.resources

    @BeforeEach
    fun setUp() {
        assumeTrue(RhiBackend.selected() == RhiBackend.VULKAN, "Vulkan is opt-in: set SKATE_RHI=vulkan")
        val created = runCatching { VulkanDevice.create() }
        assumeTrue(created.isSuccess, "no Vulkan driver")
        device = created.getOrThrow()
    }

    @AfterEach
    fun tearDown() {
        if (::device.isInitialized) device.close()
    }

    private fun bytes(vararg values: Int) = ByteBuffer.allocateDirect(values.size).also { b -> values.forEach { b.put(it.toByte()) } }.flip()

    private fun rgba(w: Int, h: Int, texel: (Int, Int) -> IntArray): ByteBuffer {
        val b = ByteBuffer.allocateDirect(w * h * 4)
        for (y in 0 until h) for (x in 0 until w) texel(x, y).forEach { b.put(it.toByte()) }
        return b.flip()
    }

    private fun tex(t: Any) = t as VkRhiTexture

    @Test
    fun Texture2D_UploadsAndGeneratesItsMipChain() {
        val size = 64
        val desc = TextureDesc(TextureType.TEXTURE_2D, PixelFormat.RGBA8, size, size, mipLevels = TextureDesc.fullMipChain(size, size),
            sampler = SamplerDesc(Filter.LINEAR_MIPMAP_LINEAR, Filter.LINEAR))
        val t = tex(device.createTexture(desc))
        val data = rgba(size, size) { x, _ -> if (x < size / 2) intArrayOf(255, 0, 0, 255) else intArrayOf(0, 0, 255, 255) }
        t.upload(0, 0, data.duplicate())
        t.generateMips()
        val level0 = t.read(0, 0)
        assertArrayEquals(ByteArray(size * size * 4).also { data.duplicate().get(it) }, level0)
        val last = t.read(desc.mipLevels - 1, 0).map { it.toInt() and 0xFF }
        assertEquals(1 * 4, last.size, "1x1 top level")
        assertTrue(abs(last[0] - 128) <= 2 && abs(last[2] - 128) <= 2, "half red, half blue averages to purple: $last")
        t.close()
    }

    @Test
    fun ArrayCubeAndRgb8_KeepEveryLayer() {
        val array = tex(device.createTexture(TextureDesc(TextureType.TEXTURE_2D_ARRAY, PixelFormat.RGBA8, 2, 2, layers = 3)))
        for (layer in 0 until 3) array.upload(0, layer, rgba(2, 2) { _, _ -> intArrayOf(layer * 50, 1, 2, 255) })
        for (layer in 0 until 3) assertEquals(layer * 50, array.read(0, layer)[0].toInt() and 0xFF, "array layer $layer")

        val cube = tex(device.createTexture(TextureDesc(TextureType.CUBE, PixelFormat.RGB8, 1, 1, layers = 6)))
        for (face in 0 until 6) cube.upload(0, face, bytes(face * 40, 7, 9))
        for (face in 0 until 6) {
            assertArrayEquals(byteArrayOf((face * 40).toByte(), 7, 9, -1), cube.read(0, face), "cube face $face, widened to RGBA")
        }
        array.close(); cube.close()
    }

    @Test
    fun DepthArray_WithACompareSampler_UploadsFloats() {
        val depth = tex(device.createTexture(TextureDesc(TextureType.TEXTURE_2D_ARRAY, PixelFormat.DEPTH32F, 2, 2, layers = 2,
            sampler = SamplerDesc(Filter.LINEAR, Filter.LINEAR, Wrap.CLAMP_TO_EDGE, compare = CompareFunc.LESS_EQUAL))))
        for (layer in 0 until 2) {
            val b = ByteBuffer.allocateDirect(16).order(ByteOrder.LITTLE_ENDIAN)
            repeat(4) { b.putFloat(0.25f * (layer + 1)) }
            depth.upload(0, layer, b.flip())
        }
        for (layer in 0 until 2) {
            val back = ByteBuffer.wrap(depth.read(0, layer)).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(0.25f * (layer + 1), back.getFloat(0), "depth layer $layer")
        }
        depth.close()
    }

    @Test
    fun Samplers_AreSharedByState() {
        val before = resources.samplers.size
        val a = tex(device.createTexture(TextureDesc(TextureType.TEXTURE_2D, PixelFormat.RGBA8, 1, 1)))
        val b = tex(device.createTexture(TextureDesc(TextureType.TEXTURE_2D, PixelFormat.RGBA8, 1, 1)))
        assertEquals(a.vkSampler, b.vkSampler)
        b.setSampler(SamplerDesc(wrap = Wrap.CLAMP_TO_EDGE))
        assertNotEquals(a.vkSampler, b.vkSampler)
        assertTrue(resources.samplers.size <= before + 2)
        a.close(); b.close()
    }

    @Test
    fun Buffers_EveryUsageAndUpdate_AndDynamicVersions() {
        val vertices = device.createBuffer(BufferUsage.VERTEX, BufferUpdate.STATIC, FloatArray(30) { it.toFloat() })
        val indices = device.createBuffer(BufferUsage.INDEX, BufferUpdate.STATIC, intArrayOf(0, 1, 2))
        val dynamic = device.createBuffer(BufferDesc(BufferUsage.VERTEX, 1024, BufferUpdate.DYNAMIC)) as VkRhiBuffer
        val stream = device.createBuffer(BufferUsage.INSTANCE, BufferUpdate.STREAM, FloatArray(0))
        val uniform = device.createBuffer(BufferDesc(BufferUsage.UNIFORM, 256, BufferUpdate.DYNAMIC)) as VkRhiBuffer
        vertices.update(0, FloatArray(3) { 9f })
        stream.resize(4096)
        stream.update(0, FloatArray(12) { 1f })
        indices.replace(intArrayOf(2, 1, 0, 0, 2, 3))
        uniform.update(16, floatArrayOf(4f))
        assertEquals(4f, uniform.contents.getFloat(16))

        resources.frames.beginFrame(0)
        dynamic.update(0, FloatArray(8) { 1f })
        fun bound(): Pair<Long, Long> { dynamic.resolve(resources.frames); return dynamic.boundBuffer to dynamic.boundOffset }
        val first = bound()
        assertEquals(first, bound(), "same frame, no write: same version")
        dynamic.update(0, FloatArray(8) { 2f })
        assertNotEquals(first, bound(), "written between draws: new version")
        val pushes = dynamic.versionPushes
        resources.frames.beginFrame(1)
        bound()
        assertEquals(pushes + 1, dynamic.versionPushes, "a new frame pushes again (its slot's ring may be reused)")

        val layout = device.createVertexLayout(VertexLayouts.mesh(listOf(0 to com.pafoid.skate.engine.render.rhi.AttributeType.VEC3)))
        device.createVertexInput(layout, listOf(vertices), indices).close()
        listOf(vertices, indices, dynamic, stream, uniform).forEach { it.close() }
    }

    @Test
    fun RenderTargets_ColorDepthFloatAndLayeredDepth() {
        val scene = device.createRenderTarget(RenderTargetDesc(320, 200)) as VkRhiRenderTarget
        assertEquals(TextureType.TEXTURE_2D, scene.depthTexture()!!.desc.type)
        scene.resize(64, 32)
        assertEquals(64, scene.colorTexture().desc.width)

        val picking = device.createRenderTarget(RenderTargetDesc(8, 8, colors = listOf(PixelFormat.RGB32F))) as VkRhiRenderTarget
        assertEquals(TextureType.TEXTURE_2D, picking.colors.single().desc.type)

        val shadow = device.createRenderTarget(RenderTargetDesc(256, 256, colors = emptyList(), layers = 4, layered = true,
            depthSampler = SamplerDesc(Filter.LINEAR, Filter.LINEAR, Wrap.CLAMP_TO_EDGE, compare = CompareFunc.LESS_EQUAL))) as VkRhiRenderTarget
        val layer3 = shadow.depth!!.layerView(3)
        assertNotEquals(shadow.depth!!.view, layer3, "a layered target renders through per-layer views")
        listOf(scene, picking, shadow).forEach { it.close() }
    }

    @Test
    fun ShaderPipelineAndTimer_ForTheDefaultProgram() {
        val shader: Shader = ShaderLoader(device = { device }).loadShader(Assets.Shaders.SHADER_3D_DEFAULT)
        shader.uploadFloat("uTextureScale", 2f)
        shader.uploadInt("u_BaseColorTexture", 0)
        val rhi = shader.rhi as VkRhiShader
        assertTrue(rhi.compiled.bindings.any { it.descriptor.name == "LightBlock" })
        assertTrue(rhi.bindUniformBlock("LightBlock", 1))
        val layout = device.createVertexLayout(VertexLayouts.mesh(listOf(0 to com.pafoid.skate.engine.render.rhi.AttributeType.VEC3)))
        device.createPipeline(PipelineState().desc(rhi, layout)).close()
        assertEquals(-1L, device.createTimer().latestNs)
        shader.destroy()
    }

    @Test
    fun Allocations_AreFreedOnClose() {
        val live = resources.allocator.liveAllocations
        val t = device.createTexture(TextureDesc(TextureType.TEXTURE_2D, PixelFormat.RGBA8, 16, 16))
        val b = device.createBuffer(BufferUsage.VERTEX, BufferUpdate.STATIC, FloatArray(16))
        val rt = device.createRenderTarget(RenderTargetDesc(16, 16))
        assertTrue(resources.allocator.liveAllocations > live)
        t.close(); b.close(); rt.close()
        assertEquals(live, resources.allocator.liveAllocations)
    }
}
