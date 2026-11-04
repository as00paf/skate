package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.ui.*

enum class ComponentType {
    ANIMATOR,
    AUDIO,
    AUDIO_LISTENER,
    BONE_OVERRIDE,
    BOX_COLLIDER_3D,
    CAMERA,
    CAPSULE_COLLIDER,
    COMPOUND_HULL_COLLIDER,
    CUSTOM_COLLIDER,
    CYLINDER_COLLIDER,
    DAY_NIGHT_CYCLE,
    DIRECTIONAL_LIGHT,
    ENVIRONMENT,
    FOLLOW_CAMERA,
    GRID_LINES,
    INPUT_STATE,
    JOINT,
    MESH_COLLIDER,
    NATIVE_SCRIPT,
    NON_PICKABLE,
    PARTICLE_EMITTER,
    PLAYER_CONTROLLER,
    POINT_LIGHT,
    RENDER,
    RIGID_BODY_3D,
    REVERB_ZONE,
    RAGDOLL,
    SCENE_PHYSICS,
    SCRIPT,
    SKELETON,
    SPOT_LIGHT,
    SPRITE_RENDERER,
    TRANSFORM,
    UI_BUTTON,
    UI_CANVAS,
    UI_ELEMENT,
    UI_IMAGE,
    UI_LIST,
    UI_PROGRESS_BAR,
    UI_SLIDER,
    UI_TEXT,
    VEHICLE;

    fun instantiate(): Component? {
        return when (this) {
            ANIMATOR -> Animator()
            AUDIO -> AudioComponent()
            AUDIO_LISTENER -> AudioListener()
            BONE_OVERRIDE -> BoneOverride()
            BOX_COLLIDER_3D -> BoxCollider3D()
            CAMERA -> CameraComponent()
            CAPSULE_COLLIDER -> CapsuleCollider3D()
            COMPOUND_HULL_COLLIDER -> CompoundHullCollider3D()
            CUSTOM_COLLIDER -> CustomCollider3D()
            CYLINDER_COLLIDER -> CylinderCollider3D()
            DAY_NIGHT_CYCLE -> DayNightCycleComponent()
            DIRECTIONAL_LIGHT -> DirectionalLightComponent()
            ENVIRONMENT -> EnvironmentComponent()
            FOLLOW_CAMERA -> FollowCameraComponent()
            GRID_LINES -> GridLines()
            INPUT_STATE -> InputStateComponent()
            JOINT -> JointComponent()
            MESH_COLLIDER -> MeshCollider3D()
            NATIVE_SCRIPT -> NativeScriptComponent()
            NON_PICKABLE -> NonPickable()
            PARTICLE_EMITTER -> ParticleEmitter()
            PLAYER_CONTROLLER -> PlayerController()
            POINT_LIGHT -> PointLightComponent()
            RENDER -> RenderComponent()
            RIGID_BODY_3D -> RigidBody3D()
            REVERB_ZONE -> ReverbZone()
            RAGDOLL -> RagdollComponent()
            VEHICLE -> VehicleComponent()
            SCENE_PHYSICS -> ScenePhysicsComponent()
            SCRIPT -> ScriptComponent()
            SKELETON -> null
            SPOT_LIGHT -> SpotLightComponent()
            SPRITE_RENDERER -> SpriteRenderer()
            TRANSFORM -> Transform()
            UI_BUTTON -> UIButtonComponent()
            UI_CANVAS -> UICanvasComponent()
            UI_ELEMENT -> UIElementComponent()
            UI_IMAGE -> UIImageComponent()
            UI_LIST -> UIListComponent()
            UI_PROGRESS_BAR -> UIProgressBarComponent()
            UI_SLIDER -> UISliderComponent()
            UI_TEXT -> UITextComponent()
        }
    }
}