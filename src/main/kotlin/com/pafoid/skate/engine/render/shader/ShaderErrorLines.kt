package com.pafoid.skate.engine.render.shader

/**
 * Rewrites the line numbers of a GLSL compiler log so they point at the original files (AST-26 §5.3 CR9): after
 * include expansion, line 412 of a stage may be `lighting.glsl:120`. Understands the common driver formats:
 * NVIDIA `0(42) : error`, AMD/Intel/Mesa `ERROR: 0:42:` and glslang `name:42:`. Lines it can't map are left alone.
 */
object ShaderErrorLines {
    private val nvidia = Regex("""\b\d+\((\d+)\)""")
    private val colon = Regex("""[\w.\-]+:(\d+):""")

    fun map(log: String, source: ProcessedSource): String = log.lines().joinToString("\n") { line ->
        when {
            nvidia.containsMatchIn(line) -> nvidia.replace(line) { m -> m.groupValues[1].toIntOrNull()?.let(source::locate)?.toString() ?: m.value }
            colon.containsMatchIn(line) -> colon.replaceFirst(line) { m ->
                m.groupValues[1].toIntOrNull()?.let(source::locate)?.let { "$it:" } ?: m.value
            }
            else -> line
        }
    }

    private fun Regex.replaceFirst(input: String, transform: (MatchResult) -> CharSequence): String {
        val m = find(input) ?: return input
        return input.substring(0, m.range.first) + transform(m) + input.substring(m.range.last + 1)
    }
}
