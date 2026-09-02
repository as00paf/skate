package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.ecs.GameObject
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
class NativeScriptComponent(
    @Transient var onInit: ((NativeScriptComponent, GameObject) -> Unit)? = null,
    @Transient var onUpdate: ((NativeScriptComponent, Float) -> Unit)? = null,
    @Transient var onReset: ((NativeScriptComponent) -> Unit)? = null,
    @Transient var onDestroy: ((NativeScriptComponent) -> Unit)? = null,
) : Component() {

    override fun init(gameObject: GameObject) {
        super.init(gameObject)
        onInit?.invoke(this, gameObject)
    }

    override fun reset() {
        super.reset()
        onReset?.invoke(this)
    }

    override fun update(dt: Float) {
        super.update(dt)
        onUpdate?.invoke(this, dt)
    }

    override fun destroy() {
        super.destroy()
        onDestroy?.invoke(this)
    }
}