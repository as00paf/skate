package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.data.SoundBuffer
import com.pafoid.skate.engine.audio.AudioEngine
import com.pafoid.skate.engine.audio.FakeAudioBackend
import com.pafoid.skate.engine.audio.WaterAudioConfig
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.AudioComponent
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.components.WaterComponent
import com.pafoid.skate.engine.events.WaterEntered
import com.pafoid.skate.engine.events.WaterExited
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.physics3d.IRigidBodyHandle
import io.mockk.every
import io.mockk.mockk
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.lwjgl.openal.AL10.AL_STOPPED
import java.io.File
import kotlin.io.path.createTempDirectory

/** AUD-01, AUD-15: a splash on PHY-16's WaterEntered and a looping ambience near water volumes, on a fake OpenAL device. */
class AudioWaterSoundsTest {

    private val dir = createTempDirectory("aud01").toFile()
    private val splashClip = File(dir, "splash.wav").apply { writeBytes(ByteArray(1)) }.path
    private val ambienceClip = File(dir, "ambience.wav").apply { writeBytes(ByteArray(1)) }.path
    private val otherAmbienceClip = File(dir, "river.wav").apply { writeBytes(ByteArray(1)) }.path
    private val al = FakeAudioBackend()
    private val logger = mockk<LoggerService>(relaxed = true)
    private val camera = Transform()
    private val cameraManager = mockk<CameraManager>(relaxed = true).also {
        every { it.camera } returns CameraComponent()
        every { it.transform } returns camera
    }
    private val assets = mockk<AssetsManager>(relaxed = true).also {
        every { it.getSound(any()) } answers {
            SoundBuffer(firstArg()).apply {
                bufferId = when (firstArg<String>()) {
                    splashClip -> 11
                    otherAmbienceClip -> 33
                    else -> 22
                }
            }
        }
    }
    private val events = EventSystem(logger)
    private val transforms = TransformSystem()
    private val scene = Scene("water").apply { isRunning = true }
    private val system = AudioSystem(AudioEngine(logger, al.backend), logger, assets, cameraManager, eventSystem = events).also {
        it.waterAudio = WaterAudioConfig(splashClips = listOf(splashClip), ambienceClip = ambienceClip)
        it.init(scene)
    }

    @AfterEach
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun frame() {
        scene.children.forEach { go -> go.getComponent<Transform>()?.let { transforms.updateTransform(it) } }
        transforms.updateTransform(camera)
        system.update(1f / 60f)
    }

    private fun pool(at: Vector3f = Vector3f(), sizeX: Float = 10f, sizeZ: Float = 6f): GameObject =
        GameObject("Pool").addComponent(Transform(at)).addComponent(WaterComponent(sizeX = sizeX, sizeZ = sizeZ))
            .also { scene.addChild(it) }

    private fun body(at: Vector3f, velocity: Vector3f? = null): GameObject {
        val go = GameObject("Crate").addComponent(Transform(at))
        if (velocity != null) go.addComponent(RigidBody3D(1f).apply {
            rawBody = object : IRigidBodyHandle by mockk(relaxed = true) { override var linearVelocity = Vector3f(velocity) }
        })
        scene.addChild(go)
        return go
    }

    private val splashes get() = al.sources.values.filter { it.buffer == 11 }
    private val ambiences get() = al.sources.values.filter { it.buffer == 22 }

    @Test
    fun WaterEntered_PlaysOneSplashAtTheBody() {
        val water = pool(at = Vector3f(0f, 0f, 100f)) // ambience out of reach
        val crate = body(Vector3f(3f, 0f, -2f))
        frame()
        assertTrue(splashes.isEmpty())

        events.publish(WaterEntered(water, crate))
        frame()
        val splash = splashes.single()
        assertTrue(splash.isPlaying)
        assertFalse(splash.looping)
        assertFalse(splash.relative)
        assertEquals(Vector3f(3f, 0f, -2f), splash.position)
        assertTrue(splash.gain > 0f)

        // A one-shot: released once it has played
        splash.state = AL_STOPPED
        frame()
        assertTrue(splashes.isEmpty())
    }

    @Test
    fun Splash_IsLouderForAHardImpact() {
        val water = pool(at = Vector3f(0f, 0f, 100f))
        events.publish(WaterEntered(water, body(Vector3f(2f, 0f, 0f), velocity = Vector3f(0f, -0.5f, 0f))))
        frame()
        val soft = splashes.single().gain
        events.publish(WaterEntered(water, body(Vector3f(-2f, 0f, 0f), velocity = Vector3f(0f, -9f, 0f))))
        frame()
        val hard = splashes.maxOf { it.gain }
        assertTrue(hard > soft * 2f, "soft $soft, hard $hard")
    }

    @Test
    fun Splash_HasACooldownPerBody_AndExitDoesNotSplash() {
        val water = pool(at = Vector3f(0f, 0f, 100f))
        val crate = body(Vector3f(1f, 0f, 0f))
        events.publish(WaterEntered(water, crate))
        events.publish(WaterExited(water, crate))
        events.publish(WaterEntered(water, crate)) // bobbing at the edge
        frame()
        assertEquals(1, splashes.size)
    }

    @Test
    fun Splash_IsCappedPerFrame() {
        val water = pool(at = Vector3f(0f, 0f, 100f))
        repeat(10) { events.publish(WaterEntered(water, body(Vector3f(it.toFloat(), 0f, 0f)))) }
        frame()
        assertEquals(WaterAudioConfig().maxSplashesPerFrame, splashes.size)
    }

    @Test
    fun EditMode_NoSplashAndNoAmbience_AndQueuedEventsAreDropped() {
        scene.isRunning = false
        val water = pool()
        events.publish(WaterEntered(water, body(Vector3f())))
        frame()
        assertTrue(al.sources.isEmpty())
        scene.isRunning = true
        frame()
        assertTrue(splashes.isEmpty(), "an edit-mode event must not splash when Play starts")
    }

    @Test
    fun Ambience_LoopsFromTheNearestEdge_FadesAndStopsOutOfReach() {
        pool(at = Vector3f(0f, 1f, 0f), sizeX = 10f, sizeZ = 6f)
        camera.translation.set(12f, 2f, 0f)
        frame()
        val ambience = ambiences.single()
        assertTrue(ambience.isPlaying && ambience.looping)
        assertEquals(Vector3f(5f, 1f, 0f), ambience.position)
        val near = ambience.gain

        camera.translation.set(25f, 2f, 0f)
        frame()
        assertTrue(ambience.gain < near && ambience.gain > 0f)
        assertEquals(1, ambiences.size, "the same loop keeps playing")

        camera.translation.set(60f, 2f, 0f)
        frame()
        assertTrue(ambiences.isEmpty())

        camera.translation.set(0f, 2f, 0f)
        frame()
        assertEquals(Vector3f(0f, 1f, 0f), ambiences.single().position)
    }

    @Test
    fun Ambience_StillPlaysWithAnAudioComponent_AndStopsWhenDisabled() {
        // AUD-15: an AudioComponent on the water plays its own sounds and no longer silences the water
        pool().addComponent(AudioComponent(splashClip, playOnAwake = false))
        frame()
        assertEquals(1, ambiences.size)

        val other = pool(at = Vector3f(5f, 0f, 0f))
        system.waterAudio = WaterAudioConfig(enabled = false, ambienceClip = ambienceClip)
        frame()
        assertTrue(ambiences.isEmpty())
        system.waterAudio = WaterAudioConfig(ambienceClip = ambienceClip)
        frame()
        assertEquals(2, ambiences.size)
        scene.removeChild(other)
        frame()
        assertEquals(1, ambiences.size, "a removed water releases its loop")
    }

    @Test
    fun Ambience_UsesTheWatersOwnClipAndVolume_AndFollowsEditsLive() {
        pool(at = Vector3f(0f, 0f, 0f))
        val custom = pool(at = Vector3f(0f, 0f, 0f))
        val water = custom.getComponent<WaterComponent>()!!
        water.ambienceClip = otherAmbienceClip
        water.ambienceVolume = 0.1f
        frame()
        val own = al.sources.values.single { it.buffer == 33 }
        val fallback = ambiences.single()
        assertTrue(own.isPlaying && own.looping)
        assertEquals(fallback.gain * 0.1f / WaterAudioConfig().ambienceVolume, own.gain, 1e-5f)

        // Volume: same loop, new gain on the next frame
        water.ambienceVolume = 0.4f
        frame()
        assertTrue(own.isPlaying)
        assertEquals(fallback.gain * 0.4f / WaterAudioConfig().ambienceVolume, own.gain, 1e-5f)

        // Clip: the loop restarts with the new clip; back to null falls back to the default
        water.ambienceClip = null
        frame()
        assertTrue(al.sources.values.none { it.buffer == 33 })
        assertEquals(2, ambiences.size)
        assertTrue(ambiences.all { it.isPlaying })
    }

    @Test
    fun Ambience_UsesTheWatersOwnReach() {
        val water = pool(at = Vector3f(0f, 1f, 0f), sizeX = 10f, sizeZ = 6f).getComponent<WaterComponent>()!!
        camera.translation.set(50f, 1f, 0f) // 45 m from the edge: beyond the default 30 m
        frame()
        assertTrue(ambiences.isEmpty())
        water.ambienceMaxDistance = 60f
        frame()
        assertEquals(1, ambiences.size)
    }

    @Test
    fun Splash_UsesTheEnteredWatersOwnClipsAndVolume() {
        val far = Vector3f(0f, 0f, 100f) // ambience out of reach
        val plain = pool(at = far)
        val custom = pool(at = far).also {
            it.getComponent<WaterComponent>()!!.apply {
                splashClips = listOf(otherAmbienceClip)
                splashVolume = 0.5f
            }
        }
        events.publish(WaterEntered(plain, body(Vector3f(1f, 0f, 0f))))
        events.publish(WaterEntered(custom, body(Vector3f(-1f, 0f, 0f))))
        frame()
        val fallback = splashes.single()
        val own = al.sources.values.single { it.buffer == 33 }
        assertTrue(fallback.isPlaying && own.isPlaying)
        assertEquals(fallback.gain * 0.5f, own.gain, 1e-5f)
    }

    @Test
    fun StopAndPause_ReleaseOrHoldTheWaterSounds() {
        val water = pool()
        events.publish(WaterEntered(water, body(Vector3f(1f, 0f, 0f))))
        frame()
        assertEquals(2, al.playing.size)

        scene.isPaused = true
        frame()
        assertTrue(al.playing.isEmpty())
        assertEquals(2, al.sources.size, "paused, not released")
        scene.isPaused = false
        frame()
        assertEquals(2, al.playing.size)

        scene.isRunning = false
        frame()
        assertTrue(al.sources.isEmpty())
    }
}
