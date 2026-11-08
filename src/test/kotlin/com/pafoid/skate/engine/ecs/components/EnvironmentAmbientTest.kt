package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.getComponent
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** REN-26: ambient light is an environment setting (colour and intensity), saved with the scene. */
class EnvironmentAmbientTest {
    private val eps = 1e-5f

    @Test
    fun defaults_WhiteAtAQuarter() {
        val env = EnvironmentComponent()
        assertEquals(Vector3f(1f, 1f, 1f), env.ambientColor)
        assertEquals(0.25f, env.ambientIntensity, eps)
        assertEquals(Vector3f(0.25f, 0.25f, 0.25f), env.ambient(Vector3f()))
    }

    @Test
    fun ambient_IsColourTimesIntensity() {
        val env = EnvironmentComponent(ambientColor = Vector3f(0.5f, 1f, 0.2f), ambientIntensity = 2f)
        assertEquals(Vector3f(1f, 2f, 0.4f), env.ambient(Vector3f()))
    }

    @Test
    fun reset_RestoresTheAmbientDefaults() {
        val env = EnvironmentComponent(ambientColor = Vector3f(0.1f, 0.2f, 0.3f), ambientIntensity = 3f)
        env.reset()
        assertEquals(Vector3f(1f, 1f, 1f), env.ambientColor)
        assertEquals(EnvironmentComponent.DEFAULT_AMBIENT_INTENSITY, env.ambientIntensity, eps)
    }

    @Test
    fun ambient_SurvivesASceneRoundTrip() {
        val serializer = Serializer()
        val scene = Scene("S").addComponent(EnvironmentComponent(ambientColor = Vector3f(0.2f, 0.4f, 0.8f), ambientIntensity = 0.6f)) as Scene
        val back = serializer.decodeFile<Scene>(FileKind.SCENE, serializer.encodeFile(FileKind.SCENE, scene))
        val env = back.getComponent<EnvironmentComponent>()!!
        assertEquals(Vector3f(0.2f, 0.4f, 0.8f), env.ambientColor)
        assertEquals(0.6f, env.ambientIntensity, eps)
    }
}
