package com.pafoid.skate.engine.assets.serialization

import com.pafoid.skate.engine.ecs.components.AmbientLightComponent
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.AudioComponent
import com.pafoid.skate.engine.ecs.components.BoneOverride
import com.pafoid.skate.engine.ecs.components.BoxCollider3D
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.CapsuleCollider3D
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.CustomCollider3D
import com.pafoid.skate.engine.ecs.components.CylinderCollider3D
import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import com.pafoid.skate.engine.ecs.components.DirectionalLightComponent
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.components.GridLines
import com.pafoid.skate.engine.ecs.components.InputStateComponent
import com.pafoid.skate.engine.ecs.components.NativeScriptComponent
import com.pafoid.skate.engine.ecs.components.NonPickable
import com.pafoid.skate.engine.ecs.components.PlayerController
import com.pafoid.skate.engine.ecs.components.PointLightComponent
import com.pafoid.skate.engine.ecs.components.RagdollComponent
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.ScenePhysicsComponent
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.ecs.components.SpotLightComponent
import com.pafoid.skate.engine.ecs.components.SpriteRenderer
import com.pafoid.skate.engine.ecs.components.Transform
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

class Serializer {
    val engineModule = SerializersModule {
        contextual(Vector2iSerializer)
        contextual(Vector2fSerializer)
        contextual(Vector3fSerializer)
        contextual(Vector4fSerializer)
        contextual(QuaternionfSerializer)
        contextual(Matrix4fSerializer)

        polymorphic(Component::class) {
            // Core components
            subclass(Transform::class)
            subclass(RenderComponent::class)
            subclass(AudioComponent::class)
            subclass(CameraComponent::class)

            // Physics components
            subclass(RigidBody3D::class)
            subclass(BoxCollider3D::class)
            subclass(CylinderCollider3D::class)
            subclass(CustomCollider3D::class)
            subclass(CapsuleCollider3D::class)

            // Animation components
            subclass(BoneOverride::class)
            subclass(SkeletonComponent::class)
            subclass(Animator::class)
            subclass(SpriteRenderer::class)
            subclass(RagdollComponent::class)

            // Light components
            subclass(AmbientLightComponent::class)//Scene component
            subclass(DirectionalLightComponent::class)//Scene component
            subclass(PointLightComponent::class)
            subclass(SpotLightComponent::class)

            // Scripting
            subclass(NativeScriptComponent::class)

            // Debug/Editor components
            subclass(NonPickable::class)

            // Scene components
            subclass(GridLines::class)
            subclass(ScenePhysicsComponent::class)
            subclass(EnvironmentComponent::class)
            subclass(DayNightCycleComponent::class)

            // Custom
            subclass(PlayerController::class)
            subclass(InputStateComponent::class)
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
