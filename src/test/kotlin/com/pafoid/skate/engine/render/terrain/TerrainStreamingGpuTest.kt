package com.pafoid.skate.engine.render.terrain

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.TerrainComponent
import com.pafoid.skate.engine.ecs.components.TerrainStreaming
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.render.EngineStats
import com.pafoid.skate.engine.render.rhi.opengl.OpenGlDevice
import com.pafoid.skate.engine.render.rhi.vulkan.BackendParityHarness
import com.pafoid.skate.engine.render.rhi.vulkan.BackendParityHarness.Companion.MAIN
import com.pafoid.skate.engine.render.rhi.vulkan.FrameDiff
import com.pafoid.skate.engine.render.rhi.vulkan.ParityView
import com.pafoid.skate.engine.render.rhi.vulkan.look
import com.pafoid.skate.engine.terrain.TerrainPreset
import com.pafoid.skate.testing.GpuTest
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW.glfwInit
import java.io.File

/**
 * REN-28 tasks 14–16 on OpenGL: a 4 km streamed island behind MainScene. Detail chunks stream around the camera and
 * the far field (overview tiles, macro colour) fills the rest out to the horizon without holes. Writes
 * `build/ren37/streamed-{gl,vulkan,diff}.png` ("gl" without the terrain, "vulkan" with it).
 */
@GpuTest
class TerrainStreamingGpuTest {
    private val passes = { pass: String -> pass == "GeometryPass" || pass == "ShadowPass" || pass == "TerrainPass" }
    private var resident = 0
    private var farTiles = 0

    private fun Engine.view() = look(Vector3f(0f, 40f, 60f), Vector3f(0f, 10f, -200f))

    private fun Engine.addStreamedIsland() {
        val terrain = TerrainComponent(sizeMetres = 4096f, sampleSpacing = 2f, chunkQuads = 64, heightScaleMetres = 120f,
            recipe = TerrainPreset.ISLAND.recipe(), streaming = TerrainStreaming.ON)
        sceneManager.currentScene!!.addChild(GameObject("Big Island").addComponent(Transform().apply { translation.set(0f, -8f, -2100f) })
            .addComponent(terrain))
        view()
        update(1f / 60f)
        terrain.runtime.awaitOverview()
        // Let the streamer and the budgeted uploads settle around the camera
        repeat(240) { update(1f / 60f); renderer.render(sceneManager.currentScene!!); Thread.sleep(5) }
        resident = EngineStats.terrainResidentChunks.get()
        farTiles = EngineStats.terrainFarTiles.get()
    }

    @Test
    fun StreamedIsland_DrawsDetailNearAndTheFarFieldBeyond() {
        assumeTrue(glfwInit(), "no GLFW")
        val without = listOf(ParityView("streamed", MAIN, passes) { it.view() })
        val with = listOf(ParityView("streamed", MAIN, passes) { it.addStreamedIsland(); it.view() })
        val harness = BackendParityHarness(File("build/ren37"))
        val a = harness.renderAll({ OpenGlDevice.create() }, gl = true, without)
        val b = harness.renderAll({ OpenGlDevice.create() }, gl = true, with)
        harness.compare("REN-37 streamed island off (gl) vs on (vulkan)", without, a, b) { FrameDiff.Tolerance(255.0, 255, 0.0) }
        println("REN-37 streamed: resident chunks $resident, far tiles $farTiles")
        assertTrue(resident in 1..1000, "only the rings are resident: $resident")
        assertTrue(farTiles > 0, "the far field draws")
        val d = FrameDiff(a.getValue("streamed"), b.getValue("streamed"))
        assertTrue(d.perPixel.count { it > 6 } > d.perPixel.size / 20)
    }
}
