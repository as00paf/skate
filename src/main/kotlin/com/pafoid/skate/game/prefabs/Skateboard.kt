package com.pafoid.skate.game.prefabs

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.BoxCollider3D
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.physics3d.BodyType
import org.joml.Vector3f

class Skateboard(
    model: `3dModel`,
    position: Vector3f = Vector3f(0f, 1f, 0f),
    scale: Vector3f = Vector3f(0.0017f, 0.0017f, 0.0017f),
    mass: Float = 1.8f,// 1.8kg mass
    hitBoxSize: Vector3f = Vector3f(0.4f, 0.02f, 0.1f),// 0.8m x 0.04m x 0.2m
    ): GameObject("Skateboard") {

    init {
        val transformComponent = Transform()
        transformComponent.translation.set(position)
        transformComponent.scale.set(scale)
        addComponent(transformComponent)
        addComponent(
            RenderComponent(
                model = model,
            )
        )
        addComponent(RigidBody3D(mass, bodyType = BodyType.Dynamic).apply {
            friction = 0.1f
            useCCD = true
        })
        addComponent(BoxCollider3D(hitBoxSize))
    }
}