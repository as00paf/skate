package com.pafoid.skate.engine.prefabs

import com.pafoid.skate.engine.entities.Entity
import com.pafoid.skate.engine.models.TexturedModel
import com.pafoid.skate.engine.physics3d.components.BoxCollider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.components.PlayerController
import com.pafoid.skate.engine.scenes.components.SkateboardPhysics
import com.pafoid.skate.engine.scenes.components.TrickDetector
import org.joml.Vector3f

class Skateboard(
    texturedModel: TexturedModel,
    position: Vector3f = Vector3f(0f, 1f, 0f),
    scale: Vector3f = Vector3f(1f, 1f, 1f),
    mass: Float = 1.8f,// 1.8kg mass
    hitBoxSize: Vector3f = Vector3f(0.4f, 0.02f, 0.1f),// 0.8m x 0.04m x 0.2m
    ): GameObject("Skateboard") {

    init {
        transform.translation.set(position)
        transform.scale.set(scale)
        addComponent(Entity(texturedModel))
        addComponent(RigidBody3D(mass).apply { friction = 0.1f })
        addComponent(BoxCollider3D(hitBoxSize))
        addComponent(SkateboardPhysics())
        addComponent(PlayerController())
        addComponent(TrickDetector())
    }
}