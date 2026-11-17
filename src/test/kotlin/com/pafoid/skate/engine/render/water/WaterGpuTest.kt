package com.pafoid.skate.engine.render.water

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.EngineDefaults
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.components.WaterComponent
import com.pafoid.skate.engine.ecs.components.WaterMaterials
import com.pafoid.skate.engine.render.rhi.opengl.OpenGlDevice
import com.pafoid.skate.engine.render.rhi.vulkan.BackendParityHarness
import com.pafoid.skate.engine.render.rhi.vulkan.BackendParityHarness.Companion.MAIN
import com.pafoid.skate.engine.render.rhi.vulkan.FrameDiff
import com.pafoid.skate.engine.render.rhi.vulkan.ParityView
import com.pafoid.skate.engine.render.rhi.vulkan.look
import com.pafoid.skate.engine.render.shader.ShaderFeature
import com.pafoid.skate.testing.GpuTest
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW.glfwInit
import java.io.File

/**
 * REN-29 tasks 3, 7 and 8 on OpenGL: the water shader (and its PICKING variant) and the WIND variants of the mesh,
 * shadow and picking shaders compile; a pool on MainScene changes the image where it lies and nowhere far from it.
 * Writes `build/ren35/pool-{gl,vulkan,diff}.png` ("gl" without water, "vulkan" with it).
 */
@GpuTest
class WaterGpuTest {
    private val passes = { pass: String -> pass == "GeometryPass" || pass == "ShadowPass" || pass == "WaterPass" || pass == "TransparentPass" }
    private val eye = Vector3f(0f, 4f, 9f)

    private fun Engine.view() = look(eye, Vector3f(0f, 0f, 0f))

    private fun Engine.addPool() {
        val scene = sceneManager.currentScene!!
        val pool = GameObject("Pool").addComponent(Transform().apply { translation.set(0f, 0.4f, 0f) })
            .addComponent(WaterComponent(sizeX = 6f, sizeZ = 4f, depth = 1f, material = WaterMaterials.POOL))
        scene.addChild(pool)
        // Every new permutation must compile on the driver
        for ((path, feature) in listOf(
            EngineDefaults.Shaders.DEFAULT_3D to ShaderFeature.WIND,
            EngineDefaults.Shaders.SHADOW to ShaderFeature.WIND,
            EngineDefaults.Shaders.PICKING_3D to ShaderFeature.WIND,
            EngineDefaults.Shaders.WATER to ShaderFeature.PICKING,
        )) assertNotNull(assetsManager.getShaderVariant(path, setOf(feature)).rhi, "$path $feature")
        assertNotNull(assetsManager.getShader(EngineDefaults.Shaders.WATER).rhi)
        renderer.renderResources.renderPasses.picking.renderNow(scene) // the water picking draw runs too
    }

    @Test
    fun Pool_CompilesAndShowsWhereItLies() {
        assumeTrue(glfwInit(), "no GLFW")
        val dry = listOf(ParityView("pool", MAIN, passes) { it.view() })
        val wet = listOf(ParityView("pool", MAIN, passes) { it.addPool(); it.view() })
        val harness = BackendParityHarness(File("build/ren35"))
        val without = harness.renderAll({ OpenGlDevice.create() }, gl = true, dry)
        val with = harness.renderAll({ OpenGlDevice.create() }, gl = true, wet)
        harness.compare("REN-35 pool off (gl) vs on (vulkan)", dry, without, with) { FrameDiff.Tolerance(255.0, 255, 0.0) }

        val a = without.getValue("pool")
        val b = with.getValue("pool")
        val centre = changed(a, b, 1280 / 2, 720 / 2 - 40, 40)
        val corner = changed(a, b, 60, 660, 40)
        println("REN-35 pool: centre changed %.2f, top-left corner changed %.2f".format(centre, corner))
        assertTrue(centre > 0.5, "most pixels over the pool change")
        assertTrue(corner < 0.05, "the sky far from the pool does not")
    }

    /** Share of pixels in a (2r+1)² box around (x, y) (GL rows, bottom first) whose RGB moved by more than 6. */
    private fun changed(a: ByteArray, b: ByteArray, x: Int, y: Int, r: Int): Double {
        var n = 0
        var moved = 0
        for (py in y - r..y + r) for (px in x - r..x + r) {
            val i = (py * 1280 + px) * 4
            val d = (0..2).maxOf { c -> kotlin.math.abs((a[i + c].toInt() and 255) - (b[i + c].toInt() and 255)) }
            n++
            if (d > 6) moved++
        }
        return moved.toDouble() / n
    }
}
