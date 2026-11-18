package com.pafoid.skate.engine.terrain

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.TerrainComponent
import com.pafoid.skate.engine.ecs.components.TerrainLayer
import com.pafoid.skate.engine.ecs.components.TerrainStreaming
import com.pafoid.skate.engine.ecs.systems.TerrainSystem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** REN-42: the skate-ecs review of TerrainComponent and TerrainSystem (ECS-17). */
class TerrainReviewFixesTest {
    private val seed = 7L

    // (1) total samples are capped
    @Test
    fun Samples_AreCappedByCoarseningTheSpacing() {
        val huge = TerrainComponent(sizeMetres = 16384f, sampleSpacing = 0.5f, streaming = TerrainStreaming.OFF)
        assertEquals(4f, huge.effectiveSpacing())
        assertTrue(huge.spacingCapped())
        assertTrue(TerrainComponent.samplesFor(huge.effectiveSize(), huge.effectiveSpacing()) <= TerrainComponent.MAX_RESIDENT_SAMPLES)
        // Streamed terrains only hold their rings: they keep the requested spacing (REN-37)
        assertEquals(0.5f, TerrainComponent(sizeMetres = 16384f, sampleSpacing = 0.5f).effectiveSpacing())
        val big = TerrainComponent(sizeMetres = 4096f, sampleSpacing = 0.5f, streaming = TerrainStreaming.OFF)
        assertEquals(1f, big.effectiveSpacing(), "4 km fits at 1 m")
        val small = TerrainComponent(sizeMetres = 256f, sampleSpacing = 0.5f)
        assertEquals(0.5f, small.effectiveSpacing())
        assertFalse(small.spacingCapped())
        for (size in listOf(8f, 1000f, 5000f, 9000f, 16384f)) for (spacing in TerrainComponent.SPACINGS) {
            val t = TerrainComponent(sizeMetres = size, sampleSpacing = spacing, streaming = TerrainStreaming.OFF)
            assertTrue(TerrainComponent.samplesFor(t.effectiveSize(), t.effectiveSpacing()) <= TerrainComponent.MAX_RESIDENT_SAMPLES, "$size @ $spacing")
        }
    }

    // (3) key() is pure: load-then-save leaves the file unchanged; no "terrain/Terrain" pinning
    @Test
    fun Key_NeverWritesTheSeedName() {
        val loose = TerrainComponent()
        loose.key(seed)
        assertEquals("", loose.seedName, "an unattached call does not pin terrain/Terrain")
        val terrain = TerrainComponent()
        GameObject("Valley").addComponent(terrain)
        val json = Serializer().json
        val before = json.encodeToString(Component.serializer(), terrain)
        terrain.key(seed)
        terrain.runtime.ensureBuilt(terrain.key(seed), parallel = false)
        assertEquals(before, json.encodeToString(Component.serializer(), terrain), "building changes nothing that is saved")
        assertEquals("terrain/Valley", terrain.resolvedSeedName())
        // A stored name wins over the entity name
        terrain.seedName = "terrain/Kept"
        assertEquals("terrain/Kept", terrain.key(seed).seedName)
    }

    // (4) bad values never throw out of the system, and build sane terrains
    @Test
    fun BadSettings_AreSanitizedOrReportedOnce_NeverThrownEveryFrame() {
        for (bad in listOf(Float.NaN, 0f, -5f, Float.POSITIVE_INFINITY)) {
            val t = TerrainComponent(sizeMetres = bad, sampleSpacing = bad, heightScaleMetres = bad, chunkQuads = 32)
            val key = t.key(seed)
            assertTrue(key.size.isFinite() && key.size >= TerrainComponent.MIN_SIZE, "size from $bad")
            assertTrue(key.spacing in TerrainComponent.SPACINGS, "spacing from $bad")
            assertTrue(key.heightScale.isFinite() && key.heightScale > 0f, "height scale from $bad")
            TerrainRuntime().ensureBuilt(key, parallel = false)
        }
        // Anything that still throws (a broken recipe here) is reported once and retried only after a change
        val reports = ArrayList<String>()
        val broken = TerrainComponent(sizeMetres = 64f, chunkQuads = 32,
            recipe = TerrainRecipe(listOf(TerrainStep.Noise(settings = com.pafoid.skate.engine.procedural.NoiseSettings(frequency = Float.NaN)))))
        val scene = Scene("S").also { it.addChild(GameObject("Bad").addComponent(broken)) }
        val system = TerrainSystem(report = { reports += it }, debounceSeconds = 0f).apply { init(scene) }
        repeat(5) { system.update(0.016f) }
        assertEquals(1, reports.size, reports.toString())
        assertNull(broken.runtime.key)
        broken.recipe = TerrainPreset.FLAT.recipe()
        system.update(0.016f)
        assertEquals(1, system.rebuilt, "a change retries")
    }

    // (2) dragging a slider rebuilds once, after the settings settle
    @Test
    fun Rebuilds_AreDebouncedWhileSettingsKeepChanging() {
        val terrain = TerrainComponent(sizeMetres = 64f, chunkQuads = 32)
        val scene = Scene("S").also { it.addChild(GameObject("T").addComponent(terrain)) }
        val system = TerrainSystem(debounceSeconds = 0.25f).apply { init(scene) }
        system.update(0.016f)
        assertEquals(1, system.rebuilt, "the first build is immediate")
        var rebuilds = 0
        repeat(20) { frame ->
            terrain.heightScaleMetres = 64f + frame // a drag: a new value every frame
            system.update(0.016f); rebuilds += system.rebuilt
        }
        assertEquals(0, rebuilds, "no rebuild while dragging")
        repeat(20) { system.update(0.016f); rebuilds += system.rebuilt }
        assertEquals(1, rebuilds, "one rebuild once it settled")
        assertEquals(64f + 19, terrain.runtime.key!!.heightScale)
    }

    // (5) disabled terrains free their chunks; MAX_LAYERS is enforced where layers are read
    @Test
    fun DisabledTerrain_FreesItsChunks_AndRebuildsWhenEnabled() {
        val terrain = TerrainComponent(sizeMetres = 64f, chunkQuads = 32)
        val scene = Scene("S").also { it.addChild(GameObject("T").addComponent(terrain)) }
        val system = TerrainSystem(debounceSeconds = 0f).apply { init(scene) }
        system.update(0f)
        assertEquals(4, terrain.runtime.chunks.size)
        terrain.enabled = false
        system.update(0f)
        assertTrue(terrain.runtime.chunks.isEmpty())
        val version = terrain.runtime.version
        system.update(0f)
        assertEquals(version, terrain.runtime.version, "cleared once, not every frame")
        terrain.enabled = true
        system.update(0f)
        assertEquals(4, terrain.runtime.chunks.size)
    }

    @Test
    fun Layers_BeyondTheMaximumAreIgnored_NotDeleted() {
        val terrain = TerrainComponent()
        repeat(12) { terrain.layers += TerrainLayer(tilingMetres = it.toFloat()) }
        assertEquals(TerrainComponent.MAX_LAYERS, terrain.effectiveLayers().size)
        assertEquals(16, terrain.layers.size, "the file keeps what the user wrote")
    }
}
