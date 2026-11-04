package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.PrefabsGenerator
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
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.AudioComponent
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory

/** AUD-10: the default MainScene's streamed MP3 music through generate, save, load from disk and Play. */
class MainSceneMusicTest {

    private val dir = createTempDirectory("aud10").toFile()
    private val assetsDir = File(dir, "Assets")
    private val logger = mockk<LoggerService>(relaxed = true)
    private val al = FakeAudioBackend()
    private val assetsManager = mockk<AssetsManager>(relaxed = true).also {
        every { it.loadModel(any()) } returns `3dModel`(mesh = listOf(), skeleton = Skeleton(Bone(0, "root"), 1))
        every { it.loadAnimationSync(any()) } returns Animation("idle", emptyList(), 1f, "idle.fbx")
        every { it.getTexture(any()) } returns Texture()
    }
    private val cameraManager = mockk<CameraManager>(relaxed = true).also {
        every { it.camera } returns CameraComponent()
        every { it.transform } returns Transform()
    }
    private val prefabs = PrefabsGenerator(mockk<Engine>(relaxed = true).also { every { it.assetsManager } returns assetsManager })
        .apply { projectAssetsDir = assetsDir.path + File.separator }

    @AfterEach
    fun tearDown() {
        dir.deleteRecursively()
    }

    /** Same objects as PrefabsGenerator.createDefaultScenes' MainScene, saved to disk and loaded back. */
    private fun loadMainSceneFromDisk(): Scene {
        val scene = Scene(PrefabsGenerator.DEFAULT_SCENE)
        prefabs.spawnDefaultsSync().forEach { scene.addChild(it) }
        val serializer = Serializer()
        val file = File(dir, "${scene.name}.scene").apply { writeText(serializer.encodeFile(FileKind.SCENE, scene)) }
        return serializer.decodeFile<Scene>(FileKind.SCENE, file.readText())
    }

    private fun play(scene: Scene, frames: Int): AudioSystem {
        val system = AudioSystem(AudioEngine(logger, al.backend), logger, assetsManager, cameraManager)
        system.init(scene)
        scene.isRunning = true
        repeat(frames) { system.update(1f / 60f) }
        return system
    }

    @Test
    fun MainScene_MusicIsALoopingNonPositionalMusicBusSourcePointingAtTheProjectCopy() {
        val music = loadMainSceneFromDisk().children.single { it.name == "Music" }.getComponent<AudioComponent>()!!
        assertEquals(assetsDir.path + File.separator + Assets.Bundled.MUSIC, music.soundFilePath)
        assertTrue(music.soundFilePath.endsWith("aquarela do brasil.mp3"))
        assertFalse(music.is3D)
        assertTrue(music.loops && music.playOnAwake)
        assertEquals(BusId.MUSIC, music.bus)
    }

    @Test
    fun MainScene_MissingMusicLogsOneWarningAndTheSceneKeepsRunning() {
        val scene = loadMainSceneFromDisk()
        val system = play(scene, frames = 5)
        // Stop and Play again: still a single warning.
        scene.isRunning = false
        system.update(1f / 60f)
        scene.isRunning = true
        repeat(3) { system.update(1f / 60f) }

        assertTrue(al.sources.isEmpty())
        verify(exactly = 1) { logger.log(match { it.contains("not found") && it.contains("aquarela do brasil.mp3") }, LogLevel.WARN, any()) }
        verify(exactly = 0) { logger.log(any(), LogLevel.ERROR, any()) }
    }

    @Test
    fun MainScene_PresentMusicStreamsLoopingOnTheMusicBus() {
        val scene = loadMainSceneFromDisk()
        val track = File(scene.children.single { it.name == "Music" }.getComponent<AudioComponent>()!!.soundFilePath)
        track.parentFile.mkdirs()
        File(javaClass.getResource("/audio/tone-stereo-44k.mp3")!!.toURI()).copyTo(track)

        val system = play(scene, frames = 1)
        val source = al.playing.single()
        assertTrue(source.relative)
        assertEquals(0.5f, source.gain, 1e-5f)
        // Streamed: no static buffer, OpenAL looping off (the player loops by re-queueing).
        assertEquals(0, source.buffer)
        assertFalse(source.looping)
        assertTrue(al.uploads.isNotEmpty() && al.uploads.all { it.stereo && it.sampleRate == 44100 })

        // Play well past the 0.25 s track: it keeps streaming.
        repeat(20) {
            al.playQueued(source.id)
            system.update(1f / 60f)
        }
        assertTrue(source.isPlaying)
        assertTrue(al.uploads.sumOf { it.samples } / 2f / 44100 > 1f)
        verify(exactly = 0) { logger.log(match { it.startsWith("AudioSystem") }, LogLevel.WARN, any()) }
        verify(exactly = 0) { logger.log(any(), LogLevel.ERROR, any()) }
    }
}
