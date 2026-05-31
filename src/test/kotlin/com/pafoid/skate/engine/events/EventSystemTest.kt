package com.pafoid.skate.engine.events

import com.pafoid.skate.engine.core.EventSystem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for EventSystem.
 *
 * Tests cover:
 * - System initialization
 * - Type-safe subscriptions
 * - One-time listeners
 * - Unsubscribe functionality
 * - Error handling
 */
class EventSystemTest {

    private lateinit var eventSystem: EventSystem

    @BeforeEach
    fun setup() {
        eventSystem = EventSystem()
    }

    @Test
    fun `EventSystem initializes correctly`() {
        assertNotNull(eventSystem, "EventSystem should be created")
    }

    @Test
    fun `EventSystem destroy clears all listeners`() {
        // Arrange
        var received = false
        eventSystem.subscribe<Landing> { received = true }

        // Act
        eventSystem.destroy()

        // Assert
        eventSystem.publish(Landing(org.joml.Vector3f(0f, 0f, 0f), 10f))
        assertFalse(received, "No listeners should remain after destroy")
    }

    @Test
    fun `subscribe receives typed events`() {
        // Arrange
        var receivedVelocity: org.joml.Vector3f? = null
        eventSystem.subscribe<Landing> { event ->
            receivedVelocity = event.velocity
        }

        // Act
        val testVelocity = org.joml.Vector3f(1f, 2f, 3f)
        eventSystem.publish(Landing(testVelocity, 10f))

        // Assert
        assertNotNull(receivedVelocity, "Should receive typed event data")
        assertEquals(testVelocity.x, receivedVelocity!!.x, 0.01f)
        assertEquals(testVelocity.y, receivedVelocity!!.y, 0.01f)
        assertEquals(testVelocity.z, receivedVelocity!!.z, 0.01f)
    }

    @Test
    fun `subscribe receives multiple events of same type`() {
        // Arrange
        val receivedCount = intArrayOf(0)
        eventSystem.subscribe<Takeoff> { receivedCount[0]++ }

        // Act
        eventSystem.publish(Takeoff(org.joml.Vector3f(0f, 1f, 0f)))
        eventSystem.publish(Takeoff(org.joml.Vector3f(0f, 1f, 0f)))
        eventSystem.publish(Takeoff(org.joml.Vector3f(0f, 1f, 0f)))

        // Assert
        assertEquals(3, receivedCount[0], "Should receive all events")
    }

    @Test
    fun `subscribeOnce receives only first event`() {
        // Arrange
        var callCount = 0
        eventSystem.subscribeOnce<Landing> { callCount++ }

        // Act
        eventSystem.publish(Landing(org.joml.Vector3f(0f, 0f, 0f), 10f))
        eventSystem.publish(Landing(org.joml.Vector3f(0f, 0f, 0f), 20f))

        // Assert
        assertEquals(1, callCount, "One-time listener should only be called once")
    }

    @Test
    fun `unsubscribe removes all listeners for event type`() {
        // Arrange
        var received = false
        eventSystem.subscribe<Landing> { received = true }

        // Act
        eventSystem.unsubscribe<Landing>()
        eventSystem.publish(Landing(org.joml.Vector3f(0f, 0f, 0f), 10f))

        // Assert
        assertFalse(received, "Unsubscribed listener should not receive events")
    }

    @Test
    fun `clearAllListeners removes all subscriptions`() {
        // Arrange
        var receivedA = false
        var receivedB = false

        eventSystem.subscribe<Landing> { receivedA = true }
        eventSystem.subscribe<Takeoff> { receivedB = true }

        // Act
        eventSystem.clearAllListeners()
        eventSystem.publish(Landing(org.joml.Vector3f(0f, 0f, 0f), 10f))
        eventSystem.publish(Takeoff(org.joml.Vector3f(0f, 1f, 0f)))

        // Assert
        assertFalse(receivedA, "Cleared listener A should not receive events")
        assertFalse(receivedB, "Cleared listener B should not receive events")
    }

    @Test
    fun `listener exception does not prevent other listeners from executing`() {
        // Arrange
        var firstExecuted = false
        var thirdExecuted = false

        eventSystem.subscribe<Landing> { firstExecuted = true }
        eventSystem.subscribe<Landing> { throw RuntimeException("Test exception") }
        eventSystem.subscribe<Landing> { thirdExecuted = true }

        // Act & Assert
        eventSystem.publish(Landing(org.joml.Vector3f(0f, 0f, 0f), 10f))

        assertTrue(firstExecuted, "First listener should execute")
        assertTrue(thirdExecuted, "Third listener should execute despite exception in second")
    }

    @Test
    fun `event data is correctly passed to listeners`() {
        // Arrange
        val testVelocity = org.joml.Vector3f(1f, 2f, 3f)
        val testForce = 50f
        var receivedForce: Float? = null

        eventSystem.subscribe<Landing> { event ->
            receivedForce = event.impactForce
        }

        // Act
        eventSystem.publish(Landing(testVelocity, testForce))

        // Assert
        assertEquals(testForce, receivedForce!!, 0.01f, "Event data should be passed correctly")
    }
}
