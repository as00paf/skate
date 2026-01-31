package com.pafoid.skate.engine.utils.serialization

import com.pafoid.skate.engine.animation.*
import com.pafoid.skate.engine.assets.Texture
import com.pafoid.skate.engine.entities.Entity
import com.pafoid.skate.engine.models.TexturedModel
import com.pafoid.skate.engine.physics3d.components.*
import com.pafoid.skate.engine.scenes.components.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.joml.*

object Serializer {
    val engineModule = SerializersModule {
        contextual(Vector2fSerializer)
        contextual(Vector3fSerializer)
        contextual(Vector4fSerializer)
        contextual(QuaternionfSerializer)
        contextual(Matrix4fSerializer)

        polymorphic(Component::class) {
            subclass(Transform::class)
            subclass(ModularTile::class)
            subclass(TexturedModel::class)
            subclass(BoxCollider3D::class)
            subclass(CylinderCollider3D::class)
            subclass(CustomCollider3D::class)
            subclass(RigidBody3D::class)
            subclass(Entity::class)
            subclass(Texture::class)
        }
    }

    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        serializersModule = engineModule
    }
}
