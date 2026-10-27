package com.pafoid.skate.engine.ecs.components

import kotlinx.serialization.Serializable

/**
 * Marks the entity (honours `Component.enabled`) whose transform is the audio listener. The first enabled listener in the scene
 * is used; with none, AudioSystem falls back to the active camera.
 */
@Serializable
class AudioListener : Component()
