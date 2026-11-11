package com.pafoid.skate.engine.render.shader

/**
 * The `// @skate-contract <n>` header of an engine shader (AST-26 §5.1). `n` is bumped whenever the shader's contract
 * with Kotlin code changes: uniform names, [ShaderFeature] defines, UBO layouts. A project override written for an
 * older contract would render wrong, so it falls back to the engine shader ([ShaderSourceResolver]).
 */
object ShaderContract {
    private val header = Regex("""^\s*//\s*@skate-contract\s+(\d+)\s*$""", RegexOption.MULTILINE)

    /** The contract version declared in [source], or null when it has none. */
    fun of(source: String): Int? = header.find(source)?.groupValues?.get(1)?.toIntOrNull()

    /**
     * Whether an override with source [override] may replace an engine shader with source [engine]: always when the
     * engine declares no contract; otherwise the override must declare the same or a newer one (a missing header
     * counts as 0).
     */
    fun compatible(override: String, engine: String): Boolean {
        val required = of(engine) ?: return true
        return (of(override) ?: 0) >= required
    }
}
