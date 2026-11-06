package com.pafoid.skate.engine.render.lighting

import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.rhi.opengl.OpenGlDevice
import com.pafoid.skate.engine.render.rhi.vulkan.BackendParityHarness
import com.pafoid.skate.engine.render.rhi.vulkan.BackendParityHarness.Companion.SHOWCASE
import com.pafoid.skate.engine.render.rhi.vulkan.FrameDiff
import com.pafoid.skate.engine.render.rhi.vulkan.ParityView
import com.pafoid.skate.engine.render.rhi.vulkan.environment
import com.pafoid.skate.engine.render.rhi.vulkan.find
import com.pafoid.skate.engine.render.rhi.vulkan.focus
import com.pafoid.skate.game.samples.FeatureShowcase
import com.pafoid.skate.testing.GpuTest
import org.joml.Vector4f
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW.glfwInit
import java.io.File

/**
 * REN-27: the showcase's chrome sphere ("[Materials] Mirror") reflects the sky. Renders the Materials area (OpenGL)
 * with image-based lighting off (reflection and sky light 0) and on, and checks the mirror's screen area: dark and
 * flat without IBL, bright, sky-coloured and detailed with it. Writes `build/ren27/<view>-{gl,vulkan,diff}.png`,
 * "gl" without IBL and "vulkan" with it.
 */
@GpuTest
class EnvironmentReflectionGpuTest {
    private val passes = { pass: String -> pass == "GeometryPass" || pass == "ShadowPass" }
    private var mirrorPixel = IntArray(2)

    private fun Engine.setup(ibl: Boolean) {
        val target = FeatureShowcase.focusTarget(FeatureShowcase.AREAS.first { it.key == "materials" })
        focus(target)
        environment()?.let { env: EnvironmentComponent ->
            env.reflectionIntensity = if (ibl) 1f else 0f
            env.skyLightIntensity = if (ibl) EnvironmentComponent.DEFAULT_SKY_LIGHT_INTENSITY else 0f
        }
        // Screen position of the mirror's centre, for the checks below
        // (same view and projection as CameraManager builds from the focus target)
        val camera = cameraManager.camera
        val view = org.joml.Matrix4f()
            .rotate(Math.toRadians(target.rotation.x.toDouble()).toFloat(), 1f, 0f, 0f)
            .rotate(Math.toRadians(target.rotation.y.toDouble()).toFloat(), 0f, 1f, 0f)
            .rotate(Math.toRadians(target.rotation.z.toDouble()).toFloat(), 0f, 0f, 1f)
            .translate(-target.position.x, -target.position.y, -target.position.z)
        val projection = org.joml.Matrix4f().perspective(Math.toRadians(camera.fov.toDouble()).toFloat() * camera.zoom,
            WIDTH.toFloat() / HEIGHT, camera.nearPlane, camera.farPlane)
        val centre = find("[Materials] Mirror").getComponent<Transform>()!!.translation
        val clip = Vector4f(centre, 1f).mul(view).mul(projection)
        mirrorPixel = intArrayOf(((clip.x / clip.w * 0.5f + 0.5f) * WIDTH).toInt(), ((clip.y / clip.w * 0.5f + 0.5f) * HEIGHT).toInt())
    }

    @Test
    fun MirrorSphere_ReflectsTheSky() {
        assumeTrue(glfwInit(), "no GLFW")
        val off = listOf(ParityView("materials", SHOWCASE, passes) { it.setup(false) })
        val on = listOf(ParityView("materials", SHOWCASE, passes) { it.setup(true) })
        val harness = BackendParityHarness(File("build/ren27"))
        val plain = harness.renderAll({ OpenGlDevice.create() }, gl = true, off)
        val pixelOff = mirrorPixel.copyOf()
        val lit = harness.renderAll({ OpenGlDevice.create() }, gl = true, on)
        harness.compare("REN-27 IBL off (gl) vs on (vulkan)", off, plain, lit) { FrameDiff.Tolerance(255.0, 255, 0.0) }

        val a = stats(plain.getValue("materials"), pixelOff)
        val b = stats(lit.getValue("materials"), mirrorPixel)
        println("REN-27 mirror at ${mirrorPixel.toList()}: off mean %.1f std %.1f blue-red %.1f | on mean %.1f std %.1f blue-red %.1f"
            .format(a[0], a[1], a[2], b[0], b[1], b[2]))
        assertTrue(b[0] > a[0] + 30, "the mirror is much brighter with reflections")
        assertTrue(b[2] > 5, "the mirror reflects the blue sky (blue above red)")
        assertTrue(b[1] > 10, "the reflection has detail (sky, horizon, ground), not a flat colour")
    }

    /** Mean luma, luma standard deviation and mean (blue - red) in a 41x41 box around [p] (inside the mirror). */
    private fun stats(rgba: ByteArray, p: IntArray): DoubleArray {
        val lumas = ArrayList<Double>()
        var blueMinusRed = 0.0
        for (y in p[1] - 20..p[1] + 20) for (x in p[0] - 20..p[0] + 20) {
            val i = (y.coerceIn(0, HEIGHT - 1) * WIDTH + x.coerceIn(0, WIDTH - 1)) * 4
            val r = rgba[i].toInt() and 0xFF; val g = rgba[i + 1].toInt() and 0xFF; val b = rgba[i + 2].toInt() and 0xFF
            lumas += 0.299 * r + 0.587 * g + 0.114 * b
            blueMinusRed += b - r
        }
        val mean = lumas.average()
        val std = kotlin.math.sqrt(lumas.sumOf { (it - mean) * (it - mean) } / lumas.size)
        return doubleArrayOf(mean, std, blueMinusRed / lumas.size)
    }

    private companion object {
        const val WIDTH = 1280
        const val HEIGHT = 720
    }
}
