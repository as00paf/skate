package com.pafoid.skate.engine.render.shader

/** Optional shader permutations; each maps to a `#define` of the same name. */
enum class ShaderFeature { SKINNED, SHADOWED, FOG, ALPHA_TEST, SDF }

class ShaderPreprocessException(message: String) : RuntimeException(message)

/** Identity of one compiled permutation: source path plus a canonical (sorted) set of defines. */
data class ShaderVariantKey(val path: String, val defines: Set<String>) {
    /** Stable text form, independent of the order defines were added: `path?A&B`. */
    val id: String = if (defines.isEmpty()) path else "$path?${defines.sorted().joinToString("&")}"

    companion object {
        fun of(path: String, features: Collection<ShaderFeature>) =
            ShaderVariantKey(path, features.mapTo(sortedSetOf()) { it.name })
    }
}

/** A shader source file found by an include resolver: [id] identifies it (dependencies, `#pragma once`). */
data class ResolvedSource(val id: String, val text: String)

/** Where one line of preprocessed GLSL came from: the source [id] and its 1-based [line]. */
data class SourceLine(val id: String, val line: Int) {
    override fun toString() = "${id.substringAfterLast('/').substringAfterLast(':')}:$line"
}

/**
 * Preprocessed GLSL with a line map back to the files it was expanded from (AST-26 §5.3 CR9): [lines] has one entry
 * per output line, null for lines the preprocessor injected (`#define`s). [dependencies] lists every file id read.
 */
class ProcessedSource(val text: String, val lines: List<SourceLine?>, val dependencies: Set<String>) {
    /** The source of output line [line] (1-based), or null when it is out of range or injected. */
    fun locate(line: Int): SourceLine? = lines.getOrNull(line - 1)
}

/** GL-free GLSL preprocessing: `#include "file"` expansion and `#define` injection. */
object ShaderPreprocessor {
    private val includeRegex = Regex("""^\s*#include\s+"([^"]+)"\s*$""")
    private const val MAX_DEPTH = 16

    /**
     * Expands includes (each file at most once per call, like `#pragma once`) and inserts [defines] right after
     * the `#version` line, as GLSL requires `#version` to come first. [resolve] maps an include name to its source
     * or null when missing. Names containing `..` are rejected.
     */
    fun process(source: String, defines: Set<String>, origin: String, resolve: (String) -> String?): String =
        process(source, defines, origin, firstLine = 1) { name, _ -> resolve(name)?.let { ResolvedSource(name, it) } }.text

    /**
     * Like [process], with includes resolved relative to their **includer** (AST-26 §5.3 CR4): [resolve] gets the
     * include name and the id of the file that includes it, and returns the resolved file. The once-only set, the
     * cycle check and [ProcessedSource.dependencies] use the resolved ids, so `fog.glsl` included from two folders is
     * two files, and one file reached by two names is included once. [origin] is the id of [source]; its first line
     * is line [firstLine] of that file (a stage of a `#type` file starts mid-file).
     */
    fun process(
        source: String, defines: Set<String>, origin: String, firstLine: Int = 1,
        resolve: (name: String, includerId: String) -> ResolvedSource?,
    ): ProcessedSource {
        val included = linkedSetOf(origin)
        val text = ArrayList<String>()
        val lines = ArrayList<SourceLine?>()
        expand(source, origin, firstLine, resolve, included, listOf(origin), text, lines)
        injectDefines(defines, text, lines)
        return ProcessedSource(text.joinToString("\n"), lines, included)
    }

    private fun expand(
        source: String, origin: String, firstLine: Int, resolve: (String, String) -> ResolvedSource?,
        included: MutableSet<String>, stack: List<String>, out: MutableList<String>, map: MutableList<SourceLine?>,
    ) {
        if (stack.size > MAX_DEPTH) throw ShaderPreprocessException("Include depth exceeded in $origin")
        source.lines().forEachIndexed { index, line ->
            val match = includeRegex.matchEntire(line)
            if (match == null) {
                out += line
                map += SourceLine(origin, firstLine + index)
                return@forEachIndexed
            }
            val name = match.groupValues[1]
            if (name.replace('\\', '/').split('/').contains("..")) {
                throw ShaderPreprocessException("Illegal include '$name' in $origin")
            }
            val body = resolve(name, origin) ?: throw ShaderPreprocessException("Include '$name' not found (from $origin)")
            if (body.id in stack) throw ShaderPreprocessException("Circular include: ${(stack + body.id).joinToString(" -> ")}")
            if (!included.add(body.id)) return@forEachIndexed
            expand(body.text, body.id, 1, resolve, included, stack + body.id, out, map)
        }
    }

    private fun injectDefines(defines: Set<String>, text: MutableList<String>, lines: MutableList<SourceLine?>) {
        if (defines.isEmpty()) return
        val block = defines.sorted().map { "#define $it" }
        val at = text.indexOfFirst { it.trimStart().startsWith("#version") } + 1 // 0 when there is no #version
        text.addAll(at, block)
        lines.addAll(at, block.map { null })
    }
}
