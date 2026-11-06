package com.pafoid.skate.engine.render.rhi.vulkan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GlslToVulkanTest {
    private val vertex = """
        #version 330 core
        layout (location=0) in vec3 aPos;
        out vec2 fUv;
        out mat3 fTbn;
        out float fDepth;
        uniform mat4 uMvp; // model-view-projection
        layout(std140) uniform SkinBlock {
            mat4 uJoints[4];
        };
        void main() { gl_Position = uMvp * vec4(aPos, 1.0); }
    """.trimIndent()

    private val fragment = """
        #version 330 core
        in vec2 fUv;
        in float fDepth;
        in mat3 fTbn;
        #define MAX_LIGHTS 8
        uniform sampler2D uAlbedo;
        uniform sampler2DArrayShadow uShadow;
        uniform int uLights[MAX_LIGHTS];
        uniform mat4 uMvp;
        out vec4 color;
        void main() { color = texture(uAlbedo, fUv); }
    """.trimIndent()

    private val result = GlslToVulkan.convert(vertex, fragment)

    @Test
    fun LooseUniforms_ShareOneParamsBlockInBothStages() {
        assertEquals(listOf("mat4 uMvp", "int uLights[MAX_LIGHTS]"), result.params)
        val block = "layout(std140, set = 0, binding = 0) uniform Params {\n    mat4 uMvp;\n    int uLights[MAX_LIGHTS];\n};"
        assertTrue(result.vertex.contains(block), result.vertex)
        assertTrue(result.fragment.contains(block), result.fragment)
        assertFalse(Regex("""^\s*uniform (mat4|int) """, RegexOption.MULTILINE).containsMatchIn(result.fragment))
    }

    @Test
    fun Defines_AreHoistedIntoBothStagesAboveTheBlock() {
        for (stage in listOf(result.vertex, result.fragment)) {
            assertTrue(stage.startsWith("#version 450\n#define MAX_LIGHTS 8\nlayout(std140"), stage)
        }
    }

    @Test
    fun BlocksAndSamplers_GetSharedBindingsAfterParams() {
        assertEquals(mapOf("SkinBlock" to 1), result.blocks)
        assertEquals(mapOf("uAlbedo" to 2, "uShadow" to 3), result.samplers)
        assertTrue(result.vertex.contains("layout(std140, set = 0, binding = 1) uniform SkinBlock {"))
        assertTrue(result.fragment.contains("layout(set = 0, binding = 2) uniform sampler2D uAlbedo;"))
        assertTrue(result.fragment.contains("layout(set = 0, binding = 3) uniform sampler2DArrayShadow uShadow;"))
    }

    @Test
    fun Varyings_MatchByNameAndMatricesTakeOneLocationPerColumn() {
        assertTrue(result.vertex.contains("layout(location = 0) out vec2 fUv;"))
        assertTrue(result.vertex.contains("layout(location = 1) out mat3 fTbn;"))
        assertTrue(result.vertex.contains("layout(location = 4) out float fDepth;"))
        assertTrue(result.fragment.contains("layout(location = 4) in float fDepth;"), "declared in another order")
        assertTrue(result.fragment.contains("layout(location = 1) in mat3 fTbn;"))
        assertTrue(result.fragment.contains("layout(location = 0) out vec4 color;"))
    }

    @Test
    fun VertexStage_EndsWithTheDepthRangeFixUp() {
        assertTrue(result.vertex.contains("void skate_main() {"))
        assertTrue(result.vertex.contains("gl_Position.z = (gl_Position.z + gl_Position.w) * 0.5;"))
        assertFalse(result.fragment.contains("skate_main"))
    }

    @Test
    fun Unsupported_InitializersAndUnmatchedInputsAreRejected() {
        assertThrows<IllegalArgumentException> { GlslToVulkan.convert("#version 330 core\nuniform float a = 1.0;\nvoid main() {}", "void main() {}") }
        assertThrows<IllegalArgumentException> { GlslToVulkan.convert("#version 330 core\nvoid main() {}", "in vec2 nope;\nvoid main() {}") }
    }

    @Test
    fun ClipSpace_MapsGlDepthToVulkanAndFlipsY() {
        val w = 2.5f
        assertEquals(0f, ClipSpace.vulkanZ(-w, w), 1e-6f, "near plane")
        assertEquals(w, ClipSpace.vulkanZ(w, w), 1e-6f, "far plane")
        assertEquals(w / 2, ClipSpace.vulkanZ(0f, w), 1e-6f)
        assertEquals(listOf(0f, 720f, 1280f, -720f), ClipSpace.flippedViewport(0, 0, 1280, 720, 720).toList())
        assertEquals(listOf(10f, 700f, 100f, -50f), ClipSpace.flippedViewport(10, 20, 100, 50, 720).toList())
        assertEquals(listOf(10, 650, 100, 50), ClipSpace.scissor(10, 20, 100, 50, 720).toList(), "bottom-left -> top-left")
    }

    @Test
    fun ClipSpace_ShadowDepthStoredByVulkanEqualsGlWindowDepth() {
        // VK-04: shadow maps hold the same depth on both backends, so GL-style receiver math compares alike
        for (w in listOf(1f, 3.5f)) for (ndc in listOf(-1f, -0.4f, 0f, 0.73f, 1f)) {
            val glWindowDepth = ndc * 0.5f + 0.5f
            assertEquals(glWindowDepth, ClipSpace.vulkanZ(ndc * w, w) / w, 1e-6f, "ndc $ndc, w $w")
        }
    }

    @Test
    fun Backend_IsOpenGlUnlessVulkanIsAskedFor() {
        assertEquals(RhiBackend.OPENGL, RhiBackend.selected(null, null))
        assertEquals(RhiBackend.OPENGL, RhiBackend.selected("metal", null))
        assertEquals(RhiBackend.VULKAN, RhiBackend.selected(" Vulkan ", null))
        assertEquals(RhiBackend.VULKAN, RhiBackend.selected(null, "vulkan"))
        assertEquals(RhiBackend.OPENGL, RhiBackend.selected("opengl", "vulkan"), "the property wins over the environment")
    }
}
