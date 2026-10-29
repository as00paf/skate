package com.pafoid.skate.engine.assets

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AssetReloaderTest {
    @TempDir
    lateinit var dir: File

    private fun touch(file: File, text: String, time: Long) {
        file.writeText(text)
        file.setLastModified(time)
    }

    @Test
    fun AssetReloader_ReloadsChangedFileOnceItIsStable() {
        val f = File(dir, "a.png").also { touch(it, "1", 1000) }
        val reloader = AssetReloader()
        var count = 0
        reloader.watch("a", f) { count++ }

        assertTrue(reloader.poll().isEmpty())
        touch(f, "22", 2000)
        assertTrue(reloader.poll().isEmpty(), "first sighting waits for stability")
        assertEquals(listOf("a"), reloader.poll())
        assertTrue(reloader.poll().isEmpty())
        assertEquals(1, count)
    }

    @Test
    fun AssetReloader_FileStillChangingIsNotReloaded() {
        val f = File(dir, "a.png").also { touch(it, "1", 1000) }
        val reloader = AssetReloader()
        var count = 0
        reloader.watch("a", f) { count++ }
        touch(f, "22", 2000); reloader.poll()
        touch(f, "333", 3000); reloader.poll()
        assertEquals(0, count)
        reloader.poll()
        assertEquals(1, count)
    }

    @Test
    fun AssetReloader_FailedReloadIsReportedAndRetriedOnNextChange() {
        val f = File(dir, "a.png").also { touch(it, "1", 1000) }
        val errors = mutableListOf<String>()
        val reloader = AssetReloader { key, _ -> errors += key }
        var fail = true
        var ok = 0
        reloader.watch("a", f) { if (fail) error("boom") else ok++ }
        touch(f, "22", 2000); reloader.poll(); reloader.poll()
        assertEquals(listOf("a"), errors)
        fail = false
        touch(f, "333", 3000); reloader.poll(); reloader.poll()
        assertEquals(1, ok)
    }

    @Test
    fun AssetReloader_UnwatchedAndDeletedFilesAreIgnored() {
        val f = File(dir, "a.png").also { touch(it, "1", 1000) }
        val reloader = AssetReloader()
        var count = 0
        reloader.watch("a", f) { count++ }
        f.delete()
        reloader.poll(); reloader.poll()
        reloader.unwatch("a")
        touch(f, "x", 5000); reloader.poll(); reloader.poll()
        assertEquals(0, count)
        assertTrue(reloader.watchedKeys.isEmpty())
    }
}
