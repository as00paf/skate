package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.PrefabsGenerator
import com.pafoid.skate.engine.assets.data.SoundBuffer
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.assets.loaders.SoundLoader
import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.audio.AudioEngine
import com.pafoid.skate.engine.audio.FakeAudioBackend
import com.pafoid.skate.engine.audio.SpatialAudio
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.AudioComponent
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.game.samples.FeatureShowcase
import io.mockk.every
import io.mockk.mockk
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileNotFoundException
import kotlin.io.path.createTempDirectory

/** AUD-09: the showcase audio demo through generate, save, load from disk and Play, on a fake OpenAL device. */
class AudioSystemShowcaseTest {

    private val dir = createTempDirectory("aud09").toFile()
    private val logger = mockk<LoggerService>(relaxed = true)
    private val al = FakeAudioBackend()
    private val camera = Transform()
    private val cameraManager = mockk<CameraManager>(relaxed = true).also {
        every { it.camera } returns CameraComponent()
        every { it.transform } returns camera
    }
    private val assetsManager = mockk<AssetsManager>(relaxed = true).also {
        every { it.loadModel(any()) } returns `3dModel`(mesh = listOf(), skeleton = Skeleton(Bone(0, "root"), 1))
        every { it.loadAnimationSync(any()) } returns Animation("walking", emptyList(), 1f, "walking.fbx")
        every { it.getTexture(any()) } returns Texture()
        // Like the real loader: the file must exist at the path stored in the scene.
        every { it.getSound(any()) } answers {
            val path = firstArg<String>()
            if (!File(path).isFile) throw FileNotFoundException(path)
            SoundBuffer(path).apply { bufferId = 42 }
        }
    }
    private val transforms = TransformSystem()

    @AfterEach
    fun tearDown() {
        dir.deleteRecursively()
    }

    /** What a new project does: write the assets, generate and save the scene, then load it back from disk. */
    private fun loadShowcaseFromDisk(): Scene {
        val engine = mockk<Engine>(relaxed = true).also { every { it.assetsManager } returns assetsManager }
        val assetsDir = File(dir, "Assets")
        val prefabs = PrefabsGenerator(engine).apply { projectAssetsDir = assetsDir.path + File.separator }
        FeatureShowcase.writeAssets(assetsDir)
        val serializer = Serializer()
        val file = File(dir, "${FeatureShowcase.SCENE_NAME}.scene")
        file.writeText(serializer.encodeFile(FileKind.SCENE, FeatureShowcase(prefabs).populate(Scene(FeatureShowcase.SCENE_NAME))))
        return serializer.decodeFile<Scene>(FileKind.SCENE, file.readText())
    }

    private fun Scene.named(name: String): GameObject = children.single { it.name == name }

    private fun frame(system: AudioSystem, scene: Scene) {
        scene.children.forEach { go -> go.getComponent<Transform>()?.let { transforms.updateTransform(it) } }
        transforms.updateTransform(camera)
        system.update(1f / 60f)
    }

    private fun gainOf(ac: AudioComponent) = al.playing.single { it.relative == !ac.is3D }.gain

    @Test
    fun Showcase_Audio_PlaysInPlayAttenuatesWithDistanceAndStops() {
        val scene = loadShowcaseFromDisk()
        val emitterGo = scene.named("[Audio] Emitter3D")
        val emitter = emitterGo.getComponent<AudioComponent>()!!
        val ambient = scene.named("[Audio] Ambient2D").getComponent<AudioComponent>()!!
        val system = AudioSystem(AudioEngine(logger, al.backend), logger, assetsManager, cameraManager)
        system.init(scene)
        camera.translation.set(FeatureShowcase.overviewTarget().position)

        // Edit mode: silent.
        frame(system, scene)
        assertTrue(al.playing.isEmpty())
        assertTrue(al.distanceModelDisabled)

        // Play: both looping sources start; ambient at its own volume, emitter attenuated from the overview camera.
        scene.isRunning = true
        frame(system, scene)
        assertEquals(2, al.playing.size)
        assertTrue(al.playing.all { it.looping && it.buffer == 42 })
        assertTrue(emitter.isPlaying && ambient.isPlaying)
        assertEquals(0.1f, gainOf(ambient), 1e-5f)
        val emitterPos = emitterGo.getComponent<Transform>()!!.worldMatrix.getTranslation(Vector3f())
        val far = gainOf(emitter)
        val expectedFar = 0.6f * SpatialAudio.attenuation(
            emitter.attenuationModel, emitterPos.distance(camera.translation), emitter.minDistance, emitter.maxDistance, emitter.rolloff
        )
        assertEquals(expectedFar, far, 1e-5f)
        assertEquals(emitterPos, al.playing.single { !it.relative }.position)

        // Walking up to the sphere: louder, full volume within minDistance; the listener follows the camera.
        camera.translation.set(emitterPos).add(0f, 0f, 0.5f)
        frame(system, scene)
        assertTrue(gainOf(emitter) > far)
        assertEquals(0.6f, gainOf(emitter), 1e-5f)
        assertEquals(camera.translation, al.listenerPosition)

        // Stop: every voice released.
        scene.isRunning = false
        frame(system, scene)
        assertTrue(al.sources.isEmpty())
        assertFalse(emitter.isPlaying || ambient.isPlaying)

        // Play again: playOnAwake fires again.
        scene.isRunning = true
        frame(system, scene)
        assertEquals(2, al.playing.size)
    }

    @Test
    fun Showcase_Audio_ToneOnDiskIsDecodableMono16() {
        val ambient = loadShowcaseFromDisk().named("[Audio] Ambient2D").getComponent<AudioComponent>()!!
        val wav = SoundLoader(logger).decodeWav(File(ambient.soundFilePath))
        assertEquals(1, wav.channels)
        assertEquals(16, wav.bitsPerSample)
        assertEquals(22050, wav.sampleRate)
        assertEquals(1f, wav.durationInSeconds, 1e-3f)
    }

    @Test
    fun AudioSystem_ExplicitPlayWorksInEditModeAndPlayOnAwakeWaitsForPlay() {
        val scene = Scene("s")
        val preview = AudioComponent(File(dir, "a.wav").path, is3D = false, playOnAwake = false)
        val awake = AudioComponent(File(dir, "a.wav").path, is3D = false)
        File(dir, "a.wav").writeBytes(ByteArray(1))
        scene.addChild(GameObject("preview").addComponent(preview))
        scene.addChild(GameObject("awake").addComponent(awake))
        val system = AudioSystem(AudioEngine(logger, al.backend), logger, assetsManager, cameraManager)
        system.init(scene)

        preview.play()
        frame(system, scene)

        assertTrue(preview.isPlaying)
        assertFalse(awake.isPlaying)
        assertEquals(1, al.playing.size)
    }

    @Test
    fun AudioComponent_PlayOnAwakeDefaultsToTrue() {
        assertTrue(AudioComponent().playOnAwake)
    }
}
