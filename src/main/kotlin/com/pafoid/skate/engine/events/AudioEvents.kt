package com.pafoid.skate.engine.events

import org.joml.Vector3f

/**
 * Audio-related events for the event system.
 */

/**
 * Event published when an audio source starts playing.
 * @param sourceName Name of the audio source
 * @param is3D Whether the sound is 3D spatialized
 */
class AudioPlay(val sourceName: String, val is3D: Boolean) : GameEvent("audio.play")

/**
 * Event published when an audio source stops.
 * @param sourceName Name of the audio source
 */
class AudioStop(val sourceName: String) : GameEvent("audio.stop")

/**
 * Event published when audio volume changes.
 * @param sourceName Name of the audio source (empty for master volume)
 * @param volume New volume level (0.0 to 1.0)
 */
class AudioVolumeChange(val sourceName: String, val volume: Float) : GameEvent("audio.volume_change")

/**
 * Event published when audio listener position changes significantly.
 * @param position New listener position
 * @param velocity Listener velocity
 */
class AudioListenerMove(
    val position: Vector3f,
    val velocity: Vector3f
) : GameEvent("audio.listener_move")
