package com.pafoid.skate.engine.events

import org.joml.Vector3f


sealed class TrickEvent(eventName: String) : Event(eventName)

data class TrickDetected(val trickName: String, val rotation: Vector3f) : TrickEvent("trick.detected")
data class TrickCompleted(val trickName: String, val score: Int, val style: Float) : TrickEvent("trick.completed")
data class TrickCancelled(val reason: String) : TrickEvent("trick.cancelled")
