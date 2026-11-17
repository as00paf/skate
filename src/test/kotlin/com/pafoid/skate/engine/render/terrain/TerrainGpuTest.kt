package com.pafoid.skate.engine.render.terrain

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.EngineDefaults
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.TerrainComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.render.rhi.opengl.OpenGlDevice
import com.pafoid.skate.engine.render.rhi.vulkan.BackendParityHarness
import com.pafoid.skate.engine.render.rhi.vulkan.BackendParityHarness.Companion.MAIN
import com.pafoid.skate.engine.render.rhi.vulkan.FrameDiff
import com.pafoid.skate.engine.render.rhi.vulkan.ParityView
import com.pafoid.skate.engine.render.rhi.vulkan.look
import com.pafoid.skate.engine.render.shader.ShaderFeature
import com.pafoid.skate.engine.terrain.TerrainPreset
import com.pafoid.skate.testing.GpuTest
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW.glfwInit
import java.io.File

/**
 * REN-28 task 4 on OpenGL: the terrain shader and its PICKING and DEPTH_ONLY variants compile, and a hill terrain
 * behind MainScene changes the image where it stands. Writes `build/ren36/terrain-{gl,vulkan,diff}.png` ("gl"
 * without the terrain, "vulkan" with it).
 */
@GpuTest
class TerrainGpuTest {
    private val passes = { pass: String -> pass == "GeometryPass" || pass == "ShadowPass" || pass == "TerrainPass" }

    private fun Engine.view() = look(Vector3f(0f, 12f, 30f), Vector3f(0f, 4f, -40f))

    private fun Engine.addTerrain() {
        val scene = sceneManager.currentScene!!
        scene.addChild(GameObject("Hills").addComponent(Transform().apply { translation.set(0f, -2f, -70f) })
            .addComponent(TerrainComponent(sizeMetres = 128f, chunkQuads = 32, heightScaleMetres = 30f, recipe = TerrainPreset.MOUNTAINS.recipe())))
        for (feature in listOf(ShaderFeature.PICKING, ShaderFeature.DEPTH_ONLY)) {
            assertNotNull(assetsManager.getShaderVariant(EngineDefaults.Shaders.TERRAIN, setOf(feature)).rhi, feature.name)
        }
        assertNotNull(assetsManager.getShader(EngineDefaults.Shaders.TERRAIN).rhi)
        update(1f / 60f) // TerrainSystem builds the chunks
        renderer.renderResources.renderPasses.picking.renderNow(scene)
    }

    @Test
    fun Terrain_CompilesAndDraws() {
        assumeTrue(glfwInit(), "no GLFW")
        val without = listOf(ParityView("terrain", MAIN, passes) { it.view() })
        val with = listOf(ParityView("terrain", MAIN, passes) { it.addTerrain(); it.view() })
        val harness = BackendParityHarness(File("build/ren36"))
        val a = harness.renderAll({ OpenGlDevice.create() }, gl = true, without)
        val b = harness.renderAll({ OpenGlDevice.create() }, gl = true, with)
        harness.compare("REN-36 terrain off (gl) vs on (vulkan)", without, a, b) { FrameDiff.Tolerance(255.0, 255, 0.0) }
        val d = FrameDiff(a.getValue("terrain"), b.getValue("terrain"))
        println("REN-36 terrain: ${d.summary()}")
        assertTrue(d.perPixel.count { it > 6 } > d.perPixel.size / 20, "the terrain covers a visible part of the frame")
    }
}
