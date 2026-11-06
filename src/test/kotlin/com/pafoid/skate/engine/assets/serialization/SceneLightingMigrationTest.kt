package com.pafoid.skate.engine.assets.serialization

import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneSun
import com.pafoid.skate.engine.ecs.components.DirectionalLightComponent
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** REN-26: schema 1 scenes (AmbientLightComponent, a DirectionalLightComponent on the scene) load into the new model. */
class SceneLightingMigrationTest {
    private val serializer = Serializer()
    private val c = "com.pafoid.skate.engine.ecs.components."

    private fun v1Scene(components: String, children: String = "") = """
        {"schema": 1, "name": "Old", "sceneName": "Old", "components": [$components], "children": [$children]}
    """.trimIndent()

    private val ambient = """{"type": "${c}AmbientLightComponent", "uId": 7, "enabled": true, "name": "AmbientLightComponent",
        "lightColor": {"x": 0.5, "y": 0.6, "z": 0.7}, "intensity": 0.4}"""
    private val sun = """{"type": "${c}DirectionalLightComponent", "uId": 8, "enabled": true, "name": "DirectionalLightComponent",
        "direction": {"x": 0.0, "y": -1.0, "z": 0.0}, "color": {"x": 1.0, "y": 0.5, "z": 0.25}, "intensity": 0.8, "castShadows": false}"""
    private val environment = """{"type": "${c}EnvironmentComponent", "skyExposure": 2.0}"""

    @Test
    fun ambient_MovesIntoTheExistingEnvironment() {
        val scene = serializer.decodeFile<Scene>(FileKind.SCENE, v1Scene("$environment, $ambient"))
        val env = scene.getComponent<EnvironmentComponent>()!!
        assertEquals(Vector3f(0.5f, 0.6f, 0.7f), env.ambientColor)
        assertEquals(0.4f, env.ambientIntensity, 1e-6f)
        assertEquals(2f, env.skyExposure, 1e-6f, "other environment settings are kept")
        assertEquals(1, scene.components.size)
    }

    @Test
    fun ambient_WithoutEnvironment_AddsOne() {
        val scene = serializer.decodeFile<Scene>(FileKind.SCENE, v1Scene(ambient))
        assertEquals(0.4f, scene.getComponent<EnvironmentComponent>()!!.ambientIntensity, 1e-6f)
    }

    @Test
    fun sceneLevelDirectionalLight_BecomesASunEntity() {
        val scene = serializer.decodeFile<Scene>(FileKind.SCENE, v1Scene("$environment, $sun", """{"name": "Floor", "components": [], "children": []}"""))
        assertTrue(scene.components.none { it is DirectionalLightComponent }, "no light left on the scene")
        assertEquals(listOf("Floor", SceneSun.NAME), scene.children.map { it.name })
        val sunObject = scene.children.last()
        val light = sunObject.getComponent<DirectionalLightComponent>()!!
        assertEquals(Vector3f(1f, 0.5f, 0.25f), light.color)
        assertEquals(0.8f, light.intensity, 1e-6f)
        assertEquals(false, light.castShadows)
        assertEquals(Vector3f(0f, 10f, 0f), sunObject.getComponent<Transform>()!!.translation)
    }

    @Test
    fun sceneWithoutOldLighting_IsUnchanged() {
        val scene = serializer.decodeFile<Scene>(FileKind.SCENE, v1Scene(environment))
        assertEquals(1, scene.components.size)
        assertTrue(scene.children.isEmpty())
        assertNull(scene.getComponent<DirectionalLightComponent>())
    }

    @Test
    fun newScenes_AreSavedAtSchema2_AndNotMigratedAgain() {
        assertEquals(2, FileKind.SCENE.currentSchema)
        val scene = SceneSun.addDefaults(Scene("New"))
        val back = serializer.decodeFile<Scene>(FileKind.SCENE, serializer.encodeFile(FileKind.SCENE, scene))
        assertEquals(1, back.children.count { it.name == SceneSun.NAME }, "one sun, not duplicated")
    }
}
