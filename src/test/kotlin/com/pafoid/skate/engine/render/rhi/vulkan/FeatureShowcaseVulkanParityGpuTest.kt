package com.pafoid.skate.engine.render.rhi.vulkan

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.components.ParticleEmitter
import com.pafoid.skate.engine.ecs.components.ScenePhysicsComponent
import com.pafoid.skate.engine.ecs.systems.ParticleSystem
import com.pafoid.skate.engine.getAllComponents
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.renderer.passes.PostEffect
import com.pafoid.skate.engine.render.renderer.passes.PostProcessSettings
import com.pafoid.skate.engine.render.rhi.opengl.OpenGlDevice
import com.pafoid.skate.engine.render.rhi.vulkan.BackendParityHarness.Companion.MAIN
import com.pafoid.skate.engine.render.rhi.vulkan.BackendParityHarness.Companion.SHOWCASE
import com.pafoid.skate.game.samples.FeatureShowcase
import com.pafoid.skate.testing.GpuTest
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW.glfwInit
import java.io.File

/**
 * QA-16: the whole frame an exported game renders, Vulkan against OpenGL, for every FeatureShowcase area and MainScene.
 * The VK tests each check their own passes in isolation (VK-03 geometry, VK-04 shadows, VK-05 post, VK-06 overlays);
 * here every pass runs together (shadows, geometry, particles, UI, post-processing with the engine defaults), as in
 * the game. Only the editor-only debug pass is off, except in the physics view, which shows the collider wireframes.
 *
 * Tolerances are the ones the VK tasks documented: [VulkanGeometryParityGpuTest.TOLERANCE] by default,
 * [VulkanOverlayParityGpuTest.DEBUG_TOLERANCE] with debug lines, [VulkanPostProcessParityGpuTest.SSAO_TOLERANCE] with
 * SSAO. The render test is opt-in (`SKATE_RHI=vulkan`) and GPU-only ([GpuTest]); the coverage check runs everywhere. Writes `build/qa16/<view>-{gl,vulkan,diff}.png` and
 * `build/qa16/report.txt`. How to run: docs/testing/QA-16.md.
 *
 * A new showcase area fails [Parity_CoversEveryShowcaseArea] until it gets a view here (GameEngine.md rule 8).
 */
class FeatureShowcaseVulkanParityGpuTest {

    private val allButDebug = { pass: String -> pass != DEBUG }
    private val everything = { _: String -> true }

    private fun area(key: String) = FeatureShowcase.focusTarget(FeatureShowcase.AREAS.first { it.key == key })

    /** Every view starts from the engine's default post-processing (bloom and FXAA), whatever the previous view set. */
    private fun Engine.defaults() {
        renderer.renderResources.renderPasses.postProcess.updateSettings(PostProcessSettings())
        sceneManager.currentScene?.getComponent<ScenePhysicsComponent>()?.debugEnabled = false
    }

    private fun view(name: String, scene: String, passes: (String) -> Boolean = allButDebug, setup: (Engine) -> Unit) =
        ParityView(name, scene, passes) { e -> e.defaults(); setup(e) }

    /** Showcase area keys covered by the views below, besides the generic focus views. */
    private val special = setOf("animation", "physics", "particles")

    private val views: List<ParityView> = buildList {
        add(view("main-scene", MAIN) { e ->
            // Runtime UI text (legend, HUD) from the game's string tables, as in an exported game; first view only.
            e.gameStrings.addStrings(Assets.Strings.GAME_STRINGS)
            e.look(Vector3f(6f, 4f, 8f), Vector3f(0f, 0.8f, 0f))
        })
        add(view("main-skater", MAIN) { e ->
            val skater = e.skinnedObjectPosition()
            e.look(Vector3f(skater).add(1.6f, 1.4f, 2.2f), Vector3f(skater).add(0f, 1f, 0f))
            e.animate()
        })
        // Post effects: every effect on at once (SSAO with a radius that samples the scene, as in VK-05)
        add(view("main-post-all", MAIN) { e ->
            e.look(Vector3f(6f, 4f, 8f), Vector3f(0f, 0.8f, 0f))
            e.renderer.renderResources.renderPasses.postProcess.updateSettings(PostProcessSettings().apply {
                PostEffect.entries.forEach { setEnabled(it, true) }
                bloomIntensity = 0.5f
                ssaoRadius = 0.01f
            })
        })
        // UI: the legend canvas over the whole floor
        add(view("showcase-ui-overview", SHOWCASE) { it.focus(FeatureShowcase.overviewTarget()) })
        for (a in FeatureShowcase.AREAS.filter { it.key !in special }) {
            add(view("showcase-${a.key}", SHOWCASE) { it.focus(area(a.key)) })
        }
        add(view("showcase-animation", SHOWCASE) { e -> e.focus(area("animation")); e.animate() })
        add(view("showcase-physics", SHOWCASE, everything) { e ->
            e.focus(area("physics"))
            e.sceneManager.currentScene?.getComponent<ScenePhysicsComponent>()?.debugEnabled = true
        })
        // Last: the particle system stays paused after it (in edit mode it would clear the simulated particles)
        add(view("showcase-particles", SHOWCASE) { e -> e.focus(area("particles")); e.simulateParticles() })
    }

    private fun tolerance(view: ParityView): FrameDiff.Tolerance = when (view.name) {
        "main-post-all" -> VulkanPostProcessParityGpuTest.SSAO_TOLERANCE
        "showcase-physics" -> VulkanOverlayParityGpuTest.DEBUG_TOLERANCE
        else -> VulkanGeometryParityGpuTest.TOLERANCE
    }

    /**
     * Seeds every emitter and steps only the particle system with the scene marked running, so both backends draw the
     * same particles; the system is then paused (VK-06's approach).
     */
    private fun Engine.simulateParticles() {
        val scene = checkNotNull(sceneManager.currentScene)
        scene.getAllComponents<ParticleEmitter>().forEachIndexed { i, emitter -> emitter.seed = 1000 + i }
        val particles = checkNotNull(systemManager.getSystem<ParticleSystem>()) { "no particle system" }
        scene.isRunning = true
        try {
            repeat(90) { particles.update(1f / 60f) }
        } finally {
            scene.isRunning = false
        }
        particles.enabled = false
        check(scene.getAllComponents<ParticleEmitter>().any { it.runtime.pool.count > 0 }) { "no live particles" }
    }

    @Test
    fun Parity_CoversEveryShowcaseArea() {
        val covered = views.map { it.name.removePrefix("showcase-") }.toSet()
        assertEquals(emptyList<String>(), FeatureShowcase.AREAS.map { it.key }.filter { it !in covered }, "showcase areas without a parity view")
    }

    @Test
    @GpuTest
    fun FullFrame_EveryShowcaseAreaAndMainScene_VulkanMatchesOpenGl() {
        assumeTrue(RhiBackend.selected() == RhiBackend.VULKAN, "Vulkan is opt-in: set SKATE_RHI=vulkan")
        assumeTrue(glfwInit(), "no GLFW")
        assumeTrue(runCatching { VulkanDevice.create().close() }.isSuccess, "no Vulkan driver")
        val errorsBefore = VulkanDevice.validationErrors
        val harness = BackendParityHarness(File("build/qa16"))
        val gl = harness.renderAll({ OpenGlDevice.create() }, gl = true, views)
        val vk = harness.renderAll({ VulkanDevice.create() }, gl = false, views)
        val (report, failures) = harness.compare("QA-16 full-frame parity (every pass)", views, gl, vk, ::tolerance)
        assertTrue(failures.isEmpty(), "views outside tolerance: $failures\n$report")
        assertTrue(VulkanDevice.validationErrors == errorsBefore, "Vulkan validation errors (see the skate.rhi.vulkan log)")
    }

    private companion object {
        const val DEBUG = "DebugPass"
    }
}
