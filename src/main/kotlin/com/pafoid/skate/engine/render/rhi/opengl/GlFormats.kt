package com.pafoid.skate.engine.render.rhi.opengl

import com.pafoid.skate.engine.render.rhi.BufferUpdate
import com.pafoid.skate.engine.render.rhi.BufferUsage
import com.pafoid.skate.engine.render.rhi.CompareFunc
import com.pafoid.skate.engine.render.rhi.Filter
import com.pafoid.skate.engine.render.rhi.PixelFormat
import com.pafoid.skate.engine.render.rhi.TextureType
import com.pafoid.skate.engine.render.rhi.Wrap
import org.lwjgl.opengl.GL11.GL_ALWAYS
import org.lwjgl.opengl.GL11.GL_DEPTH_COMPONENT
import org.lwjgl.opengl.GL11.GL_EQUAL
import org.lwjgl.opengl.GL11.GL_FLOAT
import org.lwjgl.opengl.GL11.GL_GEQUAL
import org.lwjgl.opengl.GL11.GL_GREATER
import org.lwjgl.opengl.GL11.GL_LEQUAL
import org.lwjgl.opengl.GL11.GL_LESS
import org.lwjgl.opengl.GL11.GL_LINEAR
import org.lwjgl.opengl.GL11.GL_LINEAR_MIPMAP_LINEAR
import org.lwjgl.opengl.GL11.GL_NEAREST
import org.lwjgl.opengl.GL11.GL_NEAREST_MIPMAP_LINEAR
import org.lwjgl.opengl.GL11.GL_NEVER
import org.lwjgl.opengl.GL11.GL_NOTEQUAL
import org.lwjgl.opengl.GL11.GL_RED
import org.lwjgl.opengl.GL11.GL_REPEAT
import org.lwjgl.opengl.GL11.GL_RGB
import org.lwjgl.opengl.GL11.GL_RGB8
import org.lwjgl.opengl.GL11.GL_RGBA
import org.lwjgl.opengl.GL11.GL_RGBA8
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE
import org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE
import org.lwjgl.opengl.GL13.GL_TEXTURE_CUBE_MAP
import org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER
import org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW
import org.lwjgl.opengl.GL15.GL_STATIC_DRAW
import org.lwjgl.opengl.GL15.GL_STREAM_DRAW
import org.lwjgl.opengl.GL30.GL_DEPTH_COMPONENT32F
import org.lwjgl.opengl.GL30.GL_R8
import org.lwjgl.opengl.GL30.GL_RGB32F
import org.lwjgl.opengl.GL30.GL_RGBA16F
import org.lwjgl.opengl.GL30.GL_TEXTURE_2D_ARRAY
import org.lwjgl.opengl.GL31.GL_COPY_WRITE_BUFFER
import org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER

/** GL enums for RHI descriptors; pure so the mapping is unit-tested. */
internal object GlFormats {
    fun usage(update: BufferUpdate): Int = when (update) {
        BufferUpdate.STATIC -> GL_STATIC_DRAW
        BufferUpdate.DYNAMIC -> GL_DYNAMIC_DRAW
        BufferUpdate.STREAM -> GL_STREAM_DRAW
    }

    /** Upload target: never GL_ELEMENT_ARRAY_BUFFER, which is state of the bound VAO. */
    fun uploadTarget(usage: BufferUsage): Int = when (usage) {
        BufferUsage.VERTEX, BufferUsage.INSTANCE -> GL_ARRAY_BUFFER
        BufferUsage.INDEX -> GL_COPY_WRITE_BUFFER
        BufferUsage.UNIFORM -> GL_UNIFORM_BUFFER
    }

    fun textureTarget(type: TextureType): Int = when (type) {
        TextureType.TEXTURE_2D -> GL_TEXTURE_2D
        TextureType.TEXTURE_2D_ARRAY -> GL_TEXTURE_2D_ARRAY
        TextureType.CUBE -> GL_TEXTURE_CUBE_MAP
    }

    /** (internal format, pixel format, component type) for glTexImage. */
    fun texture(format: PixelFormat): Triple<Int, Int, Int> = when (format) {
        PixelFormat.R8 -> Triple(GL_R8, GL_RED, GL_UNSIGNED_BYTE)
        PixelFormat.RGB8 -> Triple(GL_RGB8, GL_RGB, GL_UNSIGNED_BYTE)
        PixelFormat.RGBA8 -> Triple(GL_RGBA8, GL_RGBA, GL_UNSIGNED_BYTE)
        PixelFormat.RGB32F -> Triple(GL_RGB32F, GL_RGB, GL_FLOAT)
        PixelFormat.RGBA16F -> Triple(GL_RGBA16F, GL_RGBA, GL_FLOAT)
        PixelFormat.DEPTH32F -> Triple(GL_DEPTH_COMPONENT32F, GL_DEPTH_COMPONENT, GL_FLOAT)
    }

    fun filter(filter: Filter): Int = when (filter) {
        Filter.NEAREST -> GL_NEAREST
        Filter.LINEAR -> GL_LINEAR
        Filter.NEAREST_MIPMAP_LINEAR -> GL_NEAREST_MIPMAP_LINEAR
        Filter.LINEAR_MIPMAP_LINEAR -> GL_LINEAR_MIPMAP_LINEAR
    }

    fun wrap(wrap: Wrap): Int = when (wrap) {
        Wrap.REPEAT -> GL_REPEAT
        Wrap.CLAMP_TO_EDGE -> GL_CLAMP_TO_EDGE
    }

    fun compare(func: CompareFunc): Int = when (func) {
        CompareFunc.NEVER -> GL_NEVER
        CompareFunc.LESS -> GL_LESS
        CompareFunc.EQUAL -> GL_EQUAL
        CompareFunc.LESS_EQUAL -> GL_LEQUAL
        CompareFunc.GREATER -> GL_GREATER
        CompareFunc.NOT_EQUAL -> GL_NOTEQUAL
        CompareFunc.GREATER_EQUAL -> GL_GEQUAL
        CompareFunc.ALWAYS -> GL_ALWAYS
    }
}
