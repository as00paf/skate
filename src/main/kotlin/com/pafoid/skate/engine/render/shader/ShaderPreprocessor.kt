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

/** GL-free GLSL preprocessing: `#include "file"` expansion and `#define` injection. */
object ShaderPreprocessor {
    private val includeRegex = Regex("""^\s*#include\s+"([^"]+)"\s*$""")
    private const val MAX_DEPTH = 16

    /**
     * Expands includes (each file at most once per call, like `#pragma once`) and inserts [defines] right after
     * the `#version` line, as GLSL requires `#version` to come first. [resolve] maps an include name to its source
     * or null when missing. Names containing `..` are rejected.
     */
    fun process(source: String, defines: Set<String>, origin: String, resolve: (String) -> String?): String {
        val included = mutableSetOf<String>()
        val expanded = expand(source, origin, resolve, included, listOf(origin))
        return injectDefines(expanded, defines)
    }

    private fun expand(
        source: String, origin: String, resolve: (String) -> String?,
        included: MutableSet<String>, stack: List<String>,
    ): String {
        if (stack.size > MAX_DEPTH) throw ShaderPreprocessException("Include depth exceeded in $origin")
        val out = mutableListOf<String>()
        for (line in source.lines()) {
            val match = includeRegex.matchEntire(line)
            if (match == null) {
                out += line
                continue
            }
            val name = match.groupValues[1]
            if (name.split('/').contains("..")) {
                throw ShaderPreprocessException("Illegal include '$name' in $origin")
            }
            if (name in stack) throw ShaderPreprocessException("Circular include: ${(stack + name).joinToString(" -> ")}")
            if (!included.add(name)) continue
            val body = resolve(name) ?: throw ShaderPreprocessException("Include '$name' not found (from $origin)")
            out += expand(body, name, resolve, included, stack + name)
        }
        return out.joinToString("\n")
    }

    private fun injectDefines(source: String, defines: Set<String>): String {
        if (defines.isEmpty()) return source
        val block = defines.sorted().joinToString("") { "#define $it\n" }
        val lines = source.split('\n')
        val versionIndex = lines.indexOfFirst { it.trimStart().startsWith("#version") }
        return if (versionIndex < 0) block + source
        else (lines.take(versionIndex + 1).joinToString("\n") + "\n" + block + lines.drop(versionIndex + 1).joinToString("\n"))
    }
}
