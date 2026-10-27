package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.render.EngineStats
import org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW
import org.lwjgl.opengl.GL15.glBindBuffer
import org.lwjgl.opengl.GL15.glBufferData
import org.lwjgl.opengl.GL15.glBufferSubData
import org.lwjgl.opengl.GL15.glDeleteBuffers
import org.lwjgl.opengl.GL15.glGenBuffers
import org.lwjgl.opengl.GL30.glBindBufferBase
import org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER

/**
 * Shared std140 uniform buffer holding one skeleton's bone palette (`SkinBlock` in the skinned shaders).
 * A skeleton is re-uploaded only when it is not the one currently resident or its [SkeletonComponent.paletteVersion]
 * changed, so main, shadow and picking passes over an unchanged pose cost a single upload.
 * Must be used on the GL thread. Call [destroy] on shutdown.
 */
object SkinningUbo {
    const val BLOCK_NAME = "SkinBlock"
    const val BINDING = 1

    private var buffer = 0
    private var residentOwner: SkeletonComponent? = null
    private var residentVersion = -1

    /** Binds [skeleton]'s palette for [shader]; returns false if the shader has no skin block. */
    fun bind(shader: Shader, skeleton: SkeletonComponent): Boolean {
        if (!shader.bindUniformBlock(BLOCK_NAME, BINDING)) return false
        if (buffer == 0) {
            buffer = glGenBuffers()
            glBindBuffer(GL_UNIFORM_BUFFER, buffer)
            glBufferData(GL_UNIFORM_BUFFER, SkeletonComponent.MAX_BONES * 64L, GL_DYNAMIC_DRAW)
        }
        glBindBufferBase(GL_UNIFORM_BUFFER, BINDING, buffer)
        if (residentOwner === skeleton && residentVersion == skeleton.paletteVersion) {
            EngineStats.skinUploadsSkipped.incrementAndGet()
            return true
        }
        glBindBuffer(GL_UNIFORM_BUFFER, buffer)
        glBufferSubData(GL_UNIFORM_BUFFER, 0L, skeleton.packedPalette())
        residentOwner = skeleton
        residentVersion = skeleton.paletteVersion
        EngineStats.skinUploads.incrementAndGet()
        return true
    }

    fun destroy() {
        if (buffer != 0) glDeleteBuffers(buffer)
        buffer = 0
        residentOwner = null
        residentVersion = -1
    }
}
