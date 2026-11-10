package com.pafoid.skate.editor.assetstore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

class StoreZipAndOrmTest {
    @TempDir
    lateinit var tmp: File

    private fun zip(vararg entries: Pair<String, ByteArray>): File {
        val f = File(tmp, "a.zip")
        ZipOutputStream(f.outputStream()).use { z -> entries.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
        return f
    }

    @Test
    fun StoreZip_ExtractsSafeEntries() {
        val out = File(tmp, "out")
        val files = StoreZip.extract(zip("a.png" to byteArrayOf(1), "dir/b.jpg" to byteArrayOf(2, 3)), out)
        assertEquals(listOf("a.png", "dir/b.jpg"), files)
        assertEquals(2L, File(out, "dir/b.jpg").length())
    }

    @Test
    fun StoreZip_RejectsZipSlip() {
        val out = File(tmp, "out")
        assertThrows<IOException> { StoreZip.extract(zip("../../evil.png" to byteArrayOf(1)), out) }
        assertFalse(File(tmp, "evil.png").exists())
        assertThrows<IOException> { StoreZip.extract(zip("C:/evil.png" to byteArrayOf(1)), out) }
    }

    @Test
    fun StoreZip_RejectsTooManyEntriesOrBytes() {
        val out = File(tmp, "out")
        assertThrows<IOException> { StoreZip.extract(zip("a" to byteArrayOf(1), "b" to byteArrayOf(1)), out, maxEntries = 1) }
        assertThrows<IOException> { StoreZip.extract(zip("big.bin" to ByteArray(100)), File(tmp, "out2"), maxTotalBytes = 10) }
        assertFalse(File(tmp, "out2/big.bin").exists())
    }

    @Test
    fun OrmPacker_RejectsMismatchedSizes() {
        fun img(name: String, size: Int) = File(tmp, name).also { ImageIO.write(BufferedImage(size, size, BufferedImage.TYPE_INT_RGB), "png", it) }
        assertThrows<IOException> { OrmPacker.pack(img("r.png", 4), img("m.png", 8), null, File(tmp, "orm.png")) }
    }
}
