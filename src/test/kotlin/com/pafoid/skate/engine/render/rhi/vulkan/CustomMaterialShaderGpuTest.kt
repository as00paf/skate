package com.pafoid.skate.engine.render.rhi.vulkan

import com.pafoid.skate.engine.assets.ProjectLayout
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.renderer.RenderTraversal
import com.pafoid.skate.engine.render.rhi.opengl.OpenGlDevice
import com.pafoid.skate.engine.render.rhi.vulkan.BackendParityHarness.Companion.MAIN
import com.pafoid.skate.engine.render.shader.ShaderTemplates
import com.pafoid.skate.testing.GpuTest
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW.glfwInit
import java.io.File

/**
 * REN-31 (AST-26 §5.6): a material with a custom colour-pass shader (the "New Shader" template, a project file) draws
 * lit like the default shader, so each program gets the frame's camera, lighting, shadow and IBL uniforms; and Vulkan
 * matches OpenGL with it. GPU-only; the Vulkan half is opt-in (`SKATE_RHI=vulkan`). Writes `build/ren31/`.
 */
class CustomMaterialShaderGpuTest {

    /** The template, optionally tinting its output red (to prove the custom program is the one drawing). */
    private fun source(tinted: Boolean) = if (!tinted) ShaderTemplates.PBR_MATERIAL else ShaderTemplates.PBR_MATERIAL.replace(
        "    color = vec4(applyFog(lit, uCameraPos, fWorldPos), u_AlphaMode == 2 ? albedo.a : 1.0);",
        "    color = vec4(applyFog(lit, uCameraPos, fWorldPos) * vec3(1.0, 0.4, 0.4), u_AlphaMode == 2 ? albedo.a : 1.0);",
    ).also { check(it != ShaderTemplates.PBR_MATERIAL) }

    private fun Engine.useCustomShaderOnStaticObjects(tinted: Boolean) {
        val root = checkNotNull(assetsManager.paths.projectRoot)
        File(ProjectLayout.assetsDir(root), "Shaders/custom_pbr.glsl").apply { parentFile.mkdirs(); writeText(source(tinted)) }
        var assigned = 0
        RenderTraversal.forEachRendered(checkNotNull(sceneManager.currentScene)) { go ->
            if (go.getComponent<SkeletonComponent>() != null) return@forEachRendered // the template does not skin
            val rc = go.getComponent<RenderComponent>() ?: return@forEachRendered
            val material = rc.material ?: rc.model?.mesh?.firstOrNull()?.material?.copy() ?: return@forEachRendered
            material.shader = "Assets/Shaders/custom_pbr.glsl"
            rc.material = material
            assigned++
        }
        check(assigned > 3) { "only $assigned objects got the custom shader" }
        checkNotNull(assetsManager.materialShader("Assets/Shaders/custom_pbr.glsl")) { "the template did not load" }
    }

    private val eye = Vector3f(6f, 4f, 8f)
    private val target = Vector3f(0f, 0.8f, 0f)
    private val defaultView = ParityView("default", MAIN, { true }) { e -> e.look(eye, target) }
    private val customView = ParityView("custom", MAIN, { true }) { e -> e.look(eye, target); e.useCustomShaderOnStaticObjects(false) }
    private val tintedView = ParityView("tinted", MAIN, { true }) { e -> e.look(eye, target); e.useCustomShaderOnStaticObjects(true) }

    @Test
    @GpuTest
    fun customMaterialShader_RendersLit_AndVulkanMatchesOpenGl() {
        assumeTrue(glfwInit(), "no GLFW")
        val harness = BackendParityHarness(File("build/ren31"))
        val gl = harness.renderAll({ OpenGlDevice.create() }, gl = true, listOf(defaultView))
        val glCustom = harness.renderAll({ OpenGlDevice.create() }, gl = true, listOf(customView))
        val glTinted = harness.renderAll({ OpenGlDevice.create() }, gl = true, listOf(tintedView))
        // The template does the default shader's lighting; this scene has no normal or AO maps, so it must match the
        // default image. Missing frame uniforms (camera, lights, shadows, IBL) would leave objects black or misplaced.
        val same = FrameDiff(gl.getValue("default"), glCustom.getValue("custom"))
        println("REN-31 template vs default shader: ${same.summary()}")
        assertTrue(same.meets(VulkanGeometryParityGpuTest.TOLERANCE), "template vs default shader: ${same.summary()}")
        // ...and the custom program is really the one drawing: tinting its output changes the frame
        val tinted = FrameDiff(gl.getValue("default"), glTinted.getValue("tinted"))
        println("REN-31 tinted template vs default shader: ${tinted.summary()}")
        assertTrue(tinted.meanPerChannel > 5.0, "tinted custom shader had no effect: ${tinted.summary()}")

        if (RhiBackend.selected() != RhiBackend.VULKAN || runCatching { VulkanDevice.create().close() }.isFailure) return
        val vk = harness.renderAll({ VulkanDevice.create() }, gl = false, listOf(tintedView))
        val (report, failures) = harness.compare("REN-31 custom material shader", listOf(tintedView), glTinted, vk) { VulkanGeometryParityGpuTest.TOLERANCE }
        assertTrue(failures.isEmpty(), report)
    }
}
