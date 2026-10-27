package com.pafoid.skate.engine.ecs

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
class Scene(@SerialName("sceneName") override var name: String = "MainScene") : GameObject(name) {

    @Transient
    var hoveredGameObject: GameObject? = null
    @Transient
    private val selection = LinkedHashSet<GameObject>()

    /** Ordered selection (oldest first); the last element is the primary object. */
    val selectedGameObjects: List<GameObject> get() = selection.toList()

    /**
     * Primary selected object (the most recently selected). Assigning replaces the whole
     * selection with that object (or clears it when null).
     */
    var selectedGameObject: GameObject?
        get() = selection.lastOrNull()
        set(value) {
            selection.clear()
            if (value != null) selection.add(value)
        }

    fun isSelected(go: GameObject): Boolean = go in selection

    /** Adds [go] to the selection and makes it the primary object. */
    fun addToSelection(go: GameObject) {
        selection.remove(go)
        selection.add(go)
    }

    fun removeFromSelection(go: GameObject) {
        selection.remove(go)
    }

    /** Removes [go] from the selection if present, otherwise adds it. */
    fun toggleSelection(go: GameObject) {
        if (!selection.remove(go)) selection.add(go)
    }

    /** Replaces the selection; the last element of [objects] becomes the primary object. */
    fun setSelection(objects: Collection<GameObject>) {
        selection.clear()
        objects.forEach { addToSelection(it) }
    }

    fun clearSelection() {
        selection.clear()
    }

    var isRunning: Boolean = false
    var isDirty: Boolean = false

    override fun start() {
        isRunning = true
        super.start()
    }

    fun destroyScene() {
        super.destroy()
    }
}
