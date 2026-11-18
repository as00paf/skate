package com.pafoid.skate.engine.terrain.streaming

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.StreamingFocus
import com.pafoid.skate.engine.ecs.components.TerrainComponent
import com.pafoid.skate.engine.ecs.components.TerrainStreaming
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.TerrainStreamingSystem
import com.pafoid.skate.engine.ecs.systems.TerrainSystem
import com.pafoid.skate.engine.terrain.TerrainPreset
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** REN-28 task 16: streaming focus and the driving system. */
class TerrainStreamingSystemTest {
    private val direct = java.util.concurrent.Executor { it.run() }

    private fun terrain(streaming: TerrainStreaming = TerrainStreaming.ON) =
        TerrainComponent(sizeMetres = 1024f, chunkQuads = 32, recipe = TerrainPreset.ROLLING_HILLS.recipe(), streaming = streaming, biomes = mutableListOf())

    private fun Scene.step(vararg systems: com.pafoid.skate.engine.ecs.systems.System, frames: Int = 4) =
        repeat(frames) { systems.forEach { s -> s.update(0.1f) } }

    @Test
    fun AutoStreaming_StartsPast256Chunks() {
        assertFalse(TerrainComponent(sizeMetres = 256f).isStreamed(), "16 chunks")
        assertTrue(TerrainComponent(sizeMetres = 2048f).isStreamed(), "1024 chunks")
        assertFalse(TerrainComponent(sizeMetres = 2048f, streaming = TerrainStreaming.OFF).isStreamed())
        assertTrue(TerrainComponent(sizeMetres = 128f, streaming = TerrainStreaming.ON).isStreamed())
    }

    @Test
    fun Focus_StreamsChunksAroundItself_InTerrainSpace() {
        val t = terrain()
        val scene = Scene("S")
        scene.addChild(GameObject("World").addComponent(Transform().apply { worldMatrix.translation(1000f, 0f, 0f) }).addComponent(t))
        val player = GameObject("Player").addComponent(Transform().apply { worldMatrix.translation(1000f + 300f, 0f, 0f) }).addComponent(StreamingFocus())
        scene.addChild(player)
        val build = TerrainSystem(debounceSeconds = 0f).apply { init(scene) }
        val stream = TerrainStreamingSystem(executor = direct).apply { rings = StreamingRings(64f, 160f); init(scene) }
        scene.step(build, stream)
        assertTrue(t.runtime.chunks.isNotEmpty())
        assertTrue(t.runtime.heightField!!.isDetailed(300f, 0f), "around the player, in terrain-local space")
        assertFalse(t.runtime.heightField!!.isDetailed(-300f, 0f))
        assertNotNull(stream.streamer(t))
    }

    @Test
    fun Camera_IsARenderFocus() {
        val t = terrain()
        val scene = Scene("S").also { it.addChild(GameObject("World").addComponent(Transform()).addComponent(t)) }
        val build = TerrainSystem(debounceSeconds = 0f).apply { init(scene) }
        val stream = TerrainStreamingSystem(cameraPosition = { Vector3f(-200f, 50f, 200f) }, executor = direct)
            .apply { rings = StreamingRings(64f, 160f); init(scene) }
        scene.step(build, stream)
        assertTrue(t.runtime.heightField!!.isDetailed(-200f, 200f))
    }

    @Test
    fun SmallOrDisabledTerrains_AreNotStreamed() {
        val small = terrain(TerrainStreaming.OFF)
        val scene = Scene("S").also { it.addChild(GameObject("Small").addComponent(Transform()).addComponent(small)) }
        val build = TerrainSystem(debounceSeconds = 0f).apply { init(scene) }
        val stream = TerrainStreamingSystem(executor = direct).apply { init(scene) }
        scene.step(build, stream, frames = 1)
        assertNull(stream.streamer(small))
        assertEquals(1024, small.runtime.chunks.size, "everything resident")
        small.streaming = TerrainStreaming.ON
        scene.step(build, stream, frames = 1)
        assertNotNull(stream.streamer(small))
        small.enabled = false
        scene.step(build, stream, frames = 1)
        assertNull(stream.streamer(small), "disabled: streamer closed")
    }
}
