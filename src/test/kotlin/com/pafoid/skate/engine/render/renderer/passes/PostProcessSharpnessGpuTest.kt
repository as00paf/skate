package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.render.rhi.opengl.OpenGlDevice
import com.pafoid.skate.engine.render.rhi.vulkan.BackendParityHarness
import com.pafoid.skate.engine.render.rhi.vulkan.BackendParityHarness.Companion.MAIN
import com.pafoid.skate.engine.render.rhi.vulkan.BackendParityHarness.Companion.SHOWCASE
import com.pafoid.skate.engine.render.rhi.vulkan.FrameDiff
import com.pafoid.skate.engine.render.rhi.vulkan.ParityView
import com.pafoid.skate.engine.render.rhi.vulkan.focus
import com.pafoid.skate.engine.render.rhi.vulkan.look
import com.pafoid.skate.game.samples.FeatureShowcase
import com.pafoid.skate.testing.GpuTest
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW.glfwInit
import java.io.File

/**
 * REN-24: the default post-processing stack (bloom + FXAA) must not blur a still frame. Renders the same views with the
 * post pass off and on (OpenGL) and checks that the frame keeps its sharpness (gradient energy) and that only a small
 * share of pixels (the anti-aliased edges) change. Writes `build/ren24/<view>-{gl,vulkan,diff}.png` and a report,
 * where "gl" is post off and "vulkan" is post on.
 */
@GpuTest
class PostProcessSharpnessGpuTest {
    private val geometryOnly = { pass: String -> pass == "GeometryPass" }
    private val withPost = { pass: String -> pass == "GeometryPass" || pass == "PostProcessPass" }

    private fun Engine.defaultPost() = renderer.renderResources.renderPasses.postProcess.updateSettings(PostProcessSettings())

    private val setups: List<Pair<String, Pair<String, (Engine) -> Unit>>> = listOf(
        "main" to (MAIN to { e: Engine -> e.look(Vector3f(6f, 4f, 8f), Vector3f(0f, 0.8f, 0f)) }),
        "materials" to (SHOWCASE to { e: Engine ->
            e.focus(FeatureShowcase.focusTarget(FeatureShowcase.AREAS.first { it.key == "materials" }))
        }),
    )

    @Test
    fun DefaultPostProcessing_StillFrame_StaysSharp() {
        assumeTrue(glfwInit(), "no GLFW")
        val off = setups.map { (name, s) -> ParityView(name, s.first, geometryOnly) { e -> s.second(e); e.defaultPost() } }
        val on = setups.map { (name, s) -> ParityView(name, s.first, withPost) { e -> s.second(e); e.defaultPost() } }
        val harness = BackendParityHarness(File("build/ren24"))
        val sharp = harness.renderAll({ OpenGlDevice.create() }, gl = true, off)
        val post = harness.renderAll({ OpenGlDevice.create() }, gl = true, on)
        harness.compare("REN-24 post off (gl) vs default post on (vulkan)", off, sharp, post) { MAX_CHANGE }

        val failures = ArrayList<String>()
        for (view in off) {
            val a = sharp.getValue(view.name)
            val b = post.getValue(view.name)
            val ratio = FrameSharpness.gradientEnergy(b, WIDTH, HEIGHT) / FrameSharpness.gradientEnergy(a, WIDTH, HEIGHT)
            val unchanged = FrameDiff(a, b).within(1)
            println("REN-24 %-10s sharpness on/off %.3f, unchanged pixels %.1f%%".format(view.name, ratio, 100 * unchanged))
            if (ratio < MIN_SHARPNESS_RATIO || unchanged < MAX_CHANGE.minShareWithin) {
                failures += "${view.name}: sharpness %.3f, unchanged %.1f%%".format(ratio, 100 * unchanged)
            }
        }
        assertTrue(failures.isEmpty(), "post-processing blurs the frame: $failures")
    }

    private companion object {
        const val WIDTH = 1280
        const val HEIGHT = 720

        /** FXAA smooths edges a little; anything below this is a blur, not anti-aliasing. */
        const val MIN_SHARPNESS_RATIO = 0.9

        /** Only edge pixels may change: at least 85% of the frame stays within 1/255 of the post-off frame. */
        val MAX_CHANGE = FrameDiff.Tolerance(maxMean = 2.0, channel = 1, minShareWithin = 0.85)
    }
}
