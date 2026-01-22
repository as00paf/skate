package com.pafoid.skate.core

import com.google.gson.GsonBuilder
import components.Component
import com.pafoid.skate.components.ComponentDeserializer
import com.pafoid.skate.components.SpriteRenderer
import imgui.internal.ImGui
import util.AssetPool

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
    private var uId = ID_COUNTER++

    private val components = mutableListOf<Component>()

    @Transient var transform: Transform = Transform()

    fun <T> getComponent(componentClass: Class<T>): T? {
        return components.firstOrNull { componentClass.isAssignableFrom(it.javaClass)} as? T?
    }

    fun <T> removeComponent(componentClass: Class<T>) {
        components.firstOrNull { componentClass.isAssignableFrom(it.javaClass)}?.let {
            components.remove(it)
        }
    }

    fun addComponent(component: Component): GameObject{
        component.generateId()
        components.add(component)
        component.init(this)
        return this
    }

    fun start(){
        components.forEach { it.start() }
    }

    fun update(dt: Float) {
        components.forEach { it.update(dt) }
    }

    fun editorUpdate(dt: Float) {
        components.forEach { it.editorUpdate(dt) }
    }

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
        val gson = GsonBuilder()
            .registerTypeAdapter(Component::class.java, ComponentDeserializer())
            .registerTypeAdapter(GameObject::class.java, GameObjectSerializer())
            .enableComplexMapKeySerialization()
            .create()

        val objAsJSON = gson.toJson(this)
        val result = gson.fromJson(objAsJSON, GameObject::class.java)
        result.generateUid()
        result.getAllComponents().forEach {
            it.generateId()
        }

        val sprite = result.getComponent(SpriteRenderer::class.java)
        sprite?.getTexture()?.let{
            sprite.setTexture(AssetPool.getTexture(it.getFilePath().orEmpty()))
        }

        return result
    }

    override fun toString(): String {
        return super.toString() + "::$name"
    }
}