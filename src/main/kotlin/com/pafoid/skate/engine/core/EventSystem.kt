package com.pafoid.skate.engine.core

import com.pafoid.skate.engine.events.Event
import kotlin.reflect.KClass

typealias EventListener = (Event) -> Unit

class EventSystem {

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

    fun unsubscribe(eventName: String, listener: EventListener) {
        listenersByName[eventName]?.removeIf { entry ->
            entry.listener == listener
        }
    }

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

    fun clearAllListeners() {
        listenersByClass.clear()
        listenersByName.clear()
    }

    fun destroy() {
        clearAllListeners()
    }

    inline fun <reified T : Event> unsubscribe() {
        unsubscribe(T::class)
    }

    fun <T : Event> unsubscribe(eventType: KClass<T>) {
        listenersByClass.remove(eventType)
    }

    inline fun <reified T : Event> subscribe(
        priority: EventPriority = EventPriority.NORMAL,
        noinline listener: (T) -> Unit
    ) {
        subscribe(T::class, priority, listener)
    }

    inline fun <reified T : Event> subscribeOnce(
        priority: EventPriority = EventPriority.NORMAL,
        noinline listener: (T) -> Unit
    ) {
        subscribeOnce(T::class, priority, listener)
    }

}
