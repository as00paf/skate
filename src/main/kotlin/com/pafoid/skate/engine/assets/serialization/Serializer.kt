package com.pafoid.skate.engine.assets.serialization

import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.BaseModel
import com.pafoid.skate.engine.assets.data.models.CharacterModel
import com.pafoid.skate.engine.assets.data.models.TexturedModel
import com.pafoid.skate.engine.assets.data.models.animations.BoneOverride
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.AudioComponent
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import com.pafoid.skate.engine.ecs.components.DirectionalLightComponent
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.components.InputStateComponent
import com.pafoid.skate.engine.ecs.components.LightingComponent
import com.pafoid.skate.engine.ecs.components.LightingStateComponent
import com.pafoid.skate.engine.ecs.components.ModularTile
import com.pafoid.skate.engine.ecs.components.NonPickable
import com.pafoid.skate.engine.ecs.components.PhysicsComponent
import com.pafoid.skate.engine.ecs.components.PlayerController
import com.pafoid.skate.engine.ecs.components.PlayerStateManager
import com.pafoid.skate.engine.ecs.components.RagdollComponent
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.ecs.components.SpriteRenderer
import com.pafoid.skate.engine.ecs.components.TimeComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.components.TrickAnalyzer
import com.pafoid.skate.engine.ecs.components.TrickDetector
import com.pafoid.skate.engine.physics3d.components.BoxCollider3D
import com.pafoid.skate.engine.physics3d.components.CapsuleCollider3D
import com.pafoid.skate.engine.physics3d.components.CustomCollider3D
import com.pafoid.skate.engine.physics3d.components.CylinderCollider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.game.skateboard.SkateboardPhysics
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
            // Core components
            subclass(Transform::class)
            subclass(RenderComponent::class)
            subclass(PhysicsComponent::class)
            subclass(AudioComponent::class)

            // Input components
            subclass(InputStateComponent::class)

            // Animation components
            subclass(BoneOverride::class)
            subclass(SkeletonComponent::class)
            subclass(Animator::class)

            // Environment components
            subclass(EnvironmentComponent::class)
            subclass(TimeComponent::class)
            subclass(LightingStateComponent::class)
            subclass(LightingComponent::class)
            subclass(DayNightCycleComponent::class)
            subclass(DirectionalLightComponent::class)

            // Editor components
            subclass(NonPickable::class)
            subclass(ModularTile::class)
            subclass(SpriteRenderer::class)
            subclass(RagdollComponent::class)

            // Physics components
            subclass(RigidBody3D::class)
            subclass(BoxCollider3D::class)
            subclass(CylinderCollider3D::class)
            subclass(CustomCollider3D::class)
            subclass(CapsuleCollider3D::class)
            subclass(SkateboardPhysics::class)
            subclass(TrickDetector::class)
            subclass(TrickAnalyzer::class)
            subclass(PlayerController::class)
            subclass(PlayerStateManager::class)

            // Asset components
            subclass(Texture::class)
            subclass(TexturedModel::class)
        }

        polymorphic(BaseModel::class) {
            subclass(TexturedModel::class)
            subclass(CharacterModel::class)
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
