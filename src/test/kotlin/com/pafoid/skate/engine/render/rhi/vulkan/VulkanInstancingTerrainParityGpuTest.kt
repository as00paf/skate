package com.pafoid.skate.engine.render.rhi.vulkan

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.EngineDefaults
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.TerrainComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.render.instancing.InstanceBatch
import com.pafoid.skate.engine.render.renderer.passes.BaseRenderPass
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.opengl.OpenGlDevice
import com.pafoid.skate.engine.render.rhi.vulkan.BackendParityHarness.Companion.MAIN
import com.pafoid.skate.engine.render.shader.ShaderFeature
import com.pafoid.skate.engine.terrain.TerrainPreset
import com.pafoid.skate.testing.GpuTest
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW.glfwInit
import java.io.File

/**
 * VK-09 (REN-36): instancing and terrain on Vulkan match OpenGL.
 * - Terrain: a hill terrain behind MainScene through TerrainPass (`terrain.glsl`), with and without the shadow pass
 *   (the DEPTH_ONLY variant casts into the sun cascades); the PICKING variant draws through the picking pass.
 * - Instancing: no engine pass draws instanced meshes yet (vegetation, REN-29 task 14b), so [InstancedCubesPass]
 *   draws a 6 × 6 grid of rotated, scaled cubes with one `ModelRenderer.drawInstanced` call through the INSTANCED
 *   variant of the mesh shader (instance stream at locations 10–13), lit by the scene frame.
 * Opt-in (`SKATE_RHI=vulkan`), GPU-only (@GpuTest). Writes `build/vk09/<view>-{gl,vulkan,diff}.png` and `report.txt`.
 */
@GpuTest
class VulkanInstancingTerrainParityGpuTest {

    /** Draws [batch]'s instances after the opaque geometry into the scene target. */
    private class InstancedCubesPass(private val engine: Engine, private val batch: InstanceBatch) : BaseRenderPass() {
        override val name: String = NAME
        var drawn = false

        override fun execute(scene: Scene) {
            val passes = engine.renderer.renderResources.renderPasses
            val frame = passes.sceneFrame
            if (!frame.began) return
            val shader = engine.assetsManager.getShaderVariant(EngineDefaults.Shaders.DEFAULT_3D, setOf(ShaderFeature.INSTANCED))
            val cmd = RhiContext.require().commandList()
            cmd.beginPass(engine.renderer.frameBuffer.target)
            shader.start()
            frame.prepare(shader)
            frame.selectObjectLights(shader, Vector3f(0f, 1f, 0f), 6f)
            drawn = engine.renderer.renderResources.renderers.model.drawInstanced(batch, shader) || drawn
            shader.stop()
            cmd.endPass()
        }

        companion object {
            const val NAME = "VK09InstancedCubes"
        }
    }

    private val terrainPasses = { pass: String -> pass == "GeometryPass" || pass == "TerrainPass" }
    private val terrainShadowPasses = { pass: String -> pass == "GeometryPass" || pass == "ShadowPass" || pass == "TerrainPass" }
    private val instancedPasses = { pass: String -> pass == "GeometryPass" || pass == "ShadowPass" || pass == InstancedCubesPass.NAME }

    private fun Engine.addTerrain() {
        // Each harness run creates a project with a new random seed: pin it so both backends build the same heights
        random.reseed(SEED)
        val scene = sceneManager.currentScene!!
        scene.addChild(GameObject("[VK-09] Hills").addComponent(Transform().apply { translation.set(0f, -2f, -70f) })
            .addComponent(TerrainComponent(sizeMetres = 128f, chunkQuads = 32, heightScaleMetres = 30f, recipe = TerrainPreset.MOUNTAINS.recipe())))
        for (feature in listOf(ShaderFeature.PICKING, ShaderFeature.DEPTH_ONLY)) {
            assertNotNull(assetsManager.getShaderVariant(EngineDefaults.Shaders.TERRAIN, setOf(feature)).rhi, feature.name)
        }
        update(1f / 60f) // TerrainSystem builds the chunks
        renderer.renderResources.renderPasses.picking.renderNow(scene) // the terrain PICKING variant draws
    }

    private val passesAdded = ArrayList<InstancedCubesPass>()

    private fun Engine.addInstancedCubes() {
        val part = assetsManager.loadModel(EngineDefaults.Models.CUBE).mesh.first()
        val batch = InstanceBatch(part, Material(baseColor = Vector4f(0.85f, 0.45f, 0.2f, 1f), roughnessFactor = 0.6f))
        for (i in 0 until 6) for (j in 0 until 6) {
            val world = Matrix4f().translation(-3.75f + i * 1.5f, 0.6f + 0.15f * ((i + j) % 3), -2f + j * 1.5f)
                .rotateY(0.3f * i + 0.2f * j).scale(0.5f + 0.08f * ((i * 7 + j) % 4))
            batch.add(world, tint = 0.1f * i, random = 0.1f * j)
        }
        val pass = InstancedCubesPass(this, batch)
        renderer.renderGraph.addPass(pass)
        passesAdded += pass
    }

    private val views = listOf(
        ParityView("terrain", MAIN, terrainPasses) { e -> e.addTerrain(); e.look(Vector3f(0f, 12f, 30f), Vector3f(0f, 4f, -40f)) },
        ParityView("terrain-shadows", MAIN, terrainShadowPasses) { e -> e.look(Vector3f(-20f, 25f, -10f), Vector3f(0f, 5f, -70f)) },
        ParityView("instanced-cubes", MAIN, instancedPasses) { e -> e.addInstancedCubes(); e.look(Vector3f(0f, 6f, 10f), Vector3f(0f, 0.5f, 0f)) },
    )

    private companion object {
        const val SEED = 0x5EA7E_09L
    }

    @Test
    fun InstancingAndTerrain_EveryView_VulkanMatchesOpenGl() {
        assumeTrue(RhiBackend.selected() == RhiBackend.VULKAN, "Vulkan is opt-in: set SKATE_RHI=vulkan")
        assumeTrue(glfwInit(), "no GLFW")
        assumeTrue(runCatching { VulkanDevice.create().close() }.isSuccess, "no Vulkan driver")
        val errorsBefore = VulkanDevice.validationErrors
        val harness = BackendParityHarness(File("build/vk09"))
        val gl = harness.renderAll({ OpenGlDevice.create() }, gl = true, views)
        val vk = harness.renderAll({ VulkanDevice.create() }, gl = false, views)
        assertTrue(passesAdded.size == 2 && passesAdded.all { it.drawn }, "the instanced batch drew on both backends")
        val (report, failures) = harness.compare("VK-09 instancing and terrain parity", views, gl, vk) { VulkanGeometryParityGpuTest.TOLERANCE }
        assertTrue(failures.isEmpty(), "views outside tolerance: $failures\n$report")
        assertTrue(VulkanDevice.validationErrors == errorsBefore, "Vulkan validation errors (see the skate.rhi.vulkan log)")
    }
}
