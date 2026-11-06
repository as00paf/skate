package com.pafoid.skate.engine.render.ui

import com.pafoid.skate.engine.assets.data.Texture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.management.ManagementFactory

/** VK-06: recording the same UI frame again allocates nothing (pooled batches, reused scissor rects, scratch arrays). */
class UiDrawListAllocationTest {
    private val atlas = Texture(64, 64, 1, 4)
    private val panel = Texture(32, 32, 1, 4)
    private val insets = NineSliceInsets(8, 8, 8, 8)

    private fun record(list: UiDrawList) = UiDrawListAllocationProbe.record(list, atlas, panel, insets)
    @Test
    fun SameFrameAgain_ReusesBatchesAndScissors() {
        val list = UiDrawList()
        record(list)
        val batches = list.batches.toList()
        val scissors = batches.map { it.scissor }
        record(list)
        assertEquals(batches.size, list.batches.size)
        batches.indices.forEach { i ->
            assertSame(batches[i], list.batches[i], "batch $i was reallocated")
            assertSame(scissors[i], list.batches[i].scissor, "scissor of batch $i was reallocated")
        }
    }

    /**
     * Measured in a fresh JVM: other tests mock classes this path touches (e.g. Texture) with mockk, whose inline
     * instrumentation rewrites them for the whole test JVM and allocates on every call.
     */
    @Test
    fun SteadyFrames_AllocateNothing() {
        val javaBin = java.io.File(System.getProperty("java.home"), "bin/java").path
        val process = ProcessBuilder(javaBin, "-cp", System.getProperty("java.class.path"), UiDrawListAllocationProbe::class.java.name)
            .redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText().trim()
        assertTrue(process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS))
        val allocated = output.lines().last().toLongOrNull()
        if (allocated == -1L) return // no per-thread allocation counter on this JVM
        assertTrue(allocated != null && allocated < 16 * 1024, "1000 UI frames allocated: $output")
    }

    @Test
    fun ChangedScissor_IsNotConfusedWithTheCachedOne() {
        val list = UiDrawList()
        list.pushScissor(0, 0, 100, 100)
        list.drawQuad(10f, 10f, 10f, 10f)
        list.popScissor()
        val first = list.batches.single().scissor
        list.clear()
        list.pushScissor(0, 0, 50, 100)
        list.drawQuad(10f, 10f, 10f, 10f)
        list.popScissor()
        assertEquals(UiRect(0, 0, 50, 100), list.batches.single().scissor)
        assertEquals(UiRect(0, 0, 100, 100), first, "an earlier frame's rect must not change")
    }
}

/** Prints the bytes 1000 steady UI frames allocate after warm-up; -1 when the JVM cannot count them. */
object UiDrawListAllocationProbe {
    /** A frame shaped like the showcase: panels, nested clipped list rows, glyphs from an atlas. */
    fun record(list: UiDrawList, atlas: Texture, panel: Texture, insets: NineSliceInsets) {
        list.clear()
        list.drawNineSlice(10f, 10f, 300f, 200f, panel, insets)
        list.pushScissor(20, 20, 280, 180)
        for (row in 0 until 8) {
            list.drawQuad(20f, 20f + row * 30f, 280f, 28f, 0.2f, 0.3f, 0.5f, 0.9f)
            list.pushScissor(30, 20 + row * 30, 200, 28)
            for (c in 0 until 6) list.drawSdfGlyph(atlas, 30f + c * 12f, 22f + row * 30f, 10f, 20f, 0f, 0f, 0.1f, 0.1f)
            list.popScissor()
        }
        list.popScissor()
        list.drawQuad(400f, 10f, 100f, 40f)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val bean = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
        if (bean == null || !bean.isThreadAllocatedMemorySupported) { println(-1); return }
        val atlas = Texture(64, 64, 1, 4)
        val panel = Texture(32, 32, 1, 4)
        val insets = NineSliceInsets(8, 8, 8, 8)
        val list = UiDrawList()
        repeat(2_000) { record(list, atlas, panel, insets) } // warm up (JIT, pools)
        val id = Thread.currentThread().id
        val before = bean.getThreadAllocatedBytes(id)
        repeat(1_000) { record(list, atlas, panel, insets) }
        println(bean.getThreadAllocatedBytes(id) - before)
    }
}
