package com.pafoid.skate.editor.loading

import com.pafoid.skate.editor.events.LoadingEvent
import com.pafoid.skate.engine.core.EventSystem
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** EDT-56: steps run in order on the thread they ask for, and the loading screen hears about it. */
class LoadingRunnerTest {
    /** Queues work like the editor does: [frame] runs the main-thread queue, [drainBackground] the worker. */
    private class QueuedExecutor : StepExecutor {
        val backgroundQueue = ArrayDeque<() -> Unit>()
        val mainQueue = ArrayDeque<() -> Unit>()
        var onBackground = false

        override fun background(block: () -> Unit) {
            backgroundQueue.addLast(block)
        }

        override fun main(block: () -> Unit) {
            mainQueue.addLast(block)
        }

        fun drainBackground() {
            while (backgroundQueue.isNotEmpty()) {
                onBackground = true
                backgroundQueue.removeFirst()()
                onBackground = false
            }
        }

        fun frame() {
            repeat(mainQueue.size) { mainQueue.removeFirst()() }
        }

        fun runAll() {
            while (backgroundQueue.isNotEmpty() || mainQueue.isNotEmpty()) {
                drainBackground()
                frame()
            }
        }
    }

    private val events = EventSystem(mockk(relaxed = true))
    private val received = mutableListOf<LoadingEvent>()
    private val executor = QueuedExecutor()
    private val runner = LoadingRunner(events, executor)

    init {
        events.subscribe<LoadingEvent.Started> { received += it }
        events.subscribe<LoadingEvent.Progressed> { received += it }
        events.subscribe<LoadingEvent.Finished> { received += it }
    }

    @Test
    fun Run_RunsStepsInOrderOnTheirThreads() {
        val log = mutableListOf<String>()
        var done = false
        val operation = LoadingOperation.Builder("title", "P")
            .background("read", weight = 3f) { log += "read:" + (if (executor.onBackground) "bg" else "main") }
            .main("open") { log += "open:" + (if (executor.onBackground) "bg" else "main") }
            .build()

        assertTrue(runner.run(operation, onDone = { done = true }))
        // Nothing runs on the caller's frame: the screen gets to draw first.
        assertTrue(log.isEmpty())
        executor.runAll()

        assertEquals(listOf("read:bg", "open:main"), log)
        assertTrue(done)
        assertFalse(runner.isRunning)
        assertEquals(
            listOf(
                LoadingEvent.Started("title", "P"),
                LoadingEvent.Progressed(0f, "read"),
                LoadingEvent.Progressed(0.75f, "open"),
                LoadingEvent.Finished,
            ),
            received,
        )
    }

    @Test
    fun Run_PassesValuesBetweenSteps() {
        var read: String? = null
        var opened: String? = null
        val operation = LoadingOperation.Builder("t", "")
            .background("read") { read = "scene" }
            .main("open") { opened = read }
            .build()
        runner.run(operation)
        executor.runAll()
        assertEquals("scene", opened)
    }

    @Test
    fun Run_AFailingStepSkipsTheRestAndReportsOnTheMainThread() {
        var reached = false
        var failure: Throwable? = null
        var done = false
        val operation = LoadingOperation.Builder("t", "")
            .background("read") { error("disk gone") }
            .main("open") { reached = true }
            .build()
        runner.run(operation, onDone = { done = true }, onFailed = { failure = it })

        executor.drainBackground()
        assertNull(failure, "the failure waits for the main thread")
        executor.frame()

        assertEquals("disk gone", failure?.message)
        assertFalse(reached)
        assertFalse(done)
        assertFalse(runner.isRunning)
        assertEquals(LoadingEvent.Finished, received.last())
    }

    @Test
    fun Run_OneOperationAtATime() {
        val first = LoadingOperation.Builder("a", "").background("x") {}.build()
        assertTrue(runner.run(first))
        assertFalse(runner.run(LoadingOperation.Builder("b", "").build()))
        assertNull(runner.track("c"))
        executor.runAll()
        assertTrue(runner.run(LoadingOperation.Builder("d", "").build()))
    }

    @Test
    fun Run_WithNoStepsFinishesAtOnce() {
        var done = false
        runner.run(LoadingOperation.Builder("t", "").build(), onDone = { done = true })
        assertTrue(done)
        assertEquals(listOf(LoadingEvent.Started("t", ""), LoadingEvent.Finished), received)
    }

    @Test
    fun Track_ReportsProgressUntilFinished() {
        val tracker = runner.track("export", "P")
        assertNotNull(tracker)
        tracker!!.progress(0.4f, "packing", "a.png")
        tracker.finish()
        tracker.finish()
        tracker.progress(0.9f, "late")

        assertEquals(
            listOf(LoadingEvent.Started("export", "P"), LoadingEvent.Progressed(0.4f, "packing", "a.png"), LoadingEvent.Finished),
            received,
        )
        assertFalse(runner.isRunning)
    }
}
