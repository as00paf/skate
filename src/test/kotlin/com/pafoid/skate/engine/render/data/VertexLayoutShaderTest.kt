package com.pafoid.skate.engine.render.data

import com.pafoid.skate.engine.render.rhi.AttributeType
import com.pafoid.skate.engine.render.rhi.VertexLayoutDesc
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/**
 * REN-12 step 2 (supersedes BatchShaderLayoutTest): every vertex layout matches the `layout(location = N) in` inputs
 * of the shaders it feeds, location and type, without a GL context. A mismatch reads garbage or the attribute default
 * (GUI-07: UI colours, REN-21: 2D picking ids).
 */
class VertexLayoutShaderTest {

    private val glslTypes = mapOf(
        "float" to AttributeType.FLOAT, "vec2" to AttributeType.VEC2, "vec3" to AttributeType.VEC3,
        "vec4" to AttributeType.VEC4, "int" to AttributeType.INT, "ivec4" to AttributeType.IVEC4,
    )

    /** location -> type of the shader's vertex inputs. */
    private fun inputsOf(file: String): Map<Int, AttributeType> {
        val src = javaClass.getResourceAsStream("/shaders/$file")!!.readBytes().toString(Charsets.UTF_8)
        val vertex = src.substringAfter("#type vertex").substringBefore("#type fragment")
        return Regex("""layout\s*\(\s*location\s*=\s*(\d+)\s*\)\s*(?:flat\s+)?in\s+(\w+)\s+\w+""").findAll(vertex)
            .associate { it.groupValues[1].toInt() to (glslTypes[it.groupValues[2]] ?: error("${it.groupValues[2]} in $file")) }
    }

    private fun attributesOf(layout: VertexLayoutDesc): Map<Int, AttributeType> =
        layout.bindings.flatMap { it.attributes }.associate { it.location to it.type }

    /** The layout feeds exactly the shader's inputs. */
    private fun exact(layout: VertexLayoutDesc, vararg files: String) = files.map { file ->
        DynamicTest.dynamicTest(file) { assertEquals(inputsOf(file), attributesOf(layout)) }
    }

    @TestFactory
    fun batch2D_FeedsTheBatchAndPickingShaders() = exact(VertexLayouts.BATCH_2D, "shader_2d_batch.glsl", "picking.glsl")

    @TestFactory
    fun batch3D_FeedsTheTexture3DShader() = exact(VertexLayouts.BATCH_3D, "shader_3d_texture.glsl")

    @TestFactory
    fun debug_FeedsTheDebugLineShader() = exact(VertexLayouts.DEBUG, "debugLine2D.glsl")

    @TestFactory
    fun fullscreen2D_FeedsTheEditorGridShader() = exact(VertexLayouts.FULLSCREEN_2D, "editor_grid.glsl")

    @TestFactory
    fun particles_FeedTheParticleShader() = exact(VertexLayouts.PARTICLES, "particles.glsl")

    /** Mesh shaders read a subset of the VAOLoader attributes (location 9 of picking is a per-draw constant). */
    @TestFactory
    fun meshAttributes_CoverEveryMeshShaderInput() = listOf(
        "shader_3d_default.glsl", "shadow.glsl", "shader_3d_picking.glsl", "skydome.glsl", "skybox.glsl", "splash.glsl",
        "post_bloom.glsl", "post_fxaa.glsl", "post_gamma.glsl", "post_ssao.glsl", "post_tonemap.glsl",
    ).map { file ->
        DynamicTest.dynamicTest(file) {
            val mesh = VertexLayouts.MeshAttribute.entries.associate { it.location to it.type }
            val inputs = inputsOf(file).filterKeys { !(file == "shader_3d_picking.glsl" && it == PICKING_ENTITY_ID) }
            assertTrue(inputs.isNotEmpty(), "$file has no vertex inputs")
            inputs.forEach { (location, type) -> assertEquals(mesh[location], type, "$file location $location") }
        }
    }

    @Test
    fun meshLayout_IsOneTightBindingPerAttribute() {
        val layout = VertexLayouts.mesh(listOf(0 to AttributeType.VEC3, 6 to AttributeType.IVEC4))
        assertEquals(listOf(12, 16), layout.bindings.map { it.stride })
        assertTrue(layout.bindings.all { b -> b.attributes.single().offset == 0 && !b.perInstance })
    }

    @Test
    fun particleInstanceRecord_MatchesTheFloatsPerInstance() {
        val instance = VertexLayouts.PARTICLES.bindings[1]
        assertTrue(instance.perInstance)
        assertEquals(VertexLayouts.PARTICLE_FLOATS_PER_INSTANCE * Float.SIZE_BYTES, instance.stride)
        assertEquals(VertexLayouts.PARTICLE_FLOATS_PER_INSTANCE, instance.attributes.sumOf { it.type.components })
    }

    private companion object {
        const val PICKING_ENTITY_ID = 9
    }
}
