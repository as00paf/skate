package com.pafoid.skate.engine.render.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * GUI-07 guard without a GL context: the 2D batch shader's attribute locations must match the indices that
 * [UiBatchRenderer] and RenderBatch2D pass to glVertexAttribPointer (0 pos, 1 colour, 2 uv, 3 texId, 4 entityId).
 */
class BatchShaderLayoutTest {
    @Test
    fun BatchShader_AttributeLocationsMatchTheBatchers() {
        val src = javaClass.getResourceAsStream("/shaders/shader_2d_batch.glsl")!!.readBytes().toString(Charsets.UTF_8)
        val locations = Regex("""layout\s*\(\s*location\s*=\s*(\d+)\s*\)\s*in\s+\w+\s+(\w+)""")
            .findAll(src).associate { it.groupValues[2] to it.groupValues[1].toInt() }
        assertEquals(mapOf("aPos" to 0, "aColor" to 1, "aTexCoords" to 2, "aTexId" to 3, "aEntityId" to 4), locations)
    }
}
