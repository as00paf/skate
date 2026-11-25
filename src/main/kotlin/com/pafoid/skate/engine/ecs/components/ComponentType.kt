package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.ui.*

enum class ComponentType {
    ANIMATOR,
    AUDIO,
    AUDIO_LISTENER,
    BONE_ATTACHMENT,
    BOX_COLLIDER_3D,
    CAMERA,
    CAMERA_CYCLE,
    CAPSULE_COLLIDER,
    COMPOUND_HULL_COLLIDER,
    CUSTOM_COLLIDER,
    CYLINDER_COLLIDER,
    DAY_NIGHT_CYCLE,
    DIRECTIONAL_LIGHT,
    ENVIRONMENT,
    EQUIPMENT,
    FIRST_PERSON_CAMERA,
    FOLLOW_CAMERA,
    GRID_LINES,
    HEALTH,
    IK_SETUP,
    IK_TARGET,
    INPUT_STATE,
    INTERACTABLE,
    INVENTORY,
    JOINT,
    MESH_COLLIDER,
    NATIVE_SCRIPT,
    NON_PICKABLE,
    PARTICLE_EMITTER,
    PICKUP,
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
    TERRAIN,
    TRANSFORM,
    UI_BUTTON,
    UI_CANVAS,
    UI_ELEMENT,
    UI_IMAGE,
    UI_LIST,
    UI_PROGRESS_BAR,
    UI_SLIDER,
    UI_TEXT,
    VEHICLE,
    WATER;

    fun instantiate(): Component? {
        return when (this) {
            ANIMATOR -> Animator()
            AUDIO -> AudioComponent()
            AUDIO_LISTENER -> AudioListener()
            BOX_COLLIDER_3D -> BoxCollider3D()
            CAMERA -> CameraComponent()
            CAMERA_CYCLE -> CameraCycleComponent()
            INVENTORY -> InventoryComponent()
            BONE_ATTACHMENT -> BoneAttachment()
            HEALTH -> HealthComponent()
            INTERACTABLE -> InteractableComponent()
            PICKUP -> PickupComponent()
            EQUIPMENT -> EquipmentComponent()
            CAPSULE_COLLIDER -> CapsuleCollider3D()
            COMPOUND_HULL_COLLIDER -> CompoundHullCollider3D()
            CUSTOM_COLLIDER -> CustomCollider3D()
            CYLINDER_COLLIDER -> CylinderCollider3D()
            DAY_NIGHT_CYCLE -> DayNightCycleComponent()
            DIRECTIONAL_LIGHT -> DirectionalLightComponent()
            ENVIRONMENT -> EnvironmentComponent()
            FIRST_PERSON_CAMERA -> FirstPersonCameraComponent()
            FOLLOW_CAMERA -> FollowCameraComponent()
            GRID_LINES -> GridLines()
            IK_SETUP -> IkSetup()
            IK_TARGET -> IkTarget()
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
            WATER -> WaterComponent()
            SCENE_PHYSICS -> ScenePhysicsComponent()
            SCRIPT -> ScriptComponent()
            SKELETON -> null
            SPOT_LIGHT -> SpotLightComponent()
            SPRITE_RENDERER -> SpriteRenderer()
            TERRAIN -> TerrainComponent()
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