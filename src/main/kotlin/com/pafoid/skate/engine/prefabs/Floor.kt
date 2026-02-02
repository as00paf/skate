package com.pafoid.skate.engine.prefabs

import com.pafoid.skate.engine.entities.Entity
import com.pafoid.skate.engine.models.TexturedModel
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.physics3d.components.BoxCollider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.scenes.GameObject
import org.joml.Vector3f

class Floor(
    var texturedModel: TexturedModel,
    position: Vector3f = Vector3f(0f, -0.5f, 0f),
    size: Float = 100f,
): GameObject("Floor") {

    init {
        transform.translation.set(position)
        transform.scale.set(Vector3f(size, 1f, size))

        addComponent(Entity(
            model = texturedModel,
            textureScale = 20.0f
        ))
        val groundRb = RigidBody3D(1f)
        groundRb.bodyType = BodyType.Static
        addComponent(groundRb)
        addComponent(BoxCollider3D(Vector3f(size, 1f, size)))
    }
}