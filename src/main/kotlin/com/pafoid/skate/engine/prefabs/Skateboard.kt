package com.pafoid.skate.engine.prefabs

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.entities.Entity
import com.pafoid.skate.engine.physics3d.components.BoxCollider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.components.PlayerController
import com.pafoid.skate.engine.scenes.components.SkateboardPhysics
import com.pafoid.skate.engine.scenes.components.TrickDetector
import org.joml.Vector3f

class Skateboard(private val resourceManager: ResourceManager): GameObject("Skateboard") {
    init {
        transform.translation.set(0f, 2f, 0f)
        transform.scale.set(1.0f, 1.0f, 1.0f) // Now in Meters
        addComponent(Entity(
            model = resourceManager.loadModelSync(Assets.Models.SKATEBOARD_GLB)
        ))
        addComponent(RigidBody3D(1.8f).apply { friction = 0.1f }) // 1.8kg mass
        addComponent(BoxCollider3D(Vector3f(0.4f, 0.02f, 0.1f))) // 0.8m x 0.04m x 0.2m
        addComponent(SkateboardPhysics())
        addComponent(PlayerController())
        addComponent(TrickDetector())
    }
}