package com.pafoid.skate.engine.ecs

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import com.pafoid.skate.engine.ecs.components.DirectionalLightComponent
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.getComponent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/** REN-26: the sun is an entity found by [SceneSun]; new scenes get an environment, a day/night cycle and a Sun. */
class SceneSunTest {

    @Test
    fun addDefaults_GivesEnvironmentDayNightAndASun() {
        val scene = SceneSun.addDefaults(Scene("S"))
        assertNotNull(scene.getComponent<EnvironmentComponent>())
        assertNotNull(scene.getComponent<DayNightCycleComponent>())
        val sun = SceneSun.of(scene)!!
        assertEquals(SceneSun.NAME, sun.gameObject!!.name)
        assertSame(scene, sun.gameObject!!.parent)
    }

    @Test
    fun addDefaults_KeepsWhatTheSceneAlreadyHas() {
        val env = EnvironmentComponent(ambientIntensity = 0.7f)
        val scene = Scene("S").addComponent(env) as Scene
        SceneSun.addDefaults(scene)
        SceneSun.addDefaults(scene)
        assertSame(env, scene.getComponent<EnvironmentComponent>())
        assertEquals(1, scene.children.count { it.getComponent<DirectionalLightComponent>() != null })
    }

    @Test
    fun of_FindsTheFirstLightInSceneOrder_EvenNested() {
        val scene = Scene("S")
        val group = GameObject("Group")
        scene.addChild(group)
        val nested = DirectionalLightComponent()
        group.addChild(GameObject("Nested").addComponent(nested))
        scene.addChild(SceneSun.create())
        assertSame(nested, SceneSun.of(scene))
    }

    @Test
    fun of_IgnoresDisabledLightsAndEntities_AndFollowsChanges() {
        val scene = Scene("S")
        assertNull(SceneSun.of(scene))
        val sunObject = SceneSun.create()
        scene.addChild(sunObject)
        val light = sunObject.getComponent<DirectionalLightComponent>()!!
        assertSame(light, SceneSun.of(scene))

        light.enabled = false
        assertNull(SceneSun.of(scene))
        light.enabled = true
        sunObject.isEnabled = false
        assertNull(SceneSun.of(scene))
        sunObject.isEnabled = true

        scene.removeChild(sunObject)
        assertNull(SceneSun.of(scene), "the cache follows structural changes")
    }

    @Test
    fun directionalLight_IsAnEntityComponent_NotASceneOne() {
        val scene = Scene("S")
        scene.addComponent(DirectionalLightComponent())
        assertNull(scene.getComponent<DirectionalLightComponent>(), "a scene can no longer hold the sun")
    }
}
