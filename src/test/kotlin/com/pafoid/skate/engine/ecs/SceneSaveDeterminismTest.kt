package com.pafoid.skate.engine.ecs

import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.PrefabsGenerator
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import com.pafoid.skate.engine.ecs.components.DirectionalLightComponent
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.components.GridLines
import com.pafoid.skate.engine.ecs.components.ScenePhysicsComponent
import com.pafoid.skate.game.samples.FeatureShowcase
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import com.pafoid.skate.engine.ecs.components.JointComponent
import com.pafoid.skate.engine.ecs.components.JointType
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import org.junit.jupiter.api.Test

/**
 * EDT-60 §7.1: saving an unchanged scene gives the same bytes, also after a restart. Each "session" below loads the
 * file the way the editor does, after creating unrelated objects first, so session counters differ between saves.
 */
class SceneSaveDeterminismTest {

    private val assetsManager = mockk<AssetsManager>(relaxed = true).also {
        every { it.loadModel(any()) } returns `3dModel`(mesh = listOf(), skeleton = Skeleton(Bone(0, "root"), 1))
        every { it.loadAnimationSync(any()) } returns Animation("walking", emptyList(), 1f, "walking.fbx")
        every { it.getTexture(any()) } returns Texture()
    }
    private val engine = mockk<Engine>(relaxed = true).also { every { it.assetsManager } returns assetsManager }
    private val prefabs = PrefabsGenerator(engine).apply { projectAssetsDir = "P\\Assets\\" }
    private val serializer = Serializer()

    /** MainScene as [PrefabsGenerator.createDefaultScenes] builds it. */
    private fun mainScene(): Scene = Scene(PrefabsGenerator.DEFAULT_SCENE).apply {
        addComponent(ScenePhysicsComponent())
        addComponent(GridLines())
        addComponent(EnvironmentComponent(skyTexture = Texture()))
        addComponent(DayNightCycleComponent())
        addComponent(DirectionalLightComponent())
        prefabs.spawnDefaultsSync().forEach { addChild(it) }
    }

    private fun showcase(): Scene = FeatureShowcase(prefabs).populate(Scene(FeatureShowcase.SCENE_NAME))

    private fun save(scene: Scene): String = serializer.encodeFile(FileKind.SCENE, scene)

    /** A new editor session: other objects were created before the scene is opened, then it is loaded as the editor does. */
    private fun loadInNewSession(text: String, unrelatedObjects: Int): Scene {
        repeat(unrelatedObjects) { GameObject("Unrelated$it").addComponent(Transform()) }
        return serializer.decodeFile<Scene>(FileKind.SCENE, text).also { scene ->
            // What SceneManager.resolveSceneReferences does for identity: parent links and component init. Asset
            // lookups are left out; they need the real files and do not touch ids.
            scene.linkChildren()
            fun init(go: GameObject) {
                go.components.forEach { it.init(go) }
                go.children.forEach(::init)
            }
            init(scene)
        }
    }

    private fun assertStable(build: () -> Scene) {
        val first = save(build())
        val second = save(loadInNewSession(first, unrelatedObjects = 17))
        val third = save(loadInNewSession(second, unrelatedObjects = 3))
        assertEquals(first, second, "load -> save changed the file:\n" + diff(first, second))
        assertEquals(second, third, "load -> save -> load -> save changed the file:\n" + diff(second, third))
    }

    /** The first differing lines, so a failure says which field is unstable. */
    private fun diff(a: String, b: String): String {
        val x = a.lines()
        val y = b.lines()
        return x.indices.filter { it >= y.size || x[it] != y[it] }.take(8)
            .joinToString("\n") { "line ${it + 1}: ${x[it].trim()}  ->  ${y.getOrNull(it)?.trim()}" } +
            if (x.size != y.size) "\n(${x.size} vs ${y.size} lines)" else ""
    }

    @Test
    fun MainScene_LoadSaveLoadSave_GivesIdenticalBytes() = assertStable(::mainScene)

    @Test
    fun FeatureShowcase_LoadSaveLoadSave_GivesIdenticalBytes() = assertStable(::showcase)

    private fun all(go: GameObject): List<GameObject> = listOf(go) + go.children.flatMap(::all)

    @Test
    fun SavedFile_HasPersistentIdsAndNoSessionState() {
        val scene = mainScene().apply { isDirty = true }
        val text = save(scene)
        assertFalse(Regex(""""uId"""").containsMatchIn(text), "session ids must not be saved")
        assertFalse(text.contains("\"isDirty\""), "the unsaved marker must not be saved")
        val guids = all(scene).map { it.guid }
        assertEquals(guids.size, guids.toSet().size, "every object has its own guid")
        assertTrue(guids.all { Regex("[0-9a-f]{32}").matches(it) }, guids.toString())
        guids.forEach { assertTrue(text.contains("\"$it\""), "guid $it is saved") }
    }

    @Test
    fun DirtyAndCleanScenes_SaveTheSameBytes() {
        val scene = mainScene()
        val clean = save(scene)
        scene.isDirty = true
        assertEquals(clean, save(scene))
    }

    @Test
    fun Load_KeepsGuids_AndGivesFreshSessionIds() {
        val scene = showcase()
        val loaded = loadInNewSession(save(scene), unrelatedObjects = 5)
        assertEquals(all(scene).map { it.guid }, all(loaded).map { it.guid })
        val sessionIds = all(loaded).map { it.uId } + all(scene).map { it.uId }
        assertEquals(sessionIds.size, sessionIds.toSet().size, "session ids are unique while the program runs")
        assertFalse(loaded.isDirty)
    }

    @Test
    fun Copy_GivesTheWholeTreeNewIds() {
        val hud = mainScene().children.single { it.name == "HUD" }
        assertTrue(hud.children.isNotEmpty())
        val copy = hud.copy(serializer)
        val original = all(hud).map { it.guid }.toSet()
        val copied = all(copy).map { it.guid }
        assertEquals(all(hud).size, copied.size)
        assertTrue(copied.none { it in original }, "a copy shares no guid with its source")
        assertTrue(all(copy).map { it.uId }.none { id -> all(hud).any { it.uId == id } })
    }

    @Test
    fun JointTarget_IsSavedByGuid_AndSurvivesLoad() {
        val scene = Scene("Joints")
        val a = GameObject("A").addComponent(Transform())
        val b = GameObject("B").addComponent(Transform())
        a.addComponent(JointComponent(jointType = JointType.HINGE, connectedBodyGuid = b.guid))
        scene.addChild(a)
        scene.addChild(b)
        val loaded = loadInNewSession(save(scene), unrelatedObjects = 9)
        val joint = loaded.children.single { it.name == "A" }.getComponent<JointComponent>()!!
        assertEquals(loaded.children.single { it.name == "B" }.guid, joint.connectedBodyGuid)
    }
}
