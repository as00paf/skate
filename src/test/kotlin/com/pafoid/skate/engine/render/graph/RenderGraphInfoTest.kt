package com.pafoid.skate.engine.render.graph

import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.render.graph.RenderResourceIds.LOCAL_SHADOW_MAP
import com.pafoid.skate.engine.render.graph.RenderResourceIds.PICKING_IDS
import com.pafoid.skate.engine.render.graph.RenderResourceIds.SCENE_COLOR
import com.pafoid.skate.engine.render.graph.RenderResourceIds.SCENE_DEPTH
import com.pafoid.skate.engine.render.graph.RenderResourceIds.SHADOW_MAP
import com.pafoid.skate.engine.render.renderer.passes.BaseRenderPass
import com.pafoid.skate.engine.render.renderer.passes.DebugPass
import com.pafoid.skate.engine.render.renderer.passes.GeometryPass
import com.pafoid.skate.engine.render.renderer.passes.NoShadowPass
import com.pafoid.skate.engine.render.renderer.passes.ParticlePass
import com.pafoid.skate.engine.render.renderer.passes.PickingPass
import com.pafoid.skate.engine.render.renderer.passes.PostEffect
import com.pafoid.skate.engine.render.renderer.passes.PostProcessPass
import com.pafoid.skate.engine.render.renderer.passes.PostProcessSettings
import com.pafoid.skate.engine.render.renderer.passes.RenderPass
import com.pafoid.skate.engine.render.renderer.passes.ShadowPass
import com.pafoid.skate.engine.render.renderer.passes.StagedRenderPass
import com.pafoid.skate.engine.render.renderer.passes.UiPass
import com.pafoid.skate.engine.render.rhi.FakeRhiDevice
import com.pafoid.skate.engine.render.shadow.ShadowFrame
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Properties

/** EDT-54 step 2a: the render graph's read-only description, timings and toggles for the Render Graph window. */
class RenderGraphInfoTest {

    private fun postProcess(useFbo: Boolean = true, settings: PostProcessSettings = PostProcessSettings()) =
        PostProcessPass(mockk(relaxed = true), useFbo, mockk(), mockk(), mockk(), mockk(), mockk(), mockk(relaxed = true), settings)

    /** The real passes, in the order of `RenderResources.inExecutionOrder`, with mocked GPU dependencies. */
    private fun realPasses(shadow: RenderPass = ShadowPass(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), ShadowFrame())): List<RenderPass> = listOf(
        shadow,
        PickingPass(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true)),
        GeometryPass(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), true, mockk(relaxed = true)),
        ParticlePass(mockk(relaxed = true), mockk(relaxed = true), true, mockk(relaxed = true)) { null },
        postProcess(),
        DebugPass(mockk(relaxed = true), mockk(relaxed = true)),
        UiPass(mockk(relaxed = true), mockk(relaxed = true), {}, { 1 to 1 }),
    )

    private fun graphOf(passes: List<RenderPass>) = RenderGraph { null }.apply { passes.forEach { addPass(it) } }

    private class Pass(override val name: String, override val onDemand: Boolean = false) : BaseRenderPass() {
        var ran = false
        override val ranThisFrame: Boolean get() = isEnabled && ran
    }

    @Test
    fun Info_ListsPassesInExecutionOrder_WithTheirReadsAndWrites() {
        val info = graphOf(realPasses()).info()

        assertEquals(
            listOf("ShadowPass", "PickingPass", "GeometryPass", "ParticlePass", "PostProcessPass", "DebugPass", "UiPass"),
            info.passes.map { it.id },
        )
        val io = info.passes.associate { it.id to (it.reads to it.writes) }
        assertEquals(emptyList<String>() to listOf(SHADOW_MAP, LOCAL_SHADOW_MAP), io["ShadowPass"])
        assertEquals(emptyList<String>() to listOf(PICKING_IDS), io["PickingPass"])
        assertEquals(listOf(SHADOW_MAP, LOCAL_SHADOW_MAP) to listOf(SCENE_COLOR, SCENE_DEPTH), io["GeometryPass"])
        assertEquals(listOf(SCENE_COLOR, SCENE_DEPTH) to listOf(SCENE_COLOR), io["ParticlePass"])
        assertEquals(listOf(SCENE_COLOR, SCENE_DEPTH) to listOf(SCENE_COLOR), io["PostProcessPass"])
        assertEquals(listOf(SCENE_DEPTH) to listOf(SCENE_COLOR), io["DebugPass"])
        assertEquals(emptyList<String>() to listOf(SCENE_COLOR), io["UiPass"])
        // Only resources the renderer really has (no FinalColor).
        info.passes.flatMap { it.reads + it.writes }.forEach { assertTrue(it in RenderResourceIds.ALL, it) }
    }

    @Test
    fun Info_NoShadowPassKeepsTheIdAndWritesNothing() {
        val info = graphOf(realPasses(shadow = NoShadowPass())).info()
        val shadow = info.passes.first()
        assertEquals("ShadowPass", shadow.id)
        assertTrue(shadow.writes.isEmpty())
        // Geometry still declares its shadow inputs: the window shows them as "no writer".
        assertEquals(listOf(SHADOW_MAP, LOCAL_SHADOW_MAP), info.passes.first { it.id == "GeometryPass" }.reads)
    }

    @Test
    fun Info_FlagsRequiredAndOnDemandPasses() {
        val passes = graphOf(realPasses()).info().passes.associateBy { it.id }
        assertEquals(listOf("GeometryPass"), passes.values.filter { it.required }.map { it.id })
        assertEquals(listOf("PickingPass"), passes.values.filter { it.onDemand }.map { it.id })
        assertEquals("lbl.render_pass.geometry", passes.getValue("GeometryPass").labelKey)
        assertEquals("lbl.render_pass.post_process.desc", passes.getValue("PostProcessPass").descriptionKey)
    }

    @Test
    fun Info_ListsEveryPostEffect_DisabledOnesIncluded() {
        val settings = PostProcessSettings().apply { PostEffect.entries.forEach { setEnabled(it, false) } }
        val graph = graphOf(listOf(postProcess(settings = settings)))

        val stages = graph.info().passes.single().stages
        assertEquals(PostEffect.DEFAULT_PIPELINE.map { it.name }, stages.map { it.id })
        assertEquals(PostEffect.DEFAULT_PIPELINE.map { it.labelKey }, stages.map { it.labelKey })
        assertTrue(stages.none { it.enabled })
        assertTrue(stages.all { it.canToggle && it.active })

        // A disabled effect can be switched back on, and setStageEnabled sets (it does not toggle).
        assertTrue(graph.setStageEnabled("PostProcessPass", "FXAA", true))
        assertTrue(graph.setStageEnabled("PostProcessPass", "FXAA", true))
        assertTrue(settings.fxaaEnabled)
        assertTrue(graph.info().passes.single().stages.first { it.id == "FXAA" }.enabled)
        assertFalse(graph.setStageEnabled("PostProcessPass", "NOPE", true))
        assertFalse(graph.setStageEnabled("GeometryPass", "FXAA", true))
    }

    @Test
    fun Info_StagesInactiveWithoutTheSceneFramebuffer() {
        val stages = graphOf(listOf(postProcess(useFbo = false))).info().passes.single().stages
        assertTrue(stages.isNotEmpty() && stages.none { it.active })
    }

    @Test
    fun Info_IsCachedUntilSomethingChanges() {
        val graph = graphOf(realPasses())
        val first = graph.info()
        assertSame(first, graph.info(), "nothing changed: same instance, same version")
    }

    @Test
    fun Info_VersionChanges_WhenStateChangesOutsideSetEnabled() {
        val passes = realPasses()
        val graph = graphOf(passes)
        val post = passes.first { it is PostProcessPass } as PostProcessPass
        var last = graph.info()

        fun assertNewVersion(what: String) {
            val now = graph.info()
            assertNotEquals(last.version, now.version, what)
            last = now
        }

        // Direct `isEnabled` writes, as the GPU parity harness does.
        passes.first { it.name == "DebugPass" }.isEnabled = false
        assertNewVersion("isEnabled set directly")
        assertFalse(last.passes.first { it.id == "DebugPass" }.enabled)

        passes.first { it.name == "DebugPass" }.toggleEnable()
        assertNewVersion("toggleEnable")

        post.toggleEffect(PostEffect.SSAO)
        assertNewVersion("toggleEffect")

        post.updateSettings(PostProcessSettings().apply { setEnabled(PostEffect.BLOOM, false) })
        assertNewVersion("updateSettings")
        assertFalse(last.passes.first { it.id == "PostProcessPass" }.stages.first { it.id == "BLOOM" }.enabled)

        graph.addPass(Pass("Extra"))
        assertNewVersion("pass added")
        assertEquals(8, last.passes.size)

        // Setting a flag to its current value changes nothing.
        passes.first { it.name == "ShadowPass" }.isEnabled = true
        assertSame(last, graph.info())
    }

    @Test
    fun SetEnabled_RefusesRequiredAndUnknownPasses() {
        val graph = graphOf(realPasses())
        assertFalse(graph.setEnabled("GeometryPass", false))
        assertTrue(graph.info().passes.first { it.id == "GeometryPass" }.enabled)
        assertFalse(graph.setEnabled("NoSuchPass", false))

        assertTrue(graph.setEnabled("ShadowPass", false))
        assertTrue(graph.setEnabled("ShadowPass", false), "sets, does not toggle")
        assertFalse(graph.info().passes.first { it.id == "ShadowPass" }.enabled)
    }

    @Test
    fun ReadTimings_IndexesLikeInfo_AndKeepsOnDemandOutOfTheTotals() {
        val device = FakeRhiDevice().also { it.timerResultNs = 1_000_000L }
        val graph = RenderGraph { device }
        val a = Pass("A")
        val picking = Pass("Picking", onDemand = true)
        val off = Pass("Off").also { it.isEnabled = false }
        listOf(a, picking, off).forEach { graph.addPass(it) }
        graph.execute(Scene())

        val timings = PassTimings()
        graph.readTimings(timings)

        assertEquals(graph.info().version, timings.version)
        assertEquals(3, timings.size)
        assertEquals(1_000_000L, timings.gpuNs[0])
        assertEquals(-1L, timings.gpuNs[2], "disabled pass reads -1")
        assertEquals(-1L, timings.cpuNs[2])
        assertEquals(1_000_000L, timings.frameGpuNs, "sum of per-frame passes only: picking is left out")
        assertTrue(timings.gpuTimersAvailable)
        assertEquals(-1L, timings.lastMeasuredNs[1], "picking did not render yet")

        // Picking renders: its last measured time is that frame's CPU time, kept on later idle frames.
        picking.ran = true
        picking.executionTimeNs = 42_000L
        graph.readTimings(timings)
        assertEquals(42_000L, timings.lastMeasuredNs[1])
        picking.ran = false
        picking.executionTimeNs = 1L
        graph.readTimings(timings)
        assertEquals(42_000L, timings.lastMeasuredNs[1])

        // Structure change: the next read follows the new info.
        graph.addPass(Pass("B"))
        graph.readTimings(timings)
        assertEquals(graph.info().version, timings.version)
        assertEquals(4, timings.size)
    }

    @Test
    fun ReadTimings_WithoutGpuTimers_ReportsNoGpuTotal() {
        val graph = RenderGraph { null }.apply { addPass(Pass("A")) }
        graph.execute(Scene())
        val timings = PassTimings()
        graph.readTimings(timings)
        assertEquals(-1L, timings.frameGpuNs)
        assertFalse(timings.gpuTimersAvailable)
    }

    @Test
    fun LabelKeys_FollowTheLblConvention() {
        assertEquals("lbl.render_pass.geometry", RenderLabelKeys.passLabelKey("GeometryPass"))
        assertEquals("lbl.render_pass.post_process", RenderLabelKeys.passLabelKey("PostProcessPass"))
        assertEquals("lbl.render_pass.ui", RenderLabelKeys.passLabelKey("UiPass"))
        assertEquals("lbl.render_resource.scene_color", RenderResourceIds.labelKey(SCENE_COLOR))
        assertEquals("lbl.render_resource.local_shadow_map", RenderResourceIds.labelKey(LOCAL_SHADOW_MAP))
    }

    @Test
    fun EveryPassAndResource_HasEnglishAndFrenchLabels() {
        val info = graphOf(realPasses()).info()
        val keys = info.passes.flatMap { listOf(it.labelKey, it.descriptionKey) + it.stages.map { s -> s.labelKey } } +
            RenderResourceIds.ALL.map { RenderResourceIds.labelKey(it) }
        for (file in listOf("render.properties", "render_fr.properties")) {
            val props = Properties().apply { File("src/main/resources/editor/strings/$file").reader(Charsets.UTF_8).use { load(it) } }
            keys.forEach { assertTrue(!props.getProperty(it).isNullOrBlank(), "$file has no $it") }
        }
    }

    @Test
    fun SteadyInfoAndTimings_AllocateNothing() {
        val javaBin = File(System.getProperty("java.home"), "bin/java").path
        val process = ProcessBuilder(javaBin, "-cp", System.getProperty("java.class.path"), RenderGraphInfoAllocationProbe::class.java.name)
            .redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText().trim()
        assertTrue(process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS))
        val allocated = output.lines().last().toLongOrNull()
        if (allocated == -1L) return // no per-thread allocation counter on this JVM
        assertTrue(allocated != null && allocated < 16 * 1024, "10000 info()+readTimings() calls allocated: $output")
    }
}

/**
 * Prints the bytes 10 000 unchanged `info()` + `readTimings()` calls allocate after warm-up; -1 when the JVM cannot
 * count them. Runs in a fresh JVM (no mockk instrumentation), with plain passes and a staged pass.
 */
object RenderGraphInfoAllocationProbe {
    private class Plain(override val name: String) : BaseRenderPass()

    private class Staged : BaseRenderPass(), StagedRenderPass {
        override val name = "Staged"
        private val flags = BooleanArray(5) { it % 2 == 0 }
        override val stageCount = flags.size
        override fun stageId(index: Int) = "S$index"
        override fun stageLabelKey(index: Int) = "lbl.s$index"
        override fun isStageEnabled(index: Int) = flags[index]
        override fun setStageEnabled(index: Int, enabled: Boolean): Boolean { flags[index] = enabled; return true }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val bean = java.lang.management.ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
        if (bean == null || !bean.isThreadAllocatedMemorySupported) { println(-1); return }
        val graph = RenderGraph { null }
        // 70 passes + 5 stages: more than 64 bits, so the multi-word signature path is measured too.
        repeat(70) { graph.addPass(Plain("P$it")) }
        graph.addPass(Staged())
        val timings = PassTimings()
        repeat(20_000) { graph.info(); graph.readTimings(timings) } // warm up (JIT)
        val id = Thread.currentThread().id
        val before = bean.getThreadAllocatedBytes(id)
        repeat(10_000) { graph.info(); graph.readTimings(timings) }
        println(bean.getThreadAllocatedBytes(id) - before)
    }
}
