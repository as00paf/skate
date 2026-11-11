package com.pafoid.skate.engine.assets

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves `engine:` references to files (AST-26 §4.4) and back:
 *
 * 1. a project **override**: a file under the project's `Assets/` whose `.meta` has `engine.source` = the logical
 *    path, wherever it was moved. An unchanged copy of a **shader** is not an override: it follows the engine file
 *    (AST-26 §5.2);
 * 2. else `<engineRoot>/assets/<logical path>`;
 * 3. else the classpath copy (shaders, the app icon), extracted once to a temporary folder.
 *
 * The override index is built lazily by reading the project's sidecars and rebuilt when the project changes, when
 * an indexed file is gone (moved or deleted) or after [invalidate]. Never builds a [File] from a reference string.
 */
class EngineDefaultResolver(
    private val engineRoot: File = File(System.getProperty("user.dir")),
    private val projectRoot: () -> File? = { null },
) {
    private class Override(val file: File, val copySha256: String?)

    @Volatile
    private var index: Map<String, Override>? = null
    @Volatile
    private var indexedRoot: File? = null
    private val extracted = ConcurrentHashMap<String, File>()
    private val extractDir: File by lazy { Files.createTempDirectory("skate-engine-defaults").toFile().also { it.deleteOnExit() } }

    /** The engine repo's defaults folder (`<engineRoot>/assets`). */
    val engineAssetsDir: File get() = File(engineRoot, EngineDefaults.REPO_ASSETS_DIR)

    /** Forget the override index (a copy was added, a sidecar edited). */
    fun invalidate() {
        index = null
    }

    /** The file [reference] stands for now (override > engine file > classpath copy), or null when none exists. */
    fun resolve(reference: String): File? {
        val logical = EngineDefaults.logicalPath(reference) ?: return null
        override(logical)?.let { return it }
        return engineFile(logical)
    }

    /** The engine's own file for [logicalPath], ignoring overrides: the repo file, else the classpath copy. */
    fun engineFile(logicalPath: String): File? {
        File(engineAssetsDir, logicalPath).takeIf { it.isFile }?.let { return it.absoluteFile }
        val resource = EngineDefaults.classpathResource(logicalPath) ?: return null
        return extracted[resource] ?: extract(resource)
    }

    /** The project override of [logicalPath], or null (none, or an unchanged shader copy). */
    fun override(logicalPath: String): File? {
        val entry = entry(logicalPath) ?: return null
        if (logicalPath.startsWith("Shaders/") && entry.copySha256 != null && sha256(entry.file) == entry.copySha256) {
            return null // unchanged shader copy: follows the engine (AST-26 §5.2)
        }
        return entry.file
    }

    /** The project file copied from [logicalPath] (its `.meta` names it), changed or not; null when there is none. */
    fun overrideFile(logicalPath: String): File? = entry(logicalPath)?.file

    private fun entry(logicalPath: String): Override? {
        var entry = overrides()[logicalPath]
        if (entry != null && !entry.file.isFile) { // moved or deleted since the index was built
            invalidate()
            entry = overrides()[logicalPath]
        }
        return entry?.takeIf { it.file.isFile }
    }

    /**
     * The engine reference for [file]: an override's `engine.source`, or the logical path of a file under the engine
     * repo's `assets/`; null for anything else.
     */
    fun referenceFor(file: File): String? {
        val abs = file.absoluteFile.normalize()
        val engineDir = engineAssetsDir.absoluteFile.normalize()
        if (abs.startsWith(engineDir) && abs != engineDir) return EngineDefaults.reference(abs.relativeTo(engineDir).invariantSeparatorsPath)
        extracted.entries.firstOrNull { it.value.absoluteFile.normalize() == abs }?.let { (resource, _) ->
            return EngineDefaults.CATALOG.plus(EngineDefaults.Shaders.INTERNAL).firstOrNull { ref ->
                EngineDefaults.classpathResource(ref.removePrefix(EngineDefaults.PREFIX)) == resource
            }
        }
        return overrides().entries.firstOrNull { it.value.file.absoluteFile.normalize() == abs }?.key?.let(EngineDefaults::reference)
    }

    private fun overrides(): Map<String, Override> {
        val root = projectRoot()?.absoluteFile
        if (root != indexedRoot) {
            indexedRoot = root
            index = null
        }
        index?.let { return it }
        return scan(root).also { index = it }
    }

    private fun scan(root: File?): Map<String, Override> {
        val assets = root?.let { ProjectLayout.assetsDir(it) }?.takeIf { it.isDirectory } ?: return emptyMap()
        val found = LinkedHashMap<String, Override>()
        assets.walkTopDown()
            .onEnter { it == assets || !it.name.startsWith(".") }
            .filter { it.isFile && AssetMeta.isSidecar(it) }
            .sortedBy { it.invariantSeparatorsPath } // duplicates: the first by path wins (AST-26 §4.5)
            .forEach { sidecar ->
                val meta = runCatching { AssetMeta.decode(sidecar.readText()) }.getOrNull() ?: return@forEach
                val logical = meta.settings[EngineDefaults.META_SOURCE] ?: return@forEach
                val file = File(sidecar.parentFile, sidecar.name.removeSuffix(".${AssetMeta.EXTENSION}"))
                if (file.isFile && logical !in found) found[logical] = Override(file.absoluteFile, meta.settings[EngineDefaults.META_SHA256])
            }
        return found
    }

    private fun extract(resource: String): File? {
        val bytes = EngineDefaultResolver::class.java.getResourceAsStream(resource)?.use { it.readBytes() } ?: return null
        val target = File(extractDir, resource.trimStart('/'))
        target.parentFile.mkdirs()
        target.writeBytes(bytes)
        target.deleteOnExit()
        extracted[resource] = target
        return target
    }

    companion object {
        fun sha256(file: File): String = sha256(file.readBytes())

        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
