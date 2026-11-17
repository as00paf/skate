package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.attachComponent
import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.environment.GerstnerWave
import com.pafoid.skate.engine.getComponent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** ECS-16: WaterComponent (REN-35) is in the component registry and survives a scene save/reload. */
class WaterComponentRegistrationTest {

    private val serializer = Serializer()

    private fun customWater() = WaterComponent(sizeX = 12f, sizeZ = 30f, depth = 4f, windDrivenWaves = true).apply {
        applyPreset(WaterPreset.RIVER)
        waves.add(GerstnerWave(directionDeg = 45f, wavelength = 6f, amplitude = 0.2f, steepness = 0.7f))
        flowVelocity.set(0.5f, -1.5f)
        density = 1025f
        cutsOcean = false
    }

    @Test
    fun ComponentRegistry_WaterInstantiatesAndMapsBack() {
        val water = ComponentType.WATER.instantiate()

        assertTrue(water is WaterComponent)
        assertEquals(ComponentType.WATER, water!!.type)
    }

    @Test
    fun ComponentRegistry_GameObjectFindsWaterByType() {
        val go = GameObject("Pool")
        val water = WaterComponent()
        go.addComponent(water)

        assertSame(water, go.getComponent(ComponentType.WATER))
    }

    @Test
    fun Serialization_WaterRoundTripsThroughPolymorphicComponentJson() {
        val water: Component = customWater()

        val decoded = serializer.decode<Component>(serializer.encode(water))

        assertEquals(water, decoded)
    }

    @Test
    fun Serialization_WaterSurvivesSceneSaveAndReload() {
        val scene = Scene("WaterScene")
        val pool = GameObject("Pool").addComponent(Transform()).addComponent(customWater())
        scene.addChild(pool)

        val reloaded = serializer.decodeFile<Scene>(FileKind.SCENE, serializer.encodeFile(FileKind.SCENE, scene))

        val water = reloaded.children.single { it.name == "Pool" }.getComponent<WaterComponent>()
        assertNotNull(water)
        assertEquals(customWater(), water)
    }

    @Test
    fun ComponentRegistry_EveryAddableTypeIsFoundByTypeAndSerializable() {
        ComponentType.entries.forEach { type ->
            val component = type.instantiate() ?: return@forEach
            val go = GameObject("Holder")
            go.attachComponent(component)

            assertSame(component, go.getComponent(type), "getComponent($type) does not find ${component.javaClass.simpleName}")
            val decoded = serializer.decode<Component>(serializer.encode<Component>(component))
            assertEquals(component.javaClass, decoded.javaClass, "$type does not round-trip as a Component")
        }
    }
}
