package com.pafoid.skate.engine.ecs

import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
open class GameObject(
    var name: String,
) {
    companion object {
        private var ID_COUNTER: Int = 0

        fun init(maxId: Int) {
            ID_COUNTER = maxId
        }

        fun getIdCounter(): Int = ID_COUNTER
    }

    private var uId = ID_COUNTER++
    private var isDead: Boolean = false
    private var doSerialization = true

    @Transient
    var componentMutationVersion: Long = 0
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

    open fun update(dt: Float) {
        if (!isEnabled) return
        components.forEach {
            if (it.enabled) it.update(dt)
        }
    }

    fun getUid() = uId
    fun generateUid() { uId = ID_COUNTER++ }

    fun getAllComponents(): List<Component> = components
    fun setNoSerialize():GameObject {
        doSerialization = false
        return this
    }
    fun doSerialization():Boolean {
        return doSerialization
    }

    fun isDead():Boolean = isDead

    fun destroy() {
        this.isDead = true
        components.forEach { it.destroy() }
    }

    fun copy(serializer: Serializer): GameObject {
        val objAsJSON = serializer.encode(this)
        val result = serializer.decode<GameObject>(objAsJSON)
        result.generateUid()
        result.getAllComponents().forEach {
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
