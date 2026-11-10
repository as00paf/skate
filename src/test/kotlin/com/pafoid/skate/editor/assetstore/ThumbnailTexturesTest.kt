package com.pafoid.skate.editor.assetstore

import com.pafoid.skate.editor.ui.windows.assetStore.ThumbnailTextures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ThumbnailTexturesTest {
    @TempDir
    lateinit var tmp: File

    @Test
    fun Thumbnails_LruEvictsAndUnloads() {
        val loaded = mutableListOf<String>()
        val unloaded = mutableListOf<String>()
        val cache = ThumbnailTextures(2, { loaded += it; loaded.size.toLong() }, { unloaded += it })
        cache.texId("a")
        cache.texId("b")
        cache.texId("a")
        cache.texId("c")
        assertEquals(listOf("b"), unloaded)
        assertEquals(1L, cache.texId("a"))
        assertEquals(listOf("a", "b", "c"), loaded)
        cache.clear()
        assertEquals(setOf("b", "a", "c"), unloaded.toSet())
        assertEquals(0, cache.size)
    }

    @Test
    fun Thumbnails_FileNamedFromContentType() {
        val http = FakeStoreHttp().route("https://x/t", "jpeg").apply { contentType = "image/jpeg; charset=binary" }
        val service = AssetStoreService(
            AssetStoreRegistry(listOf(com.pafoid.skate.editor.assetstore.providers.polyhaven.PolyHavenProvider(http))),
            http, InlineJobs, {}, { null }, tmp,
        )
        service.thumbnail("https://x/t")
        val file = service.thumbnail("https://x/t")!!
        assertTrue(file.name.endsWith(".jpg"))
        assertEquals("jpg", StoreHttp.imageExtension("image/jpg"))
        assertEquals(null, StoreHttp.imageExtension("text/html"))
    }
}
