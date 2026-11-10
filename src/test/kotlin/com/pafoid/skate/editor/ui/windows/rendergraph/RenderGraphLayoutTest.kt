package com.pafoid.skate.editor.ui.windows.rendergraph

import com.pafoid.skate.engine.render.graph.PassInfo
import com.pafoid.skate.engine.render.graph.RenderGraphInfo
import com.pafoid.skate.engine.render.graph.StageInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** EDT-54 §4: the Render Graph window's pure layout, view transform, hit testing and layout cache. */
class RenderGraphLayoutTest {
    private val measure = { text: String -> text.length * 7f }
    private val label = { key: String -> key.substringAfterLast('.') }

    private fun pass(id: String, reads: List<String> = emptyList(), writes: List<String> = emptyList(), required: Boolean = false,
                     enabled: Boolean = true, onDemand: Boolean = false, stages: List<StageInfo> = emptyList()) =
        PassInfo(id, "lbl.render_pass.$id", "lbl.render_pass.$id.desc", reads, writes, required, enabled, onDemand, stages)

    private val effects = listOf("SSAO", "BLOOM", "TONEMAPPING", "GAMMA_CORRECTION", "FXAA")
        .map { StageInfo(it, "lbl.post_process.${it.lowercase()}", it == "FXAA", true, true) }

    /** The real graph (RenderResources.inExecutionOrder with the EDT-54 2a I/O). */
    private fun realGraph(shadowWrites: List<String> = listOf("ShadowMap", "LocalShadowMap"), version: Int = 1, debugEnabled: Boolean = true) =
        RenderGraphInfo(version, listOf(
            pass("ShadowPass", writes = shadowWrites),
            pass("PickingPass", writes = listOf("PickingIds"), onDemand = true),
            pass("GeometryPass", listOf("ShadowMap", "LocalShadowMap"), listOf("SceneColor", "SceneDepth"), required = true),
            pass("ParticlePass", listOf("SceneColor", "SceneDepth"), listOf("SceneColor")),
            pass("PostProcessPass", listOf("SceneColor", "SceneDepth"), listOf("SceneColor"), stages = effects),
            pass("DebugPass", listOf("SceneDepth"), listOf("SceneColor"), enabled = debugEnabled),
            pass("UiPass", writes = listOf("SceneColor")),
        ))

    private fun layout(info: RenderGraphInfo = realGraph(), expanded: Set<String> = emptySet(), orientation: GraphOrientation = GraphOrientation.HORIZONTAL) =
        RenderGraphLayout.compute(info, measure, label, expanded, orientation)

    @Test
    fun Columns_FollowExecutionOrder() {
        val nodes = layout().nodes
        assertEquals(realGraph().passes.map { it.id }, nodes.map { it.pass.id })
        nodes.zipWithNext().forEach { (a, b) -> assertTrue(a.rect.right < b.rect.x, "${a.pass.id} before ${b.pass.id}") }
    }

    @Test
    fun Nodes_DoNotOverlap_AndFitInTheGraph() {
        listOf(GraphOrientation.HORIZONTAL, GraphOrientation.VERTICAL).forEach { o ->
            val l = layout(expanded = setOf("PostProcessPass"), orientation = o)
            l.nodes.forEachIndexed { i, a -> l.nodes.drop(i + 1).forEach { b -> assertFalse(a.rect.overlaps(b.rect), "$o ${a.pass.id}/${b.pass.id}") } }
            l.nodes.forEach { assertTrue(it.rect.right <= l.width && it.rect.bottom <= l.height, "$o ${it.pass.id} inside") }
        }
    }

    @Test
    fun Edges_LinkTheLatestEarlierWriterToEachReader() {
        val info = realGraph()
        val l = layout(info)
        l.edges.forEach { e ->
            assertTrue(e.fromNode < e.toNode)
            assertTrue(e.resource in info.passes[e.fromNode].writes)
            assertTrue(e.resource in info.passes[e.toNode].reads)
            val later = (e.fromNode + 1 until e.toNode).filter { e.resource in info.passes[it].writes }
            assertTrue(later.isEmpty(), "${e.resource}: a later writer exists between ${e.fromNode} and ${e.toNode}")
        }
        // SceneColor reads as a chain of versions: Geometry -> Particles -> PostProcess (R1).
        val sceneColor = l.edges.filter { it.resource == "SceneColor" }.map { it.fromNode to it.toNode }
        assertEquals(listOf(2 to 3, 3 to 4), sceneColor)
        // Every edge starts at the writer's output pin and ends at the reader's input pin.
        l.edges.forEach { e ->
            val from = l.nodes[e.fromNode].outputs.first { it.resource == e.resource }
            val to = l.nodes[e.toNode].inputs.first { it.resource == e.resource }
            assertEquals(from.x to from.y, e.points.first())
            assertEquals(to.x to to.y, e.points.last())
        }
    }

    @Test
    fun Lanes_NeverShareAnOverlappingSpan_AndNeighboursLinkDirectly() {
        val l = layout()
        l.edges.forEach { e -> assertEquals(e.toNode - e.fromNode == 1, e.lane == 0, "${e.resource} ${e.fromNode}->${e.toNode}") }
        val routed = l.edges.filter { it.lane > 0 }
        routed.forEachIndexed { i, a ->
            routed.drop(i + 1).filter { it.lane == a.lane }.forEach { b ->
                assertFalse(RenderGraphLayout.overlaps(a.fromNode..a.toNode, b.fromNode..b.toNode), "$a / $b")
            }
        }
        assertEquals(2, l.laneCount, "ShadowMap and LocalShadowMap share a span; SceneDepth reuses the inner lane")
        // Lane edges run above every node.
        val top = l.nodes.minOf { it.rect.y }
        routed.forEach { e -> assertTrue(e.points.drop(2).take(2).all { (_, y) -> y < top }) }
    }

    @Test
    fun NoShadowMaps_ReadsWithoutAWriterGetNoEdgeAndADimmedPin() {
        val l = layout(realGraph(shadowWrites = emptyList()))
        assertTrue(l.nodes[0].outputs.isEmpty(), "NoShadowPass writes nothing (R3)")
        val geometry = l.nodes[2]
        assertEquals(listOf(false, false), geometry.inputs.map { it.hasWriter })
        assertTrue(l.edges.none { it.resource == "ShadowMap" || it.resource == "LocalShadowMap" })
        assertTrue(l.nodes[3].inputs.all { it.hasWriter })
    }

    @Test
    fun Nodes_GrowWithTheirLabels() {
        val short = layout().nodes[2].rect.w
        val long = RenderGraphLayout.compute(realGraph(), measure, { if (it == "lbl.render_pass.GeometryPass") "x".repeat(60) else label(it) }).nodes[2].rect.w
        assertTrue(long > short)
        assertTrue(long >= 60 * 7f)
    }

    @Test
    fun ExpandingPostProcess_AddsOneRowPerEffect() {
        val closed = layout().nodes[4]
        val open = layout(expanded = setOf("PostProcessPass")).nodes[4]
        assertTrue(closed.stageRows.isEmpty())
        assertEquals(5, open.stageRows.size)
        assertEquals(5 * GraphLayoutStyle().lineHeight, open.rect.h - closed.rect.h, 0.01f)
        assertTrue(open.expander != null && layout().nodes[2].expander == null)
    }

    @Test
    fun RequiredPassHasNoToggle() {
        val nodes = layout().nodes
        assertEquals(null, nodes[2].toggle)
        assertTrue(nodes.filter { it.index != 2 }.all { it.toggle != null })
    }

    @Test
    fun DisablingAPass_DoesNotMoveAnything() {
        val on = layout(realGraph(debugEnabled = true))
        val off = layout(realGraph(debugEnabled = false))
        assertEquals(on.nodes.map { it.rect }, off.nodes.map { it.rect })
        assertEquals(on.edges.map { it.points }, off.edges.map { it.points })
    }

    @Test
    fun Vertical_FlowsTopToBottom_WithTheSameLanes() {
        val h = layout()
        val v = layout(orientation = GraphOrientation.VERTICAL)
        v.nodes.zipWithNext().forEach { (a, b) -> assertTrue(a.rect.bottom < b.rect.y) }
        assertEquals(h.edges.map { it.lane }, v.edges.map { it.lane })
        // Inputs on the top edge, outputs on the bottom edge.
        v.nodes.forEach { n ->
            n.inputs.forEach { assertEquals(n.rect.y, it.y) }
            n.outputs.forEach { assertEquals(n.rect.bottom, it.y) }
        }
    }

    @Test
    fun Orientation_TurnsVerticalOnlyWhenTallAndTooNarrow() {
        assertEquals(GraphOrientation.HORIZONTAL, RenderGraphLayout.chooseOrientation(1600f, 300f, 1400f))
        assertEquals(GraphOrientation.HORIZONTAL, RenderGraphLayout.chooseOrientation(500f, 300f, 1400f), "short dock: pan instead")
        assertEquals(GraphOrientation.VERTICAL, RenderGraphLayout.chooseOrientation(300f, 900f, 1400f))
        assertEquals(GraphOrientation.HORIZONTAL, RenderGraphLayout.chooseOrientation(900f, 1000f, 1400f))
    }

    @Test
    fun HitTest_FindsToggleExpanderStagePinAndNode() {
        val l = layout(expanded = setOf("PostProcessPass"))
        val shadow = l.nodes[0]
        val toggle = shadow.toggle!!
        assertEquals(GraphHit.Toggle(0), GraphHitTest.at(l, toggle.x + 1, toggle.y + 1))
        val post = l.nodes[4]
        assertEquals(GraphHit.Expander(4), GraphHitTest.at(l, post.expander!!.x + 1, post.expander!!.y + 1))
        assertEquals(GraphHit.Stage(4, 2), GraphHitTest.at(l, post.stageRows[2].x + 20, post.stageRows[2].y + 2))
        val pin = l.nodes[2].inputs[0]
        assertEquals(GraphHit.Pin(2, "ShadowMap", true), GraphHitTest.at(l, pin.x + 1, pin.y))
        assertEquals(GraphHit.Node(3), GraphHitTest.at(l, l.nodes[3].timingRow.x + 5, l.nodes[3].timingRow.y + 2))
        assertEquals(null, GraphHitTest.at(l, -50f, -50f))
    }

    @Test
    fun View_FitContainsTheGraphAndNeverZoomsPastOneToOne() {
        val l = layout()
        val view = RenderGraphView()
        view.fit(l.width, l.height, 600f, 250f)
        assertTrue(view.zoom < 1f)
        l.nodes.forEach { n ->
            assertTrue(view.toScreenX(n.rect.x) >= -0.01f && view.toScreenX(n.rect.right) <= 600.01f)
            assertTrue(view.toScreenY(n.rect.y) >= -0.01f && view.toScreenY(n.rect.bottom) <= 250.01f)
        }
        view.fit(100f, 50f, 600f, 250f)
        assertEquals(1f, view.zoom)
        assertEquals(250f, view.panX, 0.01f, "centred")
    }

    @Test
    fun View_ZoomKeepsThePointUnderTheCursorFixed() {
        val view = RenderGraphView()
        view.panBy(30f, -12f)
        val wx = view.toWorldX(200f)
        val wy = view.toWorldY(80f)
        view.zoomAt(1.6f, 200f, 80f)
        assertEquals(200f, view.toScreenX(wx), 0.01f)
        assertEquals(80f, view.toScreenY(wy), 0.01f)
        repeat(30) { view.zoomAt(2f, 0f, 0f) }
        assertEquals(RenderGraphView.MAX_ZOOM, view.zoom)
        repeat(30) { view.zoomAt(0.5f, 0f, 0f) }
        assertEquals(RenderGraphView.MIN_ZOOM, view.zoom)
    }

    @Test
    fun Cache_RebuildsOnlyWhenVersionExpansionOrFontChange() {
        var info = realGraph(version = 1)
        val cache = RenderGraphLayoutCache { o -> RenderGraphLayout.compute(info, measure, label, emptySet(), o) }
        val first = cache.get(1, emptySet(), 16f, 2000f, 300f)
        repeat(10) { cache.get(1, emptySet(), 16f, 2000f + it, 300f) } // resizing the dock
        assertEquals(1, cache.computeCount)

        info = realGraph(version = 2, debugEnabled = false)
        val second = cache.get(2, emptySet(), 16f, 2000f, 300f)
        assertEquals(2, cache.computeCount)
        assertNotEquals(first.version, second.version)

        cache.get(2, setOf("PostProcessPass"), 16f, 2000f, 300f)
        cache.get(2, setOf("PostProcessPass"), 18f, 2000f, 300f)
        assertEquals(4, cache.computeCount)

        // A narrow, tall dock computes the vertical layout once and keeps it.
        val v = cache.get(2, setOf("PostProcessPass"), 18f, 200f, 1200f)
        cache.get(2, setOf("PostProcessPass"), 18f, 210f, 1200f)
        assertEquals(GraphOrientation.VERTICAL, v.orientation)
        assertEquals(5, cache.computeCount)
    }
}
