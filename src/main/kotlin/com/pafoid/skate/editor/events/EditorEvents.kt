package com.pafoid.skate.editor.events

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.events.Event


sealed class EditorEvent(eventName: String) : Event(eventName)

data class TextureApplied(val gameObject: GameObject, val texturePath: String) : EditorEvent("editor.texture_applied")
data class AnimationApplied(val gameObject: GameObject, val animationPath: String) :
    EditorEvent("editor.animation_applied")

data class AnimationRemoved(val gameObject: GameObject, val animationPath: String) :
    EditorEvent("editor.animation_removed")
