package com.pafoid.skate.engine.prefabs

import com.pafoid.skate.engine.scenes.components.RenderComponent
import com.pafoid.skate.engine.scenes.components.SkeletonComponent
import com.pafoid.skate.engine.models.TexturedModel
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.physics3d.components.BoxCollider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.components.ModularTile
import org.joml.Vector3f

class Tile(
    name: String,
    texturedModel: TexturedModel,
    hitBoxSize: Vector3f = Vector3f(1f, 1f, 1f)

): GameObject(name) {

    init {
        addComponent(RenderComponent(model = texturedModel))
        addComponent(ModularTile())
        addComponent(RigidBody3D(0f).apply { bodyType = BodyType.Static })
        addComponent(BoxCollider3D(hitBoxSize))
    }

}