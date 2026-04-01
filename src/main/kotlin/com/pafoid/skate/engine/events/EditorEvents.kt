package com.pafoid.skate.engine.events

import com.pafoid.skate.engine.ecs.GameObject

/**
 * Event published when a texture is applied to a GameObject
 */
data class TextureApplied(val gameObject: GameObject, val texturePath: String) : GameEvent("editor.texture_applied")

/**
 * Event published when an animation is applied to a GameObject
 */
data class AnimationApplied(val gameObject: GameObject, val animationPath: String) : GameEvent("editor.animation_applied")

/**
 * Event published when an animation is removed from a GameObject
 */
data class AnimationRemoved(val gameObject: GameObject, val animationPath: String) : GameEvent("editor.animation_removed")
