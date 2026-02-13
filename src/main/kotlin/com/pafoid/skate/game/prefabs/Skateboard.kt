package com.pafoid.skate.game.prefabs

import com.pafoid.skate.engine.assets.data.models.TexturedModel
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.physics3d.components.BoxCollider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.game.skateboard.SkateboardPhysics
import com.pafoid.skate.game.trick.TrickDetector
import org.joml.Vector3f

class Skateboard(
    texturedModel: TexturedModel,
    position: Vector3f = Vector3f(0f, 1f, 0f),
    scale: Vector3f = Vector3f(1f, 1f, 1f),
    mass: Float = 1.8f,// 1.8kg mass
    hitBoxSize: Vector3f = Vector3f(0.4f, 0.02f, 0.1f),// 0.8m x 0.04m x 0.2m
    ): GameObject("Skateboard") {

    init {
        val transformComponent = Transform()
        transformComponent.translation.set(position)
        transformComponent.scale.set(scale)
        addComponent(transformComponent)
        addComponent(RenderComponent(model = texturedModel))
        addComponent(RigidBody3D(mass).apply { friction = 0.1f })
        addComponent(BoxCollider3D(hitBoxSize))
        addComponent(SkateboardPhysics())
        addComponent(TrickDetector())
    }
}