package com.pafoid.skate.editor.assetstore

import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * Extracts a downloaded archive inside a staging folder (AST-16 §7). Every entry name goes through
 * [StorePaths.safeRelative] (zip-slip), and the entry count and total uncompressed size are capped (zip bomb).
 */
object StoreZip {
    const val MAX_ENTRIES = 500
    const val MAX_TOTAL_BYTES = 4L * 1024 * 1024 * 1024

    /** Returns the extracted files relative to [destDir], in archive order. Directories are skipped. */
    fun extract(zip: File, destDir: File, maxEntries: Int = MAX_ENTRIES, maxTotalBytes: Long = MAX_TOTAL_BYTES): List<String> {
        val extracted = ArrayList<String>()
        var total = 0L
        var entries = 0
        ZipInputStream(zip.inputStream().buffered()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                if (++entries > maxEntries) throw IOException("Archive ${zip.name} has more than $maxEntries entries")
                if (entry.isDirectory) continue
                val relative = StorePaths.safeRelative(entry.name) ?: throw IOException("Unsafe path in archive ${zip.name}: ${entry.name}")
                val target = StorePaths.resolveInside(destDir, relative) ?: throw IOException("Path escapes staging: ${entry.name}")
                target.parentFile.mkdirs()
                target.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > maxTotalBytes) {
                            out.close()
                            target.delete()
                            throw IOException("Archive ${zip.name} expands beyond $maxTotalBytes bytes")
                        }
                        out.write(buffer, 0, n)
                    }
                }
                extracted += relative
            }
        }
        return extracted
    }
}
