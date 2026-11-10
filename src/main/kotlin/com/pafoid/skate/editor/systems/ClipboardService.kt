package com.pafoid.skate.editor.systems

import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.GameObject

/** Copied objects (EDT-74: a whole multi-selection, in selection order); each paste gets fresh copies. */
class ClipboardService(private val serializer: Serializer) {
    private var clipboard: List<GameObject> = emptyList()

    val isEmpty: Boolean get() = clipboard.isEmpty()

    fun copy(gameObject: GameObject) = copy(listOf(gameObject))

    fun copy(gameObjects: List<GameObject>) {
        clipboard = gameObjects.map { it.copy(serializer) }
    }

    /** The first copied object, or null; see [pasteAll]. */
    fun paste(): GameObject? = clipboard.firstOrNull()?.copy(serializer)

    fun pasteAll(): List<GameObject> = clipboard.map { it.copy(serializer) }

    fun cut(gameObject: GameObject): GameObject {
        copy(gameObject)
        return gameObject
    }

    fun clear() {
        clipboard = emptyList()
    }
}