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

/** Orientation: the deck is +Y up and the long axis is Z with the nose toward +Z, the same as a character at yaw 0. */
class Skateboard(
    model: `3dModel`,
    /** Default: resting on the ground one meter ahead of a default Skater (which faces +Z). */
    position: Vector3f = Vector3f(0f, 0.06f, 1f),
    mass: Float = 1.8f,// 1.8kg mass
    /** Collider half extents in world meters (x = width, y = height, z = length): wheels to deck top, between the kicks. */
    hitBoxHalfExtents: Vector3f = Vector3f(SkateboardShape.HALF_WIDTH, 0.05f, 0.38f),
    ): GameObject("Skateboard") {

    init {
        // Physics lives on the root with a centred box so the collider and its debug wireframe are exact; the
        // visual child (SkateboardShape, in metres) only drops the model to the bottom of the box.
        val transformComponent = Transform()
        transformComponent.translation.set(position)
        addComponent(transformComponent)
        addComponent(RigidBody3D(mass, bodyType = BodyType.Dynamic).apply {
            friction = 0.1f
            useCCD = true
        })
        addComponent(BoxCollider3D(Vector3f(hitBoxHalfExtents), Vector3f()))

        // The model origin is the ground contact, so the visual sits at the bottom of the box.
        val visual = GameObject("Skateboard Model")
        visual.addComponent(Transform().apply {
            translation.set(0f, -hitBoxHalfExtents.y, 0f)
        })
        visual.addComponent(RenderComponent(model = model))
        addChild(visual)
    }
}