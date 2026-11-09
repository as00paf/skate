package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.engine.render.rhi.opengl.OpenGlDevice
import com.pafoid.skate.engine.render.rhi.vulkan.BackendParityHarness
import com.pafoid.skate.engine.render.rhi.vulkan.BackendParityHarness.Companion.MAIN
import com.pafoid.skate.engine.render.rhi.vulkan.FrameDiff
import com.pafoid.skate.engine.render.rhi.vulkan.ParityView
import com.pafoid.skate.engine.render.rhi.vulkan.look
import com.pafoid.skate.testing.GpuTest
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW.glfwInit
import java.io.File

/**
 * REN-25: renders the infinite editor grid (OpenGL) close up, from high above and at a grazing angle, against the same
 * views without the debug pass. The grid must show (a few percent of pixels change), stay thin (most pixels do not),
 * and show the red X and blue Z axes. Writes `build/ren25/<view>-{gl,vulkan,diff}.png`, "gl" without the grid and
 * "vulkan" with it.
 */
@GpuTest
class EditorGridGpuTest {
    private val geometry = { pass: String -> pass == "GeometryPass" }
    private val withGrid = { pass: String -> pass == "GeometryPass" || pass == "DebugPass" }

    private val cameras = listOf(
        "close" to (Vector3f(3f, 1.5f, 6f) to Vector3f(0f, 0f, 0f)),
        "high" to (Vector3f(10f, 60f, 40f) to Vector3f(0f, 0f, 0f)),
        "grazing" to (Vector3f(2f, 0.4f, 25f) to Vector3f(0f, 0.3f, -60f)),
    )

    @Test
    fun EditorGrid_IsThinAndShowsTheAxes() {
        assumeTrue(glfwInit(), "no GLFW")
        val without = cameras.map { (name, c) -> ParityView(name, MAIN, geometry) { e -> e.look(c.first, c.second) } }
        val with = cameras.map { (name, c) -> ParityView(name, MAIN, withGrid) { e -> e.look(c.first, c.second) } }
        val harness = BackendParityHarness(File("build/ren25"))
        val plain = harness.renderAll({ OpenGlDevice.create() }, gl = true, without)
        val grid = harness.renderAll({ OpenGlDevice.create() }, gl = true, with)
        harness.compare("REN-25 grid off (gl) vs on (vulkan)", without, plain, grid) { FrameDiff.Tolerance(255.0, 255, 0.0) }

        val failures = ArrayList<String>()
        for (view in without) {
            val diff = FrameDiff(plain.getValue(view.name), grid.getValue(view.name))
            val changed = 1.0 - diff.within(8)
            // Axis pixels: changed by the grid (the sky is blue too) and dominated by the axis colour
            val red = count(grid.getValue(view.name), diff) { r, g, b -> r > 150 && r > g + 60 && r > b + 60 }
            val blue = count(grid.getValue(view.name), diff) { r, g, b -> b > 150 && b > r + 60 && b > g + 30 }
            println("REN-25 %-8s changed %.2f%%, red %d, blue %d".format(view.name, 100 * changed, red, blue))
            if (changed < 0.005 || changed > 0.25) failures += "${view.name}: ${"%.2f".format(100 * changed)}% of pixels changed"
            if (red < 50 || blue < 50) failures += "${view.name}: axes missing (red $red, blue $blue)"
        }
        assertTrue(failures.isEmpty(), failures.joinToString())
    }

    private fun count(rgba: ByteArray, diff: FrameDiff, test: (Int, Int, Int) -> Boolean): Int {
        var n = 0
        for (p in 0 until rgba.size / 4) {
            if (diff.perPixel[p] > 8 && test(rgba[p * 4].toInt() and 0xFF, rgba[p * 4 + 1].toInt() and 0xFF, rgba[p * 4 + 2].toInt() and 0xFF)) n++
        }
        return n
    }
}
