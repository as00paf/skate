package com.pafoid.skate.editor.systems

import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.GameObject

class ClipboardService(private val serializer: Serializer) {
    private var clipboardGameObject: GameObject? = null

    fun copy(gameObject: GameObject) {
        clipboardGameObject = gameObject.copy(serializer)
    }

    fun paste(): GameObject? {
        return clipboardGameObject?.copy(serializer)
    }

    fun cut(gameObject: GameObject): GameObject {
        copy(gameObject)
        return gameObject
    }

    fun clear() {
        clipboardGameObject = null
    }
}