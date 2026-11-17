package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.terrain.StampFootprint
import com.pafoid.skate.engine.terrain.StampMode
import com.pafoid.skate.engine.terrain.TerrainPreset
import com.pafoid.skate.engine.terrain.TerrainStamp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** ECS-17: TerrainComponent (REN-36) is in the component registry and survives a scene save/reload. */
class TerrainComponentRegistrationTest {

    private val serializer = Serializer()

    private fun customTerrain() = TerrainComponent(
        sizeMetres = 512f,
        sampleSpacing = 2f,
        heightScaleMetres = 90f,
        chunkQuads = 32,
        source = TerrainSource.PROCEDURAL,
        streaming = TerrainStreaming.ON,
        seedName = "terrain/Hills",
        seedOffset = 7,
        castShadows = false,
    ).apply {
        applyPreset(TerrainPreset.MOUNTAINS)
        stamps.add(TerrainStamp(footprint = StampFootprint.CIRCLE, x = 10f, y = 3f, z = -20f, radius = 12f, mode = StampMode.RAISE_TO))
        layers.removeAt(layers.lastIndex)
        layers.add(TerrainLayer(material = "Materials/snow.skmat", tilingMetres = 10f, triplanar = true))
    }

    @Test
    fun ComponentRegistry_TerrainInstantiatesAndMapsBack() {
        val terrain = ComponentType.TERRAIN.instantiate()

        assertTrue(terrain is TerrainComponent)
        assertEquals(ComponentType.TERRAIN, terrain!!.type)
    }

    @Test
    fun ComponentRegistry_GameObjectFindsTerrainByType() {
        val terrain = TerrainComponent()
        val go = GameObject("Ground").addComponent(terrain)

        assertSame(terrain, go.getComponent(ComponentType.TERRAIN))
    }

    @Test
    fun Serialization_TerrainRoundTripsThroughPolymorphicComponentJson() {
        val terrain: Component = customTerrain()

        val decoded = serializer.decode<Component>(serializer.encode(terrain))

        assertEquals(terrain, decoded)
    }

    @Test
    fun Serialization_TerrainSurvivesSceneSaveAndReloadWithTheSameHeightKey() {
        val scene = Scene("TerrainScene")
        scene.addChild(GameObject("Ground").addComponent(Transform()).addComponent(customTerrain()))

        val reloaded = serializer.decodeFile<Scene>(FileKind.SCENE, serializer.encodeFile(FileKind.SCENE, scene))

        val terrain = reloaded.children.single { it.name == "Ground" }.getComponent<TerrainComponent>()!!
        assertEquals(customTerrain(), terrain)
        // Same key -> TerrainSystem rebuilds the same heights after reload.
        assertEquals(customTerrain().key(42L), terrain.key(42L))
        assertEquals(null, terrain.runtime.key, "runtime chunks are not saved")
    }
}
