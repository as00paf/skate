package com.pafoid.skate.engine.render.wind

import com.pafoid.skate.engine.render.shader.ShaderFeature
import com.pafoid.skate.engine.render.shader.ShaderPreprocessor
import com.pafoid.skate.engine.render.shader.ShaderVariantKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** REN-29 §5.2: the WIND permutation, and the same displacement in the colour, shadow and picking shaders. */
class WindShaderTest {
    private fun read(name: String) = File("assets/Shaders/$name").readText()

    private fun expand(name: String, defines: Set<String>): String {
        val stage = read(name).substringAfter("#type vertex").substringBefore("#type fragment")
        return ShaderPreprocessor.process(stage, defines, name) { include -> File("assets/Shaders/$include").takeIf { it.exists() }?.readText() }
    }

    @Test
    fun WindFeature_IsADefineAndAVariantKey() {
        assertEquals("a.glsl?SKINNED&WIND", ShaderVariantKey.of("a.glsl", listOf(ShaderFeature.WIND, ShaderFeature.SKINNED)).id)
        val out = expand("shadow.glsl", setOf(ShaderFeature.WIND.name))
        val lines = out.lines().map { it.trim() }
        assertEquals(lines.indexOf("#version 330 core") + 1, lines.indexOf("#define WIND"), "right after #version")
    }

    @Test
    fun WindDisplacement_IsAppliedByColourShadowAndPickingShaders() {
        for (name in listOf("shader_3d_default.glsl", "shadow.glsl", "shader_3d_picking.glsl")) {
            val vertex = expand(name, emptySet())
            assertTrue("#ifdef WIND" in vertex && "windDisplacement(" in vertex, name)
            // The include text itself lands in the source (guarded by the #ifdef)
            assertTrue("vec3 windSample(vec2 p, float t)" in vertex, name)
        }
    }

    @Test
    fun WindGlsl_MirrorsWindFieldConstants() {
        val glsl = read("wind.glsl")
        for (term in listOf("0.5 * sin(WIND_TAU * along)", "0.3 * sin(WIND_TAU * 2.3 * along + 1.7)",
            "0.2 * sin(WIND_TAU * 0.37 * along + 4.1)", "(0.75 + 0.25 * sin(WIND_TAU * 0.37 * across + 0.6))")) {
            assertTrue(term in glsl, term)
        }
        assertFalse("texture(" in glsl, "wind uses sines, no noise texture")
    }
}
