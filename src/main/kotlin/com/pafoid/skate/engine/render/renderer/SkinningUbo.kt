package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.engine.render.rhi.BufferDesc
import com.pafoid.skate.engine.render.rhi.BufferUpdate
import com.pafoid.skate.engine.render.rhi.BufferUsage
import com.pafoid.skate.engine.render.rhi.RhiBuffer
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.render.EngineStats

/**
 * Shared std140 uniform buffer holding one skeleton's bone palette (`SkinBlock` in the skinned shaders).
 * A skeleton is re-uploaded only when it is not the one currently resident or its [SkeletonComponent.paletteVersion]
 * changed, so main, shadow and picking passes over an unchanged pose cost a single upload.
 * Must be used on the GL thread. Call [destroy] on shutdown.
 */
object SkinningUbo {
    const val BLOCK_NAME = "SkinBlock"
    const val BINDING = 1

    private var buffer: RhiBuffer? = null
    private var residentOwner: SkeletonComponent? = null
    private var residentVersion = -1

    /** Binds [skeleton]'s palette for [shader]; returns false if the shader has no skin block. */
    fun bind(shader: Shader, skeleton: SkeletonComponent): Boolean {
        if (!shader.bindUniformBlock(BLOCK_NAME, BINDING)) return false
        val device = RhiContext.require()
        val gpu = buffer ?: device.createBuffer(
            BufferDesc(BufferUsage.UNIFORM, SkeletonComponent.MAX_BONES * 64L, BufferUpdate.DYNAMIC),
        ).also { buffer = it }
        device.commandList().setUniformBuffer(BINDING, gpu)
        if (residentOwner === skeleton && residentVersion == skeleton.paletteVersion) {
            EngineStats.skinUploadsSkipped.incrementAndGet()
            return true
        }
        gpu.update(0L, skeleton.packedPalette())
        residentOwner = skeleton
        residentVersion = skeleton.paletteVersion
        EngineStats.skinUploads.incrementAndGet()
        return true
    }

    fun destroy() {
        buffer?.close()
        buffer = null
        residentOwner = null
        residentVersion = -1
    }
}
