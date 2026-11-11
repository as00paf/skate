package com.pafoid.skate.engine.render.lighting

import com.pafoid.skate.engine.render.shader.ShaderPreprocessor
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/**
 * REN-28 task 1: scene lighting (sun, lights, shadows, IBL, BRDF) lives in the shared `lighting.glsl` include, so terrain,
 * water and vegetation shaders light exactly like meshes. GL-free: reads and expands the shader sources only.
 */
class LightingShaderIncludeTest {

    private fun src(name: String) = javaClass.getResourceAsStream("/shaders/$name")!!.readBytes().toString(Charsets.UTF_8).replace("\r", "")

    private fun expand(source: String, origin: String) = ShaderPreprocessor.process(source, emptySet(), origin) { src(it) }

    private val defaultFragment: String by lazy {
        val raw = src("shader_3d_default.glsl")
        expand(raw.substring(raw.indexOf("#type fragment")), "shader_3d_default.glsl")
    }

    private fun declaredUniforms(glsl: String) =
        Regex("""^\s*uniform\s+\w+\s+(\w+)""", RegexOption.MULTILINE).findAll(glsl).map { it.groupValues[1] }.toList()

    @Test
    fun defaultShader_IncludesLighting_AndNoLongerDefinesItsFunctions() {
        val raw = src("shader_3d_default.glsl")
        assertTrue(raw.contains("#include \"lighting.glsl\""))
        for (fn in listOf("DistributionGGX", "sunShadow", "localShadow", "iblSpecularRadiance", "CalcPointLight")) {
            assertFalse(Regex("""\b$fn\s*\([^;{]*\)\s*\{""").containsMatchIn(raw), "$fn is still defined in shader_3d_default.glsl")
        }
    }

    @TestFactory
    fun expandedDefaultShader_DefinesEachLightingFunctionOnce() = listOf(
        "pcf", "sunShadow", "localShadow", "DistributionGGX", "GeometrySmith", "fresnelSchlick", "iblIrradiance",
        "iblSpecularRadiance", "CalcPointLight", "CalcSpotLight", "sunLighting", "localLighting", "ambientLighting", "applyFog",
    ).map { fn ->
        DynamicTest.dynamicTest(fn) {
            assertEquals(1, Regex("""\b(?:float|vec2|vec3|vec4|int|bool)\s+$fn\s*\([^;{]*\)\s*\{""").findAll(defaultFragment).count())
        }
    }

    @Test
    fun expandedDefaultShader_DeclaresNoUniformTwice() {
        val names = declaredUniforms(defaultFragment)
        assertEquals(names.size, names.toSet().size, "duplicates: ${names.groupBy { it }.filter { it.value.size > 1 }.keys}")
    }

    /** Every uniform the fragment stage declared before the extraction is still declared (the CPU side uploads them). */
    @Test
    fun expandedDefaultShader_KeepsEveryUniformItHadBeforeTheExtraction() {
        val before = listOf(
            "u_BaseColorTexture", "u_NormalMap", "u_MetallicRoughnessTexture", "u_AOTexture", "u_EmissiveTexture",
            "uBaseColor", "u_BaseColorFactor", "u_MetallicFactor", "u_RoughnessFactor", "u_EmissiveFactor", "u_AlphaMode",
            "u_AlphaCutoff", "uCameraPos", "uSunDirection", "uSunColor", "uAmbientLight",
            "uEnvEnabled", "uEnvSH", "uEnvRotation", "uEnvMaxLod", "uEnvReflection", "uEnvSkyLight", "uEnvSkyScale",
            "uEnvPrefiltered", "uEnvSky", "uEnvBrdfLut",
            "uCascadeShadowMap", "uLocalShadowMap", "uCascadeCount", "uCascadeMatrices", "uCascadeSplits",
            "uCascadeNormalOffset", "uCascadeDepthBias", "uShadowTexelSize", "uShadowFadeStart", "uPcfRadius",
            "uShadowedLightCount", "uShadowLightIndex", "uShadowLightBase", "uShadowLightTexel", "uLocalShadowMatrices",
            "uLocalShadowTexelSize", "uHasBaseColorTexture", "u_HasNormalMap", "u_HasMetallicRoughnessTexture",
            "u_HasAOTexture", "u_HasEmissiveTexture", "uSelected", "uLightCount", "uObjLightCount", "uObjLights",
            Uniforms.FOG_MODE, Uniforms.FOG_COLOR,
        )
        val now = declaredUniforms(defaultFragment).toSet()
        assertEquals(emptyList<String>(), before.filterNot { it in now })
        assertTrue(defaultFragment.contains("uniform LightBlock"))
    }

    @TestFactory
    fun lightingInclude_DeclaresTheUploadedLightingUniforms() = listOf(
        Uniforms.CAMERA_POSITION, Uniforms.SUN_DIRECTION, Uniforms.SUN_COLOR, Uniforms.AMBIENT_LIGHT,
        Uniforms.LIGHT_COUNT, Uniforms.OBJECT_LIGHT_COUNT, Uniforms.OBJECT_LIGHTS,
        Uniforms.CASCADE_SHADOW_MAP, Uniforms.LOCAL_SHADOW_MAP, Uniforms.CASCADE_COUNT, Uniforms.CASCADE_MATRICES,
        Uniforms.CASCADE_SPLITS, Uniforms.CASCADE_NORMAL_OFFSET, Uniforms.CASCADE_DEPTH_BIAS, Uniforms.SHADOW_TEXEL_SIZE,
        Uniforms.SHADOW_FADE_START, Uniforms.PCF_RADIUS, Uniforms.SHADOWED_LIGHT_COUNT, Uniforms.SHADOW_LIGHT_INDEX,
        Uniforms.SHADOW_LIGHT_BASE, Uniforms.SHADOW_LIGHT_TEXEL, Uniforms.LOCAL_SHADOW_MATRICES,
        Uniforms.LOCAL_SHADOW_TEXEL_SIZE,
    ).map { name -> DynamicTest.dynamicTest(name) { assertTrue(name in declaredUniforms(src("lighting.glsl"))) } }

    /** Reusable by any lit shader: no stage varyings, no stage markers, no entry point. */
    @Test
    fun lightingInclude_IsStageIndependent() {
        val s = src("lighting.glsl")
        assertFalse(Regex("""\bf(WorldPos|ViewDepth|Normal|TBN)\b""").containsMatchIn(s), "uses a stage varying")
        assertFalse(s.contains("#type") || s.contains("#version") || Regex("""\bvoid\s+main\s*\(""").containsMatchIn(s))
    }

    /** A shader that includes only lighting.glsl gets fog too, once; one that includes both gets each once. */
    @Test
    fun lightingInclude_BringsFogOnce() {
        val alone = expand("#version 330 core\n#include \"lighting.glsl\"\nvoid main() {}", "probe.glsl")
        assertEquals(1, Regex("""uniform \w+ ${Uniforms.FOG_COLOR};""").findAll(alone).count())
        assertEquals(1, Regex("""uniform \w+ ${Uniforms.FOG_COLOR};""").findAll(defaultFragment).count())
    }
}
