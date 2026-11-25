package com.pafoid.skate.game.samples

import com.pafoid.skate.engine.assets.EngineDefaults
import com.pafoid.skate.engine.ui.UiRectF
import com.pafoid.skate.engine.ui.UiTree
import com.pafoid.skate.engine.ui.UiLayout
import com.pafoid.skate.engine.getAllChildren
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.SceneSun
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
import com.pafoid.skate.engine.physics3d.water.BuoyancyMode
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.ecs.components.WaterMaterials
import com.pafoid.skate.engine.ecs.components.WaterComponent
import org.joml.Vector4f
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
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.core.StringTables
import java.io.File

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
        val unlabelled = scene.children.map { it.name }.filterNot { it.startsWith("[") || it == "Camera" || it == "Floor" || it == SceneSun.NAME }
        assertEquals(emptyList<String>(), unlabelled)
    }

    @Test
    fun `scene has camera, sun, physics settings and no sprites`() {
        assertNotNull(SceneSun.of(scene), "a Sun entity")
        assertNotNull(scene.getComponent<EnvironmentComponent>(), "ambient light lives in the environment")
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
        val audio = withComponent<AudioComponent>().single().getComponent<AudioComponent>()!!
        assertEquals(com.pafoid.skate.engine.assets.EngineDefaults.Sounds.MUSIC, audio.soundFilePath)
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
        val canvas = withComponent<UICanvasComponent>().single { it.name != FeatureShowcase.MAP_CANVAS }
        val buttons = canvas.getAllChildren().filter { it.getComponent<UIButtonComponent>() != null }.map { it.name }
        val expected = listOf(FeatureShowcase.OVERVIEW) + FeatureShowcase.AREAS.map { it.key }
        assertTrue(buttons.containsAll(expected.map { FeatureShowcase.focusButtonName(it) }), buttons.toString())
        val widgets = canvas.getAllChildren().flatMap { it.components }
        assertTrue(widgets.any { it is UISliderComponent })
        assertTrue(widgets.filterIsInstance<UITextComponent>().any { it.args.isNotEmpty() })
    }

    @Test
    fun `UI overlay is grouped by screen region, with the legend items under the legend panel`() {
        val canvas = withComponent<UICanvasComponent>().single { it.name != FeatureShowcase.MAP_CANVAS }
        assertEquals(
            listOf(FeatureShowcase.HEADER, FeatureShowcase.LEGEND_PANEL, FeatureShowcase.TOOLBAR, FeatureShowcase.WIDGET_PANEL, FeatureShowcase.MAP_CORNER),
            canvas.children.map { it.name },
        )
        fun group(name: String) = canvas.children.single { it.name == name }.children.map { it.name }
        val legendButtons = (listOf(FeatureShowcase.OVERVIEW) + FeatureShowcase.AREAS.map { it.key }).map { FeatureShowcase.focusButtonName(it) }
        assertEquals(listOf("LegendTitle") + legendButtons, group(FeatureShowcase.LEGEND_PANEL))
        assertEquals(listOf("Title", "Hint"), group(FeatureShowcase.HEADER))
        assertEquals(listOf(FeatureShowcase.FOG_TOGGLE_BUTTON, FeatureShowcase.WIND_TOGGLE_BUTTON, FeatureShowcase.SEED_LABEL), group(FeatureShowcase.TOOLBAR))
        assertEquals(listOf("FormattedText", "Button", "Progress", "Slider"), group(FeatureShowcase.WIDGET_PANEL))
        assertEquals(listOf(FeatureShowcase.MINIMAP, FeatureShowcase.MAP_BUTTON), group(FeatureShowcase.MAP_CORNER))
        val map = withComponent<UICanvasComponent>().single { it.name == FeatureShowcase.MAP_CANVAS }
        assertEquals(listOf("Dim", FeatureShowcase.HEADER, "FullMap", FeatureShowcase.MAP_CLOSE), map.children.map { it.name })
    }

    /** Grouping only re-parents the items: on screen they stay where the flat overlay put them (canvas-anchored). */
    @Test
    fun `grouped UI items keep their screen positions`() {
        val canvas = withComponent<UICanvasComponent>().single { it.name != FeatureShowcase.MAP_CANVAS }
        val (w, h) = 1920f to 1080f
        val rects = UiLayout.resolve(UiTree.spec(canvas), w, h)
        fun rect(name: String) = rects.getValue(canvas.getAllChildren().single { it.name == name }.uId)
        // A flat item placed with placed(anchorX, anchorY, x, y, width, height) directly on the canvas.
        fun flat(ax: Float, ay: Float, x: Float, y: Float, rw: Float, rh: Float) = UiRectF(ax * w + x - ax * rw, ay * h + y - ay * rh, rw, rh)
        assertEquals(flat(0f, 0f, 40f, 150f, 520f, 44f), rect("LegendTitle"))
        val keys = listOf(FeatureShowcase.OVERVIEW) + FeatureShowcase.AREAS.map { it.key }
        keys.forEachIndexed { i, key ->
            val row = FeatureShowcase.LEGEND_ROW
            assertEquals(flat(0f, 0f, 30f, 200f + i * row, 540f, row - 6f), rect(FeatureShowcase.focusButtonName(key)), key)
        }
        assertEquals(flat(0.5f, 0f, 0f, 20f, 1000f, 60f), rect("Title"))
        assertEquals(flat(1f, 0f, -20f, 20f, 320f, 64f), rect(FeatureShowcase.FOG_TOGGLE_BUTTON))
        assertEquals(flat(1f, 0f, -20f, 96f, 320f, 64f), rect(FeatureShowcase.WIND_TOGGLE_BUTTON))
        assertEquals(flat(1f, 0f, -20f, 172f, 520f, 36f), rect(FeatureShowcase.SEED_LABEL))
        assertEquals(flat(1f, 1f, -40f, -280f, 440f, 44f), rect("FormattedText"))
        assertEquals(flat(1f, 1f, -110f, -180f, 300f, 76f), rect("Button"))
        assertEquals(flat(1f, 1f, -60f, -110f, 400f, 30f), rect("Progress"))
        assertEquals(flat(1f, 1f, -60f, -50f, 400f, 30f), rect("Slider"))
        assertEquals(flat(0f, 1f, 20f, -20f, FeatureShowcase.MINIMAP_SIZE, FeatureShowcase.MINIMAP_SIZE), rect(FeatureShowcase.MINIMAP))
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
    fun `overlay string keys resolve in the bundled game tables, English and French`() {
        // The editor merges the game tables too, so these keys also resolve in the editor viewport.
        val dir = File(Assets.Strings.GAME_STRINGS)
        val english = StringTables.merged(dir)
        val french = StringTables.baseTables(dir).map { StringTables.localized(it, "fr") }.filter { it.exists() }.map(StringTables::read)
        FeatureShowcase.STRING_KEYS.forEach { key ->
            assertTrue(english.containsKey(key), "$key missing from the game tables")
            assertTrue(french.any { it.containsKey(key) }, "$key has no French translation")
        }
    }

    @Test
    fun `shadows area has child models under a mesh-less parent (REN-20)`() {
        val group = scene.children.single { it.name == "[Shadows] Group" }
        assertTrue(group.components.none { it is RenderComponent })
        val child = group.children.single()
        val grandchild = child.children.single()
        assertTrue(listOf(child, grandchild).all { go -> go.components.any { it is RenderComponent && it.castShadow } })
    }

    private fun named(name: String) = scene.children.single { it.name == name }

    @Test
    fun `nature pool is a 10 x 6 x 1,5 m pool of wind-driven pool water inside its walls (TLG-17)`() {
        val pool = named(FeatureShowcase.POOL)
        val water = pool.getComponent<WaterComponent>()!!
        assertEquals(10f, water.sizeX)
        assertEquals(6f, water.sizeZ)
        assertEquals(1.5f, water.depth)
        assertEquals(WaterMaterials.POOL, water.material)
        assertTrue(water.windDrivenWaves)
        val surface = pool.getComponent<Transform>()!!.translation
        assertEquals(water.depth, surface.y, 1e-5f, "the pool floor is the showcase floor (y = 0)")
        val walls = scene.children.filter { it.name.startsWith("[Nature] Wall") }
        assertEquals(4, walls.size)
        walls.forEach { wall ->
            val t = wall.getComponent<Transform>()!!
            assertTrue(t.translation.y + t.scale.y > surface.y, "${wall.name} top is below the water")
            assertEquals(BodyType.Static, wall.getComponent<RigidBody3D>()!!.bodyType)
            // The wall's inner face lines up with the water's edge.
            val inner = listOf(
                kotlin.math.abs(t.translation.x - surface.x) - t.scale.x to water.sizeX / 2f,
                kotlin.math.abs(t.translation.z - surface.z) - t.scale.z to water.sizeZ / 2f,
            )
            assertTrue(inner.any { (face, edge) -> kotlin.math.abs(face - edge) < 1e-4f }, "${wall.name} does not touch the water's edge")
        }
    }

    @Test
    fun `nature pool has steps up its outside wall and down into the water`() {
        val surface = named(FeatureShowcase.POOL).getComponent<Transform>()!!.translation
        val westEdge = surface.x - FeatureShowcase.POOL_SIZE_X / 2f
        fun tops(prefix: String) = scene.children.filter { it.name.startsWith(prefix) }.map {
            val t = it.getComponent<Transform>()!!
            assertEquals(BodyType.Static, it.getComponent<RigidBody3D>()!!.bodyType)
            Triple(t.translation.y + t.scale.y, t.translation.x - t.scale.x, t.translation.x + t.scale.x)
        }.sortedBy { it.first }
        val inside = tops("[Nature] StepIn_")
        val outside = tops("[Nature] StepOut_")
        assertEquals(3, inside.size)
        assertEquals(3, outside.size)
        assertEquals(listOf(0.4f, 0.8f, 1.2f), inside.map { it.first }.map { Math.round(it * 10f) / 10f })
        assertTrue(inside.all { it.first < surface.y && it.second >= westEdge - 1e-4f }, "inner steps must be under water, in the pool")
        assertTrue(outside.all { it.third <= westEdge + 1e-4f }, "outer steps must be outside the pool")
        // Higher steps are shorter, so they climb towards the wall.
        assertTrue(inside.zipWithNext().all { (low, high) -> high.third < low.third })
        assertTrue(outside.zipWithNext().all { (low, high) -> high.second > low.second })
    }

    @Test
    fun `nature floating props get their mass from density and start over the open water`() {
        val pool = named(FeatureShowcase.POOL)
        val water = pool.getComponent<WaterComponent>()!!
        val surface = pool.getComponent<Transform>()!!.translation
        val crates = FeatureShowcase.CRATE_DENSITIES.map { (suffix, density) -> named(FeatureShowcase.crateName(suffix)) to density }
        assertEquals(3, crates.map { it.second }.distinct().size)
        crates.forEach { (crate, density) ->
            val volume = 8f * FeatureShowcase.CRATE_HALF * FeatureShowcase.CRATE_HALF * FeatureShowcase.CRATE_HALF
            assertEquals(density * volume, crate.getComponent<RigidBody3D>()!!.mass, 1e-3f, crate.name)
            assertTrue(density < water.density, "${crate.name} would sink")
        }
        val props = crates.map { it.first } + named(FeatureShowcase.BALL) + named(FeatureShowcase.LOG)
        props.forEach { prop ->
            val body = prop.getComponent<RigidBody3D>()!!
            assertEquals(BodyType.Dynamic, body.bodyType, prop.name)
            assertEquals(BuoyancyMode.AUTO, body.buoyancy, prop.name)
            assertTrue(prop.components.any { it is com.pafoid.skate.engine.ecs.components.Collider3D }, "${prop.name} has no collider")
            val p = prop.getComponent<Transform>()!!.translation
            assertTrue(p.y > surface.y + 0.3f, "${prop.name} starts in the water")
            assertTrue(kotlin.math.abs(p.x - surface.x) < water.sizeX / 2f - 1f && kotlin.math.abs(p.z - surface.z) < water.sizeZ / 2f - 0.5f,
                "${prop.name} is not over the water")
        }
        assertEquals(FeatureShowcase.BALL_DENSITY, named(FeatureShowcase.BALL).getComponent<RigidBody3D>()!!.mass /
            (4f / 3f * kotlin.math.PI.toFloat() * 0.28f * 0.28f * 0.28f), 1f)
        val log = named(FeatureShowcase.LOG)
        assertTrue(log.getComponent<RigidBody3D>()!!.mass / (kotlin.math.PI.toFloat() * 0.22f * 0.22f * 2.85f * 0.7f) in 590f..610f)
    }

    @Test
    fun `wind cycle button is named in the environment and exists in the overlay`() {
        val env = scene.getComponent<EnvironmentComponent>()!!
        assertEquals(FeatureShowcase.WIND_TOGGLE_BUTTON, env.windCycleButton)
        val button = withComponent<UIButtonComponent>().single { it.name == env.windCycleButton }
        assertEquals("showcase.wind_toggle", button.getComponent<UIButtonComponent>()!!.labelKey)
    }

    @Test
    fun `floor and map cover every pad, and the legend stays above the minimap at 1080p`() {
        FeatureShowcase.AREAS.forEach { area ->
            corners(area).forEach { c ->
                assertTrue(kotlin.math.abs(c.x - FeatureShowcase.FLOOR_CENTER.x) <= FeatureShowcase.FLOOR_HALF.x &&
                    kotlin.math.abs(c.z - FeatureShowcase.FLOOR_CENTER.z) <= FeatureShowcase.FLOOR_HALF.z, "${area.key} corner $c is off the floor")
                val map = FeatureShowcase.MAP_WORLD
                assertTrue(c.x in map.minX..map.maxX && c.z in map.minZ..map.maxZ, "${area.key} corner $c is off the map")
            }
        }
        val canvas = withComponent<UICanvasComponent>().single { it.name != FeatureShowcase.MAP_CANVAS }
        val rects = UiLayout.resolve(UiTree.spec(canvas), 1920f, 1080f)
        fun rect(name: String) = rects.getValue(canvas.getAllChildren().single { it.name == name }.uId)
        val legend = rect(FeatureShowcase.LEGEND_PANEL)
        val minimap = rect(FeatureShowcase.MINIMAP)
        assertTrue(legend.y + legend.h <= minimap.y, "legend $legend overlaps the minimap $minimap")
    }
}
