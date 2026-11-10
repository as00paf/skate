package com.pafoid.skate.editor.assetimport.blender

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class BlenderImportQueueTest {
    private val io = ArrayDeque<() -> Unit>()
    private val converted = mutableListOf<String>()
    private val outcomes = mutableListOf<BlenderImportOutcome>()
    private var fail = false

    private val queue = BlenderImportQueue(
        io = { io.addLast(it) },
        main = { it() },
        convert = { blend ->
            converted += blend.nameWithoutExtension
            if (fail) error("blender crashed") else BlenderConversion.Converted(blend, File("staging"), BlenderReport(outputs = listOf("x.glb")), "h", "5.2.3")
        },
        apply = { c -> BlenderImportResult(c.blend, emptyList(), emptyList(), emptyList()) },
        onOutcome = { outcomes += it },
        maxJobs = 2,
    )

    private fun runOne() = io.removeFirst()()
    private fun f(name: String) = File("Assets/$name.blend")

    @Test
    fun SaveWhileImporting_QueuesExactlyOneFollowUp() {
        queue.request(f("cap"))
        queue.request(f("cap"))
        queue.request(f("cap"))
        assertEquals(1, io.size, "one Blender at a time per file")

        runOne()
        assertEquals(1, io.size, "the follow-up starts after the first finishes")
        runOne()

        assertEquals(listOf("cap", "cap"), converted)
        assertFalse(queue.busy)
    }

    @Test
    fun AtMostMaxJobs_TheRestWaitInOrder() {
        listOf("a", "b", "c", "d").forEach { queue.request(f(it)) }
        assertEquals(2, io.size)
        assertTrue(queue.isImporting(f("a")))

        repeat(4) { runOne() }

        assertEquals(listOf("a", "b", "c", "d"), converted)
        assertEquals(4, outcomes.count { it is BlenderImportOutcome.Imported })
    }

    @Test
    fun ConverterCrash_IsAFailedOutcome() {
        fail = true
        queue.request(f("cap"))
        runOne()

        val outcome = outcomes.single()
        assertTrue(outcome is BlenderImportOutcome.Failed)
        assertTrue("crashed" in (outcome as BlenderImportOutcome.Failed).reason)
        assertFalse(queue.busy)
    }
}
