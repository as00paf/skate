package com.pafoid.skate.game.samples

import com.pafoid.skate.engine.getAllChildren
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
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.CameraControlSystem
import com.pafoid.skate.engine.ecs.systems.CameraManager
import com.pafoid.skate.engine.ecs.systems.FlyInput
import com.pafoid.skate.engine.ecs.systems.GameObjectManager
import com.pafoid.skate.engine.ecs.systems.SystemManager
import com.pafoid.skate.engine.ecs.systems.TransformSystem
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.ui.UICanvasComponent
import com.pafoid.skate.engine.ui.UIElementComponent
import com.pafoid.skate.engine.ui.UiInputFrame
import com.pafoid.skate.engine.ui.UiInputSystem
import com.pafoid.skate.engine.ui.UiLayoutSystem
import com.pafoid.skate.engine.ui.UiScreenSize
import io.mockk.every
import io.mockk.mockk
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The human's path for the showcase legend (TLG-09): default scenes generated and saved, the showcase decoded from
 * disk like ProjectManager.loadDefaultScenes, opened through SceneManager with the Play-time systems, then a real
 * pointer press/release over a legend button through the UI input pipeline.
 */
class FeatureShowcasePlayTest {

    private val dir: File = kotlin.io.path.createTempDirectory("showcase-play").toFile()

    @AfterEach
    fun cleanup() { runCatching { dir.deleteRecursively() } }

    private var pointer = UiInputFrame()
    private var fly = FlyInput()
    private val systems = SystemManager()
    private val serializer = Serializer()
    private val logger = LoggerService()
    private val assets = mockk<AssetsManager>(relaxed = true).also {
        every { it.loadModel(any()) } answers { `3dModel`(firstArg(), skeleton = Skeleton(Bone(0, "root"), 1)) }
        every { it.loadAnimationSync(any()) } answers { Animation("clip", emptyList(), 1f, firstArg()) }
        every { it.getTexture(any()) } answers { Texture(filePath = firstArg()) }
    }
    private val events = EventSystem()
    private val sceneManager = SceneManager(assets, events, serializer, systems, logger)

    private fun loadShowcaseFromDisk(): Scene {
        val engine = mockk<Engine>(relaxed = true)
        every { engine.assetsManager } returns assets
        every { engine.sceneManager } returns sceneManager
        every { engine.logger } returns logger
        val scenesDir = File(dir, "Scenes")
        PrefabsGenerator(engine).apply { projectAssetsDir = File(dir, "Assets").path + File.separator }
            .createDefaultScenes(scenesDir)
        sceneManager.closeAllScenes()

        listOf(
            GameObjectManager(), TransformSystem(), CameraManager(events),
            UiLayoutSystem(screen = { UiScreenSize(1920, 1080) }),
            UiInputSystem { pointer },
            CameraControlSystem { fly },
        ).forEach { systems.addSystem(it) }
        systems.start()
        val text = File(scenesDir, "${FeatureShowcase.SCENE_NAME}.scene").readText()
        val scene = serializer.decodeFile<Scene?>(FileKind.SCENE, text)!!
        sceneManager.openScenes(listOf(scene))
        return scene
    }

    /** One editor frame while playing (Engine.updateRunningState). */
    private fun frame(scene: Scene, input: UiInputFrame = UiInputFrame(pointer.pointerX, pointer.pointerY)) {
        pointer = input
        systems.update(1f / 60f)
        scene.isRunning = true
        scene.update(1f / 60f)
    }

    private fun buttonCentre(scene: Scene, name: String): Pair<Float, Float> {
        val canvas = scene.children.single { it.getComponent<UICanvasComponent>() != null && it.name != FeatureShowcase.MAP_CANVAS }
        val scale = canvas.getComponent<UICanvasComponent>()!!.scale
        val rect = canvas.getAllChildren().single { it.name == name }.getComponent<UIElementComponent>()!!.rect
        return (rect.x + rect.w / 2f) * scale to (rect.y + rect.h / 2f) * scale
    }

    private fun click(scene: Scene, x: Float, y: Float) {
        frame(scene, UiInputFrame(x, y))
        frame(scene, UiInputFrame(x, y, pointerDown = true, pointerPressed = true))
        frame(scene, UiInputFrame(x, y, pointerDown = false, pointerReleased = true))
    }

    @Test
    fun `clicking a legend button in Play flies the saved and reloaded camera to that area`() {
        val scene = loadShowcaseFromDisk()
        scene.isRunning = true
        frame(scene) // layout pass
        val camera = scene.children.single { it.name == "Camera" }.getComponent<Transform>()!!
        val start = Vector3f(camera.translation)
        val target = FeatureShowcase.focusTarget(FeatureShowcase.AREAS.single { it.key == "vehicle" })

        val (x, y) = buttonCentre(scene, FeatureShowcase.focusButtonName("vehicle"))
        click(scene, x, y)
        repeat(240) { frame(scene) }

        assertTrue(camera.translation.distance(start) > 1f, "camera did not move from $start")
        assertTrue(camera.translation.distance(target.position) < 0.05f, "camera at ${camera.translation}, expected ${target.position}")

        val (ox, oy) = buttonCentre(scene, FeatureShowcase.focusButtonName(FeatureShowcase.OVERVIEW))
        click(scene, ox, oy)
        repeat(240) { frame(scene) }
        assertTrue(camera.translation.distance(start) < 0.05f, "overview did not return: ${camera.translation}")
        assertEquals(FeatureShowcase.overviewTarget().rotation.x, camera.rotation.x, 0.1f)
    }

    @Test
    fun `free-fly moves and turns the reloaded camera in Play and resumes after a focus move`() {
        val scene = loadShowcaseFromDisk()
        scene.isRunning = true
        frame(scene)
        val camera = scene.children.single { it.name == "Camera" }.getComponent<Transform>()!!

        val (x, y) = buttonCentre(scene, FeatureShowcase.focusButtonName("joints"))
        click(scene, x, y)
        repeat(240) { frame(scene) }
        val focused = Vector3f(camera.translation)
        val view = com.pafoid.skate.engine.ecs.components.FreeFlyCameraComponent.forward(camera.rotation)

        fly = FlyInput(forward = 1f) // hold W for one second
        repeat(60) { frame(scene) }
        fly = FlyInput()
        val travelled = Vector3f(camera.translation).sub(focused)
        assertEquals(8f, travelled.length(), 0.05f)
        assertTrue(travelled.normalize().distance(view) < 1e-3f, "flew along $travelled, view $view")

        val yaw = camera.rotation.y
        fly = FlyInput(lookDx = 50f, lookDy = 20f) // right-drag
        frame(scene)
        fly = FlyInput()
        assertEquals(yaw + 5f, camera.rotation.y, 1e-3f)
        frame(scene)
        assertEquals(yaw + 5f, camera.rotation.y, 1e-3f) // nothing glides it back
    }
}
