package com.pafoid.skate.engine.render.fog

import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/** REN-09: every scene-colour shader takes its fog from the shared include, so models, sky and particles agree. */
class FogShaderIncludeTest {
    private fun src(name: String) = javaClass.getResourceAsStream("/shaders/$name")!!.readBytes().toString(Charsets.UTF_8)

    @TestFactory
    fun sceneShaders_IncludeSharedFog() = listOf("shader_3d_default.glsl", "skydome.glsl", "particles.glsl").map { file ->
        DynamicTest.dynamicTest(file) {
            val s = src(file)
            assertTrue(s.contains("#include \"fog.glsl\""), "$file must include fog.glsl")
            assertFalse(s.contains("uFogGradient") || s.contains("fVisibility"), "$file still has the old vertex fog")
        }
    }

    @TestFactory
    fun fogInclude_DeclaresEveryUploadedUniform() = listOf(
        Uniforms.FOG_MODE, Uniforms.FOG_COLOR, Uniforms.FOG_DENSITY, Uniforms.FOG_START, Uniforms.FOG_END,
        Uniforms.FOG_HEIGHT, Uniforms.FOG_HEIGHT_FALLOFF,
    ).map { name -> DynamicTest.dynamicTest(name) { assertTrue(Regex("""uniform \w+ $name;""").containsMatchIn(src("fog.glsl"))) } }
}
