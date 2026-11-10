package com.pafoid.skate.engine.physics3d.native

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** PHY-21: natives shared by concurrent processes are versioned, reused and never deleted. */
class NativeExtractorTest {

    @TempDir
    lateinit var root: File

    private val v1 = ByteArray(4096) { (it % 251).toByte() }
    private val v2 = ByteArray(4096) { (it % 241).toByte() }

    @Test
    fun NativeExtractor_DifferentContent_GoesToDifferentVersionFolders() {
        val a = NativeExtractor.install(root, "lib", "lib.dll", v1)
        val b = NativeExtractor.install(root, "lib", "lib.dll", v2)

        assertNotEquals(a.parentFile, b.parentFile)
        assertArrayEquals(v1, a.readBytes(), "installing another version must not touch the first")
        assertArrayEquals(v2, b.readBytes())
    }

    @Test
    fun NativeExtractor_SameContent_ReusesTheFileWithoutRewriting() {
        val first = NativeExtractor.install(root, "lib", "lib.dll", v1)
        val stamp = first.lastModified() - 10_000
        first.setLastModified(stamp)

        val second = NativeExtractor.install(root, "lib", "lib.dll", v1)

        assertEquals(first, second)
        assertEquals(stamp, second.lastModified())
    }

    @Test
    fun NativeExtractor_CorruptedCopy_IsReplaced() {
        val file = NativeExtractor.install(root, "lib", "lib.dll", v1)
        file.writeBytes(v1.copyOf(100)) // e.g. a copy truncated by a crash before PHY-21

        val again = NativeExtractor.install(root, "lib", "lib.dll", v1)

        assertEquals(file, again)
        assertArrayEquals(v1, again.readBytes())
    }

    @Test
    fun NativeExtractor_NeverDeletesOtherFiles_AndLeavesNoTempFiles() {
        val legacy = File(root, "lib.dll").apply { writeBytes(v2) } // flat PHY-18 layout, maybe held by an old editor
        val file = NativeExtractor.install(root, "lib", "lib.dll", v1)

        assertTrue(legacy.isFile)
        assertEquals(listOf("lib.dll"), file.parentFile.list()!!.toList())
    }

    @Test
    fun NativeExtractor_ConcurrentInstalls_AllGetTheCompleteFile() {
        val threads = 8
        val barrier = CyclicBarrier(threads)
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val results = (0 until threads).map {
                pool.submit<File> {
                    barrier.await()
                    NativeExtractor.install(root, "lib", "lib.dll", v1)
                }
            }.map { it.get(30, TimeUnit.SECONDS) }

            assertEquals(1, results.toSet().size)
            assertArrayEquals(v1, results.first().readBytes())
            assertEquals(listOf("lib.dll"), results.first().parentFile.list()!!.toList())
        } finally {
            pool.shutdownNow()
        }
    }
}
