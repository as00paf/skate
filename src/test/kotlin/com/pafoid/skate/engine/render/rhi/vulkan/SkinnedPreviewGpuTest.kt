package com.pafoid.skate.engine.render.rhi.vulkan

import com.pafoid.skate.engine.animation.edit.ClipRig
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.render.preview.PreviewCamera
import com.pafoid.skate.engine.render.rhi.IntRect
import com.pafoid.skate.engine.render.rhi.PixelFormat
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.opengl.OpenGlDevice
import com.pafoid.skate.engine.render.rhi.vulkan.BackendParityHarness.Companion.MAIN
import com.pafoid.skate.testing.GpuTest
import org.joml.Matrix4f
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW.glfwInit
import org.lwjgl.stb.STBImageWrite.stbi_flip_vertically_on_write
import org.lwjgl.stb.STBImageWrite.stbi_write_png
import java.io.File
import java.nio.ByteBuffer

/**
 * REN-33 on a real GPU: the offscreen preview draws the skater's skinned mesh, and a scrubbed pose (an arm raised)
 * changes it. Writes `build/ren33/{bind,posed}.png` for a visual check. GPU-only.
 */
class SkinnedPreviewGpuTest {
    private val w = 480
    private val h = 480
    private val frames = HashMap<String, ByteArray>()

    private fun Engine.renderPreview(name: String, pose: (ClipRig, Array<Matrix4f>) -> Unit) {
        val model = assetsManager.getModel(Assets.Models.JAMES) ?: assetsManager.loadModel(Assets.Models.JAMES)
        val rig = ClipRig(checkNotNull(model.skeleton))
        val locals = Array(rig.boneCount) { Matrix4f(rig.bindLocals[it]) }
        pose(rig, locals)
        val eye = Vector3f(0f, 1.1f, 3.2f)
        val camera = PreviewCamera(Matrix4f().lookAt(eye, Vector3f(0f, 0.9f, 0f), Vector3f(0f, 1f, 0f)), Matrix4f().perspective(0.8f, 1f, 0.05f, 100f), eye)
        val viewport = renderer.renderResources.renderers.skinnedPreview
        check(viewport.render(w, h, model, rig.parents, locals, camera) != 0) { "nothing drawn" }
        val pixels = ByteBuffer.allocateDirect(w * h * 4)
        RhiContext.require().readPixels(viewport.renderTarget, 0, IntRect(0, 0, w, h), PixelFormat.RGBA8, pixels)
        File("build/ren33").mkdirs()
        stbi_flip_vertically_on_write(true) // GL rows are bottom-up
        stbi_write_png("build/ren33/$name.png", w, h, 4, pixels, w * 4)
        stbi_flip_vertically_on_write(false)
        frames[name] = ByteArray(pixels.capacity()).also { pixels.get(it) }
    }

    @Test
    @GpuTest
    fun preview_DrawsTheSkinnedSkater_AndFollowsThePose() {
        assumeTrue(glfwInit(), "no GLFW")
        val view = ParityView("preview", MAIN, { true }) { e ->
            e.renderPreview("bind") { _, _ -> }
            e.renderPreview("posed") { rig, locals ->
                val arm = rig.names.indexOfFirst { it.endsWith("LeftArm") }
                check(arm >= 0) { "no LeftArm bone in ${rig.names.toList()}" }
                locals[arm].rotateZ(Math.toRadians(-70.0).toFloat())
            }
        }
        BackendParityHarness(File("build/ren33")).renderAll({ OpenGlDevice.create() }, gl = true, listOf(view))

        val bind = frames.getValue("bind")
        val background = intArrayOf(26, 26, 31) // the preview's clear colour (0.1, 0.1, 0.12)
        fun covered(f: ByteArray) = (0 until w * h).count { p ->
            (0..2).any { c -> kotlin.math.abs((f[p * 4 + c].toInt() and 0xff) - background[c]) > 6 }
        }
        val coverage = covered(bind).toFloat() / (w * h)
        assertTrue(coverage in 0.03f..0.7f, "the character covers ${coverage * 100}% of the preview")
        val changed = (0 until w * h).count { p -> (0..2).any { c -> bind[p * 4 + c] != frames.getValue("posed")[p * 4 + c] } }
        assertTrue(changed > 500, "raising the arm changed only $changed pixels")
    }
}
