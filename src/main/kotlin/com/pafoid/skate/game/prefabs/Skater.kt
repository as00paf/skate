package com.pafoid.skate.game.prefabs

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.data.models.TexturedModel
import com.pafoid.skate.engine.assets.data.models.animations.SkeletonPose
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.BoneOverride
import com.pafoid.skate.engine.ecs.components.BoxCollider3D
import com.pafoid.skate.engine.ecs.components.InputStateComponent
import com.pafoid.skate.engine.ecs.components.PlayerController
import com.pafoid.skate.engine.ecs.components.PlayerStateManager
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.ecs.components.Transform
import org.joml.Vector3f

class Skater(
    name: String,
    model: TexturedModel,
    skate: GameObject? = null,
    position: Vector3f = Vector3f(0f, 1.05f, 0f),
    rotation: Vector3f = Vector3f(0f, 90f, 0f),
    scale: Vector3f = Vector3f(1f, 1f, 1f),
    mass: Float = 100f,// 100kg mass
    hitBoxSize: Vector3f = Vector3f(0.2f, 0.95f, 0.2f),
): GameObject(name) {

    val transform = Transform(position, scale, rotation)
    val renderComponent = RenderComponent(
        model = model,
        castShadow = true,
        receiveShadow = true
    )

    init {
        // Parenting: Skater follows Skateboard
        //skate?.addChild(this)

        val skeleton = model.skeleton?.copy()
        addComponent(transform)
        addComponent(renderComponent)
        skeleton?.let { addComponent(SkeletonComponent(SkeletonPose(it))) }
        addComponent(Animator())
        addComponent(RigidBody3D(mass).apply {
            useCCD = true
            friction = 1.2f
            linearDamping = 0.2f
            angularDamping = 0.3f
        })
        addComponent(BoxCollider3D(hitBoxSize).apply {
            offset.set(transform.translation.add(Vector3f(0f, -0.115f, 0f)))
            margin = 0.01f
        })
        addComponent(InputStateComponent())
        addComponent(PlayerStateManager())
        addComponent(PlayerController())
        addComponent(BoneOverride())
    }

    companion object {
        val DEFAULT_ANIMATIONS = listOf(
            Assets.Animations.IDLE_0,
            Assets.Animations.IDLE_1,
            Assets.Animations.JUMP,
            Assets.Animations.WALKING,
            Assets.Animations.RUNNING,
            Assets.Animations.LEFT_TURN,
            Assets.Animations.LEFT_TURN_90,
            Assets.Animations.LEFT_STRAFE,
            Assets.Animations.LEFT_STRAFE_WALKING,
            Assets.Animations.RIGHT_TURN,
            Assets.Animations.RIGHT_TURN_90,
            Assets.Animations.RIGHT_STRAFE,
            Assets.Animations.RIGHT_STRAFE_WALKING,
        )
    }
}