package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.PrefabsGenerator
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.attachComponent
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.CompoundHullCollider3D
import com.pafoid.skate.engine.ecs.components.CustomCollider3D
import com.pafoid.skate.engine.ecs.components.MeshCollider3D
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.physics3d.CompoundAndMeshColliderTest
import com.pafoid.skate.engine.physics3d.bullet.BulletPhysics3D
import com.pafoid.skate.engine.physics3d.native.NativeLibraryLoader
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.game.prefabs.PrefabCatalog
import com.pafoid.skate.game.prefabs.PrefabType
import com.pafoid.skate.game.samples.FeatureShowcase
import io.mockk.every
import io.mockk.mockk
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * PHY-13 on the human's path: scenes generated and saved like a new project, decoded from disk, opened through
 * SceneManager with the physics system, then played.
 */
class PhysicsCollidersFromDiskTest {

    private val dir: File = kotlin.io.path.createTempDirectory("phy13").toFile()
    private val debug = mockk<DebugRenderer>(relaxed = true)
    private val serializer = Serializer()
    private val logger = LoggerService()
    private val events = EventSystem()
    private val systems = SystemManager()
    private val assets = mockk<AssetsManager>(relaxed = true).also {
        every { it.loadModel(any()) } answers { `3dModel`(firstArg(), skeleton = Skeleton(Bone(0, "root"), 1)) }
        every { it.loadAnimationSync(any()) } answers { Animation("clip", emptyList(), 1f, firstArg()) }
        every { it.getTexture(any()) } answers { Texture(filePath = firstArg()) }
    }
    private val sceneManager = SceneManager(assets, events, serializer, systems, logger)
    private lateinit var world: BulletPhysics3D
    private lateinit var physics: PhysicsSystem
    private val engine = mockk<Engine>(relaxed = true).also {
        every { it.assetsManager } returns assets
        every { it.sceneManager } returns sceneManager
        every { it.logger } returns logger
    }

    @BeforeEach
    fun setUp() {
        NativeLibraryLoader().loadNativeLibrary()
        world = BulletPhysics3D(debug)
        physics = PhysicsSystem(debug, physics3d = world)
    }

    @AfterEach
    fun cleanup() {
        world.destroy()
        runCatching { dir.deleteRecursively() }
    }

    private fun open(scene: Scene): Scene {
        listOf(GameObjectManager(), TransformSystem(), physics).forEach { systems.addSystem(it) }
        systems.start()
        sceneManager.openScenes(listOf(scene))
        return scene
    }

    private fun play(scene: Scene, seconds: Float) {
        scene.start()
        repeat((seconds * 60).toInt()) { physics.fixedUpdate(1f / 60f) }
    }

    private fun assertEveryBodyRegistered(scene: Scene) = scene.children.forEach { go ->
        go.getComponent<RigidBody3D>()?.let { assertNotNull(it.rawBody, "${go.name} has no body") }
    }

    @Test
    fun showcase_MeshAndHullBanks_LoadFromDiskAndCarryBoxesOnTheirDecks() {
        val scenesDir = File(dir, "Scenes")
        PrefabsGenerator(engine).apply { projectAssetsDir = File(dir, "Assets").path + File.separator }
            .createDefaultScenes(scenesDir)
        sceneManager.closeAllScenes()
        val text = File(scenesDir, "${FeatureShowcase.SCENE_NAME}.scene").readText()
        val scene = open(serializer.decodeFile<Scene?>(FileKind.SCENE, text)!!)

        val meshBank = scene.children.single { it.name == "[Physics] Triangle mesh bank" }
        val hullBank = scene.children.single { it.name == "[Physics] Compound hull bank" }
        assertNotNull(meshBank.getComponent<MeshCollider3D>())
        assertEquals(1, hullBank.components.count { it is CompoundHullCollider3D })
        assertEveryBodyRegistered(scene)
        listOf(meshBank, hullBank).forEach { assertEquals(0f, it.getComponent<RigidBody3D>()!!.rawBody!!.mass, it.name) }

        play(scene, 1f)

        // A ray straight down on each deck stops on the bank, not on the floor (top at y=0) below it
        val deckTop = PrefabCatalog.shape(PrefabType.BANK)!!.hulls.flatten().maxOf { it.y }
        listOf(meshBank, hullBank).forEach { bank ->
            val p = bank.getComponent<Transform>()!!.translation
            val hit = world.raycast(Vector3f(p.x, 5f, p.z - 0.75f), Vector3f(p.x, -1f, p.z - 0.75f))
            assertEquals(bank, hit?.gameObject, bank.name)
            assertEquals(deckTop, hit!!.point.y, 0.05f, bank.name)
        }
    }

    @Test
    fun bowlAndHalfPipe_SavedAndReloaded_CollideLikeOneCustomColliderPerHull() {
        val generator = PrefabsGenerator(engine)
        val park = Scene("Park").apply {
            addChild(generator.spawn(PrefabType.BOWL, Vector3f(0f, 0f, 0f)))
            addChild(generator.spawn(PrefabType.HALF_PIPE, Vector3f(30f, 0f, 0f)))
        }
        val file = File(dir, "Park.scene").apply { writeText(serializer.encodeFile(FileKind.SCENE, park)) }
        val scene = open(serializer.decodeFile<Scene?>(FileKind.SCENE, file.readText())!!)
        play(scene, 0.5f)

        assertEquals(2, physics.worldBodyCount)
        listOf(PrefabType.BOWL to 0f, PrefabType.HALF_PIPE to 30f).forEach { (type, x) ->
            val hulls = PrefabCatalog.shape(type)!!.hulls
            val reference = BulletPhysics3D(debug)
            reference.add(GameObject("Reference").apply {
                addComponent(Transform(Vector3f(x, 0f, 0f)))
                addComponent(RigidBody3D(0f, bodyType = BodyType.Static))
                hulls.forEach { attachComponent(CustomCollider3D(it)) }
            })
            val footprint = hulls.flatten().map { Vector3f(it).add(x, 0f, 0f) }
            CompoundAndMeshColliderTest.assertSameHeights(reference, world, footprint, type)
            reference.destroy()
        }
    }
}
