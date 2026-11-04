package com.pafoid.skate.game.samples

import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.PrefabsGenerator
import com.pafoid.skate.engine.assets.data.SoundBuffer
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.audio.AudioEngine
import com.pafoid.skate.engine.audio.Mixer
import com.pafoid.skate.engine.audio.VoiceManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.AudioComponent
import com.pafoid.skate.engine.ecs.components.CameraFocusComponent
import com.pafoid.skate.engine.ecs.components.JointComponent
import com.pafoid.skate.engine.ecs.components.ParticleEmitter
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.components.VehicleComponent
import com.pafoid.skate.engine.ecs.systems.AnimationSystem
import com.pafoid.skate.engine.ecs.systems.AudioSystem
import com.pafoid.skate.engine.ecs.systems.CameraControlSystem
import com.pafoid.skate.engine.ecs.systems.CameraManager
import com.pafoid.skate.engine.ecs.systems.FlyInput
import com.pafoid.skate.engine.ecs.systems.GameObjectManager
import com.pafoid.skate.engine.ecs.systems.ParticleSystem
import com.pafoid.skate.engine.ecs.systems.PhysicsFrameSystem
import com.pafoid.skate.engine.ecs.systems.PhysicsSystem
import com.pafoid.skate.engine.ecs.systems.SystemManager
import com.pafoid.skate.engine.ecs.systems.TransformSystem
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.physics3d.bullet.BulletPhysics3D
import com.pafoid.skate.engine.physics3d.native.NativeLibraryLoader
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.engine.ui.UIButtonComponent
import com.pafoid.skate.engine.ui.UICanvasComponent
import com.pafoid.skate.engine.ui.UIElementComponent
import com.pafoid.skate.engine.ui.UiInputFrame
import com.pafoid.skate.engine.ui.UiInputSystem
import com.pafoid.skate.engine.ui.UiLayoutSystem
import com.pafoid.skate.engine.ui.UiScreenSize
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.joml.Vector3f
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import java.io.File

/**
 * QA-13: the Feature Showcase played the way the human plays it. A new project is created with [ProjectManager]
 * (bundled assets copied, default scenes generated and saved), the showcase is decoded from disk by
 * `loadDefaultScenes`, switched to, and played with the real Play-time systems (Bullet physics, animation, particles,
 * UI, camera, audio). Each test then checks the visible effect of one demo area, so a demo that ships inert fails here.
 *
 * Fakes only replace what needs a GPU, a window or a sound card: model/texture loading ([AssetsManager]), the debug
 * line renderer and the OpenAL device ([AudioEngine]).
 *
 * Adding a demo area (GameEngine.md rule 8): add a `Showcase_<Area>_<Effect>` test below that reads the area's
 * objects by their `[Area] Name` and asserts what the human would see in Play. See docs/samples.md.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class FeatureShowcaseSmokeTest {

    // Not @TempDir: copied bundled assets can stay locked on Windows and fail JUnit's cleanup.
    private val dir: File = kotlin.io.path.createTempDirectory("showcase-smoke").toFile()
    private val serializer = Serializer()
    private val logger = LoggerService()
    private val events = EventSystem()
    private val systems = SystemManager()
    private val debug = mockk<DebugRenderer>(relaxed = true)
    private val assets = mockk<AssetsManager>(relaxed = true).also {
        every { it.loadModel(any()) } answers { `3dModel`(firstArg(), skeleton = Skeleton(Bone(0, "root"), 1)) }
        every { it.loadAnimationSync(any()) } answers { Animation("clip", emptyList(), 1f, firstArg()) }
        every { it.getTexture(any()) } answers { Texture(filePath = firstArg()) }
        every { it.getSound(any()) } answers { SoundBuffer(firstArg()).apply { bufferId = 1 } }
    }
    private val sceneManager = SceneManager(assets, events, serializer, systems, logger)

    // No sound card in CI: the device is faked, the mixer, voice manager and AudioSystem are real. Once AUD-09 lands,
    // switch to `AudioEngine(logger, FakeAudioBackend().backend)` and assert the fake sources play with the expected gains.
    private val voices = spyk(VoiceManager<Int>())
    private val audioEngine = mockk<AudioEngine>(relaxed = true).also {
        every { it.init() } returns true
        every { it.isInitialized } returns false
        every { it.mixer } returns Mixer()
        every { it.voices } returns voices
    }

    private var pointer = UiInputFrame()
    private lateinit var world: BulletPhysics3D
    private lateinit var scene: Scene
    private val start = mutableMapOf<String, Vector3f>()

    /** Samples taken every frame while playing, keyed by object name. */
    private val heights = mutableMapOf<String, MutableList<Float>>()
    private val rotations = mutableMapOf<String, MutableList<Vector3f>>()
    private val animationTimes = mutableListOf<Float>()
    private val particleCounts = mutableMapOf<String, Int>()

    private fun obj(name: String): GameObject = scene.children.singleOrNull { it.name == name }
        ?: error("'$name' missing from the showcase: ${scene.children.map { it.name }}")

    private fun area(label: String) = scene.children.filter { it.name.startsWith("[$label] ") && it.name != "[$label] Area" }

    // Demo objects are top-level (physics and animation only drive top-level objects), so local is world.
    private fun Transform.pos() = Vector3f(translation)

    @BeforeAll
    fun createProjectLoadShowcaseAndPlay() {
        NativeLibraryLoader().loadNativeLibrary()
        world = BulletPhysics3D(debug)
        val physics = PhysicsSystem(debug, events, physics3d = world)
        val cameraManager = CameraManager(events)
        listOf(
            GameObjectManager(), TransformSystem(), cameraManager,
            AudioSystem(audioEngine, logger, assets, cameraManager),
            physics, PhysicsFrameSystem(physics),
            AnimationSystem(), ParticleSystem(),
            CameraControlSystem { FlyInput() },
            UiLayoutSystem(screen = { UiScreenSize(1920, 1080) }),
            UiInputSystem { pointer },
        ).forEach { systems.addSystem(it) }
        systems.start()

        val engine = mockk<Engine>(relaxed = true)
        every { engine.assetsManager } returns assets
        every { engine.sceneManager } returns sceneManager
        every { engine.serializer } returns serializer
        every { engine.eventSystem } returns events
        every { engine.logger } returns logger
        every { engine.systemManager } returns systems
        val prefabs = PrefabsGenerator(engine)
        every { engine.prefabsGenerator } returns prefabs

        val project = ProjectManager(engine).createProject("Smoke", dir)
        assertTrue(project.isSuccess, project.exceptionOrNull()?.toString())
        assertTrue(File(dir, "Smoke/Scenes/${FeatureShowcase.SCENE_NAME}.scene").exists())
        scene = sceneManager.openScenes.single { it.name == FeatureShowcase.SCENE_NAME }
        sceneManager.switchScene(scene)

        scene.children.forEach { go -> go.getComponent<Transform>()?.let { start[go.name] = it.pos() } }
        repeat(PLAY_FRAMES) { i -> frame(); sample(i) }
    }

    @AfterAll
    fun cleanup() {
        runCatching { systems.destroy() }
        runCatching { world.destroy() }
        runCatching { dir.deleteRecursively() }
    }

    /** One editor frame while playing (Engine.updateRunningState with runtimePlaying). */
    private fun frame(input: UiInputFrame = UiInputFrame(pointer.pointerX, pointer.pointerY)) {
        pointer = input
        systems.update(DT)
        scene.isRunning = true
        scene.update(DT)
    }

    private fun sample(frame: Int) {
        scene.children.forEach { go ->
            val t = go.getComponent<Transform>() ?: return@forEach
            if (go.getComponent<RigidBody3D>() != null) {
                heights.getOrPut(go.name) { mutableListOf() }.add(t.pos().y)
                rotations.getOrPut(go.name) { mutableListOf() }.add(Vector3f(t.rotation))
            }
        }
        scene.children.firstNotNullOfOrNull { it.getComponent<Animator>() }?.let { animationTimes.add(it.currentTime) }
        if (frame == PARTICLE_SAMPLE_FRAME) {
            area("Particles").forEach { go -> particleCounts[go.name] = go.getComponent<ParticleEmitter>()!!.runtime.pool.count }
        }
    }

    @Test
    @Order(1)
    fun Showcase_Particles_EveryEmitterHasLiveParticlesAfterHalfASecond() {
        val emitters = area("Particles").filter { it.getComponent<ParticleEmitter>() != null }
        assertEquals(4, emitters.size, "particle emitters: ${emitters.map { it.name }}")
        val dead = emitters.filter { (particleCounts[it.name] ?: 0) == 0 }.map { it.name }
        assertTrue(dead.isEmpty(), "emitters with no live particles 0.5 s into Play: $dead ($particleCounts)")
        val looping = emitters.filter { it.name != "[Particles] Sparks" }
        assertTrue(looping.all { it.getComponent<ParticleEmitter>()!!.runtime.pool.count > 0 }, "looping emitters stopped")
    }

    @Test
    @Order(2)
    fun Showcase_Physics_DynamicBodiesFallAndSettleWithoutFallingThrough() {
        val bodies = area("Physics").filter { it.getComponent<RigidBody3D>()?.let { b -> b.bodyType == BodyType.Dynamic } == true }
        assertTrue(bodies.size >= 7, "dynamic bodies: ${bodies.map { it.name }}")
        bodies.forEach { go ->
            val body = go.getComponent<RigidBody3D>()!!
            assertNotNull(body.rawBody, "${go.name} has no physics body")
            val y = go.getComponent<Transform>()!!.pos().y
            assertTrue(y > FLOOR_TOP, "${go.name} fell through the floor: y=$y")
        }
        val stack = bodies.filter { it.name.startsWith("[Physics] Box_") }
        assertEquals(5, stack.size)
        val topStart = start.getValue("[Physics] Box_4").y
        val topNow = obj("[Physics] Box_4").getComponent<Transform>()!!.pos().y
        assertTrue(topNow < topStart - 0.05f, "the box stack never dropped: $topStart -> $topNow")
        stack.forEach { go ->
            val speed = go.getComponent<RigidBody3D>()!!.linearVelocity.length()
            assertTrue(speed < SETTLED_SPEED, "${go.name} has not settled: $speed m/s")
        }
        val ball = obj("[Physics] Ball")
        assertTrue(ball.getComponent<Transform>()!!.pos().y < start.getValue("[Physics] Ball").y - 3f, "ball never fell")
    }

    @Test
    @Order(3)
    fun Showcase_Joints_HingePendulumAndConeTwistLampSwing() {
        listOf("[Joints] HingePendulum", "[Joints] ConeTwistLamp").forEach { name ->
            assertNotNull(obj(name).getComponent<JointComponent>(), "$name lost its joint")
            val samples = rotations.getValue(name)
            val swing = samples.maxOf { a -> samples.maxOf { b -> a.distance(b) } }
            assertTrue(swing > 20f, "$name barely moved: ${swing} degrees of rotation range")
            // Hanging from a world pivot: it swings but never drops away.
            val drop = start.getValue(name).y - heights.getValue(name).min()
            assertTrue(drop < 2f, "$name fell off its joint: dropped $drop m")
        }
    }

    /** BUG-019 (PHY): Bullet's slider limits run the other way, so the [-2, 0] travel range pins the block at its start. */
    @Test
    @Order(3)
    fun Showcase_Joints_SliderBlockDropsToItsLowerLimit() {
        val slider = obj("[Joints] SliderBlock").getComponent<Transform>()!!.pos()
        val sliderStart = start.getValue("[Joints] SliderBlock")
        assertEquals(sliderStart.y - 2f, slider.y, 0.1f, "slider block should rest at its lower limit")
        assertEquals(sliderStart.x, slider.x, 0.05f, "slider left its rail")
        assertEquals(sliderStart.z, slider.z, 0.05f, "slider left its rail")
    }

    @Test
    @Order(4)
    fun Showcase_Vehicle_CarDropsAndRestsOnItsSuspension() {
        val car = obj("[Vehicle] Car")
        val vehicle = car.getComponent<VehicleComponent>()!!
        val y = car.getComponent<Transform>()!!.pos().y
        assertTrue(y < start.getValue("[Vehicle] Car").y, "car never dropped: y=$y")
        assertTrue(y > 0.3f, "car body sits on (or under) the floor instead of its wheels: y=$y")
        assertEquals(4, vehicle.wheelStates.size)
        assertTrue(vehicle.wheelStates.all { it.grounded }, "not every wheel touches the ground")
        val speed = car.getComponent<RigidBody3D>()!!.linearVelocity.length()
        assertTrue(speed < SETTLED_SPEED, "car still moving at $speed m/s")
        val last = heights.getValue("[Vehicle] Car").takeLast(30)
        assertTrue(last.max() - last.min() < 0.01f, "car still bouncing over the last half second: $last")
    }

    @Test
    @Order(5)
    fun Showcase_Animation_CharacterClipTimeAdvancesInPlay() {
        val animator = obj("[Animation] Character").getComponent<Animator>()!!
        assertNotNull(animator.currentAnimation, "clip not restored from disk (missing file under Assets?)")
        assertTrue(animator.isPlaying)
        assertEquals(PLAY_FRAMES, animationTimes.size)
        assertTrue(animationTimes.zipWithNext().all { (a, b) -> b > a }, "animation time stalled")
        assertEquals(PLAY_FRAMES * DT, animationTimes.last(), 0.05f)
    }

    /** Fails until AUD-09 merges: `playOnAwake` defaulted to false, so nothing started the showcase sources (shipped silent). */
    @Test
    @Order(6)
    fun Showcase_Audio_BothSourcesStartPlayingInPlay() {
        val sources = area("Audio").filter { it.getComponent<AudioComponent>() != null }
        assertEquals(listOf("[Audio] Emitter3D", "[Audio] Ambient2D"), sources.map { it.name })
        sources.forEach { go ->
            val clip = File(go.getComponent<AudioComponent>()!!.soundFilePath)
            assertTrue(clip.isFile && clip.length() > 44, "${go.name} clip missing on disk: $clip")
            // The real AudioSystem asks the voice manager for a voice when it starts a source.
            verify(atLeast = 1) { voices.request(go.uId, any(), any()) }
        }
    }

    @Test
    @Order(7)
    fun Showcase_UiLegend_EveryButtonIsBoundAndFliesTheCameraToItsArea() {
        val canvas = scene.children.single { it.getComponent<UICanvasComponent>() != null }
        val scale = canvas.getComponent<UICanvasComponent>()!!.scale
        val focus = obj("Camera").getComponent<CameraFocusComponent>()!!
        val camera = obj("Camera").getComponent<Transform>()!!
        val keys = listOf(FeatureShowcase.OVERVIEW) + FeatureShowcase.AREAS.map { it.key }
        // Visit each area, then come back to the overview, so every click moves the camera.
        (keys.drop(1) + FeatureShowcase.OVERVIEW).forEach { key ->
            val button = canvas.children.single { it.name == FeatureShowcase.focusButtonName(key) }
            assertNotNull(button.getComponent<UIButtonComponent>()!!.onClick, "${button.name} is not bound to a camera target")
            val target = focus.targets.single { it.buttonName == button.name }
            val rect = button.getComponent<UIElementComponent>()!!.rect
            val x = (rect.x + rect.w / 2f) * scale
            val y = (rect.y + rect.h / 2f) * scale
            frame(UiInputFrame(x, y))
            frame(UiInputFrame(x, y, pointerDown = true, pointerPressed = true))
            frame(UiInputFrame(x, y, pointerDown = false, pointerReleased = true))
            repeat(FOCUS_FRAMES) { frame() }
            assertTrue(camera.translation.distance(target.position) < 0.05f,
                "after clicking ${button.name} the camera is at ${camera.translation}, expected ${target.position}")
        }
    }

    private companion object {
        const val DT = 1f / 60f
        const val PLAY_FRAMES = 300 // 5 s
        const val PARTICLE_SAMPLE_FRAME = 30 // 0.5 s
        const val FOCUS_FRAMES = 240
        const val FLOOR_TOP = 0f
        const val SETTLED_SPEED = 0.05f
    }
}
