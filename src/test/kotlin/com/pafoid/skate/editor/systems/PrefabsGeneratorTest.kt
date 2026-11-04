package com.pafoid.skate.editor.systems

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.PrefabsGenerator
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.game.prefabs.Skater
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.ecs.ComponentQuery
import com.pafoid.skate.engine.ecs.EcsChanges
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.BoxCollider3D
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.DirectionalLightComponent
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.ScenePhysicsComponent
import com.pafoid.skate.engine.ecs.components.SpriteRenderer
import com.pafoid.skate.engine.ui.UIButtonComponent
import com.pafoid.skate.engine.ui.UICanvasComponent
import com.pafoid.skate.engine.ui.UITextComponent
import com.pafoid.skate.engine.ecs.components.CustomCollider3D
import com.pafoid.skate.engine.ecs.components.PointLightComponent
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.SpotLightComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.game.prefabs.MaterialType
import com.pafoid.skate.game.prefabs.PrefabCatalog
import com.pafoid.skate.game.prefabs.PrefabType
import org.joml.Vector3f
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class PrefabsGeneratorTest {

    private val assetsManager = mockk<AssetsManager>(relaxed = true)
    private val sceneManager = mockk<SceneManager>(relaxed = true)
    private val engine = mockk<Engine>().also {
        every { it.assetsManager } returns assetsManager
        every { it.sceneManager } returns sceneManager
        every { it.logger } returns LoggerService()
    }

    init {
        val model = `3dModel`(mesh = listOf(), skeleton = Skeleton(Bone(0, "root"), 1))
        every { assetsManager.loadModel(any()) } returns model
        every { assetsManager.getModel(any()) } returns model
        every { assetsManager.loadAnimationSync(any()) } returns Animation("idle", emptyList(), 1.0f, "anim/idle")
        every { assetsManager.getTexture(any()) } returns Texture()
        every { assetsManager.getBundledTexture(any()) } returns Texture()
        every { sceneManager.createNewScene(any(), any()) } answers { Scene(firstArg()) }
    }

    private fun GameObject.descendants(): List<GameObject> = listOf(this) + children.flatMap { it.descendants() }

    @Test
    fun `spawnDefaultsSync returns the skate scene objects without touching the scene`() {
        val spawned = PrefabsGenerator(engine).spawnDefaultsSync()

        assertEquals(listOf("Camera", "Skateboard", "Skater", "Floor", "Concrete_Ledge", "HUD"), spawned.map { it.name })
        assertNotNull(spawned[0].getComponent<CameraComponent>())
        assertTrue(spawned.all { it.parent == null })
    }

    @Test
    fun `default skater uses the skater animation controller`() {
        val skater = PrefabsGenerator(engine).spawnDefaultsSync().single { it.name == "Skater" }

        assertEquals(Skater.CONTROLLER_PATH, skater.getComponent<Animator>()!!.controllerPath)
    }

    @Test
    fun `default objects carry no 2D sprite test content`() {
        val all = PrefabsGenerator(engine).spawnDefaultsSync().flatMap { it.descendants() }

        assertTrue(all.none { it.getComponent<SpriteRenderer>() != null }, "SpriteRenderer found on ${all.filter { it.getComponent<SpriteRenderer>() != null }.map { it.name }}")
        assertTrue(all.none { it.name == "Sprite" })
    }

    @Test
    fun `ledge is a static physics body with a box collider`() {
        val ledge = PrefabsGenerator(engine).spawnDefaultsSync().single { it.name == "Concrete_Ledge" }

        assertNotNull(ledge.getComponent<RigidBody3D>())
        assertNotNull(ledge.getComponent<BoxCollider3D>())
    }

    @Test
    fun `HUD canvas holds a score text and a restart button using bundled game string keys`() {
        val hud = PrefabsGenerator(engine).spawnHud()

        assertNotNull(hud.getComponent<UICanvasComponent>())
        val text = hud.children.single { it.name == "ScoreText" }.getComponent<UITextComponent>()!!
        val button = hud.children.single { it.name == "RestartButton" }.getComponent<UIButtonComponent>()!!
        assertEquals("hud.score", text.key)
        assertEquals("pause.restart", button.labelKey)
        // Keys must resolve both in the editor viewport and in the exported game.
        listOf("assets/strings/game_strings.properties", "assets/strings/editor_strings.properties").forEach { path ->
            val props = java.util.Properties().apply { File(path).reader(Charsets.UTF_8).use { load(it) } }
            assertTrue(props.containsKey(text.key), "${text.key} missing from $path")
            assertTrue(props.containsKey(button.labelKey), "${button.labelKey} missing from $path")
        }
    }

    @Test
    fun `ledge texture is loaded from the project assets folder`() {
        val generator = PrefabsGenerator(engine).apply { projectAssetsDir = "C:\\proj\\Assets\\" }

        generator.spawnLedge(material = MaterialType.CONCRETE)

        verify { assetsManager.getTexture("C:\\proj\\Assets\\" + Assets.Bundled.CONCRETE_SIMPLE) }
    }

    @Test
    fun PrefabSpawn_EveryCatalogPrefabSpawnsDetachedWithATransform() {
        val generator = PrefabsGenerator(engine)

        PrefabType.entries.forEach { type ->
            val go = generator.spawn(type, Vector3f(2f, 0f, -1f))
            val transform = go.getComponent<Transform>()
            assertNotNull(transform, "$type has no transform")
            assertEquals(2f, transform!!.translation.x, 1e-6f, "$type ignored the spawn point")
            assertEquals(-1f, transform.translation.z, 1e-6f, "$type ignored the spawn point")
            assertTrue(go.parent == null)
        }
    }

    @Test
    fun PrefabSpawn_GeneratedObstaclesAreStaticWithOneHullColliderPerPiece() {
        val generator = PrefabsGenerator(engine)

        PrefabType.entries.mapNotNull { type -> PrefabCatalog.shape(type)?.let { type to it } }.forEach { (type, shape) ->
            val go = generator.spawn(type)
            assertEquals(BodyType.Static, go.getComponent<RigidBody3D>()?.bodyType, "$type not static")
            val hulls = go.components.filterIsInstance<CustomCollider3D>()
            assertEquals(shape.hulls.size, hulls.size, "$type collider count")
            assertEquals(hulls.size, hulls.map { it.uId }.distinct().size, "$type colliders share an id")
            assertTrue(hulls.all { it.gameObject === go })
        }
        assertEquals(PrefabsGenerator.GRINDABLE_TAG, generator.spawn(PrefabType.HANDRAIL).tag)
    }

    @Test
    fun PrefabSpawn_HullCollidersSurviveASceneRoundTrip() {
        val scene = Scene("Park")
        scene.addChild(PrefabsGenerator(engine).spawn(PrefabType.HALF_PIPE))
        val serializer = Serializer()

        val reloaded = serializer.decode<Scene>(serializer.encode(scene))

        val pipe = reloaded.children.single()
        val hulls = pipe.components.filterIsInstance<CustomCollider3D>()
        assertEquals(PrefabCatalog.shape(PrefabType.HALF_PIPE)!!.hulls.size, hulls.size)
        assertEquals(BodyType.Static, pipe.getComponent<RigidBody3D>()!!.bodyType)
    }

    @Test
    fun PrefabSpawn_ObstaclesUseTheRequestedSurfaceTextureFromTheProject() {
        val generator = PrefabsGenerator(engine).apply { projectAssetsDir = "C:\\proj\\Assets\\" }

        generator.spawn(PrefabType.HALF_PIPE, material = MaterialType.WOOD_DARK)
        generator.spawn(PrefabType.KINKED_RAIL, material = MaterialType.WOOD_DARK)

        verify { assetsManager.loadModel("C:\\proj\\Assets\\" + Assets.Bundled.HALF_PIPE) }
        verify { assetsManager.getTexture("C:\\proj\\Assets\\" + Assets.Bundled.WOOD_DARK) }
        // Rails only come in metal.
        verify { assetsManager.getTexture("C:\\proj\\Assets\\" + Assets.Bundled.METAL) }
    }

    @Test
    fun PrefabSpawn_GenericPrefabsCarryTheirComponents() {
        val generator = PrefabsGenerator(engine)

        val light = generator.spawn(PrefabType.POINT_LIGHT, Vector3f(1f, 0f, 1f))
        assertNotNull(light.getComponent<PointLightComponent>())
        assertEquals(3f, light.getComponent<Transform>()!!.translation.y, 1e-6f)
        assertNotNull(generator.spawn(PrefabType.SPOT_LIGHT).getComponent<SpotLightComponent>())
        val trigger = generator.spawn(PrefabType.TRIGGER_VOLUME)
        assertTrue(trigger.getComponent<RigidBody3D>()!!.isTrigger)
        assertNull(trigger.getComponent<RenderComponent>())
        assertEquals(PrefabsGenerator.SPAWN_POINT_TAG, generator.spawn(PrefabType.SPAWN_POINT).tag)
        assertEquals(BodyType.Dynamic, generator.spawn(PrefabType.CRATE).getComponent<RigidBody3D>()!!.bodyType)
    }

    @Test
    fun PrefabSpawn_LedgeColliderSitsUnderItsMesh() {
        val ledge = PrefabsGenerator(engine).spawnDefaultsSync().single { it.name == "Concrete_Ledge" }

        // ledge.obj spans y 0..0.5 from its origin, so the box is lifted by its half height and the ledge sits on y = 0.
        assertEquals(0f, ledge.getComponent<Transform>()!!.translation.y, 1e-6f)
        assertEquals(0.25f, ledge.getComponent<BoxCollider3D>()!!.offset.y, 1e-6f)
    }

    @Test
    fun `createDefaultScenes saves only the lit MainScene without sprites and does not open it`() {
        val scenes = PrefabsGenerator(engine).createDefaultScenes(File("."))

        assertEquals(listOf("MainScene"), scenes.map { it.name })
        val scene = scenes.single()
        assertNotNull(scene.getComponent<ScenePhysicsComponent>())
        assertNotNull(scene.getComponent<EnvironmentComponent>())
        assertNotNull(scene.getComponent<DirectionalLightComponent>())
        assertTrue(scene.children.flatMap { it.descendants() }.none { it.getComponent<SpriteRenderer>() != null })
        verify { sceneManager.saveScene(scene, any()) }
        // ProjectManager reopens the saved files; opening here too loaded every scene twice (PHY-12).
        verify(exactly = 0) { sceneManager.openScenes(any()) }
    }

    @Test
    fun `createDefaultScenes attaches objects through the scene API so cached queries see them`() {
        lateinit var currentScene: Scene
        val canvases = ComponentQuery(root = { currentScene }, required = listOf(UICanvasComponent::class))
        val before = EcsChanges.version

        val scenes = PrefabsGenerator(engine).createDefaultScenes(File("."))
        currentScene = scenes[0]

        assertTrue(EcsChanges.version > before)
        scenes.forEach { scene -> scene.children.forEach { assertSame(scene, it.parent) } }
        val names = mutableListOf<String>()
        canvases.forEach { names += it.name }
        assertEquals(listOf("HUD"), names)
    }
}
