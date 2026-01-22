package com.pafoid.skate.observers

import marki.GameObject
import com.pafoid.skate.observers.events.Event

interface Observer {
    fun onNotify(event: Event, go: GameObject?)
}