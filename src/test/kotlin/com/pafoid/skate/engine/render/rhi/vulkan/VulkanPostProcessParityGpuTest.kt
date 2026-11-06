package com.pafoid.skate.engine.render.rhi.vulkan

import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.render.renderer.passes.PostEffect
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
 * VK-05: post-processing on Vulkan matches OpenGL. The geometry pass and the post-processing pass run on both
 * backends: the PostChain ping-pong between scratch targets, the copy back into the scene target (blit), and every
 * effect alone (SSAO reads the scene depth) and chained. Any orientation mistake mirrors the image at each effect, so
 * chains of one to five effects also check the Y convention ([VkOrientation]). Opt-in (`SKATE_RHI=vulkan`),
 * GPU-only (@GpuTest). Writes `build/vk05/<view>-{gl,vulkan,diff}.png` and `build/vk05/report.txt`.
 */
@GpuTest
class VulkanPostProcessParityGpuTest {
    private val withPost = { pass: String -> pass == "GeometryPass" || pass == "PostProcessPass" }

    /** Effect sets, applied to a fresh [PostProcessSettings] (engine defaults: bloom and FXAA). */
    private val configs: List<Pair<String, (PostProcessSettings) -> Unit>> = listOf(
        "default" to {},
        // The default radius (1.5, used as a UV offset) puts every sample off screen, so SSAO would be a no-op on both
        // backends; a small radius exercises the scene-depth sampling
        "ssao" to { s -> s.only(PostEffect.SSAO); s.ssaoRadius = 0.01f },
        "bloom" to { s -> s.only(PostEffect.BLOOM); s.bloomIntensity = 1f; s.bloomThreshold = 0.6f },
        "tonemap" to { s -> s.only(PostEffect.TONEMAPPING); s.acesExposure = 1.4f },
        "gamma" to { s -> s.only(PostEffect.GAMMA_CORRECTION) },
        "fxaa" to { s -> s.only(PostEffect.FXAA) },
        "all" to { s -> PostEffect.entries.forEach { s.setEnabled(it, true) }; s.bloomIntensity = 0.5f; s.ssaoRadius = 0.01f },
    )

    /** Views whose effect set includes SSAO (see [SSAO_TOLERANCE]). */
    private val ssaoViews: Set<String> get() = views.map { it.name }.filter { it.endsWith("-ssao") || it.endsWith("-all") }.toSet()

    private fun PostProcessSettings.only(effect: PostEffect) = PostEffect.entries.forEach { setEnabled(it, it == effect) }

    private fun Engine.post(config: (PostProcessSettings) -> Unit) =
        renderer.renderResources.renderPasses.postProcess.updateSettings(PostProcessSettings().also(config))

    private val views: List<ParityView> = buildList {
        for ((name, config) in configs) {
            add(ParityView("main-$name", MAIN, withPost) { e ->
                e.look(Vector3f(6f, 4f, 8f), Vector3f(0f, 0.8f, 0f))
                e.post(config)
            })
        }
        val materials = FeatureShowcase.focusTarget(FeatureShowcase.AREAS.first { it.key == "materials" })
        for (name in listOf("default", "bloom", "all")) {
            val config = configs.first { it.first == name }.second
            add(ParityView("showcase-materials-$name", SHOWCASE, withPost) { e -> e.focus(materials); e.post(config) })
        }
        add(ParityView("showcase-overview-all", SHOWCASE, withPost) { e ->
            e.focus(FeatureShowcase.overviewTarget())
            e.post(configs.first { it.first == "all" }.second)
        })
    }

    @Test
    fun PostProcessing_EveryEffect_VulkanMatchesOpenGl() {
        assumeTrue(RhiBackend.selected() == RhiBackend.VULKAN, "Vulkan is opt-in: set SKATE_RHI=vulkan")
        assumeTrue(glfwInit(), "no GLFW")
        assumeTrue(runCatching { VulkanDevice.create().close() }.isSuccess, "no Vulkan driver")
        val errorsBefore = VulkanDevice.validationErrors
        val harness = BackendParityHarness(File("build/vk05"))
        val gl = harness.renderAll({ OpenGlDevice.create() }, gl = true, views)
        val vk = harness.renderAll({ VulkanDevice.create() }, gl = false, views)
        val (report, failures) = harness.compare("VK-05 post-processing parity", views, gl, vk) { v -> if (v.name in ssaoViews) SSAO_TOLERANCE else TOLERANCE }
        assertTrue(failures.isEmpty(), "views outside tolerance: $failures\n$report")
        assertTrue(VulkanDevice.validationErrors == errorsBefore, "Vulkan validation errors (see the skate.rhi.vulkan log)")
    }

    companion object {
        /** Same bar as the geometry pass (VK-03): driver rounding on scattered pixels only. */
        val TOLERANCE = VulkanGeometryParityGpuTest.TOLERANCE

        /**
         * Views with SSAO: its shader compares raw depths with no bias (`sampleDepth < currentDepth`), so a last-bit
         * depth rounding difference between the backends (GL's fixed-function depth range vs the Vulkan vertex-stage
         * remap) flips single samples, each worth intensity/8 (about 25/255 at most). Those flips sit on flat floors and
         * along the floor's triangle diagonal: at most 1% of pixels, none past 16/255. A broken depth read (VK-05 found
         * one: the dummy texture was bound) gives about 7% of pixels off by up to 87/255 and fails.
         */
        val SSAO_TOLERANCE = FrameDiff.Tolerance(maxMean = 0.25, channel = 16, minShareWithin = 0.99)
    }
}
