package com.pafoid.skate.editor.systems

import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.GameObject

class ClipboardService(private val serializer: Serializer) {
    private var clipboardGameObject: GameObject? = null

    fun copy(gameObject: GameObject) {
        // Use the existing copy mechanism which serializes/deserializes
        // This performs a deep copy of the GameObject and its components.
        clipboardGameObject = gameObject.copy(serializer)
    }

    fun paste(): GameObject? {
        // Return a deep copy of the object on the clipboard.
        // We need to ensure it's a fresh object with new UIDs and re-initialized components.
        return clipboardGameObject?.copy(serializer)
    }

    fun cut(gameObject: GameObject): GameObject {
        // For cut, we copy first, then return the original object to be destroyed/removed.
        copy(gameObject)
        return gameObject
    }

    fun clear() {
        clipboardGameObject = null
    }
}