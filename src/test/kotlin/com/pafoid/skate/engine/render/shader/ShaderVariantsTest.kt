package com.pafoid.skate.engine.render.shader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ShaderVariantsTest {
    private val none: (String) -> String? = { null }

    @Test
    fun ShaderVariant_KeyIsOrderIndependent() {
        val a = ShaderVariantKey.of("/s.glsl", listOf(ShaderFeature.SHADOWED, ShaderFeature.FOG))
        val b = ShaderVariantKey.of("/s.glsl", listOf(ShaderFeature.FOG, ShaderFeature.SHADOWED))
        assertEquals(a, b)
        assertEquals("/s.glsl?FOG&SHADOWED", a.id)
        assertEquals("/s.glsl", ShaderVariantKey.of("/s.glsl", emptyList()).id)
    }

    @Test
    fun ShaderPreprocessor_DefinesGoAfterVersionLine() {
        val out = ShaderPreprocessor.process("#version 330 core\nvoid main(){}\n", setOf("FOG", "SKINNED"), "x", none)
        assertEquals("#version 330 core\n#define FOG\n#define SKINNED\nvoid main(){}\n", out)
    }

    @Test
    fun ShaderPreprocessor_DefinesWithoutVersionArePrepended() {
        assertEquals("#define A\nfoo\n", ShaderPreprocessor.process("foo\n", setOf("A"), "x", none))
    }

    @Test
    fun ShaderPreprocessor_ExpandsNestedIncludesOnce() {
        val files = mapOf("a.glsl" to "#include \"b.glsl\"\nA", "b.glsl" to "B")
        val out = ShaderPreprocessor.process("#include \"a.glsl\"\n#include \"b.glsl\"\nmain", emptySet(), "m") { files[it] }
        assertEquals("B\nA\nmain", out)
    }

    @Test
    fun ShaderPreprocessor_RejectsCyclesMissingAndTraversal() {
        val files = mapOf("a" to "#include \"b\"", "b" to "#include \"a\"")
        assertFailsWith<ShaderPreprocessException> { ShaderPreprocessor.process("#include \"a\"", emptySet(), "m") { files[it] } }
        assertFailsWith<ShaderPreprocessException> { ShaderPreprocessor.process("#include \"nope\"", emptySet(), "m", none) }
        assertFailsWith<ShaderPreprocessException> { ShaderPreprocessor.process("#include \"../x\"", emptySet(), "m") { "" } }
    }
}
