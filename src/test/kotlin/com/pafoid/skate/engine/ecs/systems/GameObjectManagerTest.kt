package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.physics3d.IPhysics3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GameObjectManagerTest {

    private lateinit var scene: Scene
    private lateinit var physics3d: IPhysics3D
    private lateinit var gameObjectManager: GameObjectManager
    private lateinit var gameObjects: MutableList<GameObject>
    private lateinit var pendingObjects: MutableList<GameObject>

    @BeforeEach
    fun setup() {
        scene = mockk(relaxed = true)
        physics3d = mockk(relaxed = true)
        gameObjects = mutableListOf()
        pendingObjects = mutableListOf()

        every { scene.physics3d } returns physics3d
        every { scene.gameObjects } returns gameObjects
        every { scene.pendingObjects } returns pendingObjects
        every { scene.markObjectSetChanged() } returns Unit

        gameObjectManager = GameObjectManager()
        gameObjectManager.init(scene)
    }

    @Test
    fun `update registers existing rigid bodies when play mode starts`() {
        val dynamicObject = GameObject("Dynamic").addComponent(RigidBody3D())
        gameObjects.add(dynamicObject)

        every { scene.isRunning } returnsMany listOf(false, true)

        gameObjectManager.update(0.016f)
        gameObjectManager.update(0.016f)

        verify(exactly = 1) { physics3d.add(dynamicObject) }
    }

    @Test
    fun `update registers missing rigid body during runtime updates`() {
        every { scene.isRunning } returns true

        gameObjectManager.update(0.016f)

        val dynamicObject = GameObject("LateDynamic").addComponent(RigidBody3D())
        gameObjects.add(dynamicObject)

        gameObjectManager.update(0.016f)

        verify(exactly = 1) { physics3d.add(dynamicObject) }
    }
}
