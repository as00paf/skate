package com.pafoid.skate.engine.render.shader

import com.pafoid.skate.engine.assets.EngineDefaultResolver
import com.pafoid.skate.engine.assets.EngineDefaults
import java.io.File

/** Where a shader source was read from. */
enum class ShaderOrigin {
    /** A modified project copy of a basic engine shader (`engine.source` in its `.meta`). */
    PROJECT_OVERRIDE,
    /** The engine repo's `assets/Shaders` (dev runs). */
    ENGINE_FILE,
    /** The jar's `/shaders/` copy. */
    CLASSPATH,
    /** An entry of the exported game's pack. */
    PACK,
    /** A project shader that is not an engine default (`Assets/Shaders/my_water.glsl`). */
    PROJECT,
}

/** One resolved shader file: its canonical [id], [text], [origin] and [location] (a file path, or `pack:`/`classpath:`). */
class ShaderSource(val id: String, val text: String, val origin: ShaderOrigin, val location: String) {
    val isEngine: Boolean get() = origin == ShaderOrigin.ENGINE_FILE || origin == ShaderOrigin.CLASSPATH
    /** What to show in messages: the file for project shaders and overrides, the reference otherwise. */
    val displayName: String get() = if (origin == ShaderOrigin.PROJECT || origin == ShaderOrigin.PROJECT_OVERRIDE) location else id
    fun asResolved() = ResolvedSource(id, text)
}

/**
 * Finds shader sources (AST-26 §5.3). Engine shaders are `engine:Shaders/<name>`; the legacy classpath form
 * `/shaders/<name>` means the same ([canonical]). For an engine shader, in order:
 *
 * 1. the exported game's pack entry, or a **modified** project override of a basic shader (unchanged copies follow the
 *    engine: [EngineDefaultResolver.override]), if its `@skate-contract` is current ([ShaderContract]); an override
 *    written for an older contract logs one error and is skipped;
 * 2. the engine's file: the repo's `assets/Shaders` (dev runs), else the jar's `/shaders/`.
 *
 * A project shader (`Assets/Shaders/my_water.glsl`) is its pack entry or its file. Internal engine shaders (picking,
 * splash, ...) are never overridden.
 *
 * Includes ([resolveInclude]) resolve relative to their includer: a name inside an engine shader is a sibling engine
 * shader (override-aware, so overriding `fog.glsl` changes every includer); inside a project shader it is a sibling
 * project file, else the engine shader of that name. An explicit `#include "engine:Shaders/fog.glsl"` always means the
 * engine's own file, so an override can wrap the original.
 *
 * With [engineOnly], overrides and pack entries are ignored: the fallback when an override fails to compile.
 */
class ShaderSourceResolver(
    private val engineDefaults: () -> EngineDefaultResolver? = { null },
    /** Bytes of a pack entry (exported game), or null when there is no pack or no such entry. */
    private val readPacked: (String) -> ByteArray? = { null },
    /** The file of a project-relative shader path, or null. */
    private val projectFile: (String) -> File? = { path -> File(path).takeIf { it.isAbsolute } },
    private val readClasspath: (String) -> String? = { resource ->
        ShaderSourceResolver::class.java.getResourceAsStream(resource)?.use { it.readBytes().toString(Charsets.UTF_8) }
    },
    /** Receives each stale-override error once. */
    private val onError: (String) -> Unit = {},
    private val engineOnly: Boolean = false,
) {
    private val reported = HashSet<String>()

    /** The same resolver, ignoring overrides and pack entries. */
    fun engineOnly(): ShaderSourceResolver =
        if (engineOnly) this else ShaderSourceResolver(engineDefaults, readPacked, projectFile, readClasspath, onError, engineOnly = true)

    /** The source [reference] stands for now, or null when nothing provides it. */
    fun resolve(reference: String): ShaderSource? {
        val id = canonical(reference)
        val logical = engineLogical(id) ?: return if (engineOnly) null else project(id)
        val engine = engine(id, logical)
        if (engineOnly) return engine
        val candidate = packed(id) ?: if (EngineDefaults.isBasicShader(id)) override(id, logical) else null
        if (candidate == null) return engine
        if (engine != null && !ShaderContract.compatible(candidate.text, engine.text)) {
            reportOnce(candidate, engine)
            return engine
        }
        return candidate
    }

    /** The engine's own source of [reference] (no override, no pack), or null for a project shader or a missing one. */
    fun engineSource(reference: String): ShaderSource? {
        val id = canonical(reference)
        return engine(id, engineLogical(id) ?: return null)
    }

    /**
     * Resolves `#include "<name>"` found in the file [includerId]. Sibling project files that were looked for and not
     * found are added to [misses], so a watcher can notice when one appears.
     */
    fun resolveInclude(name: String, includerId: String, misses: MutableSet<String>? = null): ShaderSource? {
        if (EngineDefaults.isReference(name)) return engineSource(name)
        val clean = name.replace('\\', '/')
        val includerLogical = engineLogical(includerId)
        if (includerLogical != null) {
            val dir = includerLogical.substringBeforeLast('/', "")
            return resolve(EngineDefaults.reference(if (dir.isEmpty()) clean else "$dir/$clean"))
        }
        val dir = includerId.replace('\\', '/').substringBeforeLast('/', "")
        val sibling = if (dir.isEmpty()) clean else "$dir/$clean"
        val local = if (engineOnly) null else project(sibling)
        if (local != null) return local
        misses?.add(sibling)
        return resolve(EngineDefaults.reference("$SHADERS_DIR/$clean"))
    }

    /**
     * A fingerprint of every file that could provide [id] (pack entry, override, engine file, project file), so a
     * watcher sees edits, an override appearing or disappearing, and a contract fix. Cheap: no hashing.
     */
    fun fingerprint(id: String): String {
        val canonicalId = canonical(id)
        val logical = engineLogical(canonicalId)
        val parts = ArrayList<String>(3)
        if (logical == null) {
            parts += stamp(projectFile(canonicalId))
        } else {
            if (EngineDefaults.isBasicShader(canonicalId)) parts += "o:" + stamp(engineDefaults()?.overrideFile(logical))
            parts += "e:" + stamp(engineDefaults()?.engineFile(logical))
        }
        return parts.joinToString("|")
    }

    private fun project(id: String): ShaderSource? {
        readPacked(id)?.let { return ShaderSource(id, it.toString(Charsets.UTF_8), ShaderOrigin.PACK, "pack:$id") }
        val file = projectFile(id)?.takeIf { it.isFile } ?: return null
        return ShaderSource(id, file.readText(), ShaderOrigin.PROJECT, file.path)
    }

    private fun packed(id: String): ShaderSource? =
        readPacked(id)?.let { ShaderSource(id, it.toString(Charsets.UTF_8), ShaderOrigin.PACK, "pack:$id") }

    private fun override(id: String, logical: String): ShaderSource? {
        val file = engineDefaults()?.override(logical) ?: return null
        return ShaderSource(id, file.readText(), ShaderOrigin.PROJECT_OVERRIDE, file.path)
    }

    private fun engine(id: String, logical: String): ShaderSource? {
        val defaults = engineDefaults()
        if (defaults != null) {
            val file = defaults.engineFile(logical) ?: return null
            val inRepo = file.absoluteFile.normalize().startsWith(defaults.engineAssetsDir.absoluteFile.normalize())
            return ShaderSource(id, file.readText(), if (inRepo) ShaderOrigin.ENGINE_FILE else ShaderOrigin.CLASSPATH, file.path)
        }
        val resource = EngineDefaults.classpathResource(logical) ?: return null
        val text = readClasspath(resource) ?: return null
        return ShaderSource(id, text, ShaderOrigin.CLASSPATH, "classpath:$resource")
    }

    private fun reportOnce(candidate: ShaderSource, engine: ShaderSource) {
        val have = ShaderContract.of(candidate.text) ?: 0
        val need = ShaderContract.of(engine.text) ?: 0
        if (!reported.add("${candidate.location}#$have#$need")) return
        onError(
            "Shader override ${candidate.location} is written for @skate-contract $have, but the engine's ${candidate.id} " +
                "needs $need: using the engine shader. Merge your edits into the engine version, then set " +
                "'// @skate-contract $need' (docs/shaders.md).",
        )
    }

    private fun stamp(file: File?): String = file?.takeIf { it.isFile }?.let { "${it.path}@${it.lastModified()}:${it.length()}" } ?: "-"

    companion object {
        const val SHADERS_DIR = "Shaders"
        private const val CLASSPATH_PREFIX = "/shaders/"

        /** `engine:Shaders/x.glsl` for an engine shader in any accepted form (`/shaders/x.glsl`, `shaders/x.glsl`). */
        fun canonical(path: String): String {
            if (EngineDefaults.isReference(path)) return path
            val p = path.replace('\\', '/')
            return when {
                p.startsWith(CLASSPATH_PREFIX) -> EngineDefaults.reference("$SHADERS_DIR/" + p.removePrefix(CLASSPATH_PREFIX))
                p.startsWith(CLASSPATH_PREFIX.drop(1)) -> EngineDefaults.reference("$SHADERS_DIR/" + p.removePrefix(CLASSPATH_PREFIX.drop(1)))
                else -> path
            }
        }

        private fun engineLogical(id: String): String? =
            EngineDefaults.logicalPath(id)?.takeIf { it.startsWith("$SHADERS_DIR/") }
    }
}
