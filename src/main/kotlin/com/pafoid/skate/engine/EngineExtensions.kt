package com.pafoid.skate.engine

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.AudioComponent
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.ComponentType
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.physics3d.components.BoxCollider3D
import com.pafoid.skate.engine.physics3d.components.CylinderCollider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import kotlin.reflect.KClass

fun GameObject.getComponent(type: ComponentType) =
    when (type) {
        ComponentType.AUDIO -> getComponent<AudioComponent>()
        ComponentType.BOX_COLLIDER_3D -> getComponent<BoxCollider3D>()
        ComponentType.CYLINDER_COLLIDER_3D -> getComponent<CylinderCollider3D>()
        ComponentType.RENDER -> getComponent<RenderComponent>()
        ComponentType.RIGID_BODY_3D -> getComponent<RigidBody3D>()
        ComponentType.TRANSFORM -> getComponent<Transform>()
    }

inline fun <reified T> GameObject.getComponent(): T? {
    return components.filterIsInstance<T>().firstOrNull()
}

fun <T : Component> GameObject.getComponent(componentClass: KClass<T>): T? {
    return components.find { componentClass.isInstance(it) } as? T
}

inline fun <reified T : Component> GameObject.addComponent(component: T): GameObject = addComponent(T::class, component)

fun <T : Component> GameObject.removeComponent(componentClass: KClass<T>) {
    val removed = components.removeAll { componentClass.isInstance(it) }
    if (removed) {
        componentMutationVersion++
    }
}

inline fun <reified T : Component> GameObject.removeComponent() = removeComponent(T::class)

inline fun <reified T> GameObject.hasComponent(): Boolean {
    return components.filterIsInstance<T>().isNotEmpty()
}

fun <T : Component> GameObject.addComponent(componentClass: KClass<T>, component: T): GameObject {
    components.removeAll { componentClass.isInstance(it) }
    component.generateId()
    components.add(component)
    component.init(this)
    componentMutationVersion++
    return this
}