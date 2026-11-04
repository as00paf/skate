package com.pafoid.skate.engine.render.ui

import com.pafoid.skate.engine.utils.RenderConsts2D
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Guards the batched-quad vertex layout without a GL context: every shader fed by RenderBatch2D, RenderBatch3D or
 * [UiBatchRenderer] must declare the attribute locations those batchers pass to glVertexAttribPointer
 * (RenderConsts2D.*_LOCATION). GUI-07 (UI colours) and REN-21 (2D picking read entity id 0) were both this mismatch.
 */
class BatchShaderLayoutTest {

    private val expected = mapOf(
        "aPos" to RenderConsts2D.POS_LOCATION,
        "aColor" to RenderConsts2D.COLOR_LOCATION,
        "aTexCoords" to RenderConsts2D.TEX_COORDS_LOCATION,
        "aTexId" to RenderConsts2D.TEX_ID_LOCATION,
        "aEntityId" to RenderConsts2D.ENTITY_ID_LOCATION,
    )

    @TestFactory
    fun batchShaders_DeclareTheBatcherAttributeLocations() =
        listOf("shader_2d_batch.glsl", "picking.glsl", "shader_3d_texture.glsl").map { file ->
            DynamicTest.dynamicTest(file) { assertEquals(expected, locationsIn(file)) }
        }

    private fun locationsIn(file: String): Map<String, Int> {
        val src = javaClass.getResourceAsStream("/shaders/$file")!!.readBytes().toString(Charsets.UTF_8)
        return Regex("""layout\s*\(\s*location\s*=\s*(\d+)\s*\)\s*in\s+\w+\s+(\w+)""")
            .findAll(src).associate { it.groupValues[2] to it.groupValues[1].toInt() }
    }
}
