package com.pafoid.skate.engine.core

import com.pafoid.skate.engine.events.Event
import kotlin.reflect.KClass

class EventSystem {

    private val listeners = mutableMapOf<KClass<out Event>, MutableList<ListenerEntry>>()

    private data class ListenerEntry(
        val listener: (Event) -> Unit,
        val isOneTime: Boolean
    )

    fun <T : Event> subscribe(eventType: KClass<T>, listener: (T) -> Unit) {
        val entry = ListenerEntry(
            listener = { event -> @Suppress("UNCHECKED_CAST") listener(event as T) },
            isOneTime = false
        )
        listeners.getOrPut(eventType) { mutableListOf() }.add(entry)
    }

    fun <T : Event> subscribeOnce(eventType: KClass<T>, listener: (T) -> Unit) {
        val entry = ListenerEntry(
            listener = { event -> @Suppress("UNCHECKED_CAST") listener(event as T) },
            isOneTime = true
        )
        listeners.getOrPut(eventType) { mutableListOf() }.add(entry)
    }

    fun publish(event: Event) {
        val eventClass = event::class
        val eventListeners = listeners[eventClass] ?: return
        
        // We use a copy to allow listeners to modify the list (subscribe/unsubscribe) during execution
        val iterator = eventListeners.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            try {
                entry.listener(event)
            } catch (e: Exception) {
                System.err.println("Event listener error for ${eventClass.simpleName}: ${e.message}")
                e.printStackTrace()
            }
            if (entry.isOneTime) {
                iterator.remove()
            }
        }
    }

    fun <T : Event> unsubscribe(eventType: KClass<T>) {
        listeners.remove(eventType)
    }

    fun clearAllListeners() {
        listeners.clear()
    }

    fun destroy() {
        clearAllListeners()
    }

    // Reified helpers
    inline fun <reified T : Event> subscribe(noinline listener: (T) -> Unit) {
        subscribe(T::class, listener)
    }

    inline fun <reified T : Event> subscribeOnce(noinline listener: (T) -> Unit) {
        subscribeOnce(T::class, listener)
    }

    inline fun <reified T : Event> unsubscribe() {
        unsubscribe(T::class)
    }
}
