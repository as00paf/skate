package com.pafoid.skate.engine.events

import com.pafoid.skate.engine.core.EventListener
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.config.ExecutionPriority
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
 * - String-based subscriptions
 * - One-time listeners
 * - Event priority ordering
 * - Unsubscribe functionality
 */
class EventSystemTest {

    private lateinit var eventSystem: EventSystem
    private lateinit var scene: Scene

    @BeforeEach
    fun setup() {
        scene = Scene("TestScene")
        eventSystem = EventSystem()
        eventSystem.init(scene)
    }

    // =========================================================================
    // INITIALIZATION TESTS
    // =========================================================================

    @Test
    fun `EventSystem initializes correctly`() {
        // Assert
        assertNotNull(eventSystem, "EventSystem should be created")
        assertEquals(ExecutionPriority.EARLY, eventSystem.priority, "System should run EARLY")
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

    // =========================================================================
    // TYPE-SAFE SUBSCRIPTION TESTS
    // =========================================================================

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
    fun `subscribe to multiple event types`() {
        // Arrange
        var landingReceived = false
        var takeoffReceived = false

        eventSystem.subscribe<Landing> { landingReceived = true }
        eventSystem.subscribe<Takeoff> { takeoffReceived = true }

        // Act
        eventSystem.publish(Landing(org.joml.Vector3f(0f, 0f, 0f), 10f))
        eventSystem.publish(Takeoff(org.joml.Vector3f(0f, 1f, 0f)))

        // Assert
        assertTrue(landingReceived, "Landing event should be received")
        assertTrue(takeoffReceived, "Takeoff event should be received")
    }

    // =========================================================================
    // ONE-TIME LISTENER TESTS
    // =========================================================================

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
    fun `subscribeOnce with reified type`() {
        // Arrange
        var received = false
        eventSystem.subscribeOnce<GroundedStateChanged> { received = true }

        // Act
        eventSystem.publish(GroundedStateChanged(true))

        // Assert
        assertTrue(received, "One-time listener should receive the event")
    }

    // =========================================================================
    // STRING-BASED SUBSCRIPTION TESTS
    // =========================================================================

    @Test
    fun `string-based subscription receives events`() {
        // Arrange
        var receivedEvent: Event? = null
        eventSystem.subscribe("physics.landing") { event ->
            receivedEvent = event
        }

        // Act
        eventSystem.publish(Landing(org.joml.Vector3f(0f, 0f, 0f), 10f))

        // Assert
        assertNotNull(receivedEvent, "Should receive event")
        assertEquals("physics.landing", receivedEvent!!.eventName, "Event name should match")
    }

    @Test
    fun `string-based subscribeOnce receives only first event`() {
        // Arrange
        var callCount = 0
        eventSystem.subscribeOnce("physics.landing") { callCount++ }

        // Act
        eventSystem.publish(Landing(org.joml.Vector3f(0f, 0f, 0f), 10f))
        eventSystem.publish(Landing(org.joml.Vector3f(0f, 0f, 0f), 20f))

        // Assert
        assertEquals(1, callCount, "One-time string listener should only be called once")
    }

    // =========================================================================
    // PRIORITY TESTS
    // =========================================================================

    @Test
    fun `listeners execute in priority order`() {
        // Arrange
        val executionOrder = mutableListOf<String>()

        eventSystem.subscribe<Landing>(EventSystem.EventPriority.LOW) {
            executionOrder.add("low")
        }
        eventSystem.subscribe<Landing>(EventSystem.EventPriority.HIGH) {
            executionOrder.add("high")
        }
        eventSystem.subscribe<Landing>(EventSystem.EventPriority.NORMAL) {
            executionOrder.add("normal")
        }

        // Act
        eventSystem.publish(Landing(org.joml.Vector3f(0f, 0f, 0f), 10f))

        // Assert
        assertEquals(listOf("high", "normal", "low"), executionOrder, "Should execute in priority order")
    }

    @Test
    fun `multiple listeners at same priority execute in registration order`() {
        // Arrange
        val executionOrder = mutableListOf<Int>()

        eventSystem.subscribe<Landing> { executionOrder.add(1) }
        eventSystem.subscribe<Landing> { executionOrder.add(2) }
        eventSystem.subscribe<Landing> { executionOrder.add(3) }

        // Act
        eventSystem.publish(Landing(org.joml.Vector3f(0f, 0f, 0f), 10f))

        // Assert
        assertEquals(listOf(1, 2, 3), executionOrder, "Should execute in registration order")
    }

    // =========================================================================
    // UNSUBSCRIBE TESTS
    // =========================================================================

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
    fun `unsubscribe by name removes specific listener`() {
        // Arrange
        var receivedA = false
        var receivedB = false

        val listenerA: EventListener = { receivedA = true }
        val listenerB: EventListener = { receivedB = true }

        eventSystem.subscribe("physics.landing", listener = listenerA)
        eventSystem.subscribe("physics.takeoff", listener = listenerB)

        // Act
        eventSystem.unsubscribe("physics.landing", listenerA)
        eventSystem.publish(Landing(org.joml.Vector3f(0f, 0f, 0f), 10f))
        eventSystem.publish(Takeoff(org.joml.Vector3f(0f, 1f, 0f)))

        // Assert
        assertFalse(receivedA, "Unsubscribed listener should not receive events")
        assertTrue(receivedB, "Other listener should still receive events")
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

    // =========================================================================
    // ERROR HANDLING TESTS
    // =========================================================================

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

    // =========================================================================
    // INTEGRATION TESTS
    // =========================================================================

    @Test
    fun `both type-safe and string-based listeners receive same event`() {
        // Arrange
        var typeSafeReceived = false
        var stringBasedReceived = false

        eventSystem.subscribe<Landing> { typeSafeReceived = true }
        eventSystem.subscribe("physics.landing") { stringBasedReceived = true }

        // Act
        eventSystem.publish(Landing(org.joml.Vector3f(0f, 0f, 0f), 10f))

        // Assert
        assertTrue(typeSafeReceived, "Type-safe listener should receive event")
        assertTrue(stringBasedReceived, "String-based listener should receive event")
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
