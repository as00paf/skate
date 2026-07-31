package com.pafoid.skate.engine.ecs.components

enum class ComponentType {
    ANIMATOR,
    AUDIO,
    BONE_OVERRIDE,
    BOX_COLLIDER_3D,
    CAMERA,
    CAPSULE_COLLIDER,
    CUSTOM_COLLIDER,
    CYLINDER_COLLIDER,
    DAY_NIGHT_CYCLE,
    DIRECTIONAL_LIGHT,
    ENVIRONMENT,
    GRID_LINES,
    INPUT_STATE,
    LIGHTING,
    MODULAR_TILE,
    NON_PICKABLE,
    PHYSICS,
    PLAYER_CONTROLLER,
    PLAYER_STATE_MANAGER,
    RENDER,
    RIGID_BODY_3D,
    RAGDOLL,
    SCENE_PHYSICS,
    SKELETON,
    SPRITE_RENDERER,
    TRANSFORM;

    fun instantiate(): Component? {
        return when (this) {
            ANIMATOR -> Animator()
            AUDIO -> AudioComponent()
            BONE_OVERRIDE -> BoneOverride()
            BOX_COLLIDER_3D -> BoxCollider3D()
            CAMERA -> CameraComponent()
            CAPSULE_COLLIDER -> CapsuleCollider3D()
            CUSTOM_COLLIDER -> CustomCollider3D()
            CYLINDER_COLLIDER -> CylinderCollider3D()
            DAY_NIGHT_CYCLE -> DayNightCycleComponent()
            DIRECTIONAL_LIGHT -> DirectionalLightComponent()
            ENVIRONMENT -> EnvironmentComponent()
            GRID_LINES -> GridLines()
            INPUT_STATE -> InputStateComponent()
            LIGHTING -> LightingComponent()
            MODULAR_TILE -> ModularTile()
            NON_PICKABLE -> NonPickable()
            PHYSICS -> PhysicsComponent()
            PLAYER_CONTROLLER -> PlayerController()
            PLAYER_STATE_MANAGER -> PlayerStateManager()
            RENDER -> RenderComponent()
            RIGID_BODY_3D -> RigidBody3D()
            RAGDOLL -> RagdollComponent()
            SCENE_PHYSICS -> ScenePhysicsComponent()
            SKELETON -> null
            SPRITE_RENDERER -> SpriteRenderer()
            TRANSFORM -> Transform()
        }
    }
}