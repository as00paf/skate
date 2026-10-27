package com.pafoid.skate.engine.core

import com.pafoid.skate.engine.events.Event
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KClass

/**
 * Thread-safe publish/subscribe. Subscriber lists are copy-on-write, so listeners may
 * (un)subscribe during a publish; handler exceptions are logged and never break publishing.
 */
class EventSystem(private val logger: LoggerService = LoggerService()) {

    private val listeners = ConcurrentHashMap<KClass<out Event>, CopyOnWriteArrayList<ListenerEntry>>()

    private class ListenerEntry(
        val listener: (Event) -> Unit,
        val isOneTime: Boolean
    ) {
        val fired = AtomicBoolean(false)
    }

    fun <T : Event> subscribe(eventType: KClass<T>, listener: (T) -> Unit) {
        add(eventType, listener, isOneTime = false)
    }

    fun <T : Event> subscribeOnce(eventType: KClass<T>, listener: (T) -> Unit) {
        add(eventType, listener, isOneTime = true)
    }

    private fun <T : Event> add(eventType: KClass<T>, listener: (T) -> Unit, isOneTime: Boolean) {
        val entry = ListenerEntry(
            listener = { event -> @Suppress("UNCHECKED_CAST") listener(event as T) },
            isOneTime = isOneTime
        )
        listeners.computeIfAbsent(eventType) { CopyOnWriteArrayList() }.add(entry)
    }

    fun publish(event: Event) {
        val eventClass = event::class
        val eventListeners = listeners[eventClass] ?: return

        // Iterating a copy-on-write snapshot allows (un)subscribing from within a listener
        for (entry in eventListeners) {
            if (entry.isOneTime) {
                if (!entry.fired.compareAndSet(false, true)) continue
                eventListeners.remove(entry)
            }
            try {
                entry.listener(event)
            } catch (e: Exception) {
                logger.logError("Event listener error for ${eventClass.simpleName}", e)
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
