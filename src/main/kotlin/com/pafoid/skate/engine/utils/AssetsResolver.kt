package com.pafoid.skate.engine.utils

import com.pafoid.skate.engine.assets.pack.PackBuilder
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import com.pafoid.skate.engine.fileExtension
import org.lwjgl.system.MemoryUtil
import java.io.File
import java.nio.ByteBuffer

/**
 * Reads assets out of an exported `game.bin`. Project files are packed under the absolute path they had at export
 * time; [find] also accepts the same path with other separators or `..` segments, and project-relative paths
 * (`Assets/Materials/rock.skmat`), which is how scenes and `.skmat` files may refer to them.
 */
class AssetsResolver(
    val serializer: Serializer,
    val logger: LoggerService
) {
    lateinit var assetsAtlas: Atlas
    lateinit var binData: ByteArray
    var headerOffset: Int = 0
    var initialized = false

    fun read(info: AssetInfo): ByteArray =
        PackBuilder.extract(binData, info.position + headerOffset, info.size, info.compressed)

    fun initialize(atlas: Atlas, binData: ByteArray, headerOffset: Int) {
        this.assetsAtlas = atlas
        this.binData = binData
        this.headerOffset = headerOffset
        initialized = true
    }

    /** The packed entry for [path], or null (also before [initialize]). */
    fun find(path: String): AssetInfo? {
        if (!initialized || path.isBlank()) return null
        val extension = path.fileExtension()
        val candidates = listOf(extension, extension.lowercase()).distinct().flatMap { assetsAtlas[it].orEmpty() }
        candidates.firstOrNull { it.path == path }?.let { return it }
        val wanted = normalize(path)
        candidates.firstOrNull { normalize(it.path) == wanted }?.let { return it }
        if (File(path).isAbsolute) return null
        val suffix = "/" + wanted.trimStart('/')
        return candidates.filter { normalize(it.path).endsWith(suffix, ignoreCase = true) }.minByOrNull { it.path.length }
    }

    /** Original bytes of the packed asset at [path], or null (logged) when it is not in the pack. */
    fun resolveBytes(path: String): ByteArray? {
        if (!initialized) {
            logger.logEngine("AssetsResolver must be initialized first", LogLevel.ERROR)
            return null
        }
        val info = find(path) ?: run {
            logger.logEngine("Could not resolve data for path: $path", LogLevel.ERROR)
            return null
        }
        return read(info)
    }

    /** Original bytes of every packed entry whose path matches [filter], keyed by path; empty before [initialize]. */
    fun readAll(filter: (String) -> Boolean): Map<String, ByteArray> {
        if (!initialized) return emptyMap()
        return assetsAtlas.values.flatten().filter { filter(it.path) }.associate { it.path to read(it) }
    }

    inline fun <reified T> resolve(path: String): T? {
        val bytes = resolveBytes(path) ?: return null
        return serializer.decode<T>(bytes.toString(Charsets.UTF_8))
    }

    /** The asset in native memory; the caller frees it with `MemoryUtil.memFree`. */
    fun resolveData(path: String): ByteBuffer? {
        val bytes = resolveBytes(path) ?: return null
        val data = MemoryUtil.memAlloc(bytes.size)
        data.put(bytes)
        data.flip()
        return data
    }

    fun resolveModel(path: String): ByteBuffer? {
        val bytes = resolveBytes(path) ?: return null
        val data = ByteBuffer.allocateDirect(bytes.size)
        data.put(bytes)
        data.flip()
        return data
    }

    private companion object {
        /** Forward slashes, no `.`/`..` segments, drive letter in lower case. */
        fun normalize(path: String): String {
            val unified = path.replace('\\', '/')
            val absolute = unified.startsWith("/") || Regex("^[A-Za-z]:/").containsMatchIn(unified)
            val parts = ArrayDeque<String>()
            unified.split('/').forEach { part ->
                when (part) {
                    "", "." -> Unit
                    ".." -> if (parts.isNotEmpty() && parts.last() != "..") parts.removeLast() else parts.addLast(part)
                    else -> parts.addLast(part)
                }
            }
            val joined = parts.joinToString("/")
            val lead = if (unified.startsWith("/")) "/" else ""
            return if (absolute && joined.length > 1 && joined[1] == ':') joined[0].lowercaseChar() + joined.substring(1) else lead + joined
        }
    }
}
