package com.pafoid.skate.engine.ecs

import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.MainThread
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
open class GameObject(
    open var name: String,
) {
    companion object {
        private var ID_COUNTER: Int = 0

        fun getIdCounter(): Int = ID_COUNTER

        const val LAYER_COUNT = 32
        const val ALL_LAYERS_MASK = -1

        fun layerBit(layer: Int): Int = 1 shl layer
    }

    /** Unique id; changing it bumps [EcsChanges] so id lookups (`GameObjectManager.getGameObject`) stay consistent. */
    var uId = ID_COUNTER++
        set(value) {
            field = value
            EcsChanges.touch()
        }
    var isDead: Boolean = false
    var isEnabled = true
    var isVisible = true
    var isLocked = false

    /** Free-form tag for lookup (`Scene.findByTag`); empty means untagged. */
    var tag: String = ""

    /** Layer index in [0, [LAYER_COUNT]); used by layer masks in queries, culling and picking. Default layer is 0. */
    var layer: Int = 0
        set(value) {
            require(value in 0 until LAYER_COUNT) { "layer must be in 0..${LAYER_COUNT - 1}, was $value" }
            field = value
        }

    val components = mutableListOf<Component>()

    /**
     * The object this one hangs under. Top-level objects have their [Scene] as parent (set by [addChild],
     * `GameObjectManager.addGameObject` and scene loading via [linkChildren]); only a detached object or a
     * Scene itself has none. Not serialized: links are rebuilt from [children] on load.
     */
    @Transient
    var parent: GameObject? = null
    val children = mutableListOf<GameObject>()

    /** The one rule for "top-level": directly under its [Scene]. */
    val isTopLevel: Boolean get() = parent is Scene

    /** Parents from the nearest one up to the top-level object; the [Scene] is not included. */
    fun ancestors(): Sequence<GameObject> = generateSequence(parent) { it.parent }.takeWhile { it !is Scene }

    /** Restores [parent] on every descendant from [children] (after decoding, links are missing). */
    fun linkChildren() {
        children.forEach { child ->
            child.parent = this
            child.linkChildren()
        }
        EcsChanges.touch()
    }

    /** Reports (see [MainThread]) a scene mutation attempted off the main thread while attached to a [Scene]. */
    fun assertSceneMutationOnMainThread(what: String) {
        if (MainThread.isMainThread()) return
        var node: GameObject? = this
        while (node != null) {
            if (node is Scene) {
                MainThread.assertOnMainThread("Scene mutation ($what on '$name')")
                return
            }
            node = node.parent
        }
    }

    /** Moves [child] under this object, at [index] among [children] (appended when out of range). */
    fun addChild(child: GameObject, index: Int = -1) {
        assertSceneMutationOnMainThread("addChild")
        child.parent?.removeChild(child)
        child.parent = this
        if (index in 0..children.size) children.add(index, child) else children.add(child)
        EcsChanges.touch()
    }

    fun removeChild(child: GameObject) {
        assertSceneMutationOnMainThread("removeChild")
        children.remove(child)
        child.parent = null
        EcsChanges.touch()
    }

    open fun start() {}

    open fun reset() {
        components.forEach { component -> component.reset() }
    }

    open fun update(dt: Float) {
        if (!isEnabled) return
        components.forEach {
            if (it.enabled) it.update(dt)
        }
    }

    fun generateUid() { uId = ID_COUNTER++ }

    fun destroy() {
        this.isDead = true
        components.forEach { it.destroy() }
    }

    fun copy(serializer: Serializer): GameObject {
        val objAsJSON = serializer.encode(this)
        val result = serializer.decode<GameObject>(objAsJSON)
        result.linkChildren()
        result.generateUid()
        result.components.forEach {
            it.generateId()
            it.init(result) // Re-initialize with the new parent
        }

        // Restore transform reference
        val currentTransform = getComponent<Transform>() ?: return result
        result.getComponent<Transform>()?.let {
            it.scale.set(currentTransform.scale)
            it.rotation.set(currentTransform.rotation)
            it.translation.set(currentTransform.translation)
        }

        return result
    }

    override fun toString(): String {
        return super.toString() + "::$name"
    }
}
