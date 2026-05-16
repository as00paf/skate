package com.pafoid.skate.engine.events

/**
 * Base class for all game events.
 *
 * Events are used to decouple systems and components, enabling event-driven architecture.
 * Each event has a string name for scripting integration and carries strongly-typed data.
 *
 * @property eventName Unique identifier for the event (e.g., "physics.landing", "input.jump_pressed")
 *
 * ## Usage
 *
 * ```kotlin
 * // Define custom event
 * data class MyEvent(val value: Int) : Event("my.event")
 *
 * // Publish event
 * eventSystem.publish(MyEvent(42))
 *
 * // Subscribe (type-safe)
 * eventSystem.subscribe<MyEvent> { event ->
 *     println("Received: ${event.value}")
 * }
 *
 * // Subscribe (string-based for scripting)
 * eventSystem.subscribe("my.event") { event ->
 *     val myEvent = event as MyEvent
 *     println("Received: ${myEvent.value}")
 * }
 * ```
 */
open class Event(val eventName: String)
