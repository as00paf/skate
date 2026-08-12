package com.pafoid.skate.engine.ecs

import com.pafoid.skate.engine.assets.serialization.Serializer
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

        fun init(maxId: Int) {
            ID_COUNTER = maxId
        }

        fun getIdCounter(): Int = ID_COUNTER
    }

    var uId = ID_COUNTER++
    var isDead: Boolean = false
    var isEnabled = true
    var isVisible = true
    var isLocked = false

    val components = mutableListOf<Component>()

    @Transient
    var parent: GameObject? = null
    val children = mutableListOf<GameObject>()

    fun addChild(child: GameObject) {
        child.parent?.removeChild(child)
        child.parent = this
        children.add(child)
    }

    fun removeChild(child: GameObject) {
        children.remove(child)
        child.parent = null
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
