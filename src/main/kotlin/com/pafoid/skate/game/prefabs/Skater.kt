package com.pafoid.skate.game.prefabs

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.data.models.TexturedModel
import com.pafoid.skate.engine.assets.data.models.animations.BoneOverride
import com.pafoid.skate.engine.assets.data.models.animations.SkeletonPose
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.InputStateComponent
import com.pafoid.skate.engine.ecs.components.PlayerController
import com.pafoid.skate.engine.ecs.components.PlayerStateManager
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.physics3d.components.BoxCollider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import org.joml.Vector3f

class Skater(
    name: String,
    characterModel: TexturedModel,
    skate: GameObject? = null,
    position: Vector3f = Vector3f(0f, 1.05f, 0f),
    rotation: Vector3f = Vector3f(0f, 90f, 0f),
    scale: Vector3f = Vector3f(1f, 1f, 1f),
    mass: Float = 100f,// 100kg mass
    hitBoxSize: Vector3f = Vector3f(0.2f, 0.95f, 0.2f),
): GameObject(name) {

    val transformComponent = Transform()
    val renderComponent = RenderComponent(
        model = characterModel,
        castShadow = true,
        receiveShadow = true
    )
    val animator = Animator()

    init {
        // Parenting: Skater follows Skateboard
        //skate?.addChild(this)

        val skeleton = characterModel.skeleton?.copy()

        transformComponent.translation.set(position)
        transformComponent.rotation.set(rotation) // Face sideways for skating
        transformComponent.scale.set(scale) // Now in Meters
        addComponent(transformComponent)
        addComponent(renderComponent)
        skeleton?.let { addComponent(SkeletonComponent(SkeletonPose(it))) }
        addComponent(animator)
        addComponent(RigidBody3D(mass).apply {
            useCCD = true
            friction = 1.2f
            linearDamping = 0.2f
            angularDamping = 0.3f
        })
        addComponent(BoxCollider3D(hitBoxSize).apply {
            offset.set(transformComponent.translation.add(Vector3f(0f, -0.115f, 0f)))
            margin = 0.01f
        })
        addComponent(InputStateComponent())
        addComponent(PlayerStateManager())
        addComponent(PlayerController())
        addComponent(BoneOverride())
    }

}