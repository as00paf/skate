package com.pafoid.skate.engine

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.AudioComponent
import com.pafoid.skate.engine.ecs.components.BoneOverride
import com.pafoid.skate.engine.ecs.components.BoxCollider3D
import com.pafoid.skate.engine.ecs.components.CapsuleCollider3D
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.ComponentType
import com.pafoid.skate.engine.ecs.components.CustomCollider3D
import com.pafoid.skate.engine.ecs.components.CylinderCollider3D
import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import com.pafoid.skate.engine.ecs.components.DirectionalLightComponent
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.components.GridLines
import com.pafoid.skate.engine.ecs.components.InputStateComponent
import com.pafoid.skate.engine.ecs.components.LightingComponent
import com.pafoid.skate.engine.ecs.components.ModularTile
import com.pafoid.skate.engine.ecs.components.NonPickable
import com.pafoid.skate.engine.ecs.components.PhysicsComponent
import com.pafoid.skate.engine.ecs.components.PlayerController
import com.pafoid.skate.engine.ecs.components.PlayerStateManager
import com.pafoid.skate.engine.ecs.components.RagdollComponent
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.ScenePhysicsComponent
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.ecs.components.SpriteRenderer
import com.pafoid.skate.engine.ecs.components.Transform
import kotlin.reflect.KClass

fun GameObject.getComponent(type: ComponentType) =
    when (type) {
        ComponentType.ANIMATOR -> getComponent<Animator>()
        ComponentType.AUDIO -> getComponent<AudioComponent>()
        ComponentType.BONE_OVERRIDE -> getComponent<BoneOverride>()
        ComponentType.BOX_COLLIDER_3D -> getComponent<BoxCollider3D>()
        ComponentType.CYLINDER_COLLIDER -> getComponent<CylinderCollider3D>()
        ComponentType.CAPSULE_COLLIDER -> getComponent<CapsuleCollider3D>()
        ComponentType.CUSTOM_COLLIDER -> getComponent<CustomCollider3D>()
        ComponentType.DAY_NIGHT_CYCLE -> getComponent<DayNightCycleComponent>()
        ComponentType.DIRECTIONAL_LIGHT -> getComponent<DirectionalLightComponent>()
        ComponentType.ENVIRONMENT -> getComponent<EnvironmentComponent>()
        ComponentType.GRID_LINES -> getComponent<GridLines>()
        ComponentType.INPUT_STATE -> getComponent<InputStateComponent>()
        ComponentType.LIGHTING -> getComponent<LightingComponent>()
        ComponentType.MODULAR_TILE -> getComponent<ModularTile>()
        ComponentType.NON_PICKABLE -> getComponent<NonPickable>()
        ComponentType.PHYSICS -> getComponent<PhysicsComponent>()
        ComponentType.PLAYER_CONTROLLER -> getComponent<PlayerController>()
        ComponentType.PLAYER_STATE_MANAGER -> getComponent<PlayerStateManager>()
        ComponentType.RAGDOLL -> getComponent<RagdollComponent>()
        ComponentType.RENDER -> getComponent<RenderComponent>()
        ComponentType.RIGID_BODY_3D -> getComponent<RigidBody3D>()
        ComponentType.SCENE_PHYSICS -> getComponent<ScenePhysicsComponent>()
        ComponentType.SKELETON -> getComponent<SkeletonComponent>()
        ComponentType.SPRITE_RENDERER -> getComponent<SpriteRenderer>()
        ComponentType.TRANSFORM -> getComponent<Transform>()
    }

inline fun <reified T : Component> GameObject.getComponent(): T? {
    return components.filterIsInstance<T>().firstOrNull()
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