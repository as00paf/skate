package com.pafoid.skate.editor.assetstore

import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Downloads a [DownloadPlan] into a staging folder (AST-16 §7): sanitised paths, size and MD5 checks, and reuse of
 * files already staged with the right MD5. Nothing here touches the assets folder.
 */
class StagingDownloader(private val http: StoreHttp) {

    /** Returns the staged relative paths, in plan order. A failed or cancelled run leaves no partial file behind. */
    fun download(
        plan: DownloadPlan,
        stagingDir: File,
        cancel: CancelToken,
        progress: (done: Long, total: Long?) -> Unit = { _, _ -> },
    ): List<String> {
        val total = plan.items.mapNotNull { it.sizeBytes }.takeIf { it.size == plan.items.size }?.sum()
        var finished = 0L
        val staged = ArrayList<String>()
        stagingDir.mkdirs()
        for (item in plan.items) {
            if (cancel.isCancelled) throw StoreCancelledException()
            val relative = StorePaths.safeRelative(item.relativePath) ?: throw IOException("Unsafe path in download plan: ${item.relativePath}")
            val target = StorePaths.resolveInside(stagingDir, relative) ?: throw IOException("Path escapes staging: ${item.relativePath}")
            if (!(target.isFile && matches(target, item))) {
                val part = File(target.parentFile, target.name + ".part")
                try {
                    http.download(item.url, plan.headers, part, cancel) { done, _ -> progress(finished + done, total) }
                    verify(part, item)
                    target.delete()
                    if (!part.renameTo(target)) throw IOException("Cannot move ${part.name} into place")
                } finally {
                    part.delete()
                }
            }
            finished += item.sizeBytes ?: target.length()
            progress(finished, total)
            staged += relative
        }
        return staged
    }

    private fun matches(file: File, item: DownloadItem): Boolean =
        (item.sizeBytes == null || file.length() == item.sizeBytes) && (item.md5 == null || md5(file).equals(item.md5, ignoreCase = true))

    private fun verify(file: File, item: DownloadItem) {
        if (item.sizeBytes != null && file.length() != item.sizeBytes) {
            throw IOException("Size mismatch for ${item.relativePath}: ${file.length()} != ${item.sizeBytes}")
        }
        if (item.md5 != null && !md5(file).equals(item.md5, ignoreCase = true)) {
            throw IOException("Checksum mismatch for ${item.relativePath}")
        }
    }

    companion object {
        fun md5(file: File): String {
            val digest = MessageDigest.getInstance("MD5")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
