package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.game.player.PlayerState
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlayerStateManagerTest {

    @Test
    fun `state manager picks up physics component added after first update`() {
        val gameObject = GameObject("Skater")
        val controller = PlayerController()
        val stateManager = PlayerStateManager()

        gameObject.addComponent(controller)
        gameObject.addComponent(stateManager)

        controller.isGrounded = true
        controller.desiredMoveDirection.set(1f, 0f, 0f)

        stateManager.update(0.016f) // First update without PhysicsComponent

        val physics = PhysicsComponent()
        physics.updateFromPhysics(Vector3f(2f, 0f, 0f), Vector3f())
        gameObject.addComponent(physics)

        stateManager.update(0.016f)

        assertEquals(PlayerState.WALKING, stateManager.currentState)
    }

    @Test
    fun `state manager transitions to falling when no longer grounded`() {
        val gameObject = GameObject("Skater")
        val controller = PlayerController()
        val stateManager = PlayerStateManager()
        val physics = PhysicsComponent()

        gameObject.addComponent(controller)
        gameObject.addComponent(stateManager)
        gameObject.addComponent(physics)

        controller.isGrounded = false
        controller.isJumping = false
        controller.desiredMoveDirection.set(0f, 0f, 0f)
        physics.updateFromPhysics(Vector3f(0f, 0f, 0f), Vector3f())

        stateManager.update(0.016f)

        assertEquals(PlayerState.FALLING, stateManager.currentState)
    }

    @Test
    fun `state manager transitions between move and idle from runtime motion`() {
        val gameObject = GameObject("Skater")
        val controller = PlayerController()
        val stateManager = PlayerStateManager()
        val physics = PhysicsComponent()

        gameObject.addComponent(controller)
        gameObject.addComponent(stateManager)
        gameObject.addComponent(physics)

        controller.isGrounded = true
        controller.desiredMoveDirection.set(1f, 0f, 0f)
        physics.updateFromPhysics(Vector3f(3f, 0f, 0f), Vector3f())
        stateManager.update(0.016f)
        assertEquals(PlayerState.WALKING, stateManager.currentState)

        controller.desiredMoveDirection.zero()
        physics.updateFromPhysics(Vector3f(0f, 0f, 0f), Vector3f())
        stateManager.update(0.016f)
        assertEquals(PlayerState.IDLE, stateManager.currentState)
    }
}
