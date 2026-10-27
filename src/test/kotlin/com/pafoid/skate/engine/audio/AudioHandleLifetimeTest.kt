package com.pafoid.skate.engine.audio

import com.pafoid.skate.engine.assets.data.SoundSource
import com.pafoid.skate.engine.assets.data.SourceApi
import com.pafoid.skate.engine.core.LoggerService
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AudioHandleLifetimeTest {

    private class FakeSourceApi : SourceApi {
        val calls = mutableListOf<String>()
        var nextId = 7
        override fun genSource() = nextId.also { calls += "gen" }
        override fun deleteSource(id: Int) { calls += "delete:$id" }
        override fun sourceInt(id: Int, param: Int, value: Int) { calls += "int" }
        override fun sourceFloat(id: Int, param: Int, value: Float) { calls += "float" }
        override fun source3f(id: Int, param: Int, x: Float, y: Float, z: Float) { calls += "3f" }
        override fun getSourceInt(id: Int, param: Int) = 0
        override fun play(id: Int) { calls += "play" }
        override fun pause(id: Int) { calls += "pause" }
        override fun stop(id: Int) { calls += "stop" }
        override fun genBuffer() = 1
        override fun deleteBuffer(id: Int) {}
        override fun bufferData(id: Int, stereo: Boolean, pcm: ShortArray, sampleRate: Int) {}
        override fun queueBuffer(source: Int, buffer: Int) {}
        override fun unqueueBuffer(source: Int) = 1
    }

    private class FakeAlc(var contextResult: Long = 20L) : AlcApi {
        var current = 0L
        var contextsDestroyed = 0
        var devicesClosed = 0
        var capabilitiesFail = false
        override fun openDevice() = 10L
        override fun createContext(device: Long) = contextResult
        override fun makeCurrent(context: Long) { current = context }
        override fun currentContext() = current
        override fun createCapabilities(device: Long) { if (capabilitiesFail) error("no caps") }
        override fun destroyContext(context: Long) { contextsDestroyed++ }
        override fun closeDevice(device: Long) { devicesClosed++ }
    }

    private val logger = mockk<LoggerService>(relaxed = true)

    @Test
    fun SoundSource_CloseDeletesSourceOnceAndIsIdempotent() {
        val api = FakeSourceApi()
        val source = SoundSource(false, true, api)
        source.close()
        source.close()
        assertEquals(1, api.calls.count { it == "delete:7" })
        assertTrue(source.isClosed)
    }

    @Test
    fun SoundSource_CallsAfterCloseAreNoOps() {
        val api = FakeSourceApi()
        val source = SoundSource(false, true, api)
        source.close()
        val before = api.calls.size
        source.setVolume(1f)
        source.setPosition(1f, 2f, 3f)
        source.setBuffer(3)
        source.play()
        source.pause()
        source.stop()
        assertFalse(source.isPlaying())
        assertEquals(before, api.calls.size)
    }

    @Test
    fun SoundSource_UseBlockClosesEvenWhenBodyThrows() {
        val api = FakeSourceApi()
        runCatching { SoundSource(false, true, api).use { error("boom") } }
        assertTrue(api.calls.contains("delete:7"))
    }

    @Test
    fun AudioEngine_CloseReleasesContextAndDeviceOnceAndIsIdempotent() {
        val alc = FakeAlc()
        val engine = AudioEngine(logger, alc)
        assertTrue(engine.init())
        engine.close()
        engine.close()
        assertEquals(1, alc.contextsDestroyed)
        assertEquals(1, alc.devicesClosed)
        assertEquals(0L, alc.current)
        assertFalse(engine.isInitialized)
        assertEquals(0L, engine.getDevice())
    }

    @Test
    fun AudioEngine_FailedContextCreationDoesNotLeakDevice() {
        val alc = FakeAlc(contextResult = 0L)
        val engine = AudioEngine(logger, alc)
        assertFalse(engine.init())
        assertEquals(1, alc.devicesClosed)
        engine.close()
        assertEquals(1, alc.devicesClosed)
    }

    @Test
    fun AudioEngine_FailureAfterContextCreationReleasesBoth() {
        val alc = FakeAlc().apply { capabilitiesFail = true }
        val engine = AudioEngine(logger, alc)
        assertFalse(engine.init())
        assertEquals(1, alc.contextsDestroyed)
        assertEquals(1, alc.devicesClosed)
    }

    @Test
    fun AudioEngine_UseBlockClosesAndClearsVoices() {
        val alc = FakeAlc()
        val engine = AudioEngine(logger, alc)
        engine.use {
            it.init()
            it.voices.request(1, 1, 1f)
        }
        assertEquals(1, alc.devicesClosed)
        assertEquals(0, engine.voices.activeCount)
    }

    @Test
    fun AudioEngine_ReinitAfterCloseWorks() {
        val alc = FakeAlc()
        val engine = AudioEngine(logger, alc)
        engine.init(); engine.close()
        assertTrue(engine.init())
        engine.close()
        assertEquals(2, alc.devicesClosed)
    }
}
