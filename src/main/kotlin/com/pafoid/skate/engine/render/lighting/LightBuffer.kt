package com.pafoid.skate.engine.render.lighting

import com.pafoid.skate.engine.render.rhi.BufferDesc
import com.pafoid.skate.engine.render.rhi.BufferUpdate
import com.pafoid.skate.engine.render.rhi.BufferUsage
import com.pafoid.skate.engine.render.rhi.RhiBuffer
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import org.lwjgl.BufferUtils

/** Uniform buffer with up to [LightPacking.MAX_LIGHTS] lights (`LightBlock`), plus the per-object index list. GL thread only. */
class LightBuffer {
    private var buffer: RhiBuffer? = null
    private val staging = BufferUtils.createFloatBuffer(LightPacking.MAX_LIGHTS * LightPacking.FLOATS_PER_LIGHT)

    var lightCount = 0
        private set

    fun upload(lights: List<LightData>) {
        val gpu = buffer ?: RhiContext.require().createBuffer(
            BufferDesc(BufferUsage.UNIFORM, LightPacking.MAX_LIGHTS.toLong() * LightPacking.BYTES_PER_LIGHT, BufferUpdate.DYNAMIC),
        ).also { buffer = it }
        staging.clear()
        lightCount = LightPacking.pack(lights, staging)
        staging.flip()
        gpu.update(0L, staging)
    }

    fun bind(shader: Shader) {
        val gpu = buffer ?: return
        shader.bindUniformBlock(BLOCK_NAME, BINDING)
        RhiContext.require().commandList().setUniformBuffer(BINDING, gpu)
        shader.uploadInt(Uniforms.LIGHT_COUNT, lightCount)
    }

    /** Selects which lights shade the next draw; [indices] holds [count] entries into the uploaded list. */
    fun setObjectLights(shader: Shader, indices: IntArray, count: Int) {
        shader.uploadInt(Uniforms.OBJECT_LIGHT_COUNT, count)
        shader.uploadIntArray(Uniforms.OBJECT_LIGHTS, indices)
    }

    fun destroy() {
        buffer?.close()
        buffer = null
        lightCount = 0
    }

    companion object {
        const val BLOCK_NAME = "LightBlock"
        const val BINDING = 2
    }
}
