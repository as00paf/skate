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
import com.pafoid.skate.engine.ecs.components.Collider3D
import com.pafoid.skate.engine.ecs.components.CompoundHullCollider3D
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

        assertEquals(
            listOf("Camera", "FirstPersonCamera", "FixedCamera", "Skateboard", "Skater", "Floor", "Concrete_Ledge", "QuarterPipe", "Funbox", "HUD"),
            spawned.map { it.name },
        )
        assertNotNull(spawned[0].getComponent<CameraComponent>())
        // GPL-05: the default camera follows the skater with spring-arm collision.
        val follow = spawned[0].getComponent<com.pafoid.skate.engine.ecs.components.FollowCameraComponent>()!!
        assertEquals("Skater", follow.targetName)
        assertTrue(follow.collision)
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
    fun `MainScene camera button cycles third person, first person and fixed cameras, also after a save and load`() {
        val generated = Scene("MainScene").apply { PrefabsGenerator(engine).spawnDefaultsSync().forEach { addChild(it) } }
        val serializer = Serializer()
        val text = serializer.encodeFile(com.pafoid.skate.engine.assets.serialization.FileKind.SCENE, generated)
        val loaded = serializer.decodeFile<Scene?>(com.pafoid.skate.engine.assets.serialization.FileKind.SCENE, text)!!

        listOf(generated, loaded).forEach { scene ->
            val cameras = scene.children.filter { it.getComponent<CameraComponent>() != null }
            assertEquals(listOf(PrefabsGenerator.CAMERA_THIRD_PERSON), cameras.filter { it.getComponent<CameraComponent>()!!.isDefault }.map { it.name })
            val cycle = scene.children.single { it.name == PrefabsGenerator.CAMERA_THIRD_PERSON }
                .getComponent<com.pafoid.skate.engine.ecs.components.CameraCycleComponent>()!!
            assertEquals(PrefabsGenerator.CAMERA_BUTTON, cycle.buttonName)
            assertEquals(com.pafoid.skate.engine.gameplay.camera.CameraCycle.ACTION, cycle.actionId)
            assertNotNull(scene.children.single { it.name == PrefabsGenerator.CAMERA_FIRST_PERSON }
                .getComponent<com.pafoid.skate.engine.ecs.components.FirstPersonCameraComponent>())

            var active = cameras.single { it.name == PrefabsGenerator.CAMERA_THIRD_PERSON }.getComponent<CameraComponent>()
            val seen = ArrayList<String>()
            val system = com.pafoid.skate.engine.ecs.systems.CameraCycleSystem(null, { active }) { camera, transform ->
                active = camera
                seen += cameras.single { it.getComponent<Transform>() === transform && it.getComponent<CameraComponent>() === camera }.name
            }
            system.init(scene)
            scene.isRunning = true
            system.update(1f / 60f)
            val button = scene.findFirstByName(PrefabsGenerator.CAMERA_BUTTON)!!.getComponent<UIButtonComponent>()!!
            repeat(3) { button.onClick!!(); system.update(1f / 60f) }

            assertEquals(listOf(PrefabsGenerator.CAMERA_FIRST_PERSON, PrefabsGenerator.CAMERA_FIXED, PrefabsGenerator.CAMERA_THIRD_PERSON), seen)
        }
    }

    @Test
    fun `MainScene fixed camera looks at the spawn from above`() {
        val fixed = PrefabsGenerator(engine).spawnDefaultsSync().single { it.name == PrefabsGenerator.CAMERA_FIXED }
        val t = fixed.getComponent<Transform>()!!
        val expected = Transform(Vector3f(t.translation)).apply { lookAt(Vector3f(0f, 0.8f, 0f)) }

        assertTrue(t.translation.y > 2f)
        assertEquals(expected.rotation, t.rotation)
    }

    @Test
    fun `HUD canvas holds a score text and a camera button using bundled game string keys`() {
        val hud = PrefabsGenerator(engine).spawnHud()

        assertNotNull(hud.getComponent<UICanvasComponent>())
        val text = hud.children.single { it.name == "ScoreText" }.getComponent<UITextComponent>()!!
        val button = hud.children.single { it.name == PrefabsGenerator.CAMERA_BUTTON }.getComponent<UIButtonComponent>()!!
        assertEquals("hud.score", text.key)
        assertEquals("hud.camera", button.labelKey)
        // GPL-22: restart moved to the Esc pause menu.
        assertTrue(hud.children.none { it.name == "RestartButton" })
        // Keys must resolve both in the editor viewport and in the exported game.
        listOf(com.pafoid.skate.engine.assets.Assets.Strings.GAME_STRINGS).forEach { path ->
            val props = com.pafoid.skate.engine.core.StringTables.merged(File(path))
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
    fun PrefabSpawn_GeneratedObstaclesAreStaticWithOneCompoundHullCollider() {
        val generator = PrefabsGenerator(engine)

        PrefabType.entries.mapNotNull { type -> PrefabCatalog.shape(type)?.let { type to it } }.forEach { (type, shape) ->
            val go = generator.spawn(type)
            assertEquals(BodyType.Static, go.getComponent<RigidBody3D>()?.bodyType, "$type not static")
            val colliders = go.components.filterIsInstance<Collider3D>()
            assertEquals(1, colliders.size, "$type collider count")
            assertEquals(shape.hulls, (colliders.single() as CompoundHullCollider3D).hulls, "$type hulls")
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
        val collider = pipe.components.filterIsInstance<Collider3D>().single() as CompoundHullCollider3D
        assertEquals(PrefabCatalog.shape(PrefabType.HALF_PIPE)!!.hulls, collider.hulls)
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
    fun `createDefaultScenes saves the lit MainScene and the feature showcase without opening them`() {
        val dir = kotlin.io.path.createTempDirectory("prefabs-default").toFile()
        val scenes = try {
            PrefabsGenerator(engine).apply { projectAssetsDir = dir.path + File.separator }.createDefaultScenes(dir)
        } finally {
            dir.deleteRecursively()
        }

        assertEquals(listOf("MainScene", "FeatureShowcase"), scenes.map { it.name })
        val scene = scenes.first()
        assertNotNull(scene.getComponent<ScenePhysicsComponent>())
        assertNotNull(scene.getComponent<EnvironmentComponent>())
        assertNotNull(scene.getComponent<DirectionalLightComponent>())
        assertTrue(scene.children.flatMap { it.descendants() }.none { it.getComponent<SpriteRenderer>() != null })
        verify { sceneManager.saveScene(scene, any()) }
        verify { sceneManager.saveScene(scenes[1], any()) }
        // ProjectManager reopens the saved files; opening here too loaded every scene twice (PHY-12).
        verify(exactly = 0) { sceneManager.openScenes(any()) }
    }

    @Test
    fun `createDefaultScenes attaches objects through the scene API so cached queries see them`() {
        lateinit var currentScene: Scene
        val canvases = ComponentQuery(root = { currentScene }, required = listOf(UICanvasComponent::class))
        val before = EcsChanges.version

        val dir = kotlin.io.path.createTempDirectory("prefabs-query").toFile()
        val scenes = try {
            PrefabsGenerator(engine).apply { projectAssetsDir = dir.path + File.separator }.createDefaultScenes(dir)
        } finally {
            dir.deleteRecursively()
        }
        currentScene = scenes[0]

        assertTrue(EcsChanges.version > before)
        scenes.forEach { scene -> scene.children.forEach { assertSame(scene, it.parent) } }
        val names = mutableListOf<String>()
        canvases.forEach { names += it.name }
        assertEquals(listOf("HUD"), names)
    }

    @Test
    fun `path-only mode spawns prefabs and the animated character without loading assets`() {
        val generator = PrefabsGenerator(engine, loadAssets = false).apply { projectAssetsDir = "P\\" }

        val bank = generator.spawn(PrefabType.BANK, material = MaterialType.CONCRETE)
        val character = generator.spawnAnimatedCharacter("Walker", Vector3f(), Assets.Bundled.WALKING)

        verify(exactly = 0) { assetsManager.loadModel(any()) }
        verify(exactly = 0) { assetsManager.getTexture(any()) }
        verify(exactly = 0) { assetsManager.loadAnimationSync(any()) }
        val render = bank.getComponent<com.pafoid.skate.engine.ecs.components.RenderComponent>()!!
        assertEquals("P\\" + PrefabCatalog.entry(PrefabType.BANK).bundledModelPath, render.model!!.path)
        assertEquals("P\\" + MaterialType.CONCRETE.texturePath, render.material!!.baseColorTexture!!.filePath)
        val animator = character.getComponent<com.pafoid.skate.engine.ecs.components.Animator>()!!
        assertEquals(listOf("P\\" + Assets.Bundled.WALKING), animator.animations.map { it.path })
    }
}
