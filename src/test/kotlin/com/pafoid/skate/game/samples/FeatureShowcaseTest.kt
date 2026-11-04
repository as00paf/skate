package com.pafoid.skate.game.samples

import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.PrefabsGenerator
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.AudioComponent
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.CameraFocusComponent
import com.pafoid.skate.engine.ecs.components.CameraFocusTarget
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.systems.CameraManager
import org.joml.Vector3f
import org.joml.Vector4f
import com.pafoid.skate.engine.ecs.components.DirectionalLightComponent
import com.pafoid.skate.engine.ecs.components.JointComponent
import com.pafoid.skate.engine.ecs.components.JointType
import com.pafoid.skate.engine.ecs.components.ParticleEmitter
import com.pafoid.skate.engine.ecs.components.PointLightComponent
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.ScenePhysicsComponent
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.ecs.components.SpotLightComponent
import com.pafoid.skate.engine.ecs.components.SpriteRenderer
import com.pafoid.skate.engine.ecs.components.VehicleComponent
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.ui.UIButtonComponent
import com.pafoid.skate.engine.ui.UICanvasComponent
import com.pafoid.skate.engine.ui.UISliderComponent
import com.pafoid.skate.engine.ui.UITextComponent
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Properties

class FeatureShowcaseTest {

    private val assetsManager = mockk<AssetsManager>(relaxed = true).also {
        every { it.loadModel(any()) } returns `3dModel`(mesh = listOf(), skeleton = Skeleton(Bone(0, "root"), 1))
        every { it.loadAnimationSync(any()) } returns Animation("walking", emptyList(), 1f, "walking.fbx")
        every { it.getTexture(any()) } returns Texture()
    }
    private val engine = mockk<Engine>(relaxed = true).also { every { it.assetsManager } returns assetsManager }
    private val prefabs = PrefabsGenerator(engine).apply { projectAssetsDir = "P\\Assets\\" }
    private val scene = FeatureShowcase(prefabs).populate(Scene(FeatureShowcase.SCENE_NAME))

    private fun all(): List<GameObject> {
        fun walk(go: GameObject): List<GameObject> = listOf(go) + go.children.flatMap { walk(it) }
        return scene.children.flatMap { walk(it) }
    }

    private inline fun <reified T : com.pafoid.skate.engine.ecs.components.Component> withComponent() =
        all().filter { o -> o.components.any { it is T } }

    @Test
    fun `every demo area has a labelled ground pad and labelled objects`() {
        FeatureShowcase.AREAS.forEach { area ->
            assertNotNull(scene.children.firstOrNull { it.name == "[${area.label}] Area" }, "pad for ${area.key}")
            assertTrue(scene.children.count { it.name.startsWith("[${area.label}] ") } > 1, "objects in ${area.key}")
        }
        val unlabelled = scene.children.map { it.name }.filterNot { it.startsWith("[") || it == "Camera" || it == "Floor" }
        assertEquals(emptyList<String>(), unlabelled)
    }

    @Test
    fun `scene has camera, sun, physics settings and no sprites`() {
        assertNotNull(scene.getComponent<DirectionalLightComponent>())
        assertNotNull(scene.getComponent<ScenePhysicsComponent>())
        assertEquals(1, withComponent<CameraComponent>().size)
        assertTrue(withComponent<SpriteRenderer>().isEmpty())
    }

    @Test
    fun `lights and shadow areas have point and spot lights`() {
        assertTrue(withComponent<PointLightComponent>().any { it.name.startsWith("[Shadows]") })
        assertTrue(withComponent<SpotLightComponent>().any { it.name.startsWith("[Shadows]") })
        assertTrue(withComponent<PointLightComponent>().count { it.name.startsWith("[Lights]") } >= 2)
        val noCaster = all().single { it.name == "[Shadows] NoCaster" }.getComponent<RenderComponent>()!!
        assertEquals(false, noCaster.castShadow)
    }

    @Test
    fun `particles, joints, vehicle and audio demos are present`() {
        assertEquals(4, withComponent<ParticleEmitter>().size)
        assertTrue(withComponent<ParticleEmitter>().any { it.getComponent<ParticleEmitter>()!!.additive })
        val joints = withComponent<JointComponent>().map { it.getComponent<JointComponent>()!!.jointType }.toSet()
        assertEquals(setOf(JointType.HINGE, JointType.CONE_TWIST, JointType.SLIDER), joints)
        val car = withComponent<VehicleComponent>().single()
        assertNotNull(car.getComponent<RigidBody3D>())
        assertEquals(2, withComponent<AudioComponent>().size)
        assertTrue(withComponent<AudioComponent>().all { it.getComponent<AudioComponent>()!!.soundFilePath == "P\\Assets\\tone.wav" })
    }

    @Test
    fun `physics bodies and the animated character are top-level`() {
        val nested = scene.children.flatMap { it.children }
        assertTrue(nested.none { o -> o.components.any { it is RigidBody3D || it is Animator } })
        val character = scene.children.single { it.name == "[Animation] Character" }
        assertTrue(character.getComponent<Animator>()!!.isPlaying)
        assertNotNull(character.getComponent<SkeletonComponent>())
    }

    @Test
    fun `prefab library pieces come from the prefab catalog`() {
        val pieces = scene.children.filter { it.name.startsWith("[Prefabs] ") && it.name != "[Prefabs] Area" }
        assertEquals(FeatureShowcase.PREFAB_PIECES.size, pieces.size)
        assertTrue(pieces.all { it.getComponent<RigidBody3D>() != null })
    }

    @Test
    fun `UI overlay has a focus button per area plus overview, a slider and formatted text`() {
        val canvas = withComponent<UICanvasComponent>().single()
        val buttons = canvas.children.filter { it.getComponent<UIButtonComponent>() != null }.map { it.name }
        val expected = listOf(FeatureShowcase.OVERVIEW) + FeatureShowcase.AREAS.map { it.key }
        assertTrue(buttons.containsAll(expected.map { FeatureShowcase.focusButtonName(it) }), buttons.toString())
        val widgets = canvas.children.flatMap { it.components }
        assertTrue(widgets.any { it is UISliderComponent })
        assertTrue(widgets.filterIsInstance<UITextComponent>().any { it.args.isNotEmpty() })
    }

    @Test
    fun `camera starts at the overview and has a focus target bound to every legend button`() {
        val camera = withComponent<CameraComponent>().single()
        val focus = camera.getComponent<CameraFocusComponent>()!!
        val transform = camera.getComponent<Transform>()!!
        val overview = FeatureShowcase.overviewTarget()
        assertEquals(overview.position, transform.translation)
        assertEquals(overview.rotation, transform.rotation)
        assertTrue(transform.rotation.x > 0f, "camera must pitch down (+X), was ${transform.rotation}")
        val canvasButtons = withComponent<UIButtonComponent>().map { it.name }.toSet()
        assertEquals(FeatureShowcase.AREAS.size + 1, focus.targets.size)
        focus.targets.forEach { assertTrue(it.buttonName in canvasButtons, "no button ${it.buttonName}") }
    }

    /** Projects [point] with the engine camera at [pose] (16:9, default fov); x and y in -1..1 means on screen. */
    private fun ndc(pose: CameraFocusTarget, point: Vector3f): Vector3f {
        val manager = CameraManager(EventSystem())
        manager.camera = CameraComponent().apply { viewportWidth = 1920; viewportHeight = 1080 }
        manager.transform = Transform(Vector3f(pose.position), rotation = Vector3f(pose.rotation))
            .also { it.worldMatrix.translation(it.translation) }
        manager.update(0f)
        val clip = Vector4f(point, 1f).mul(manager.camera.view).mul(manager.camera.projection)
        assertTrue(clip.w > 0f, "$point is behind the camera")
        return Vector3f(clip.x / clip.w, clip.y / clip.w, clip.z / clip.w)
    }

    private fun corners(area: FeatureShowcase.Area) = listOf(-1f, 1f).flatMap { sx ->
        listOf(-1f, 1f).map { sz -> Vector3f(area.center).add(sx * area.size.x, 0f, sz * area.size.z) }
    }

    @Test
    fun `each focus view frames its whole pad, centred, from above`() {
        FeatureShowcase.AREAS.forEach { area ->
            val pose = FeatureShowcase.focusTarget(area)
            assertTrue(pose.position.y > area.center.y + 2f && pose.position.z > area.center.z, "${area.key} eye ${pose.position}")
            val centre = ndc(pose, Vector3f(area.center).add(0f, 1f, 0f))
            assertEquals(0f, centre.x, 1e-3f, area.key)
            assertEquals(0f, centre.y, 1e-3f, area.key)
            corners(area).forEach { c ->
                val p = ndc(pose, c)
                assertTrue(kotlin.math.abs(p.x) < 1f && kotlin.math.abs(p.y) < 1f, "${area.key} corner $c off screen: $p")
            }
        }
    }

    @Test
    fun `overview shows every pad`() {
        val pose = FeatureShowcase.overviewTarget()
        FeatureShowcase.AREAS.forEach { area ->
            corners(area).forEach { c ->
                val p = ndc(pose, c)
                assertTrue(kotlin.math.abs(p.x) < 1f && kotlin.math.abs(p.y) < 1f, "${area.key} corner $c off screen: $p")
            }
        }
    }

    @Test
    fun `overlay string keys resolve in the bundled game and editor tables, English and French`() {
        listOf("game_strings", "editor_strings", "game_strings_fr", "editor_strings_fr").forEach { name ->
            val props = Properties().apply { File("assets/strings/$name.properties").reader(Charsets.UTF_8).use { load(it) } }
            FeatureShowcase.STRING_KEYS.forEach { assertTrue(props.containsKey(it), "$it missing from $name") }
        }
    }

    @Test
    fun `writeAssets writes the looping tone`() {
        val dir = kotlin.io.path.createTempDirectory("showcase-assets").toFile()
        try {
            FeatureShowcase.writeAssets(dir)
            val wav = File(dir, FeatureShowcase.TONE_FILE)
            assertEquals("RIFF", String(wav.readBytes().copyOfRange(0, 4)))
        } finally {
            dir.deleteRecursively()
        }
    }
}
