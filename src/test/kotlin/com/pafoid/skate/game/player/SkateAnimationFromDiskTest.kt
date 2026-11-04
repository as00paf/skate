package com.pafoid.skate.game.player

import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.PrefabsGenerator
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.PlayerController
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.CharacterAnimationDriver
import com.pafoid.skate.engine.ecs.systems.GameObjectManager
import com.pafoid.skate.engine.ecs.systems.PhysicsSystem
import com.pafoid.skate.engine.ecs.systems.SystemManager
import com.pafoid.skate.engine.ecs.systems.TransformSystem
import com.pafoid.skate.engine.gameplay.CharacterInput
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.physics3d.bullet.BulletPhysics3D
import com.pafoid.skate.engine.physics3d.native.NativeLibraryLoader
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.game.prefabs.Skater
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * GPL-06 on the human's path: the default project is generated and saved, MainScene is decoded from disk and opened
 * with real Bullet physics, then played. The skater's ground probe reports the grindable ledge, and its behavior
 * (from the saved PlayerController) drives the skater controller (rebuilt on a humanoid test rig) into a grind.
 */
class SkateAnimationFromDiskTest {

    private val dir: File = kotlin.io.path.createTempDirectory("gpl06").toFile()
    private val debug = mockk<DebugRenderer>(relaxed = true)
    private val world = NativeLibraryLoader().loadNativeLibrary().let { BulletPhysics3D(debug) }

    @AfterEach
    fun cleanup() {
        world.destroy()
        runCatching { dir.deleteRecursively() }
    }

    private fun loadMainScene(physics: PhysicsSystem): Scene {
        val serializer = Serializer()
        val logger = LoggerService()
        val events = EventSystem()
        val systems = SystemManager()
        val assets = mockk<AssetsManager>(relaxed = true).also {
            every { it.loadModel(any()) } answers { `3dModel`(firstArg(), skeleton = Skeleton(Bone(0, "root"), 1)) }
            every { it.loadAnimationSync(any()) } answers { Animation("clip", emptyList(), 1f, firstArg()) }
            every { it.getTexture(any()) } answers { Texture(filePath = firstArg()) }
        }
        val sceneManager = SceneManager(assets, events, serializer, systems, logger)
        val engine = mockk<Engine>(relaxed = true).also {
            every { it.assetsManager } returns assets
            every { it.sceneManager } returns sceneManager
            every { it.logger } returns logger
        }
        val scenesDir = File(dir, "Scenes")
        PrefabsGenerator(engine).apply { projectAssetsDir = File(dir, "Assets").path + File.separator }.createDefaultScenes(scenesDir)
        sceneManager.closeAllScenes()
        listOf(GameObjectManager(), TransformSystem(), physics).forEach { systems.addSystem(it) }
        systems.start()
        val text = File(scenesDir, "${PrefabsGenerator.DEFAULT_SCENE}.scene").readText()
        return serializer.decodeFile<Scene?>(FileKind.SCENE, text)!!.also { sceneManager.openScenes(listOf(it)) }
    }

    @Test
    fun DefaultScene_FromDisk_LedgeIsGrindableUnderfootAndDrivesTheGrindState() {
        SkateGameplay.register()
        val physics = PhysicsSystem(debug, physics3d = world)
        val scene = loadMainScene(physics)
        val skater = scene.children.single { it.name == "Skater" }
        val ledge = scene.children.single { it.name.endsWith("_Ledge") }
        val controller = skater.getComponent<PlayerController>()!!
        scene.start()

        // On the floor at the spawn: nothing grindable underfoot.
        repeat(5) { physics.fixedUpdate(1f / 60f) }
        assertNull(controller.groundTag)

        // Feet just above the ledge top (0.5 m): the probe reports its tag.
        val top = ledge.getComponent<Transform>()!!.translation
        skater.getComponent<Transform>()!!.translation.set(top.x, 0.55f, top.z)
        physics.teleport(skater)
        physics.fixedUpdate(1f / 60f)
        assertEquals(PrefabsGenerator.GRINDABLE_TAG, controller.groundTag)

        // The saved skater's behavior and controller, driven with that probe result.
        val behavior = controller.behavior as SkateBehavior
        val loadedAnimator = skater.getComponent<Animator>()!!
        assertEquals(Skater.CONTROLLER_PATH, loadedAnimator.controllerPath)
        assertEquals(SkateControllerConfig().blendTimes, behavior.animationBlendTimes)
        val animator = SkateAnimationPipelineTest.animator()
        behavior.mounted = true
        var state = ""
        fun frame(input: CharacterInput) {
            state = CharacterAnimationDriver.drive(behavior, behavior.update(input, 1f / 60f), state, animator)
            animator.stateMachine!!.update(1f / 60f)
        }
        repeat(30) { frame(CharacterInput(horizontalSpeed = 4f)) }
        repeat(30) { frame(CharacterInput(horizontalSpeed = 4f, groundTag = controller.groundTag)) }

        assertEquals("GRIND", behavior.state)
        assertEquals("grind", animator.stateMachine!!.currentState)
        assertTrue(animator.parameters!!.getFloat("speed") > 3f)
    }
}
