package com.pafoid.skate.engine.render.material

import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.render.shader.ShaderFeature
import com.pafoid.skate.engine.render.shader.ShaderTemplates
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** REN-31 / AST-26 §5.6: custom material shaders (the `.skmat` field, uniform contract, feature opt-in, program ids). */
class MaterialShadersTest {

    @Test
    fun version1Materials_ReadWithoutACustomShader() {
        val v1 = """{ "name": "Old", "schema": "pbr-metallic-roughness", "params": {}, "textures": {}, "version": 1 }"""
        val asset = MaterialAsset.fromJson(v1)
        assertNull(asset.shader)
        assertNull(asset.toMaterial { null }.shader)
        assertEquals(2, MaterialAsset.CURRENT_VERSION)
    }

    @Test
    fun customShader_RoundTripsThroughTheSkmatAndTheRuntimeMaterial() {
        val asset = MaterialAsset(name = "Water", shader = "Assets/Shaders/my_water.glsl")
        val back = MaterialAsset.fromJson(asset.toJson())
        assertEquals("Assets/Shaders/my_water.glsl", back.shader)
        val material = back.toMaterial { null }
        assertEquals("Assets/Shaders/my_water.glsl", material.shader)
        assertEquals("Assets/Shaders/my_water.glsl", MaterialAsset.from(material).shader)
        assertNull(MaterialAsset.from(Material()).shader)
    }

    @Test
    fun missingSchemaUniforms_ListsWhatTheShaderDoesNotDeclare() {
        val declared = MaterialShaders.declaredUniforms(
            "uniform vec4 uBaseColor;\nuniform float u_BaseColorFactor;", "  uniform sampler2D u_BaseColorTexture; // x",
        )
        assertEquals(setOf("uBaseColor", "u_BaseColorFactor", "u_BaseColorTexture"), declared)
        val missing = MaterialShaders.missingSchemaUniforms(MaterialSchema.PBR, declared)
        assertTrue("u_MetallicFactor" in missing && "u_NormalMap" in missing)
        assertFalse("uBaseColor" in missing)
    }

    @Test
    fun theNewShaderTemplate_DeclaresEveryPbrUniform() {
        val missing = MaterialShaders.missingSchemaUniforms(MaterialSchema.PBR, MaterialShaders.declaredUniforms(ShaderTemplates.PBR_MATERIAL))
        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun featureOptIn_IsATextScanOfTheDefineTests() {
        assertTrue(MaterialShaders.handles("#ifdef SKINNED\n...\n#endif", ShaderFeature.SKINNED))
        assertTrue(MaterialShaders.handles("#if defined( FOG ) && X", ShaderFeature.FOG))
        assertTrue(MaterialShaders.handles("#ifndef ALPHA_TEST", ShaderFeature.ALPHA_TEST))
        assertFalse(MaterialShaders.handles("uniform bool u_HasSkin; // SKINNED", ShaderFeature.SKINNED))
        assertFalse(MaterialShaders.handles("#ifdef SKINNED_EXTRA", ShaderFeature.SKINNED))
    }

    @Test
    fun framePrograms_GiveStableIds_AndOneFrameUploadPerProgram() {
        val p = FramePrograms<String>()
        assertEquals(0, p.idOf(null))
        val a = p.idOf("Assets/Shaders/a.glsl")
        val b = p.idOf("Assets/Shaders/b.glsl")
        assertTrue(a > 0 && b > 0 && a != b)
        assertEquals(a, p.idOf("Assets/Shaders/a.glsl"))
        p.beginFrame()
        assertTrue(p.firstUse("progA"))
        assertFalse(p.firstUse("progA"))
        assertTrue(p.firstUse("progB"))
        p.beginFrame()
        assertTrue(p.firstUse("progA"))
    }
}
