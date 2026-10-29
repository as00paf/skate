package com.pafoid.skate.engine.render.shader

/** Compiles shader permutations on demand and caches them by [ShaderVariantKey]. [S] is the program type. */
class ShaderVariantCache<S : Any>(
    private val compile: (ShaderVariantKey) -> S,
    private val destroy: (S) -> Unit,
) {
    private val variants = LinkedHashMap<ShaderVariantKey, S>()

    val size: Int get() = variants.size
    var compileCount = 0
        private set

    fun get(key: ShaderVariantKey): S = variants.getOrPut(key) {
        compileCount++
        compile(key)
    }

    fun allPaths(): List<String> = variants.keys.map { it.path }.distinct()

    fun keysFor(path: String): List<ShaderVariantKey> = variants.keys.filter { it.path == path }

    /**
     * Recompiles every cached variant of [path] (hot reload). A variant whose new source fails keeps its old
     * program and is reported in the returned map; successful ones are passed to [swap] (old, new).
     */
    fun reload(path: String, swap: (old: S, new: S) -> Unit): Map<ShaderVariantKey, Throwable> {
        val errors = LinkedHashMap<ShaderVariantKey, Throwable>()
        for (key in keysFor(path)) {
            try {
                val fresh = compile(key)
                compileCount++
                swap(variants.getValue(key), fresh)
            } catch (e: Exception) {
                errors[key] = e
            }
        }
        return errors
    }

    fun disposeAll() {
        val all = variants.values.toList()
        variants.clear()
        all.forEach(destroy)
    }
}
