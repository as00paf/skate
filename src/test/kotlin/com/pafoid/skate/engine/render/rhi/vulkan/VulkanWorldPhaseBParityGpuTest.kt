package com.pafoid.skate.engine.render.rhi.vulkan

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.components.WaterComponent
import com.pafoid.skate.engine.ecs.components.WaterMaterials
import com.pafoid.skate.engine.render.renderer.passes.PostProcessSettings
import com.pafoid.skate.engine.render.rhi.opengl.OpenGlDevice
import com.pafoid.skate.engine.render.rhi.vulkan.BackendParityHarness.Companion.MAIN
import com.pafoid.skate.engine.render.rhi.vulkan.BackendParityHarness.Companion.SHOWCASE
import com.pafoid.skate.game.samples.FeatureShowcase
import com.pafoid.skate.testing.GpuTest
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW.glfwInit
import java.io.File

/**
 * VK-08 (REN-29 task 14a): world phase B on Vulkan matches OpenGL. The water pass copies the scene colour and depth
 * (the Vulkan depth copy), then refracts, absorbs and reflects (SSR off, Medium and High); wind drives the waves and
 * the detail normals; blended meshes in the transparent pass draw over the water surface, which writes depth. Opt-in
 * (`SKATE_RHI=vulkan`), GPU-only (@GpuTest). Writes `build/vk08/<view>-{gl,vulkan,diff}.png` and `build/vk08/report.txt`.
 */
@GpuTest
class VulkanWorldPhaseBParityGpuTest {
    private val passes = { pass: String -> pass == "GeometryPass" || pass == "ShadowPass" || pass == "WaterPass" || pass == "TransparentPass" }

    private fun Engine.addPool(at: Vector3f, sizeX: Float = 6f, sizeZ: Float = 4f, material: String = WaterMaterials.POOL): WaterComponent {
        val water = WaterComponent(sizeX = sizeX, sizeZ = sizeZ, depth = 1f, material = material)
        sceneManager.currentScene!!.addChild(GameObject("[VK-08] Pool").addComponent(Transform().apply { translation.set(at) }).addComponent(water))
        return water
    }

    private fun Engine.ssr(steps: Int) =
        renderer.renderResources.renderPasses.postProcess.updateSettings(PostProcessSettings().also { it.ssrSteps = steps })

    private val views: List<ParityView> = buildList {
        for ((name, steps) in listOf("off" to 0, "medium" to PostProcessSettings.SSR_STEPS_MEDIUM, "high" to PostProcessSettings.SSR_STEPS_HIGH)) {
            add(ParityView("main-pool-ssr-$name", MAIN, passes) { e ->
                e.ssr(steps)
                e.addPool(Vector3f(0f, 0.4f, 0f))
                e.look(Vector3f(0f, 4f, 9f), Vector3f(0f, 0f, 0f))
            })
        }
        add(ParityView("main-pool-storm", MAIN, passes) { e ->
            e.addPool(Vector3f(0f, 0.4f, 0f), material = WaterMaterials.DEFAULT).windDrivenWaves = true
            e.environment()?.apply { windSpeed = 16f; windDirection = 60f; windTurbulence = 0.8f }
            e.look(Vector3f(3f, 3f, 7f), Vector3f(0f, 0.2f, 0f))
        })
        // The blended cube (alpha 0.4) half under a pool: water over the submerged half, the cube blended over the water
        add(ParityView("showcase-transparent-over-water", SHOWCASE, passes) { e ->
            // The cube's own translation: its world matrix is not updated before the scene's first frame
            val cube = Vector3f(checkNotNull(e.find("[Materials] Transparent").getComponent<Transform>()).translation)
            e.addPool(cube, sizeX = 4f, sizeZ = 4f)
            e.focus(FeatureShowcase.focusTarget(FeatureShowcase.AREAS.first { it.key == "materials" }))
        })
        add(ParityView("showcase-materials-transparent", SHOWCASE, passes) { e ->
            e.focus(FeatureShowcase.focusTarget(FeatureShowcase.AREAS.first { it.key == "materials" }))
        })
    }

    @Test
    fun WaterWindAndTransparent_EveryView_VulkanMatchesOpenGl() {
        assumeTrue(RhiBackend.selected() == RhiBackend.VULKAN, "Vulkan is opt-in: set SKATE_RHI=vulkan")
        assumeTrue(glfwInit(), "no GLFW")
        assumeTrue(runCatching { VulkanDevice.create().close() }.isSuccess, "no Vulkan driver")
        val errorsBefore = VulkanDevice.validationErrors
        val harness = BackendParityHarness(File("build/vk08"))
        val gl = harness.renderAll({ OpenGlDevice.create() }, gl = true, views)
        val vk = harness.renderAll({ VulkanDevice.create() }, gl = false, views)
        val (report, failures) = harness.compare("VK-08 world phase B parity", views, gl, vk) { v ->
            if (v.name.startsWith("main-pool") || v.name.contains("water")) WATER_TOLERANCE else VulkanGeometryParityGpuTest.TOLERANCE
        }
        assertTrue(failures.isEmpty(), "views outside tolerance: $failures\n$report")
        assertTrue(VulkanDevice.validationErrors == errorsBefore, "Vulkan validation errors (see the skate.rhi.vulkan log)")
    }

    companion object {
        /**
         * Water views: mean channel difference at most 0.5/255 and 99.5% of pixels within 3/255. The SSR ray march and
         * the refraction re-sample the scene copy at offsets that depend on interpolated normals, so a 1-ulp difference
         * can move a sample by a texel along silhouettes; a wrong depth copy or Y convention breaks the whole surface.
         */
        val WATER_TOLERANCE = FrameDiff.Tolerance(maxMean = 0.5, channel = 3, minShareWithin = 0.995)
    }
}
