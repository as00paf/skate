package com.pafoid.skate.engine.scenes

import com.pafoid.skate.engine.scenes.components.Transform
import com.pafoid.skate.engine.scenes.components.Component
import com.pafoid.skate.engine.utils.serialization.Serializer
import imgui.ImGui
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@Serializable
open class GameObject(
    var name: String,
) {
    companion object {
        private var ID_COUNTER: Int = 0

        fun init(maxId: Int) {
            ID_COUNTER = maxId
        }
    }

    private var isDead: Boolean = false
    private var doSerialization = true
    private var isEnabled = true
    private var uId = ID_COUNTER++

    val components = mutableListOf<Component>()

    @Transient var transform: Transform = Transform()
    @Transient var parent: GameObject? = null
    @Transient val children = mutableListOf<GameObject>()

    init {
        addComponent(transform)
    }

    fun addChild(child: GameObject) {
        child.parent?.removeChild(child)
        child.parent = this
        children.add(child)
    }

    fun removeChild(child: GameObject) {
        children.remove(child)
        child.parent = null
    }

    inline fun <reified T> getComponent(): T? {
        return components.filterIsInstance<T>().firstOrNull()
    }

    fun <T : Component> getComponent(componentClass: Class<T>): T? {
        for (c in components) {
            if (componentClass.isAssignableFrom(c.javaClass)) {
                @Suppress("UNCHECKED_CAST")
                return c as T
            }
        }
        return null
    }

    fun <T> removeComponent(componentClass: Class<T>) {
        components.firstOrNull { componentClass.isAssignableFrom(it.javaClass)}?.let {
            components.remove(it)
        }
    }

    fun addComponent(component: Component): GameObject{
        if (component is Transform) {
            // If we already have a transform in components, remove it first
            components.removeAll { it is Transform }
            this.transform = component
        }
        component.generateId()
        components.add(component)
        component.init(this)
        return this
    }

    fun start(){
        components.forEach { it.start() }
    }

    fun update(dt: Float) {
        if (!isEnabled) return
        components.forEach { 
            if (it.enabled) it.update(dt) 
        }
    }

    fun editorUpdate(dt: Float) {
        if (!isEnabled) return
        components.forEach { 
            if (it.enabled) it.editorUpdate(dt) 
        }
    }

    fun isEnabled(): Boolean = isEnabled
    fun setEnabled(enabled: Boolean) { isEnabled = enabled }

    fun imgui() {
        components.forEach {
            if(ImGui.collapsingHeader(it.javaClass.simpleName))
                it.imgui()
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

    fun copy(): GameObject {
        val objAsJSON = Serializer.json.encodeToString(this)
        val result = Serializer.json.decodeFromString<GameObject>(objAsJSON)
        result.generateUid()
        result.getAllComponents().forEach {
            it.generateId()
            it.init(result) // Re-initialize with the new parent
        }
        
        // Restore transform reference
        result.transform = result.getComponent<Transform>() ?: Transform()

        return result
    }

    override fun toString(): String {
        return super.toString() + "::$name"
    }
}