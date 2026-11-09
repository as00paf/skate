package com.pafoid.skate.engine.physics3d

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.BoxCollider3D
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.ScenePhysicsComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.PhysicsSystem
import com.pafoid.skate.engine.ecs.systems.SystemManager
import com.pafoid.skate.engine.physics3d.native.NativeLibraryLoader
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/** Regression tests for PHY-10: the physics debug view must emit collider wireframes. */
class PhysicsDebugDrawTest {

    private lateinit var debugRenderer: DebugRenderer

    @BeforeEach
    fun setUp() {
        NativeLibraryLoader().loadNativeLibrary()
        debugRenderer = mockk(relaxed = true)
    }

    private fun boxObject(): GameObject = GameObject("Box").apply {
        addComponent(Transform().apply { translation.set(0f, 1f, 0f) })
        addComponent(RigidBody3D(1f))
        addComponent(BoxCollider3D(Vector3f(0.5f)))
    }

    private fun sceneWithBox(debugEnabled: Boolean = false): Scene = Scene().apply {
        addComponent(ScenePhysicsComponent(debugEnabled = debugEnabled))
        addChild(boxObject())
    }

    private fun physicsSystemFor(scene: Scene): Pair<SystemManager, PhysicsSystem> {
        val physics = PhysicsSystem(debugRenderer)
        val manager = SystemManager()
        manager.addSystem(physics)
        manager.loadScene(scene)
        manager.start()
        return manager to physics
    }

    @ParameterizedTest
    @MethodSource("com.pafoid.skate.engine.physics3d.PhysicsBackends#core")
    fun debugDraw_Enabled_DrawsBoxWireframe(backend: PhysicsBackend) {
        val physics = PhysicsBackends.create(backend, debugRenderer)
        physics.add(boxObject())
        physics.debugEnabled = true

        physics.debugDraw(0f)

        verify(exactly = 1) { debugRenderer.addBox3D(any(), any(), any(), any()) }
        physics.destroy()
    }

    @ParameterizedTest
    @MethodSource("com.pafoid.skate.engine.physics3d.PhysicsBackends#core")
    fun debugDraw_Disabled_DrawsNothing(backend: PhysicsBackend) {
        val physics = PhysicsBackends.create(backend, debugRenderer)
        physics.add(boxObject())

        physics.debugDraw(0f)

        verify(exactly = 0) { debugRenderer.addBox3D(any(), any(), any(), any()) }
        physics.destroy()
    }

    @Test
    fun toggleDebug_InEditMode_FrameDrawsColliderLines() {
        val (_, physics) = physicsSystemFor(sceneWithBox())

        physics.toggleDebug()
        physics.onFrame(0f, 1f)

        verify(exactly = 1) { debugRenderer.addBox3D(any(), any(), any(), any()) }
    }

    @Test
    fun toggleDebug_WritesScenePhysicsComponent_SoToolbarStateMatches() {
        val scene = sceneWithBox()
        val (_, physics) = physicsSystemFor(scene)

        physics.toggleDebug()
        assertTrue(scene.getSceneComponent().debugEnabled)
        assertTrue(physics.isDebugEnabled())

        physics.toggleDebug()
        assertFalse(scene.getSceneComponent().debugEnabled)
        assertFalse(physics.isDebugEnabled())
    }

    @Test
    fun toggleDebug_SurvivesSceneReinit() {
        val scene = sceneWithBox()
        val (manager, physics) = physicsSystemFor(scene)
        physics.toggleDebug()

        manager.loadScene(scene) // play / snapshot restore re-runs init
        clearMocks(debugRenderer, answers = false)
        physics.onFrame(0f, 1f)

        assertTrue(physics.isDebugEnabled())
        verify(exactly = 1) { debugRenderer.addBox3D(any(), any(), any(), any()) }
    }

    private fun Scene.getSceneComponent(): ScenePhysicsComponent =
        components.filterIsInstance<ScenePhysicsComponent>().first()
}
