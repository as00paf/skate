package com.pafoid.skate.engine.assets

import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.JobSystem
import com.pafoid.skate.engine.events.AssetEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class AssetLoadServiceTest {
    private lateinit var jobs: JobSystem
    private lateinit var events: EventSystem
    private lateinit var service: AssetLoadService
    private val published = mutableListOf<String>()
    private val errors = mutableListOf<String>()

    @BeforeEach
    fun setup() {
        jobs = JobSystem()
        events = EventSystem()
        events.subscribe(AssetEvent.Loaded::class) { published += "loaded:${it.key}" }
        events.subscribe(AssetEvent.Failed::class) { published += "failed:${it.key}" }
        events.subscribe(AssetEvent.Unloaded::class) { published += "unloaded:${it.key}" }
        service = AssetLoadService(jobs, events) { errors += it }
    }

    @AfterEach
    fun teardown() = jobs.destroy()

    private fun pump(until: () -> Boolean) {
        val end = System.currentTimeMillis() + 5000
        while (!until() && System.currentTimeMillis() < end) { jobs.update(); Thread.sleep(2) }
        assertTrue(until(), "timed out")
    }

    @Test
    fun AssetLoad_DecodesOffMainThreadAndUploadsOnMain() {
        val decodeThread = AtomicInteger(0)
        var uploadOnMain = false
        val t = service.request("a", placeholder = "ph",
            decode = { if (jobs.isMainThread()) decodeThread.incrementAndGet(); 42 },
            upload = { uploadOnMain = jobs.isMainThread(); "asset$it" })
        assertEquals(AssetState.Loading, t.state)
        assertEquals("ph", t.get())
        pump { t.isReady }
        assertEquals("asset42", t.get())
        assertEquals(0, decodeThread.get())
        assertTrue(uploadOnMain)
        assertEquals(listOf("loaded:a"), published)
    }

    @Test
    fun AssetLoad_MainThreadIsNotBlockedByDecode() {
        val gate = CountDownLatch(1)
        val t = service.request("slow", decode = { gate.await(); 1 }, upload = { it })
        jobs.update()
        assertEquals(AssetState.Loading, t.state)
        gate.countDown()
        pump { t.isReady }
    }

    @Test
    fun AssetLoad_ConcurrentRequestsShareOneLoadAndRefCount() {
        val decodes = AtomicInteger()
        val a = service.request("k", decode = { decodes.incrementAndGet() }, upload = { it })
        val b = service.request("k", decode = { decodes.incrementAndGet() }, upload = { it })
        assertSame(a, b)
        assertEquals(2, a.references)
        pump { a.isReady }
        assertEquals(1, decodes.get())
    }

    @Test
    fun AssetLoad_ReleaseToZeroDisposesOnceAndPublishesUnloaded() {
        val disposed = mutableListOf<Int>()
        val a = service.request("k", decode = { 7 }, upload = { it }, dispose = { disposed += it })
        service.request("k", decode = { 7 }, upload = { it }, dispose = { disposed += it })
        pump { a.isReady }
        service.release(a)
        assertTrue(disposed.isEmpty())
        service.release(a)
        assertEquals(listOf(7), disposed)
        assertEquals(0, service.loadedCount)
        assertEquals("unloaded:k", published.last())
        service.release(a)
        assertEquals(1, disposed.size)
    }

    @Test
    fun AssetLoad_FailureKeepsPlaceholderPublishesEventAndLogsOnce() {
        val t = service.request<Int, String>("bad", placeholder = "ph", decode = { error("boom") }, upload = { "x" })
        pump { t.state is AssetState.Failed }
        assertEquals("ph", t.get())
        assertEquals("boom", (t.state as AssetState.Failed).reason)
        assertEquals(listOf("failed:bad"), published)
        assertEquals(1, errors.size)
    }

    @Test
    fun AssetLoad_ReleaseWhileLoadingDiscardsDecodedData() {
        val gate = CountDownLatch(1)
        val discarded = AtomicInteger()
        var uploaded = false
        val t = service.request("k", decode = { gate.await(); 5 }, upload = { uploaded = true; it }, discard = { discarded.incrementAndGet() })
        service.release(t)
        gate.countDown()
        Thread.sleep(100)
        repeat(5) { jobs.update() }
        assertFalse(uploaded)
        assertFalse(t.isReady)
        assertEquals(0, service.loadedCount)
    }

    @Test
    fun AssetLoad_RequestAfterUnloadStartsFreshLoad() {
        val a = service.request("k", decode = { 1 }, upload = { it })
        pump { a.isReady }
        service.release(a)
        val b = service.request("k", decode = { 2 }, upload = { it })
        assertNotSame(a, b)
        pump { b.isReady }
        assertEquals(2, b.get())
    }
}
