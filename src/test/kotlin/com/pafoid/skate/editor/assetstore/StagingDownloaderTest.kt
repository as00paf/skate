package com.pafoid.skate.editor.assetstore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException

class StagingDownloaderTest {
    @TempDir
    lateinit var tmp: File

    private val body = "gltf-bytes".toByteArray()
    private val md5 = run { val f = File.createTempFile("md5", ".bin"); f.writeBytes(body); StagingDownloader.md5(f).also { f.delete() } }
    private val http = FakeStoreHttp().route("https://x/a.gltf", body).route("https://x/t.jpg", "jpg".toByteArray())

    @Test
    fun Staging_DownloadsVerifiesAndKeepsRelativePaths() {
        val plan = DownloadPlan(listOf(DownloadItem("https://x/a.gltf", "a.gltf", body.size.toLong(), md5), DownloadItem("https://x/t.jpg", "textures/t.jpg")))
        var last = 0L
        val staged = StagingDownloader(http).download(plan, tmp, CancelToken()) { done, _ -> last = done }
        assertEquals(listOf("a.gltf", "textures/t.jpg"), staged)
        assertTrue(File(tmp, "textures/t.jpg").isFile)
        assertEquals(body.size + 3L, last)
        assertFalse(tmp.walkTopDown().any { it.name.endsWith(".part") })
    }

    @Test
    fun Staging_ReusesFileWithMatchingChecksum() {
        val plan = DownloadPlan(listOf(DownloadItem("https://x/a.gltf", "a.gltf", body.size.toLong(), md5)))
        StagingDownloader(http).download(plan, tmp, CancelToken())
        StagingDownloader(http).download(plan, tmp, CancelToken())
        assertEquals(1, http.downloads.size)
    }

    @Test
    fun Staging_ChecksumMismatch_FailsAndLeavesNoFile() {
        val plan = DownloadPlan(listOf(DownloadItem("https://x/a.gltf", "a.gltf", null, "00000000000000000000000000000000")))
        assertThrows<IOException> { StagingDownloader(http).download(plan, tmp, CancelToken()) }
        assertFalse(File(tmp, "a.gltf").exists())
        assertFalse(File(tmp, "a.gltf.part").exists())
    }

    @Test
    fun Staging_UnsafePath_IsRejectedBeforeDownloading() {
        val plan = DownloadPlan(listOf(DownloadItem("https://x/a.gltf", "../evil.gltf")))
        assertThrows<IOException> { StagingDownloader(http).download(plan, File(tmp, "staging"), CancelToken()) }
        assertTrue(http.downloads.isEmpty())
        assertFalse(File(tmp, "evil.gltf").exists())
    }

    @Test
    fun Staging_Cancelled_Throws() {
        val token = CancelToken().apply { cancel() }
        val plan = DownloadPlan(listOf(DownloadItem("https://x/a.gltf", "a.gltf")))
        assertThrows<StoreCancelledException> { StagingDownloader(http).download(plan, tmp, token) }
    }
}
