package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.data.SoundBuffer
import com.pafoid.skate.engine.audio.AudioEngine
import com.pafoid.skate.engine.audio.BusId
import com.pafoid.skate.engine.audio.FakeAudioBackend
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.AudioComponent
import com.pafoid.skate.engine.ecs.components.AudioListener
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.physics3d.IRigidBodyHandle
import io.mockk.every
import io.mockk.mockk
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory

/** AUD-11: Doppler comes from physics motion only, and never applies to music. */
class AudioDopplerTest {

    private val dir = createTempDirectory("aud11").toFile()
    private val clip = File(dir, "engine.wav").apply { writeBytes(ByteArray(1)) }.path
    private val al = FakeAudioBackend()
    private val logger = mockk<LoggerService>(relaxed = true)
    private val camera = Transform()
    private val cameraManager = mockk<CameraManager>(relaxed = true).also {
        every { it.camera } returns CameraComponent()
        every { it.transform } returns camera
    }
    private val assets = mockk<AssetsManager>(relaxed = true).also {
        every { it.getSound(any()) } answers { SoundBuffer(firstArg()).apply { bufferId = 7 } }
    }
    private val transforms = TransformSystem()
    private val scene = Scene("doppler").apply { isRunning = true }
    private val system = AudioSystem(AudioEngine(logger, al.backend), logger, assets, cameraManager).also { it.init(scene) }

    @AfterEach
    fun tearDown() {
        dir.deleteRecursively()
    }

    /** A body moving at [velocity] (a stand-in for a live physics body). */
    private fun body(velocity: Vector3f) = RigidBody3D(1f).apply {
        rawBody = mockk<IRigidBodyHandle>(relaxed = true).also { every { it.linearVelocity } returns Vector3f(velocity) }
    }

    private fun source(name: String, at: Vector3f, bus: BusId = BusId.SFX, body: RigidBody3D? = null): GameObject {
        val go = GameObject(name).addComponent(Transform(at)).addComponent(AudioComponent(clip, is3D = true, loops = true, bus = bus))
        body?.let { go.addComponent(it) }
        scene.addChild(go)
        return go
    }

    private fun frame() {
        scene.children.forEach { go -> go.getComponent<Transform>()?.let { transforms.updateTransform(it) } }
        transforms.updateTransform(camera)
        system.update(1f / 60f)
    }

    private fun pitchOf(go: GameObject) = al.sources.values.single { it.position == go.getComponent<Transform>()!!.translation }.pitch

    @Test
    fun SfxOnAMovingPhysicsBodyStillDopplerShifts() {
        // 30 m/s straight at the listener at the origin: higher pitch.
        val car = source("car", Vector3f(0f, 0f, -20f), body = body(Vector3f(0f, 0f, 30f)))
        frame()
        frame()
        val expected = 343.3f / (343.3f - 30f)
        assertEquals(expected, pitchOf(car), 1e-3f)
        assertTrue(pitchOf(car) > 1.05f)
    }

    @Test
    fun MusicBusSourceOnAMovingBodyIsNeverShifted() {
        val radio = source("radio", Vector3f(0f, 0f, -20f), bus = BusId.MUSIC, body = body(Vector3f(0f, 0f, 30f)))
        frame()
        frame()
        assertEquals(1f, pitchOf(radio), 1e-6f)
    }

    @Test
    fun CameraGlideDoesNotShiftSfxEither() {
        val bell = source("bell", Vector3f(0f, 0f, -40f))
        frame()
        repeat(10) {
            camera.translation.add(0f, 0f, -2.5f) // 150 m/s toward the bell
            frame()
            assertEquals(1f, pitchOf(bell), 1e-6f)
        }
    }

    @Test
    fun ListenerOnAPhysicsBodyDopplerShifts() {
        scene.addChild(GameObject("rider").addComponent(Transform()).addComponent(AudioListener())
            .addComponent(body(Vector3f(0f, 0f, -30f))))
        val bell = source("bell", Vector3f(0f, 0f, -40f))
        frame()
        frame()
        assertTrue(pitchOf(bell) > 1.05f, "pitch ${pitchOf(bell)}")
    }
}
