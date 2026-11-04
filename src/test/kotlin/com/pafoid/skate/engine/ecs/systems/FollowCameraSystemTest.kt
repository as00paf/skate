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
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.ComponentType
import com.pafoid.skate.engine.ecs.components.FollowCameraComponent
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.events.Collision
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.physics3d.bullet.BulletPhysics3D
import com.pafoid.skate.engine.physics3d.native.NativeLibraryLoader
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import io.mockk.every
import io.mockk.mockk
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class FollowCameraSystemTest {

    private val dt = 1f / 60f

    private fun sceneWithCamera(events: EventSystem? = null, probe: CameraProbe? = null): Triple<Scene, FollowCameraSystem, GameObject> {
        val scene = Scene("test")
        scene.addChild(GameObject("Skater").addComponent(Transform(Vector3f(2f, 0f, 0f))))
        val camera = GameObject("Camera").addComponent(Transform(Vector3f(0f, 9f, 9f))).addComponent(FollowCameraComponent())
        scene.addChild(camera)
        val system = FollowCameraSystem(probe, events)
        system.init(scene)
        return Triple(scene, system, camera)
    }

    @Test
    fun FollowCameraSystem_EditModeLeavesThePlacedCameraAlone() {
        val (_, system, camera) = sceneWithCamera()

        system.update(dt)

        assertEquals(Vector3f(0f, 9f, 9f), camera.getComponent<Transform>()!!.translation)
    }

    @Test
    fun FollowCameraSystem_PlayFollowsTheNamedTargetAndProbesWithoutItsOwnBody() {
        var excluded: GameObject? = null
        val (scene, system, camera) = sceneWithCamera(probe = { _, _, _, exclude -> excluded = exclude; null })
        scene.isRunning = true

        system.update(dt)

        val eye = camera.getComponent<Transform>()!!.translation
        assertEquals(2f, eye.x, 1e-3f)
        assertTrue(eye.z < -3f, "camera not behind the skater: $eye")
        assertEquals("Skater", excluded?.name)
    }

    @Test
    fun FollowCameraSystem_StoppingPlaySnapsBehindTheTargetOnTheNextPlay() {
        val (scene, system, camera) = sceneWithCamera()
        val follow = camera.getComponent<FollowCameraComponent>()!!
        scene.isRunning = true
        system.update(dt)
        assertTrue(follow.state.initialized)

        scene.isRunning = false
        system.update(dt)

        assertTrue(!follow.state.initialized)
    }

    @Test
    fun FollowCameraSystem_HardImpactsOnTheTargetShakeTheCamera() {
        val events = EventSystem()
        val (scene, system, camera) = sceneWithCamera(events)
        val follow = camera.getComponent<FollowCameraComponent>()!!
        val skater = scene.children.first { it.name == "Skater" }
        scene.isRunning = true
        system.update(dt)

        events.publish(Collision(skater, camera, Vector3f(), Vector3f(), impulse = follow.impactShakeImpulse - 1f))
        events.publish(Collision(camera, skater, Vector3f(), Vector3f(), impulse = 1000f))
        system.update(dt)
        assertEquals(0f, follow.state.trauma, 0f)

        events.publish(Collision(skater, camera, Vector3f(), Vector3f(), impulse = follow.impactShakeImpulse))
        system.update(dt)
        assertTrue(follow.state.trauma > 0.4f)
    }

    @Test
    fun FollowCameraComponent_RoundTripsThroughSceneJsonAndIsAddableInTheEditor() {
        val serializer = Serializer()
        val original = FollowCameraComponent(targetName = "Rider", distance = 7f, probeRadius = 0.4f, collision = false)
        original.shake(1f)

        val decoded = serializer.decode<Component>(serializer.encode<Component>(original)) as FollowCameraComponent

        assertEquals("Rider", decoded.targetName)
        assertEquals(7f, decoded.distance)
        assertEquals(0.4f, decoded.probeRadius)
        assertTrue(!decoded.collision)
        // Runtime state is not saved.
        assertEquals(0f, decoded.state.trauma)
        assertTrue(ComponentType.FOLLOW_CAMERA.instantiate() is FollowCameraComponent)
    }
}

/**
 * GPL-05 on the human's path: the default project is generated and saved, MainScene is decoded from disk and opened
 * with real Bullet physics, then played; the camera's spring arm hits the quarter pipe behind the skater.
 */
class FollowCameraFromDiskTest {

    @Test
    fun DefaultScene_FollowCamFromDiskPullsInFrontOfTheQuarterPipe() {
        val dir = kotlin.io.path.createTempDirectory("gpl05").toFile()
        val debug = mockk<DebugRenderer>(relaxed = true)
        NativeLibraryLoader().loadNativeLibrary()
        val world = BulletPhysics3D(debug)
        try {
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

            val physics = PhysicsSystem(debug, physics3d = world)
            val follow = FollowCameraSystem({ from, to, r, exclude -> physics.sphereCast(from, to, r, exclude.getComponent<RigidBody3D>()) }, events)
            listOf(GameObjectManager(), TransformSystem(), physics, follow).forEach { systems.addSystem(it) }
            systems.start()
            val text = File(scenesDir, "${PrefabsGenerator.DEFAULT_SCENE}.scene").readText()
            val scene = serializer.decodeFile<Scene?>(FileKind.SCENE, text)!!
            sceneManager.openScenes(listOf(scene))

            val camera = scene.children.single { it.name == "Camera" }
            val cam = camera.getComponent<FollowCameraComponent>()
            assertNotNull(cam)
            cam!!
            assertEquals("Skater", cam.targetName)
            val skater = scene.children.single { it.name == "Skater" }
            val pipe = scene.children.single { it.name == "QuarterPipe" }
            scene.start()
            physics.fixedUpdate(dt())

            // At the spawn the way behind the skater is clear: full arm.
            follow.update(dt())
            assertEquals(cam.state.distance, cam.state.armLength, 1e-3f)

            // Backed up to the quarter pipe with a level camera, the arm stops in front of the ramp instead of
            // going through it.
            cam.pitch = 0f
            cam.state.initialized = false
            skater.getComponent<Transform>()!!.translation.set(0f, 0.15f, -3.5f)
            physics.teleport(skater)
            physics.fixedUpdate(dt())
            follow.update(dt())

            assertTrue(cam.state.armLength < cam.state.distance - 0.5f, "arm ${cam.state.armLength} of ${cam.state.distance}")
            val eye = camera.getComponent<Transform>()!!.translation
            val pivot = cam.state.pivot
            assertNull(world.raycast(pivot, eye, excludeBody = skater.getComponent<RigidBody3D>()), "camera behind the ramp surface at $eye")
            // The pivot clears the ramp's 1.5 m top but the probe sphere does not: a ray along the sphere's bottom hits it.
            val low = Vector3f(pivot).sub(0f, cam.probeRadius * 0.9f, 0f)
            val blocker = world.raycast(low, Vector3f(low).add(0f, 0f, -cam.state.distance), excludeBody = skater.getComponent<RigidBody3D>())
            assertEquals(pipe, blocker?.gameObject, "the quarter pipe should be what blocks the arm")
        } finally {
            world.destroy()
            runCatching { dir.deleteRecursively() }
        }
    }

    private fun dt() = 1f / 60f
}
