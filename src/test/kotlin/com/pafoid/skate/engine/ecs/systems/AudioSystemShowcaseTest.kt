package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.PrefabsGenerator
import com.pafoid.skate.engine.assets.data.SoundBuffer
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.audio.AudioEngine
import com.pafoid.skate.engine.audio.BusId
import com.pafoid.skate.engine.audio.FakeAudioBackend
import com.pafoid.skate.engine.audio.SpatialAudio
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.AudioComponent
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.game.samples.FeatureShowcase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileNotFoundException
import kotlin.io.path.createTempDirectory

/**
 * AUD-09/AUD-10: the showcase audio demo (the bundled MP3 on a positional emitter) through generate, save, load from
 * disk and Play, on a fake OpenAL device.
 */
class AudioSystemShowcaseTest {

    private val dir = createTempDirectory("aud10").toFile()
    private val assetsDir = File(dir, "Assets")
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

    /** The music file a new project gets from the bundled-asset table (what EngineAssetCopier copies). */
    private fun copyBundledMusic() {
        val (dest, source) = Assets.Bundled.bundledAssets.single { it.first == Assets.Bundled.MUSIC }
        File(source).copyTo(File(assetsDir, dest))
    }

    /** What a new project does: generate and save the showcase, then load it back from disk. */
    private fun loadShowcaseFromDisk(): Scene {
        val engine = mockk<Engine>(relaxed = true).also { every { it.assetsManager } returns assetsManager }
        val prefabs = PrefabsGenerator(engine).apply { projectAssetsDir = assetsDir.path + File.separator }
        val serializer = Serializer()
        val file = File(dir, "${FeatureShowcase.SCENE_NAME}.scene")
        file.writeText(serializer.encodeFile(FileKind.SCENE, FeatureShowcase(prefabs).populate(Scene(FeatureShowcase.SCENE_NAME))))
        return serializer.decodeFile<Scene>(FileKind.SCENE, file.readText())
    }

    private fun Scene.named(name: String): GameObject = children.single { it.name == name }

    private fun system(scene: Scene) =
        AudioSystem(AudioEngine(logger, al.backend), logger, assetsManager, cameraManager).also { it.init(scene) }

    private fun frame(system: AudioSystem, scene: Scene) {
        scene.children.forEach { go -> go.getComponent<Transform>()?.let { transforms.updateTransform(it) } }
        transforms.updateTransform(camera)
        system.update(1f / 60f)
    }

    @Test
    fun Showcase_Audio_TheOnlySourceIsThePositionalBundledMp3() {
        val sources = loadShowcaseFromDisk().children.filter { it.getComponent<AudioComponent>() != null }
        assertEquals(listOf("[Audio] Emitter3D"), sources.map { it.name })
        val emitter = sources.single().getComponent<AudioComponent>()!!
        assertEquals(assetsDir.path + File.separator + Assets.Bundled.MUSIC, emitter.soundFilePath)
        assertTrue(emitter.is3D && emitter.loops && emitter.playOnAwake)
        assertEquals(BusId.MUSIC, emitter.bus)
        assertEquals(25f, emitter.maxDistance)
    }

    @Test
    fun Showcase_Audio_Mp3StreamsInPlayAttenuatesPansAndStops() {
        copyBundledMusic()
        val scene = loadShowcaseFromDisk()
        val emitterGo = scene.named("[Audio] Emitter3D")
        val emitter = emitterGo.getComponent<AudioComponent>()!!
        val system = system(scene)
        camera.translation.set(FeatureShowcase.overviewTarget().position)

        // Edit mode: silent.
        frame(system, scene)
        assertTrue(al.playing.isEmpty())

        // Play: one streamed, positional source, attenuated from the overview camera.
        scene.isRunning = true
        frame(system, scene)
        val source = al.playing.single()
        assertFalse(source.relative)
        assertEquals(0, source.buffer)
        // Downmixed to mono: OpenAL only pans mono sources.
        assertTrue(al.uploads.isNotEmpty() && al.uploads.none { it.stereo } && al.uploads.all { it.sampleRate == 44100 })
        val emitterPos = emitterGo.getComponent<Transform>()!!.worldMatrix.getTranslation(Vector3f())
        assertEquals(emitterPos, source.position)
        val far = source.gain
        val expectedFar = 0.6f * SpatialAudio.attenuation(
            emitter.attenuationModel, emitterPos.distance(camera.translation), emitter.minDistance, emitter.maxDistance, emitter.rolloff
        )
        assertEquals(expectedFar, far, 1e-5f)

        // Next to the sphere: full volume; the listener follows the camera. Keep streaming for ~3 s.
        camera.translation.set(emitterPos).add(0f, 0f, 0.5f)
        repeat(10) {
            al.playQueued(source.id)
            frame(system, scene)
        }
        assertEquals(0.6f, source.gain, 1e-5f)
        assertTrue(source.gain > far)
        assertEquals(camera.translation, al.listenerPosition)
        assertTrue(source.isPlaying)
        assertTrue(al.uploads.sumOf { it.samples } / 44100f > 3f)

        // Stop releases the voice; Play starts it again.
        scene.isRunning = false
        frame(system, scene)
        assertTrue(al.sources.isEmpty())
        assertFalse(emitter.isPlaying)
        scene.isRunning = true
        frame(system, scene)
        assertEquals(1, al.playing.size)
        verify(exactly = 0) { logger.log(match { it.startsWith("AudioSystem") }, LogLevel.WARN, any()) }
        verify(exactly = 0) { logger.log(any(), LogLevel.ERROR, any()) }
    }

    /** GUI-09: the pause menu holds the song where it is; Resume continues the same voice, Stop still releases it. */
    @Test
    fun Showcase_Audio_PauseHoldsTheSongAndResumeContinuesIt() {
        copyBundledMusic()
        val scene = loadShowcaseFromDisk()
        val emitter = scene.named("[Audio] Emitter3D").getComponent<AudioComponent>()!!
        val system = system(scene)
        scene.isRunning = true
        frame(system, scene)
        val source = al.playing.single()

        scene.isPaused = true
        frame(system, scene)
        assertFalse(source.isPlaying, "still playing while paused")
        assertTrue(source.id in al.sources, "the voice must be held, not released")
        val uploaded = al.uploads.size
        repeat(30) { frame(system, scene) }
        assertEquals(uploaded, al.uploads.size, "the stream kept decoding while paused")

        scene.isPaused = false
        frame(system, scene)
        assertTrue(source.isPlaying, "Resume must continue the same voice")
        assertEquals(listOf(source), al.playing)

        // A sound paused by gameplay stays paused across the pause menu.
        emitter.pause()
        frame(system, scene)
        scene.isPaused = true
        frame(system, scene)
        scene.isPaused = false
        frame(system, scene)
        assertFalse(source.isPlaying)

        // Stop while the pause menu is open releases everything.
        emitter.play()
        frame(system, scene)
        scene.isPaused = true
        frame(system, scene)
        scene.isRunning = false
        frame(system, scene)
        assertTrue(al.sources.isEmpty())
    }

    /** ECS-13: switching scenes while paused releases the held voice instead of resuming it in the new scene. */
    @Test
    fun Showcase_Audio_SceneSwitchWhilePausedReleasesHeldVoices() {
        copyBundledMusic()
        val scene = loadShowcaseFromDisk()
        val system = system(scene)
        scene.isRunning = true
        frame(system, scene)
        assertEquals(1, al.playing.size)
        scene.isPaused = true
        frame(system, scene)

        val other = Scene("Other").apply { isRunning = true }
        system.init(other)
        frame(system, other)

        assertTrue(al.playing.isEmpty(), "no voice may resume in the new scene")
        assertTrue(al.sources.isEmpty(), "the old scene's voices must be released")
    }

    /** AUD-11: clicking an area button glides the camera fast; the song must keep its pitch. */
    @Test
    fun Showcase_Audio_FastCameraGlideKeepsTheSongAtPitchOne() {
        copyBundledMusic()
        val scene = loadShowcaseFromDisk()
        val emitterPos = scene.named("[Audio] Emitter3D").getComponent<Transform>()!!.translation
        val system = system(scene)
        camera.translation.set(FeatureShowcase.overviewTarget().position)
        scene.isRunning = true
        frame(system, scene)
        val source = al.playing.single()

        // ~150 m/s straight at the emitter, then away, like a focus glide or a cut.
        val toward = Vector3f(emitterPos).sub(camera.translation).normalize(2.5f)
        repeat(20) {
            camera.translation.add(toward)
            frame(system, scene)
            assertEquals(1f, source.pitch, 1e-6f, "pitch while gliding in, frame $it")
        }
        camera.translation.set(FeatureShowcase.overviewTarget().position)
        frame(system, scene)
        assertEquals(1f, source.pitch, 1e-6f)
        // OpenAL's own Doppler is off, so the velocities it receives cannot shift the pitch either.
        assertEquals(0f, al.openAlDopplerFactor)
    }

    @Test
    fun Showcase_Audio_MissingMp3LogsOneWarningAndTheSceneKeepsRunning() {
        val scene = loadShowcaseFromDisk()
        val system = system(scene)
        scene.isRunning = true
        repeat(5) { frame(system, scene) }
        scene.isRunning = false
        frame(system, scene)
        scene.isRunning = true
        repeat(3) { frame(system, scene) }

        assertTrue(al.sources.isEmpty())
        verify(exactly = 1) { logger.log(match { it.contains("not found") && it.contains("aquarela do brasil.mp3") }, LogLevel.WARN, any()) }
        verify(exactly = 0) { logger.log(any(), LogLevel.ERROR, any()) }
    }

    @Test
    fun AudioSystem_ExplicitPlayWorksInEditModeAndPlayOnAwakeWaitsForPlay() {
        val scene = Scene("s")
        File(dir, "a.wav").writeBytes(ByteArray(1))
        val preview = AudioComponent(File(dir, "a.wav").path, is3D = false, playOnAwake = false)
        val awake = AudioComponent(File(dir, "a.wav").path, is3D = false)
        scene.addChild(GameObject("preview").addComponent(preview))
        scene.addChild(GameObject("awake").addComponent(awake))
        val system = system(scene)

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
