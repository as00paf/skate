package com.pafoid.skate.observers

import marki.GameObject
import com.pafoid.skate.observers.events.Event
import com.pafoid.skate.observers.events.EventType

object EventSystem {

    private val observers = mutableListOf<Observer>()

    fun addObserver(observer: Observer) {
        observers.add(observer)
    }

    fun removeObserver(observer: Observer) {
        if(observers.contains(observer)) {
            observers.remove(observer)
        }
    }

    fun notify(event: Event, go: GameObject? = null) {
        observers.forEach { it.onNotify(event, go) }
    }

    fun notify(eventType: EventType, go: GameObject? = null) {
        observers.forEach { it.onNotify(Event(eventType), go) }
    }

}