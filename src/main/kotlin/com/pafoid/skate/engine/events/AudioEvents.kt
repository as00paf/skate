package com.pafoid.skate.engine.events

import org.joml.Vector3f


sealed class AudioEvent(eventName: String) : Event(eventName)

data class AudioPlay(val sourceName: String, val is3D: Boolean) : AudioEvent("audio.play")
data class AudioStop(val sourceName: String) : AudioEvent("audio.stop")
data class AudioVolumeChange(val sourceName: String, val volume: Float) : AudioEvent("audio.volume_change")
data class AudioListenerMove(val position: Vector3f, val velocity: Vector3f) : AudioEvent("audio.listener_move")
