package com.pafoid.skate.engine.events

import com.pafoid.skate.engine.ecs.GameObject

/** Published when playback crosses an event marker of an animation controller state (footsteps, landings, trick hits). */
data class AnimationEvent(
    val gameObject: GameObject,
    val name: String,
    /** Controller state that owns the marker. */
    val state: String,
) : Event("animation.event")
