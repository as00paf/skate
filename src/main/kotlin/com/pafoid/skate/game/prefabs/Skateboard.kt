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
    scale: Vector3f = Vector3f(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE),
    mass: Float = 1.8f,// 1.8kg mass
    /** Collider half extents in world meters (x = width, y = height, z = length). */
    hitBoxHalfExtents: Vector3f = Vector3f(0.1f, 0.05f, 0.38f),
    ): GameObject("Skateboard") {

    init {
        // Physics lives on the root at scale 1 with a centred box so the collider and its debug wireframe are exact;
        // only the visual child carries the model scale.
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
            this.scale.set(scale)
        })
        visual.addComponent(RenderComponent(model = model))
        addChild(visual)
    }

    companion object {
        /** The source glb is an FBX export in centimeters (x100 in its nodes); this gives a 0.81 m deck. */
        const val MODEL_SCALE = 0.0017f
    }
}