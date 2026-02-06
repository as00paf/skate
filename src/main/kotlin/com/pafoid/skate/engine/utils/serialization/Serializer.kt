package com.pafoid.skate.engine.utils.serialization

import com.pafoid.skate.engine.assets.Texture
import com.pafoid.skate.engine.models.CharacterModel
import com.pafoid.skate.engine.physics3d.components.*
import com.pafoid.skate.engine.scenes.components.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

class Serializer {
    val engineModule = SerializersModule {
        contextual(Vector2fSerializer)
        contextual(Vector3fSerializer)
        contextual(Vector4fSerializer)
        contextual(QuaternionfSerializer)
        contextual(Matrix4fSerializer)

        polymorphic(Component::class) {
            subclass(Transform::class)
            subclass(ModularTile::class)
            subclass(CharacterModel::class)
            subclass(BoxCollider3D::class)
            subclass(CylinderCollider3D::class)
            subclass(CustomCollider3D::class)
            subclass(RigidBody3D::class)
            subclass(Texture::class)
        }
    }

    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        serializersModule = engineModule
    }

    inline fun <reified T> encode(value:T):String {
        return json.encodeToString<T>(value)
    }

    inline fun <reified T> decode(value: String): T {
        return json.decodeFromString<T>(value)
    }
}
