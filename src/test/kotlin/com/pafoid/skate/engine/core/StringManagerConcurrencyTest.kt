package com.pafoid.skate.engine.core

import com.pafoid.skate.engine.assets.Assets
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.management.ManagementFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** GUI-11: reloads on one thread while others look strings up; lookups never lock, allocate or see a half table. */
class StringManagerConcurrencyTest {

    private fun memory(label: String, vararg files: Pair<String, String>) =
        StringSource.memory(label, files.associate { it.first to it.second.toByteArray(Charsets.UTF_8) })

    private val game = memory(
        "pack",
        "hud.properties" to "title=Paused\nscore=Score: %s\nitems.one=%d item\nitems.other=%d items\n",
        "hud_fr.properties" to "title=Pause\nscore=Score : %s\nitems.one=%d objet\nitems.other=%d objets\n",
    )

    @Test
    fun ConcurrentReloadAndLookup_StressTest() {
        val sm = StringManager(mockk(relaxed = true), logSource = "game")
        sm.loadStrings(Assets.Strings.EDITOR_STRINGS)
        sm.addStrings(game)
        val editorRedo = setOf("Redo", "Rétablir")

        val stop = AtomicBoolean(false)
        val failure = AtomicReference<String?>(null)
        val readers = (1..4).map {
            Thread {
                try {
                    while (!stop.get()) {
                        val title = sm.getString("title")
                        if (title != "Paused" && title != "Pause") failure.compareAndSet(null, "title=$title")
                        val redo = sm.getString("menu.edit.redo")
                        if (redo !in editorRedo) failure.compareAndSet(null, "menu.edit.redo=$redo")
                        if (!sm.hasString("title")) failure.compareAndSet(null, "hasString(title) false")
                        val score = sm.getString("score", 7)
                        if (score != "Score: 7" && score != "Score : 7") failure.compareAndSet(null, "score=$score")
                        val items = sm.getQuantityString("items", 2)
                        if (items != "2 items" && items != "2 objets") failure.compareAndSet(null, "items=$items")
                    }
                } catch (e: Throwable) {
                    failure.compareAndSet(null, e.toString())
                }
            }.apply { start() }
        }
        // Writer: what the editor's IO thread and the game's language button do, over and over.
        repeat(60) { i ->
            sm.setLocale(if (i % 2 == 0) "fr" else "en")
            if (i % 5 == 0) sm.addStrings(game)
            if (i % 7 == 0) sm.loadStrings(Assets.Strings.EDITOR_STRINGS)
        }
        stop.set(true)
        readers.forEach { it.join(10_000) }
        assertEquals(null, failure.get())
    }

    @Test
    fun Lookup_DoesNotWaitForAReloadInProgress() {
        val sm = StringManager(mockk(relaxed = true)).apply { addStrings(game) }
        val done = CountDownLatch(1)
        // Writers serialize on the manager's monitor; hold it as a long reload would.
        synchronized(sm) {
            Thread {
                sm.getString("title"); sm.hasString("title"); sm.getString("score", 1); sm.getQuantityString("items", 1)
                done.countDown()
            }.start()
            assertTrue(done.await(5, TimeUnit.SECONDS), "a lookup blocked on the reload lock")
        }
    }

    /**
     * Measured in a fresh JVM: other tests mock StringManager with mockk, whose inline instrumentation rewrites the
     * class for the whole test JVM and adds its own allocations to every call.
     */
    @Test
    fun Lookup_HitPathAllocatesNothing() {
        val java = java.io.File(System.getProperty("java.home"), "bin/java").path
        val process = ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), StringLookupAllocationProbe::class.java.name)
            .redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText().trim()
        assertTrue(process.waitFor(60, TimeUnit.SECONDS))
        val allocated = output.lines().last().toLongOrNull()
        if (allocated == -1L) return // no per-thread allocation counter on this JVM
        assertTrue(allocated != null && allocated < 64 * 1024, "300k lookups allocated: $output")
    }
}

/** Prints the bytes allocated by 300k string lookups (hits, a cached miss, hasString) after warm-up; -1 if unmeasurable. */
object StringLookupAllocationProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        val bean = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
        if (bean == null || !bean.isThreadAllocatedMemorySupported) { println(-1); return }
        val logger = LoggerService().apply { consoleSink = { _, _ -> } }
        val sm = StringManager(logger).apply {
            addStrings(StringSource.memory("probe", mapOf("hud.properties" to "title=Paused\n".toByteArray())))
        }
        val id = Thread.currentThread().id
        var sink = 0
        repeat(50_000) { sink += sm.getString("title").length + sm.getString("missing.key").length; if (sm.hasString("title")) sink++ }
        val before = bean.getThreadAllocatedBytes(id)
        repeat(100_000) { sink += sm.getString("title").length + sm.getString("missing.key").length; if (sm.hasString("title")) sink++ }
        val allocated = bean.getThreadAllocatedBytes(id) - before
        if (sink == 0) println("unexpected")
        println(allocated)
    }
}
