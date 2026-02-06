package com.pafoid.skate.engine.prefabs

import com.pafoid.skate.engine.scenes.components.RenderComponent
import com.pafoid.skate.engine.scenes.components.SkeletonComponent
import com.pafoid.skate.engine.models.CharacterModel
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.physics3d.components.BoxCollider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.components.Transform
import org.joml.Vector3f

class Floor(
    characterModel: CharacterModel,
    position: Vector3f = Vector3f(0f, -1f, 0f),
    size: Float = 100f,
): GameObject("Floor") {

    init {
        val transformComponent = Transform()
        transformComponent.translation.set(position)
        transformComponent.scale.set(Vector3f(size, 1f, size))
        addComponent(transformComponent)

        addComponent(RenderComponent(
            model = characterModel,
            textureScale = 20.0f
        ))
        // Add skeleton component if the model has a skeleton
        characterModel.skeleton?.let { skeleton ->
            addComponent(SkeletonComponent(skeleton = skeleton.copy()))
        }
        val groundRb = RigidBody3D(1f)
        groundRb.bodyType = BodyType.Static
        addComponent(groundRb)
        addComponent(BoxCollider3D(Vector3f(size, 1f, size)))
    }
}