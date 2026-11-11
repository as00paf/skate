package com.pafoid.skate.engine.render.shader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** REN-31 / AST-26 §5.3 CR9: compile errors point at the original file and line after include expansion. */
class ShaderErrorLinesTest {
    private val files = mapOf(
        "engine:Shaders/fog.glsl" to "float fog;\nfloat broken",
        "Assets/Shaders/my_water.glsl" to "x\n#type fragment\n#version 330 core\n#include \"fog.glsl\"\nvoid main() {}",
    )

    /** The fragment stage of my_water.glsl starts at file line 2, like ShaderLoader passes it. */
    private val processed = ShaderPreprocessor.process(
        "\n#version 330 core\n#include \"fog.glsl\"\nvoid main() {}", setOf("FOG"), "Assets/Shaders/my_water.glsl", firstLine = 2,
    ) { name, _ -> ResolvedSource("engine:Shaders/$name", files.getValue("engine:Shaders/$name")) }

    @Test
    fun lineMap_FollowsIncludesAndSkipsInjectedDefines() {
        // output: "", "#version", "#define FOG", "float fog;", "float broken", "void main() {}"
        assertEquals(SourceLine("Assets/Shaders/my_water.glsl", 3), processed.locate(2))
        assertNull(processed.locate(3))
        assertEquals(SourceLine("engine:Shaders/fog.glsl", 2), processed.locate(5))
        assertEquals(SourceLine("Assets/Shaders/my_water.glsl", 5), processed.locate(6))
        assertEquals(setOf("Assets/Shaders/my_water.glsl", "engine:Shaders/fog.glsl"), processed.dependencies)
    }

    @Test
    fun driverLogs_AreRewrittenToFileAndLine() {
        assertEquals("fog.glsl:2 : error C0000: syntax error", ShaderErrorLines.map("0(5) : error C0000: syntax error", processed))
        assertEquals("ERROR: fog.glsl:2: 'broken' : syntax error", ShaderErrorLines.map("ERROR: 0:5: 'broken' : syntax error", processed))
        assertEquals("my_water.glsl:5: error", ShaderErrorLines.map("name:6: error", processed))
        assertEquals("no line here", ShaderErrorLines.map("no line here", processed))
        assertEquals("ERROR: 0:99: out of range", ShaderErrorLines.map("ERROR: 0:99: out of range", processed))
    }

    @Test
    fun includesResolveByIncluderAndOnceById() {
        val seen = ArrayList<Pair<String, String>>()
        val out = ShaderPreprocessor.process("#include \"a.glsl\"\n#include \"b.glsl\"", emptySet(), "root") { name, includer ->
            seen += name to includer
            when (name) {
                "a.glsl" -> ResolvedSource("dir/a.glsl", "#include \"c.glsl\"\nA")
                "b.glsl" -> ResolvedSource("dir/c.glsl", "C") // another name for the same file
                "c.glsl" -> ResolvedSource("dir/c.glsl", "C")
                else -> null
            }
        }
        assertEquals("C\nA", out.text)
        assertEquals(listOf("a.glsl" to "root", "c.glsl" to "dir/a.glsl", "b.glsl" to "root"), seen)
    }
}
