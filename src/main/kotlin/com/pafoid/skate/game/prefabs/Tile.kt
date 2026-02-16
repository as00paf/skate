package com.pafoid.skate.game.prefabs

import com.pafoid.skate.engine.assets.data.models.TexturedModel
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.ModularTile
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.physics3d.components.BoxCollider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import org.joml.Vector3f

class Tile(
    name: String,
    texturedModel: TexturedModel,
    position: Vector3f = Vector3f(0f, 0f, 0f),
    hitBoxSize: Vector3f = Vector3f(1f, 1f, 1f)
): GameObject(name) {

    init {
        addComponent(Transform(position, Vector3f(1f, 0.1f, 1f)))
        addComponent(RenderComponent(model = texturedModel, textureScale = 3f))
        addComponent(ModularTile())
        addComponent(RigidBody3D(1f).apply { bodyType = BodyType.Static })
        addComponent(BoxCollider3D(hitBoxSize))
    }

}