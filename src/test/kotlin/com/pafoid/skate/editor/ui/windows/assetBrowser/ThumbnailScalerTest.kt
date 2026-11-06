package com.pafoid.skate.editor.ui.windows.assetBrowser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

class ThumbnailScalerTest {

    @Test
    fun Fit_KeepsAspectAndNeverUpscales() {
        assertEquals(128 to 64, ThumbnailScaler.fit(4096, 2048))
        assertEquals(32 to 128, ThumbnailScaler.fit(512, 2048))
        assertEquals(64 to 16, ThumbnailScaler.fit(64, 16))
        assertEquals(128 to 1, ThumbnailScaler.fit(8192, 1))
    }

    @Test
    fun Downscale_AveragesEachBox() {
        // 2x2 RGBA: black, white / white, black -> one grey pixel
        val src = ByteBuffer.allocate(16)
        listOf(0, 255, 255, 0).forEachIndexed { i, v -> repeat(3) { c -> src.put(i * 4 + c, v.toByte()) }; src.put(i * 4 + 3, 255.toByte()) }
        val dst = ByteBuffer.allocate(4)

        ThumbnailScaler.downscale(src, 2, 2, dst, 1, 1)

        assertEquals(127, dst.get(0).toInt() and 0xFF)
        assertEquals(255, dst.get(3).toInt() and 0xFF)
    }

    @Test
    fun Downscale_SameSizeCopies() {
        val src = ByteBuffer.allocate(8).apply { for (i in 0 until 8) put(i, (i * 10).toByte()) }
        val dst = ByteBuffer.allocate(8)

        ThumbnailScaler.downscale(src, 2, 1, dst, 2, 1)

        assertEquals(src, dst)
    }
}
