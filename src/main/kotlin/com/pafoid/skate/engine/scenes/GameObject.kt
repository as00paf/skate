package com.pafoid.skate.engine.scenes

import com.pafoid.skate.engine.scenes.components.Transform
import com.pafoid.skate.engine.scenes.components.Component
import com.pafoid.skate.engine.utils.serialization.Serializer
import imgui.ImGui
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
    }

    private var isDead: Boolean = false
    private var doSerialization = true
    private var isEnabled = true
    private var uId = ID_COUNTER++

    val components = mutableListOf<Component>()

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

    inline fun <reified T> getComponent(): T? {
        return components.filterIsInstance<T>().firstOrNull()
    }

    inline fun <reified T> removeComponent() {
        components.removeIf { it is T }
    }

    inline fun <reified T> hasComponent(): Boolean {
        return components.filterIsInstance<T>().isNotEmpty()
    }

    inline fun <reified T: Component> addComponent(component: T): GameObject{
        // Replace
        if (hasComponent<T>()) {
            removeComponent<T>()
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