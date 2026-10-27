package com.pafoid.skate.engine

import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EngineState
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.JobSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import com.pafoid.skate.engine.core.MainThread
import com.pafoid.skate.engine.core.Screen
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.events.Landing
import com.pafoid.skate.engine.render.renderer.Renderer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EngineLifecycleTest {

    @AfterEach
    fun cleanup() {
        MainThread.unbind()
        MainThread.strict = false
        MainThread.violationHandler = {}
    }

    @Test
    fun EngineLifecycle_DestroyAfterFailedStart_ReleasesEverythingAndCancelsJobs() {
        val engine = Engine() // never started: state is BOOTING
        val screen = mockk<Screen>(relaxed = true)
        engine.screens.add(screen)
        val job = engine.jobSystem.runAsync { kotlinx.coroutines.delay(60_000) }

        engine.destroy()

        verify(exactly = 1) { screen.destroy() }
        assertTrue(job.isCancelled)
        assertEquals(EngineState.DESTROYED, engine.engineState.get())
    }

    @Test
    fun EngineLifecycle_DestroyContinuesWhenOneStepFails() {
        val engine = Engine()
        val renderer = mockk<Renderer>(relaxed = true)
        every { renderer.destroy() } throws IllegalStateException("gl gone")
        engine.renderer = renderer
        val job = engine.jobSystem.runAsync { kotlinx.coroutines.delay(60_000) }

        engine.destroy()

        assertTrue(job.isCancelled)
        val error = engine.logger.logs.single { it.level == LogLevel.ERROR }
        assertTrue(error.message.contains("renderer"))
        assertNotNull(error.throwable)
    }

    @Test
    fun EngineLifecycle_DestroyIsIdempotent() {
        val engine = Engine()
        val screen = mockk<Screen>(relaxed = true)
        engine.screens.add(screen)

        engine.destroy()
        engine.destroy()

        verify(exactly = 1) { screen.destroy() }
    }

    @Test
    fun ErrorLogging_EventListenerExceptionIsLoggedWithThrowable() {
        val logger = LoggerService().apply { consoleSink = { _, _ -> } }
        val events = EventSystem(logger)
        val boom = IllegalArgumentException("bad listener")
        var secondCalled = false
        events.subscribe<Landing> { throw boom }
        events.subscribe<Landing> { secondCalled = true }

        events.publish(Landing(Vector3f(), 1f))

        val entry = logger.logs.single()
        assertEquals(LogLevel.ERROR, entry.level)
        assertTrue(entry.message.contains("Landing") && entry.message.contains("bad listener"))
        assertEquals(boom, entry.throwable)
        assertTrue(secondCalled)
    }

    @Test
    fun ErrorLogging_JobExceptionIsLoggedWithThrowable() {
        val logger = LoggerService().apply { consoleSink = { _, _ -> } }
        val jobs = JobSystem(logger = logger)
        val boom = IllegalStateException("job failed")

        jobs.runAsync { throw boom }.also { kotlinx.coroutines.runBlocking { it.join() } }

        val entry = logger.logs.single()
        assertEquals(LogLevel.ERROR, entry.level)
        assertEquals(boom, entry.throwable)
        jobs.destroy()
    }

    @Test
    fun ErrorLogging_LoggerWritesStackTraceToConsoleSink() {
        val out = mutableListOf<String>()
        val logger = LoggerService().apply { consoleSink = { text, _ -> out.add(text) } }

        logger.logError("failed", RuntimeException("cause"))

        assertTrue(out.single().contains("failed: cause") && out.single().contains("RuntimeException"))
    }

    @Test
    fun ErrorLogging_OneTimeListenerFiresOnce() {
        val events = EventSystem()
        var count = 0
        events.subscribeOnce<Landing> { count++ }
        repeat(3) { events.publish(Landing(Vector3f(), 1f)) }
        assertEquals(1, count)
    }

    @Test
    fun ThreadSafety_ConcurrentSubscribeAndPublishDoesNotThrow() {
        val events = EventSystem()
        val failure = AtomicReference<Throwable?>(null)
        val threads = (1..4).map { n ->
            Thread {
                try {
                    repeat(500) {
                        events.subscribe<Landing> { }
                        events.publish(Landing(Vector3f(), 1f))
                        if (n == 1 && it % 100 == 0) events.unsubscribe<Landing>()
                    }
                } catch (t: Throwable) {
                    failure.set(t)
                }
            }.also { it.start() }
        }
        threads.forEach { it.join() }
        assertEquals(null, failure.get())
    }

    @Test
    fun ThreadSafety_LoggerKeepsBoundedBufferUnderConcurrentWrites() {
        val logger = LoggerService()
        (1..4).map { Thread { repeat(1500) { logger.log("m") } }.also { it.start() } }.forEach { it.join() }
        assertTrue(logger.logs.size <= 2000)
    }

    @Test
    fun MainThread_SceneMutationOffMainThreadIsReported() {
        val scene = Scene("s")
        val reports = mutableListOf<String>()
        MainThread.violationHandler = { reports.add(it) }
        MainThread.bind()

        Thread { scene.addChild(GameObject("child")) }.also { it.start() }.join()
        Thread { GameObject("detached").addChild(GameObject("c")) }.also { it.start() }.join()
        scene.addChild(GameObject("onMain"))

        assertEquals(1, reports.size)
        assertTrue(reports.single().contains("addChild"))
    }

    @Test
    fun MainThread_StrictModeThrows() {
        val scene = Scene("s")
        MainThread.strict = true
        MainThread.bind()
        val failure = AtomicReference<Throwable?>(null)

        Thread {
            try { scene.addChild(GameObject("x")) } catch (t: Throwable) { failure.set(t) }
        }.also { it.start() }.join()

        assertTrue(failure.get() is IllegalStateException)
        assertFalse(scene.children.any { it.name == "x" })
    }
}
