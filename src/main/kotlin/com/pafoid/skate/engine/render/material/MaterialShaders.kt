package com.pafoid.skate.engine.render.material

import com.pafoid.skate.engine.render.shader.ShaderFeature

/**
 * Checks for custom material shaders (AST-26 §5.6, REN-31). GL-free text scans of the preprocessed GLSL.
 */
object MaterialShaders {
    private val uniform = Regex("""^\s*uniform\s+\w+\s+(\w+)""", RegexOption.MULTILINE)

    /** Uniform names declared in [sources] (outside blocks, which is where material uniforms live). */
    fun declaredUniforms(vararg sources: String): Set<String> =
        sources.flatMapTo(HashSet()) { src -> uniform.findAll(src).map { it.groupValues[1] } }

    /**
     * The uniforms and samplers of [schema] that a custom shader does not declare: the material would set them and
     * nothing would read them, so the shader probably won't look like the material. Empty when all are there.
     */
    fun missingSchemaUniforms(schema: MaterialSchema, declared: Set<String>): List<String> {
        val wanted = schema.params.map { it.uniform } + schema.textures.flatMap { listOfNotNull(it.samplerUniform, it.hasUniform) }
        return wanted.distinct().filterNot { it in declared }
    }

    /**
     * Whether [source] handles [feature]: it opts in by testing the define (`#ifdef X`, `#ifndef X`,
     * `#if defined(X)`). A draw that needs a feature the shader doesn't handle uses the schema shader instead.
     */
    fun handles(source: String, feature: ShaderFeature): Boolean =
        Regex("""#\s*(ifdef|ifndef)\s+${feature.name}\b|defined\s*\(\s*${feature.name}\s*\)""").containsMatchIn(source)
}

/**
 * Tracks which programs got this frame's uniforms (camera, sun, lights, shadows, IBL) in the colour pass, so each
 * program used by a material gets them once, on its first draw of the frame (AST-26 §5.6 CR6), and stable 16-bit ids
 * for [com.pafoid.skate.engine.render.state.SortKey]'s shader field so draws group by program. Id 0 is the default
 * program. [P] is the program type.
 */
class FramePrograms<P : Any> {
    private val prepared = HashSet<P>()
    private val ids = HashMap<String, Int>()

    /** Call at the start of each frame. */
    fun beginFrame() = prepared.clear()

    /** True the first time [program] is seen this frame (upload the frame uniforms then). */
    fun firstUse(program: P): Boolean = prepared.add(program)

    /** The sort id of the material shader [reference] (null: the default program, 0). Stable for the session. */
    fun idOf(reference: String?): Int {
        if (reference == null) return 0
        return ids.getOrPut(reference) { (ids.size + 1).coerceAtMost(MAX_ID) }
    }

    private companion object {
        const val MAX_ID = 0xFFFF
    }
}
