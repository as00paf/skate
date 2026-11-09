package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.ComponentType.ANIMATOR
import com.pafoid.skate.engine.ecs.components.ComponentType.AUDIO
import com.pafoid.skate.engine.ecs.components.ComponentType.BOX_COLLIDER_3D
import com.pafoid.skate.engine.ecs.components.ComponentType.CAPSULE_COLLIDER
import com.pafoid.skate.engine.ecs.components.ComponentType.CUSTOM_COLLIDER
import com.pafoid.skate.engine.ecs.components.ComponentType.CYLINDER_COLLIDER
import com.pafoid.skate.engine.ecs.components.ComponentType.DAY_NIGHT_CYCLE
import com.pafoid.skate.engine.ecs.components.ComponentType.DIRECTIONAL_LIGHT
import com.pafoid.skate.engine.ecs.components.ComponentType.ENVIRONMENT
import com.pafoid.skate.engine.ecs.components.ComponentType.INPUT_STATE
import com.pafoid.skate.engine.ecs.components.ComponentType.JOINT
import com.pafoid.skate.engine.ecs.components.ComponentType.NON_PICKABLE
import com.pafoid.skate.engine.ecs.components.ComponentType.PLAYER_CONTROLLER
import com.pafoid.skate.engine.ecs.components.ComponentType.POINT_LIGHT
import com.pafoid.skate.engine.ecs.components.ComponentType.RAGDOLL
import com.pafoid.skate.engine.ecs.components.ComponentType.RENDER
import com.pafoid.skate.engine.ecs.components.ComponentType.RIGID_BODY_3D
import com.pafoid.skate.engine.ecs.components.ComponentType.SCENE_PHYSICS
import com.pafoid.skate.engine.ecs.components.ComponentType.SKELETON
import com.pafoid.skate.engine.ecs.components.ComponentType.SPRITE_RENDERER
import com.pafoid.skate.engine.ecs.components.ComponentType.TRANSFORM
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

    /** Session id (ImGui ids, runtime lookups): assigned on [init], never saved, so files carry no session counters. */
    @Transient
    var uId = -1
    var enabled = true
    var name: String = this.javaClass.simpleName

    @Transient
    var gameObject: GameObject? = null

    open fun init(gameObject: GameObject) {
        this.gameObject = gameObject
        if (uId == -1) {
            uId = ID_COUNTER++
        }
    }

    open fun reset() {}

    //TODO: remove
    open fun update(dt: Float) {}

    fun generateId() {
        if (uId == -1) uId = ID_COUNTER++
    }

    open fun destroy() {}

    val type: ComponentType?
        get() {
            return when (this) {
                is Animator -> ANIMATOR
                is AudioComponent -> AUDIO
                is AudioListener -> ComponentType.AUDIO_LISTENER
                is BoxCollider3D -> BOX_COLLIDER_3D
                is CameraComponent -> ComponentType.CAMERA
                is CapsuleCollider3D -> CAPSULE_COLLIDER
                is CompoundHullCollider3D -> ComponentType.COMPOUND_HULL_COLLIDER
                is CustomCollider3D -> CUSTOM_COLLIDER
                is CylinderCollider3D -> CYLINDER_COLLIDER
                is DayNightCycleComponent -> DAY_NIGHT_CYCLE
                is DirectionalLightComponent -> DIRECTIONAL_LIGHT
                is EnvironmentComponent -> ENVIRONMENT
                is FollowCameraComponent -> ComponentType.FOLLOW_CAMERA
                is FirstPersonCameraComponent -> ComponentType.FIRST_PERSON_CAMERA
                is CameraCycleComponent -> ComponentType.CAMERA_CYCLE
                is GridLines -> ComponentType.GRID_LINES
                is InputStateComponent -> INPUT_STATE
                is JointComponent -> JOINT
                is MeshCollider3D -> ComponentType.MESH_COLLIDER
                is PointLightComponent -> POINT_LIGHT
                is NonPickable -> NON_PICKABLE
                is ParticleEmitter -> ComponentType.PARTICLE_EMITTER
                is PlayerController -> PLAYER_CONTROLLER
                is RenderComponent -> RENDER
                is RigidBody3D -> RIGID_BODY_3D
                is ReverbZone -> ComponentType.REVERB_ZONE
                is RagdollComponent -> RAGDOLL
                is VehicleComponent -> ComponentType.VEHICLE
                is ScenePhysicsComponent -> SCENE_PHYSICS
                is ScriptComponent -> ComponentType.SCRIPT
                is SkeletonComponent -> SKELETON
                is SpriteRenderer -> SPRITE_RENDERER
                is Transform -> TRANSFORM
                is com.pafoid.skate.engine.ui.UIButtonComponent -> ComponentType.UI_BUTTON
                is com.pafoid.skate.engine.ui.UICanvasComponent -> ComponentType.UI_CANVAS
                is com.pafoid.skate.engine.ui.UIElementComponent -> ComponentType.UI_ELEMENT
                is com.pafoid.skate.engine.ui.UIImageComponent -> ComponentType.UI_IMAGE
                is com.pafoid.skate.engine.ui.UIListComponent -> ComponentType.UI_LIST
                is com.pafoid.skate.engine.ui.UIProgressBarComponent -> ComponentType.UI_PROGRESS_BAR
                is com.pafoid.skate.engine.ui.UISliderComponent -> ComponentType.UI_SLIDER
                is com.pafoid.skate.engine.ui.UITextComponent -> ComponentType.UI_TEXT
                else -> null
            }
        }
}

@Serializable
@Polymorphic
abstract class SceneComponent : Component()
