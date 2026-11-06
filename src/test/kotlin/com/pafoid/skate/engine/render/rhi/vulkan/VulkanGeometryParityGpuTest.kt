package com.pafoid.skate.engine.render.rhi.vulkan

import com.pafoid.skate.engine.assets.data.Sprite
import com.pafoid.skate.engine.assets.data.models.AlphaMode
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.SpriteRenderer
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.config.FogCycle
import com.pafoid.skate.engine.render.rhi.opengl.OpenGlDevice
import com.pafoid.skate.engine.render.rhi.vulkan.BackendParityHarness.Companion.MAIN
import com.pafoid.skate.engine.render.rhi.vulkan.BackendParityHarness.Companion.SHOWCASE
import com.pafoid.skate.game.samples.FeatureShowcase
import com.pafoid.skate.testing.GpuTest
import org.joml.Vector3f
import org.joml.Vector4f
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW.glfwInit
import java.io.File

/**
 * VK-03: the geometry pass on Vulkan matches OpenGL view by view: MainScene (lit, textured, skinned skater, sky dome)
 * and the FeatureShowcase areas the geometry pass draws (point/spot lights with per-object culling, PBR and emissive
 * materials, alpha blending, skinned characters, the HDRI sky, every fog mode). Only the geometry pass runs on either
 * backend. Opt-in (`SKATE_RHI=vulkan`), GPU-only (@GpuTest). Writes `build/vk03/<view>-{gl,vulkan,diff}.png` and
 * `build/vk03/report.txt` ([BackendParityHarness]); the tolerance is [TOLERANCE] (docs/testing/VK-03.md).
 */
@GpuTest
class VulkanGeometryParityGpuTest {
    private val geometryOnly = { pass: String -> pass == "GeometryPass" }

    private fun View(name: String, scene: String, setup: (Engine) -> Unit) = ParityView(name, scene, geometryOnly, setup)

    private val views: List<ParityView> = buildList {
        add(View("main-default", MAIN) { it.look(Vector3f(6f, 4f, 8f), Vector3f(0f, 0.8f, 0f)) })
        add(View("main-skater", MAIN) { e ->
            val skater = e.skinnedObjectPosition()
            e.look(Vector3f(skater).add(1.6f, 1.4f, 2.2f), Vector3f(skater).add(0f, 1f, 0f))
            e.animate()
        })
        add(View("main-sky", MAIN) { it.look(Vector3f(0f, 1.5f, 0f), Vector3f(10f, 6f, -10f)) })
        // 2D sprites batched in the geometry pass (dynamic vertex buffers): coloured and textured, two z-indices
        add(View("main-sprites", MAIN) { e ->
            e.look(Vector3f(0f, 1.5f, 6f), Vector3f(0f, 1.5f, 0f))
            val texture = e.anyTexture()
            val scene = checkNotNull(e.sceneManager.currentScene)
            for (i in 0 until 6) {
                val sprite = Sprite(texture = if (i % 2 == 0) texture else null)
                val colour = Vector4f(0.2f + 0.15f * i, 1f - 0.12f * i, 0.3f + 0.1f * (i % 3), 1f)
                scene.addChild(GameObject("[VK-03] Sprite_$i")
                    .addComponent(Transform(Vector3f(-2.5f + i, 1f + 0.3f * (i % 2), 0.5f * (i % 3)), Vector3f(0.9f)))
                    .addComponent(SpriteRenderer(colour, sprite, zIndex = i % 2)))
            }
        })
        add(View("showcase-overview", SHOWCASE) { it.focus(FeatureShowcase.overviewTarget()) })
        for (key in listOf("lights", "materials", "animation", "prefabs", "netcode")) {
            add(View("showcase-$key", SHOWCASE) { e ->
                e.focus(FeatureShowcase.focusTarget(FeatureShowcase.AREAS.first { a -> a.key == key }))
                if (key == "animation") e.animate()
            })
        }
        // Alpha MASK: the transparent cube (alpha 0.4) passes a 0.3 cutoff, the textured cube (alpha 0.2) is discarded
        add(View("showcase-alpha-mask", SHOWCASE) { e ->
            e.focus(FeatureShowcase.focusTarget(FeatureShowcase.AREAS.first { it.key == "materials" }))
            e.material("[Materials] Transparent").apply { alphaMode = AlphaMode.MASK; alphaCutoff = 0.3f }
            e.material("[Materials] Textured").apply { alphaMode = AlphaMode.MASK; alphaCutoff = 0.5f; baseColor.w = 0.2f }
        })
        // Culling: from inside the fog tower only double-sided faces are visible
        for (doubleSided in listOf(false, true)) {
            add(View(if (doubleSided) "showcase-inside-double-sided" else "showcase-inside-culled", SHOWCASE) { e ->
                val tower = e.objectPosition("[Fog] Tower")
                e.look(Vector3f(tower).add(0f, 1f, 0f), Vector3f(tower).add(0.4f, 1.6f, -0.3f))
                e.material("[Fog] Tower").doubleSided = doubleSided
            })
        }
        for (step in FogCycle.Step.entries) {
            add(View("showcase-fog-${step.name.lowercase()}", SHOWCASE) { e ->
                e.focus(FeatureShowcase.focusTarget(FeatureShowcase.AREAS.first { it.key == "fog" }))
                e.environment()?.let { FogCycle.apply(it, step) }
            })
        }
    }

    @Test
    fun GeometryPass_EveryView_VulkanMatchesOpenGl() {
        assumeTrue(RhiBackend.selected() == RhiBackend.VULKAN, "Vulkan is opt-in: set SKATE_RHI=vulkan")
        assumeTrue(glfwInit(), "no GLFW")
        assumeTrue(runCatching { VulkanDevice.create().close() }.isSuccess, "no Vulkan driver")
        val errorsBefore = VulkanDevice.validationErrors
        val harness = BackendParityHarness(File("build/vk03"))
        val gl = harness.renderAll({ OpenGlDevice.create() }, gl = true, views)
        val vk = harness.renderAll({ VulkanDevice.create() }, gl = false, views)
        val (report, failures) = harness.compare("VK-03 geometry pass parity", views, gl, vk) { TOLERANCE }
        assertTrue(failures.isEmpty(), "views outside tolerance: $failures\n$report")
        assertTrue(VulkanDevice.validationErrors == errorsBefore, "Vulkan validation errors (see the skate.rhi.vulkan log)")
    }

    companion object {
        /**
         * Mean channel difference at most 0.25/255 and 99.9% of pixels within 2/255 of OpenGL: allows the drivers'
         * rounding (blending, filtering, interpolation) on scattered pixels, not a visible difference.
         */
        val TOLERANCE = FrameDiff.Tolerance(maxMean = 0.25, channel = 2, minShareWithin = 0.999)
    }
}
