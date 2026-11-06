package com.pafoid.skate.engine.assets.pack

import com.pafoid.skate.engine.render.rhi.GraphicsBackend
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class PackInput(val path: String, val relativePath: String, val type: String, val bytes: ByteArray)

/** [position] and [storedSize] address the entry inside the pack's data section; [rawSize] is the uncompressed size. */
@Serializable
data class ManifestEntry(
    val path: String,
    val type: String,
    val position: Int,
    val storedSize: Int,
    val rawSize: Int,
    val compressed: Boolean,
    val sha256: String,
)

/**
 * `manifest.json` next to an exported `game.bin`. Schema 2 (AST-12) adds [graphicsBackend], the project's backend
 * at export time; it is build metadata (startup reads the packed project and `game.settings.json`).
 */
@Serializable
data class PackManifest(
    val schema: Int = SCHEMA,
    val project: String = "",
    val binSize: Int = 0,
    val binSha256: String = "",
    val entries: List<ManifestEntry> = emptyList(),
    val graphicsBackend: GraphicsBackend = GraphicsBackend.DEFAULT,
) {
    companion object {
        const val SCHEMA = 2
    }
}

class PackResult(val data: ByteArray, val manifest: PackManifest)

object PackBuilder {
    private val alreadyCompressed = setOf("png", "jpg", "jpeg", "gif", "ogg", "mp3", "glb")

    /** Lays [inputs] out back to back. Entries are gzipped when that makes them clearly smaller. */
    fun build(
        project: String,
        inputs: List<PackInput>,
        compress: Boolean = true,
        graphicsBackend: GraphicsBackend = GraphicsBackend.DEFAULT,
    ): PackResult {
        val out = ByteArrayOutputStream()
        val entries = inputs.map { input ->
            val gz = if (compress && input.relativePath.substringAfterLast('.', "").lowercase() !in alreadyCompressed)
                gzip(input.bytes).takeIf { it.size < input.bytes.size * 0.9 } else null
            val stored = gz ?: input.bytes
            val entry = ManifestEntry(
                input.relativePath, input.type, out.size(), stored.size, input.bytes.size, gz != null, sha256(input.bytes)
            )
            out.write(stored)
            entry
        }
        val data = out.toByteArray()
        return PackResult(data, PackManifest(PackManifest.SCHEMA, project, data.size, sha256(data), entries, graphicsBackend))
    }

    /** Returns the original bytes of an entry stored at [position] of [data]. */
    fun extract(data: ByteArray, position: Int, storedSize: Int, compressed: Boolean): ByteArray {
        val slice = data.copyOfRange(position, position + storedSize)
        return if (compressed) gunzip(slice) else slice
    }

    /** Returns a description of every mismatch between [data] and [manifest]; empty when intact. */
    fun verify(data: ByteArray, manifest: PackManifest): List<String> {
        val problems = ArrayList<String>()
        if (data.size != manifest.binSize) problems += "size ${data.size} != ${manifest.binSize}"
        if (sha256(data) != manifest.binSha256) problems += "pack checksum mismatch"
        for (e in manifest.entries) {
            val ok = runCatching {
                sha256(extract(data, e.position, e.storedSize, e.compressed)) == e.sha256
            }.getOrDefault(false)
            if (!ok) problems += "corrupt entry ${e.path}"
        }
        return problems
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun gzip(bytes: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(bytes) }
        return bos.toByteArray()
    }

    private fun gunzip(bytes: ByteArray): ByteArray = GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
}
