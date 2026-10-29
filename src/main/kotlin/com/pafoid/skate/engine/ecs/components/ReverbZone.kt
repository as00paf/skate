package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.audio.ReverbPreset
import kotlinx.serialization.Serializable

/**
 * Spherical reverb zone centred on the entity. While the audio listener is inside, sounds get the
 * zone's reverb (scaled by [sendLevel] and each bus's reverb send) and its low-pass ([lowPassGainHF], 1 = off).
 */
@Serializable
data class ReverbZone(
    var preset: ReverbPreset = ReverbPreset.HALL,
    var radius: Float = 10f,
    var priority: Int = 0,
    var sendLevel: Float = 1f,
    var lowPassGainHF: Float = 1f
) : Component()
