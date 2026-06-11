package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.ecs.GameObject
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
@Polymorphic
abstract class Component {

    companion object {
        private var ID_COUNTER: Int = 0

        fun init(maxId: Int) {
            ID_COUNTER = maxId
        }

        fun getIdCounter(): Int = ID_COUNTER

        /** Cache for reflection results — avoids expensive Class.getDeclaredFields() per frame */
        private val fieldCache = mutableMapOf<Class<*>, Array<java.lang.reflect.Field>>()

        fun getCachedFields(clazz: Class<*>): Array<java.lang.reflect.Field> {
            return fieldCache.getOrPut(clazz) { clazz.declaredFields }
        }
    }

    var uId = -1
    var enabled = true

    @Transient
    lateinit var gameObject: GameObject
        private set

    /**
     * Called by GameObject.addComponent() and after scene deserialization.
     * Idempotent — safe to call multiple times.
     */
    open fun init(gameObject: GameObject) {
        if (::gameObject.isInitialized) return
        this.gameObject = gameObject
        if (uId == -1) {
            uId = ID_COUNTER++
        }
    }

    open fun update(dt: Float) {}

    fun generateId() {
        if (uId == -1) uId = ID_COUNTER++
    }

    fun getUid() = uId

    open fun getName(): String {
        return this.javaClass.simpleName
    }

    open fun destroy() {
    }
}
