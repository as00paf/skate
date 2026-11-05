package com.pafoid.skate.engine.assets.loaders

import com.pafoid.skate.engine.assets.data.CubeMap
import com.pafoid.skate.engine.render.rhi.FakeRhiDevice
import com.pafoid.skate.engine.render.rhi.Filter
import com.pafoid.skate.engine.render.rhi.PixelFormat
import com.pafoid.skate.engine.render.rhi.TextureDesc
import com.pafoid.skate.engine.render.rhi.TextureType
import com.pafoid.skate.engine.render.rhi.Wrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.nio.file.Paths

/** REN-12 step 3: image textures and cube maps are created through the RHI device (stb decodes, no GL needed). */
class TextureLoaderRhiTest {
    private val device = FakeRhiDevice()
    private val loader = TextureLoader { device }
    private val png = Paths.get("src/main/resources/textures/lightbulb.png").toAbsolutePath().toString()

    @Test
    fun Upload_IsAMipmappedRepeatingRgba8Texture() {
        val texture = loader.upload(loader.decodeFromFile(png))
        val rhi = device.textures.single()
        assertSame(rhi, texture.rhi)
        assertEquals(rhi.nativeHandle.toInt(), texture.texId)
        assertEquals(TextureType.TEXTURE_2D, rhi.desc.type)
        assertEquals(PixelFormat.RGBA8, rhi.desc.format)
        assertEquals(texture.width to texture.height, rhi.desc.width to rhi.desc.height)
        assertEquals(TextureDesc.fullMipChain(texture.width, texture.height), rhi.desc.mipLevels)
        assertEquals(listOf(0 to 0), rhi.uploads)
        assertTrue(rhi.mipsGenerated)
        assertEquals(Filter.LINEAR_MIPMAP_LINEAR, rhi.sampler.minFilter)
        assertEquals(Wrap.REPEAT, rhi.sampler.wrap)
        assertEquals(png, texture.filePath, "the resolved path is kept as given (AST-09 resolves it before)")
    }

    @Test
    fun Replace_ClosesTheOldTextureAndKeepsTheObject() {
        val texture = loader.upload(loader.decodeFromFile(png))
        val old = device.textures.single()
        loader.replace(texture, loader.decodeFromFile(png))
        assertTrue(old.closed)
        assertSame(device.textures.last(), texture.rhi)
        loader.delete(texture)
        assertTrue(device.textures.last().closed)
        assertNull(texture.rhi)
        assertEquals(-1, texture.texId)
    }

    @Test
    fun UndecodableFile_Throws_SoGetTextureFallsBackToTheDefault() {
        assertThrows<IllegalStateException> { loader.loadFromFile(File("does/not/exist.png").absolutePath) }
        assertTrue(device.textures.isEmpty())
    }

    @Test
    fun CubeMap_SixFacesClampedLinear() {
        val cube = CubeMap { device }.init(Array(6) { png })
        val rhi = device.textures.single()
        assertEquals(TextureType.CUBE, rhi.desc.type)
        assertEquals(6, rhi.desc.layers)
        assertEquals((0 until 6).map { 0 to it }, rhi.uploads)
        assertEquals(Wrap.CLAMP_TO_EDGE, rhi.sampler.wrap)
        assertEquals(Filter.LINEAR, rhi.sampler.minFilter)
        assertEquals(rhi.nativeHandle.toInt(), cube.getId())
        cube.close()
        assertTrue(rhi.closed)
        assertEquals(-1, cube.getId())
    }
}
