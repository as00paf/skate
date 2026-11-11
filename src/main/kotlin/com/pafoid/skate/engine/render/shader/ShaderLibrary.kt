package com.pafoid.skate.engine.render.shader

import com.pafoid.skate.engine.assets.EngineDefaults

/** A shader load that failed; [dependencies] are the files it read, so a watcher can retry when one changes. */
open class ShaderLoadException(message: String, val dependencies: Set<String>, cause: Throwable? = null) :
    RuntimeException(message, cause)

/**
 * The one shader cache (AST-26 §5.3 CR3): every program is keyed by `ShaderVariantKey(reference, defines)`, where the
 * reference is canonical (`engine:Shaders/x.glsl` or a project path) and empty defines is the base program. GL-free:
 * [load] compiles, [swap] moves a fresh program into the cached object, [destroy] frees one. [P] is the program type.
 *
 * - **Fallback:** when an engine shader fails (a broken project override, or a broken overridden include), the engine's
 *   own version of the **same** reference is used ([loadEngine]) and the error is logged. A broken `post_bloom`
 *   override gets the engine `post_bloom`, never the mesh shader. If that fails too, or a project shader fails, it is
 *   a hard error ([ShaderLoadException]).
 * - **Hot reload:** each program remembers the files it was built from; [reload] recompiles, in place, exactly the
 *   programs that depend on a changed file, re-resolving first (an override added or removed changes the source). A
 *   program that fails to recompile keeps its previous version.
 */
class ShaderLibrary<P : Any>(
    private val load: (reference: String, defines: Set<String>) -> Loaded<P>,
    private val loadEngine: (reference: String, defines: Set<String>) -> Loaded<P>,
    private val swap: (old: P, fresh: P) -> Unit,
    private val destroy: (P) -> Unit,
    private val log: (String) -> Unit = {},
) {
    class Loaded<P>(val program: P, val dependencies: Set<String>)

    private class Entry<P>(val program: P, var dependencies: Set<String>, var usesFallback: Boolean)

    private val entries = LinkedHashMap<ShaderVariantKey, Entry<P>>()

    /** Bumped whenever the set of watched files may have changed (a load, a reload, an unload). */
    var dependencyVersion: Int = 0
        private set

    val size: Int get() = entries.size
    var compileCount: Int = 0
        private set

    /** The program for [reference] with [defines], compiled on first use. */
    fun get(reference: String, defines: Set<String> = emptySet()): P {
        val key = ShaderVariantKey(ShaderSourceResolver.canonical(reference), defines.toSortedSet())
        entries[key]?.let { return it.program }
        val entry = compile(key)
        entries[key] = entry
        dependencyVersion++
        return entry.program
    }

    fun contains(reference: String, defines: Set<String> = emptySet()): Boolean =
        ShaderVariantKey(ShaderSourceResolver.canonical(reference), defines.toSortedSet()) in entries

    /** True when [reference]'s base program currently runs the engine's version because its override failed. */
    fun usesFallback(reference: String, defines: Set<String> = emptySet()): Boolean =
        entries[ShaderVariantKey(ShaderSourceResolver.canonical(reference), defines.toSortedSet())]?.usesFallback == true

    fun programs(): List<P> = entries.values.map { it.program }

    fun keys(): Set<ShaderVariantKey> = entries.keys.toSet()

    /** Every file id any cached program was built from. */
    fun dependencies(): Set<String> = entries.values.flatMapTo(HashSet()) { it.dependencies }

    /** The cached programs that depend on any of [ids]. */
    fun dependents(ids: Set<String>): List<ShaderVariantKey> =
        entries.filter { (_, e) -> e.dependencies.any { it in ids } }.keys.toList()

    /**
     * Recompiles in place every program that depends on one of [changedIds]; returns the keys that failed (they keep
     * their previous program).
     */
    fun reload(changedIds: Set<String>): Map<ShaderVariantKey, Throwable> {
        val errors = LinkedHashMap<ShaderVariantKey, Throwable>()
        for (key in dependents(changedIds)) {
            val entry = entries.getValue(key)
            try {
                val fresh = compile(key)
                swap(entry.program, fresh.program)
                entry.dependencies = fresh.dependencies
                entry.usesFallback = fresh.usesFallback
            } catch (e: Exception) {
                (e as? ShaderLoadException)?.let { entry.dependencies = entry.dependencies + it.dependencies }
                log("Shader ${key.id} failed to reload, keeping the previous version: ${e.message}")
                errors[key] = e
            }
        }
        dependencyVersion++
        return errors
    }

    /** Recompiles everything (e.g. a project was opened, so overrides changed). */
    fun reloadAll(): Map<ShaderVariantKey, Throwable> = reload(dependencies())

    /** Frees every variant of [reference]; returns whether any existed. */
    fun unload(reference: String): Boolean {
        val id = ShaderSourceResolver.canonical(reference)
        val keys = entries.keys.filter { it.path == id }
        keys.forEach { destroy(entries.remove(it)!!.program) }
        if (keys.isNotEmpty()) dependencyVersion++
        return keys.isNotEmpty()
    }

    fun disposeAll() {
        val all = entries.values.map { it.program }
        entries.clear()
        dependencyVersion++
        all.forEach(destroy)
    }

    private fun compile(key: ShaderVariantKey): Entry<P> {
        compileCount++
        val loaded = try {
            load(key.path, key.defines)
        } catch (e: Exception) {
            if (!EngineDefaults.isReference(key.path)) throw e
            val tried = (e as? ShaderLoadException)?.dependencies.orEmpty()
            val engine = try {
                loadEngine(key.path, key.defines)
            } catch (engineError: Exception) {
                throw ShaderLoadException("Engine shader ${key.id} failed: ${engineError.message}", tried + key.path, engineError)
            }
            log("Shader ${key.id} failed (${e.message}); using the engine's version until it is fixed.")
            // Keep watching what failed, so fixing the override switches back to it
            return Entry(engine.program, engine.dependencies + tried, usesFallback = true)
        }
        return Entry(loaded.program, loaded.dependencies, usesFallback = false)
    }
}
