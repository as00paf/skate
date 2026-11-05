package com.pafoid.skate.engine.render.rhi.opengl

import com.pafoid.skate.engine.render.rhi.CompareFunc
import com.pafoid.skate.engine.render.rhi.Filter
import com.pafoid.skate.engine.render.rhi.PixelFormat
import com.pafoid.skate.engine.render.rhi.TextureType
import com.pafoid.skate.engine.render.rhi.Wrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL30

class GlFormatsTest {
    @Test
    fun TextureFormats_AreSizedWithMatchingUploadFormats() {
        assertEquals(Triple(GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE), GlFormats.texture(PixelFormat.RGBA8))
        assertEquals(Triple(GL11.GL_RGB8, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE), GlFormats.texture(PixelFormat.RGB8))
        assertEquals(Triple(GL30.GL_RGB32F, GL11.GL_RGB, GL11.GL_FLOAT), GlFormats.texture(PixelFormat.RGB32F))
        assertEquals(Triple(GL30.GL_DEPTH_COMPONENT32F, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT), GlFormats.texture(PixelFormat.DEPTH32F))
    }

    @Test
    fun TargetsFiltersWrapsAndCompareFunctions() {
        assertEquals(listOf(GL11.GL_TEXTURE_2D, GL30.GL_TEXTURE_2D_ARRAY, GL13.GL_TEXTURE_CUBE_MAP), TextureType.entries.map(GlFormats::textureTarget))
        assertEquals(listOf(GL11.GL_NEAREST, GL11.GL_LINEAR, GL11.GL_NEAREST_MIPMAP_LINEAR, GL11.GL_LINEAR_MIPMAP_LINEAR), Filter.entries.map(GlFormats::filter))
        assertEquals(listOf(GL11.GL_REPEAT, GL12.GL_CLAMP_TO_EDGE), Wrap.entries.map(GlFormats::wrap))
        assertEquals((0..7).map { GL11.GL_NEVER + it }, CompareFunc.entries.map(GlFormats::compare))
    }
}
