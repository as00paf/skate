package com.pafoid.skate.engine.render.rhi.vulkan

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.TerrainComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.render.EngineStats
import com.pafoid.skate.engine.render.rhi.opengl.OpenGlDevice
import com.pafoid.skate.engine.render.rhi.vulkan.BackendParityHarness.Companion.MAIN
import com.pafoid.skate.engine.render.terrain.TerrainDebug
import com.pafoid.skate.testing.GpuTest
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW.glfwInit
import java.io.File

/**
 * VK-10 (REN-36 tasks 5–7): splat-shaded terrain and instanced scatter on Vulkan match OpenGL. The showcase island
 * (beach, grass with rocks, rocky peak) shades its chunks from the splat weight arrays and the layer albedo / normal /
 * ORM arrays (`sampler2DArray`, units 0–4), with the `vec4` stream at location 1; its scatter draws through the
 * INSTANCED mesh shader and, with the shadow pass on, the INSTANCED shadow pipeline; the biome debug view uses
 * `uBiomeColor0..7`. The picking pass runs the terrain and scatter PICKING variants. Opt-in (`SKATE_RHI=vulkan`),
 * GPU-only (@GpuTest). Writes `build/vk10/<view>-{gl,vulkan,diff}.png` and `build/vk10/report.txt`.
 */
@GpuTest
class VulkanTerrainSplatScatterParityGpuTest {
    private val colour = { pass: String -> pass == "GeometryPass" || pass == "TerrainPass" }
    private val shadowed = { pass: String -> pass == "GeometryPass" || pass == "ShadowPass" || pass == "TerrainPass" }

    /** Instances drawn in the last frame of the island-scatter view, per backend: the scatter really drew. */
    private val scatterInstances = ArrayList<Int>()

    private fun Engine.addIsland() {
        random.reseed(SEED) // each harness run creates a project with a new seed: same heights, splat and scatter
        val scene = sceneManager.currentScene!!
        scene.addChild(GameObject("[VK-10] Island").addComponent(Transform().apply { translation.set(0f, -0.5f, -60f) })
            .addComponent(TerrainComponent.showcaseIsland()))
        update(1f / 60f) // TerrainSystem builds the chunks, splat and scatter
        renderer.renderResources.renderPasses.picking.renderNow(scene) // terrain and scatter PICKING variants
    }

    private val views = listOf(
        ParityView("island", MAIN, colour) { e -> e.addIsland(); e.look(Vector3f(0f, 14f, -5f), Vector3f(0f, 2f, -60f)) },
        ParityView("island-scatter", MAIN, colour) { e -> e.look(Vector3f(-4f, 9f, -14f), Vector3f(-14f, 3f, -35f)) },
        ParityView("island-scatter-shadows", MAIN, shadowed) { e ->
            scatterInstances += EngineStats.instances.get() // still the previous view's last frame
            e.look(Vector3f(-4f, 9f, -14f), Vector3f(-14f, 3f, -35f)) },
        ParityView("island-biomes", MAIN, colour) { e -> TerrainDebug.showBiomes = true; e.look(Vector3f(0f, 30f, -10f), Vector3f(0f, 0f, -60f)) },
    )

    @AfterEach
    fun resetDebug() {
        TerrainDebug.showBiomes = false
    }

    @Test
    fun SplatAndScatter_EveryView_VulkanMatchesOpenGl() {
        assumeTrue(RhiBackend.selected() == RhiBackend.VULKAN, "Vulkan is opt-in: set SKATE_RHI=vulkan")
        assumeTrue(glfwInit(), "no GLFW")
        assumeTrue(runCatching { VulkanDevice.create().close() }.isSuccess, "no Vulkan driver")
        val errorsBefore = VulkanDevice.validationErrors
        val harness = BackendParityHarness(File("build/vk10"))
        val gl = harness.renderAll({ OpenGlDevice.create() }, gl = true, views).also { TerrainDebug.showBiomes = false }
        val vk = harness.renderAll({ VulkanDevice.create() }, gl = false, views).also { TerrainDebug.showBiomes = false }
        val (report, failures) = harness.compare("VK-10 terrain splat and scatter parity", views, gl, vk) { VulkanGeometryParityGpuTest.TOLERANCE }
        assertTrue(failures.isEmpty(), "views outside tolerance: $failures\n$report")
        println("VK-10 scatter instances in the last island-scatter frame (gl, vulkan): $scatterInstances")
        assertTrue(scatterInstances.size == 2 && scatterInstances.all { it > 0 } && scatterInstances[0] == scatterInstances[1],
            "the scatter drew the same instances on both backends: $scatterInstances")
        assertTrue(VulkanDevice.validationErrors == errorsBefore, "Vulkan validation errors (see the skate.rhi.vulkan log)")
    }

    private companion object {
        const val SEED = 0x5EA7E_10L
    }
}
