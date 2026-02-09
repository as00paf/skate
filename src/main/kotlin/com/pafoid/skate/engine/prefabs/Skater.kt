package com.pafoid.skate.engine.prefabs

import com.pafoid.skate.engine.animation.Animator
import com.pafoid.skate.engine.animation.PoseGizmo
import com.pafoid.skate.engine.animation.SkeletonPose
import com.pafoid.skate.engine.models.CharacterModel
import com.pafoid.skate.engine.physics3d.components.BoxCollider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.components.RenderComponent
import com.pafoid.skate.engine.scenes.components.SkeletonComponent
import com.pafoid.skate.engine.scenes.components.Transform
import org.joml.Vector3f

class Skater(
    name: String,
    characterModel: CharacterModel,
    skate: GameObject? = null,
    position: Vector3f = Vector3f(0f, 1.05f, 0f),
    rotation: Vector3f = Vector3f(0f, 90f, 0f),
    scale: Vector3f = Vector3f(1f, 1f, 1f),
    mass: Float = 1.8f,// 1.8kg mass
    hitBoxSize: Vector3f = Vector3f(0.4f, 0.02f, 0.1f),
): GameObject(name) {

    val transformComponent = Transform()
    val renderComponent = RenderComponent(model = characterModel)
    val skeletonComponent = SkeletonComponent(SkeletonPose(characterModel.skeleton.copy()))
    val animator = Animator()

    init {
        // Parenting: Skater follows Skateboard
        skate?.addChild(this)

        transformComponent.translation.set(Vector3f(position.x, position.y + 0.0425f, position.z))
        transformComponent.rotation.set(rotation) // Face sideways for skating
        transformComponent.scale.set(scale) // Now in Meters
        addComponent(transformComponent)
        addComponent(renderComponent)
        addComponent(skeletonComponent)
        addComponent(animator)
        addComponent(RigidBody3D(mass).apply { friction = 0.1f })
        addComponent(BoxCollider3D(hitBoxSize))
        //addComponent(BoneOverride())
        addComponent(PoseGizmo())
    }

}