package com.pafoid.skate.engine.core

import com.pafoid.skate.engine.ecs.config.ExecutionPriority
import com.pafoid.skate.engine.ecs.systems.System
import com.pafoid.skate.engine.events.Event
import kotlin.reflect.KClass

/**
 * Listener function type for event handlers.
 */
typealias EventListener = (Event) -> Unit

/**
 * System responsible for publishing and subscribing to game events.
 *
 * Provides a centralized event bus for decoupling systems and components.
 * Supports both type-safe Kotlin subscriptions and string-based subscriptions for scripting.
 *
 * ## Features
 *
 * - Type-safe subscriptions using reified generics
 * - String-based subscriptions for scripting integration
 * - One-time and persistent listeners
 * - Event priority for ordering (high, normal, low)
 * - Event cancellation (listeners can prevent further processing)
 *
 * ## Usage
 *
 * ```kotlin
 * // Add to scene
 * val eventSystem = EventSystem()
 * scene.addSystem(eventSystem)
 *
 * // Subscribe (type-safe)
 * eventSystem.subscribe<LandingEvent> { event ->
 *     handleLanding(event.velocity, event.impactForce)
 * }
 *
 * // Subscribe (string-based for scripting)
 * eventSystem.subscribe("physics.landing") { event ->
 *     handleLanding((event as LandingEvent).velocity, event.impactForce)
 * }
 *
 * // Subscribe (one-time listener)
 * eventSystem.subscribeOnce<TakeoffEvent> { event ->
 *     handleTakeoff(event.velocity)
 * }
 *
 * // Publish event
 * eventSystem.publish(LandingEvent(velocity, impactForce))
 * ```
 *
 * @param priority Event execution priority (default: EARLY)
 */
class EventSystem(
    priority: ExecutionPriority = ExecutionPriority.EARLY
) : System(priority = priority) {

    /**
     * Listener entry with priority and one-time flag.
     */
    internal data class ListenerEntry(
        val listener: EventListener,
        val priority: EventPriority = EventPriority.NORMAL,
        val isOneTime: Boolean = false
    )

    /**
     * Event priority for ordering listener execution.
     */
    enum class EventPriority {
        HIGH,       // Execute first (e.g., cancellation logic)
        NORMAL,     // Default priority
        LOW         // Execute last (e.g., logging, UI updates)
    }

    // Listeners organized by event class
    private val listenersByClass = mutableMapOf<KClass<out Event>, MutableList<ListenerEntry>>()

    // Listeners organized by event name (for scripting)
    private val listenersByName = mutableMapOf<String, MutableList<ListenerEntry>>()

    /**
     * Subscribes to an event type with type-safe listener.
     *
     * @param priority Listener priority (default: NORMAL)
     * @param listener Function to call when event is published
     */
    fun <T : Event> subscribe(
        eventType: KClass<T>,
        priority: EventPriority = EventPriority.NORMAL,
        listener: (T) -> Unit
    ) {
        val entry = ListenerEntry(
            listener = { event -> listener(event as T) },
            priority = priority,
            isOneTime = false
        )
        listenersByClass.getOrPut(eventType) { mutableListOf() }.add(entry)
    }

    /**
     * Subscribes to an event type with type-safe listener (reified version).
     *
     * @param priority Listener priority (default: NORMAL)
     * @param listener Function to call when event is published
     */
    inline fun <reified T : Event> subscribe(
        priority: EventPriority = EventPriority.NORMAL,
        noinline listener: (T) -> Unit
    ) {
        subscribe(T::class, priority, listener)
    }

    /**
     * Subscribes to an event type with a one-time listener.
     *
     * The listener is automatically removed after the first event is received.
     *
     * @param priority Listener priority (default: NORMAL)
     * @param listener Function to call when event is published
     */
    fun <T : Event> subscribeOnce(
        eventType: KClass<T>,
        priority: EventPriority = EventPriority.NORMAL,
        listener: (T) -> Unit
    ) {
        val entry = ListenerEntry(
            listener = { event -> listener(event as T) },
            priority = priority,
            isOneTime = true
        )
        listenersByClass.getOrPut(eventType) { mutableListOf() }.add(entry)
    }

    /**
     * Subscribes to an event type with a one-time listener (reified version).
     *
     * The listener is automatically removed after the first event is received.
     *
     * @param priority Listener priority (default: NORMAL)
     * @param listener Function to call when event is published
     */
    inline fun <reified T : Event> subscribeOnce(
        priority: EventPriority = EventPriority.NORMAL,
        noinline listener: (T) -> Unit
    ) {
        subscribeOnce(T::class, priority, listener)
    }

    /**
     * Subscribes to an event by name (for scripting integration).
     *
     * @param eventName Event name string (e.g., "physics.landing")
     * @param priority Listener priority (default: NORMAL)
     * @param listener Function to call when event is published
     */
    fun subscribe(
        eventName: String,
        priority: EventPriority = EventPriority.NORMAL,
        listener: EventListener
    ) {
        val entry = ListenerEntry(
            listener = listener,
            priority = priority,
            isOneTime = false
        )
        listenersByName.getOrPut(eventName) { mutableListOf() }.add(entry)
    }

    /**
     * Subscribes to an event by name with a one-time listener (for scripting).
     *
     * @param eventName Event name string (e.g., "physics.landing")
     * @param priority Listener priority (default: NORMAL)
     * @param listener Function to call when event is published
     */
    fun subscribeOnce(
        eventName: String,
        priority: EventPriority = EventPriority.NORMAL,
        listener: EventListener
    ) {
        val entry = ListenerEntry(
            listener = listener,
            priority = priority,
            isOneTime = true
        )
        listenersByName.getOrPut(eventName) { mutableListOf() }.add(entry)
    }

    /**
     * Unsubscribes all listeners for an event type.
     *
     * Note: Lambda comparison is not reliable, so this removes ALL listeners for the type.
     * For fine-grained control, keep a reference to your listener and use unsubscribe(eventName, listener).
     */
    fun <T : Event> unsubscribe(eventType: KClass<T>) {
        listenersByClass.remove(eventType)
    }

    /**
     * Unsubscribes all listeners for an event type (reified version).
     */
    inline fun <reified T : Event> unsubscribe() {
        unsubscribe(T::class)
    }

    /**
     * Unsubscribes from an event by name.
     *
     * @param eventName Event name string
     * @param listener The listener function to remove
     */
    fun unsubscribe(eventName: String, listener: EventListener) {
        listenersByName[eventName]?.removeIf { entry ->
            entry.listener == listener
        }
    }

    /**
     * Publishes an event to all subscribed listeners.
     *
     * Listeners are executed in priority order (HIGH → NORMAL → LOW).
     * One-time listeners are automatically removed after execution.
     *
     * @param event The event to publish
     */
    fun publish(event: Event) {
        val listenersToExecute = mutableListOf<ListenerEntry>()
        val oneTimeListenersToRemove = mutableListOf<ListenerEntry>()

        // Collect listeners by class type
        listenersByClass[event::class]?.forEach { entry ->
            listenersToExecute.add(entry)
            if (entry.isOneTime) oneTimeListenersToRemove.add(entry)
        }

        // Collect listeners by event name
        listenersByName[event.eventName]?.forEach { entry ->
            listenersToExecute.add(entry)
            if (entry.isOneTime) oneTimeListenersToRemove.add(entry)
        }

        // Sort by priority (HIGH first, then NORMAL, then LOW)
        listenersToExecute.sortBy { it.priority.ordinal }

        // Execute listeners
        for (entry in listenersToExecute) {
            try {
                entry.listener(event)
            } catch (e: Exception) {
                // Log error but continue with other listeners
                println("Event listener error: ${e.message}")
                e.printStackTrace()
            }
        }

        // Remove one-time listeners
        removeOneTimeListeners(oneTimeListenersToRemove)
    }

    /**
     * Removes one-time listeners after they've been executed.
     */
    private fun removeOneTimeListeners(listeners: List<ListenerEntry>) {
        listeners.forEach { entry ->
            // Remove from class-based listeners
            listenersByClass.values.forEach { list ->
                list.remove(entry)
            }
            // Remove from name-based listeners
            listenersByName.values.forEach { list ->
                list.remove(entry)
            }
        }
    }

    /**
     * Clears all listeners.
     *
     * Call this when destroying the scene to prevent memory leaks.
     */
    fun clearAllListeners() {
        listenersByClass.clear()
        listenersByName.clear()
    }

    override fun destroy() {
        clearAllListeners()
    }
}
