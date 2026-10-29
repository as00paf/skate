package com.pafoid.skate.game.prefabs

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.SkeletonPose
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.BoneOverride
import com.pafoid.skate.engine.ecs.components.BoxCollider3D
import com.pafoid.skate.engine.ecs.components.InputStateComponent
import com.pafoid.skate.engine.ecs.components.PlayerController
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.game.player.SkateBehavior
import com.pafoid.skate.game.player.SkateGameplay
import org.joml.Vector3f

class Skater(
    name: String,
    model: `3dModel`,
    skate: GameObject? = null,
    position: Vector3f = Vector3f(0f, 0.15f, 0f),
    rotation: Vector3f = Vector3f(0f, 90f, 0f),
    scale: Vector3f = Vector3f(1f, 1f, 1f),
    mass: Float = 180f,// kg
    /** Collider half extents in meters; the james model is 1.82 m tall. */
    hitBoxSize: Vector3f = Vector3f(0.2f, 0.91f, 0.2f),
    animations: List<Animation> = emptyList()
): GameObject(name) {

    val transform = Transform(position, scale, rotation)
    val renderComponent = RenderComponent(model)
    val animator = Animator()
    val skeletonComponent = SkeletonComponent(SkeletonPose(model.skeleton!!))
    val playerController = PlayerController(SkateBehavior.ID, CONFIG_PATH)

    init {
        SkateGameplay.register()
        // Parenting: Skater follows Skateboard
        skate?.addChild(this)

        addComponent(transform)
        addComponent(renderComponent)
        addComponent(animator)
        addComponent(skeletonComponent)

        addComponent(RigidBody3D(mass).apply {
            useCCD = true
            friction = 1.2f
            linearDamping = 0.2f
            angularDamping = 0.3f
        })
        addComponent(
            BoxCollider3D(
                hitBoxSize,
                Vector3f(0f, hitBoxSize.y, 0f),// model origin is at the feet, so the box sits on top of it
                0.01f
            )
        )
        addComponent(InputStateComponent())
        addComponent(playerController)
        addComponent(BoneOverride())

        animations.forEach { animator.addAnimation(it) }
    }

    companion object {
        const val CONFIG_PATH = "configs/skater.character.json"

        val DEFAULT_ANIMATIONS = listOf(
            Assets.Bundled.IDLE_0,
            Assets.Bundled.IDLE_1,
            Assets.Bundled.FALLING_IDLE,
            Assets.Bundled.JUMP,
            Assets.Bundled.WALKING,
            Assets.Bundled.RUNNING,
            Assets.Bundled.LEFT_TURN,
            Assets.Bundled.LEFT_TURN_90,
            Assets.Bundled.LEFT_STRAFE,
            Assets.Bundled.LEFT_STRAFE_WALKING,
            Assets.Bundled.RIGHT_TURN,
            Assets.Bundled.RIGHT_TURN_90,
            Assets.Bundled.RIGHT_STRAFE,
            Assets.Bundled.RIGHT_STRAFE_WALKING,
        )
    }
}