package com.pafoid.skate.engine.physics3d.native

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Installs a native binary into a shared folder that several processes use at once (editor, exported game, parallel
 * test runs). The binary goes to `<root>/<name>-<content hash>/<fileName>`, so different versions never share a file.
 * An identical file already there is reused as is. A new one is written to a unique temp file and moved into place
 * atomically, so no process ever sees a half-written DLL. Nothing is ever deleted, because another process may have
 * the file loaded (Windows locks loaded DLLs).
 */
internal object NativeExtractor {
    private const val HASH_HEX_CHARS = 16

    /** Folder for [bytes] of library [name] under [root]: the name plus a prefix of the content's SHA-256. */
    fun versionDir(root: File, name: String, bytes: ByteArray): File = File(root, "$name-${hashOf(bytes)}")

    /** Returns the installed file holding exactly [bytes], extracting it only when no identical copy exists. */
    fun install(root: File, name: String, fileName: String, bytes: ByteArray): File {
        val dir = versionDir(root, name, bytes)
        val target = File(dir, fileName)
        if (holds(target, bytes)) return target

        Files.createDirectories(dir.toPath())
        val temp = Files.createTempFile(dir.toPath(), "$fileName.", ".tmp")
        try {
            Files.write(temp, bytes)
            try {
                moveIntoPlace(temp, target)
            } catch (e: Exception) {
                // Another process won the race and may already hold the file loaded: use its identical copy
                if (!holds(target, bytes)) throw e
            }
        } finally {
            Files.deleteIfExists(temp)
        }
        check(holds(target, bytes)) { "installed native $target does not match the bundled binary" }
        return target
    }

    private fun moveIntoPlace(temp: java.nio.file.Path, target: File) {
        try {
            Files.move(temp, target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(temp, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /**
     * True when [file] exists with exactly [bytes] (a corrupted or truncated copy does not count). On Windows a file is
     * briefly unreadable while another process moves its copy over it, so I/O errors are retried for up to ~1 s.
     */
    private fun holds(file: File, bytes: ByteArray): Boolean {
        repeat(READ_ATTEMPTS) {
            try {
                return file.isFile && file.length() == bytes.size.toLong() && file.readBytes().contentEquals(bytes)
            } catch (e: IOException) {
                Thread.sleep(READ_RETRY_MS)
            }
        }
        return false
    }

    private const val READ_ATTEMPTS = 50
    private const val READ_RETRY_MS = 20L

    private fun hashOf(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
            .take(HASH_HEX_CHARS)
}
