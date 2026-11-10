package com.pafoid.skate.editor.ui.windows.rendergraph

import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.events.RenderGraphAction
import com.pafoid.skate.editor.imgui.EditorWindow
import com.pafoid.skate.editor.imgui.EmptyState
import com.pafoid.skate.editor.imgui.ImGuiIds
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.editor.imgui.tippedWith
import com.pafoid.skate.editor.settings.ShadowQuality
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.render.graph.PassInfo
import com.pafoid.skate.engine.render.graph.PassTimings
import com.pafoid.skate.engine.render.graph.RenderGraph
import com.pafoid.skate.engine.render.graph.RenderGraphInfo
import com.pafoid.skate.engine.render.shadow.ShadowSettings
import imgui.ImDrawList
import imgui.ImGui
import imgui.flag.ImGuiButtonFlags
import imgui.flag.ImGuiMouseButton
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import imgui.type.ImInt
import kotlin.math.abs
import kotlin.math.pow

/**
 * The Render Graph window (EDT-54): passes as boxes in execution order with their resources as labelled edges,
 * per-pass GPU/CPU timings, enable/disable toggles and a details panel. It reads the graph only through
 * `RenderGraph.info()` / `readTimings()` and changes it through [RenderGraphAction] events (run as commands by
 * `RenderGraphActionHandler`); it never touches
 * pass classes or the RHI (RenderGraphWindowIsolationGuardTest).
 */
class RenderGraphWindow(
    private val engine: Engine,
) : EditorWindow(NAME, true) {
    private val strings = engine.stringManager
    private val graph: RenderGraph get() = engine.renderer.renderGraph

    /** Current font size in pixels (ImGui returns it as an Int). */
    private val fontPx: Float get() = ImGui.getFontSize().toFloat()

    override val icon: String = Icons.PROJECT_DIAGRAM
    override val relatedWindows: List<String> = listOf("window.profiler", "window.editor_settings")

    private val timings = PassTimings()
    private val stats = PassTimingStats()
    private val view = RenderGraphView()
    private val autoRefresh = ImBoolean(true)

    private var layout: GraphLayout? = null
    private var currentInfo: RenderGraphInfo? = null
    private val layoutCache = RenderGraphLayoutCache { orientation -> compute(currentInfo ?: graph.info(), orientation) }
    private var needsFit = true
    private var expanded = mutableSetOf<String>()
    private var selected: String? = null
    private var highlighted: String? = null
    private var hovered: GraphHit? = null

    override fun imgui() {
        if (beginWindow(strings)) {
            val info = graph.info()
            header(strings) { toolbar(info) }
            if (autoRefresh.get()) {
                graph.readTimings(timings)
                if (timings.version == info.version) stats.update(info, timings, ImGui.getIO().deltaTime)
            }
            if (info.passes.isEmpty()) {
                EmptyState.nothingYet(strings, Icons.PROJECT_DIAGRAM, "lbl.render_graph.no_passes", "lbl.render_graph.no_passes.hint")
            } else {
                body(info)
            }
        }
        ImGui.end()
    }

    // ── Header strip ──────────────────────────────────────────────────────────────────────────────────────────────

    private fun toolbar(info: RenderGraphInfo) {
        if (strings.tipped(ImGui.smallButton("${Icons.EXPAND} ${strings.getString("lbl.render_graph.fit")}##rg_fit"), "lbl.render_graph.fit")) needsFit = true
        ImGui.sameLine()
        if (strings.tipped(ImGui.smallButton("${strings.getString("lbl.render_graph.actual_size")}##rg_one"), "lbl.render_graph.actual_size")) view.actualSize()
        ImGui.sameLine()
        ImGui.checkbox("${strings.getString("lbl.render_graph.auto_update")}##rg_auto", autoRefresh)
        if (ImGui.isItemHovered()) ImGui.setTooltip(strings.getString("tooltip.render_graph.auto_update"))
        ImGui.sameLine()
        ImGui.textDisabled("|")
        ImGui.sameLine()
        ImGui.text(strings.getString("lbl.render_graph.frame_cpu", PassTimingStats.format(stats.frameCpuMs)))
        ImGui.sameLine()
        ImGui.text(strings.getString("lbl.render_graph.frame_gpu", PassTimingStats.format(stats.frameGpuMs)))
        if (ImGui.isItemHovered()) ImGui.setTooltip(strings.getString("tooltip.render_graph.frame_gpu"))
        ImGui.sameLine()
        ImGui.textDisabled("|")
        ImGui.sameLine()
        ImGui.text(strings.getString("lbl.render_graph.passes_enabled", info.passes.count { it.enabled }, info.passes.size))
        if (!timings.gpuTimersAvailable && timings.version >= 0) {
            ImGui.sameLine()
            MImGui.warningText("${Icons.INFO} ${strings.getString("lbl.render_graph.no_timers")}")
            if (ImGui.isItemHovered()) ImGui.setTooltip(strings.getString("tooltip.render_graph.no_timers"))
        }
    }

    // ── Body: canvas, details, status ─────────────────────────────────────────────────────────────────────────────

    private fun body(info: RenderGraphInfo) {
        val availW = ImGui.getContentRegionAvailX()
        val availH = ImGui.getContentRegionAvailY() - ImGui.getFrameHeightWithSpacing()
        val selectedIndex = info.passes.indexOfFirst { it.id == selected }.takeIf { it >= 0 }
        val detailsW = if (selectedIndex != null) minOf(DETAILS_WIDTH * fontPx / BASE_FONT, availW * 0.45f) else 0f
        val canvasW = availW - detailsW - if (detailsW > 0f) ImGui.getStyle().itemSpacingX else 0f

        val flags = ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoScrollWithMouse or ImGuiWindowFlags.NoMove
        if (ImGui.beginChild("##rg_canvas", canvasW, availH, true, flags)) canvas(info)
        ImGui.endChild()
        if (selectedIndex != null) {
            ImGui.sameLine()
            if (ImGui.beginChild("##rg_details", detailsW, availH, true)) details(info, selectedIndex)
            ImGui.endChild()
        }
        statusLine(info)
    }

    /** The layout for this canvas size, from [layoutCache]; a change of orientation or pass count re-fits the view. */
    private fun layoutFor(info: RenderGraphInfo, viewW: Float, viewH: Float): GraphLayout {
        currentInfo = info
        val chosen = layoutCache.get(info.version, expanded, fontPx, viewW, viewH)
        val previous = layout
        if (previous == null || previous.orientation != chosen.orientation || previous.nodes.size != chosen.nodes.size) needsFit = true
        layout = chosen
        return chosen
    }

    private fun compute(info: RenderGraphInfo, orientation: GraphOrientation): GraphLayout {
        val fontSize = fontPx
        val style = GraphLayoutStyle(
            lineHeight = ImGui.getTextLineHeightWithSpacing(),
            padding = fontSize * 0.5f,
            columnGap = fontSize * 3.5f,
            laneGap = fontSize * 0.75f,
            margin = fontSize,
            toggleSize = fontSize,
            minNodeWidth = fontSize * 9f,
        )
        return RenderGraphLayout.compute(info, { ImGui.calcTextSize(it).x }, { strings.getString(it) }, expanded, orientation, style)
    }

    private fun canvas(info: RenderGraphInfo) {
        val ox = ImGui.getCursorScreenPosX()
        val oy = ImGui.getCursorScreenPosY()
        val w = ImGui.getContentRegionAvailX().coerceAtLeast(1f)
        val h = ImGui.getContentRegionAvailY().coerceAtLeast(1f)
        val graphLayout = layoutFor(info, w, h)
        if (needsFit) {
            view.fit(graphLayout.width, graphLayout.height, w, h)
            needsFit = false
        }

        ImGui.invisibleButton("##rg_canvas_input", w, h, ImGuiButtonFlags.MouseButtonLeft or ImGuiButtonFlags.MouseButtonRight or ImGuiButtonFlags.MouseButtonMiddle)
        val io = ImGui.getIO()
        val mx = io.mousePosX - ox
        val my = io.mousePosY - oy
        val isHovered = ImGui.isItemHovered()
        if (isHovered && io.mouseWheel != 0f) view.zoomAt(1.1f.pow(io.mouseWheel), mx, my)
        if (ImGui.isItemActive() && (ImGui.isMouseDragging(ImGuiMouseButton.Middle) || ImGui.isMouseDragging(ImGuiMouseButton.Right))) {
            view.panBy(io.mouseDeltaX, io.mouseDeltaY)
        }
        hovered = if (isHovered) GraphHitTest.at(graphLayout, view.toWorldX(mx), view.toWorldY(my), PIN_RADIUS / view.zoom) else null
        if (ImGui.isItemClicked(ImGuiMouseButton.Left)) click(info, hovered)
        hoverTooltip(info, hovered)

        val dl = ImGui.getWindowDrawList()
        dl.pushClipRect(ox, oy, ox + w, oy + h, true)
        graphLayout.edges.forEach { drawEdge(dl, ox, oy, info, it) }
        graphLayout.nodes.forEach { drawNode(dl, ox, oy, info, it) }
        dl.popClipRect()
    }

    private fun click(info: RenderGraphInfo, hit: GraphHit?) {
        when (hit) {
            is GraphHit.Toggle -> info.passes[hit.node].let { setPass(it, !it.enabled) }
            is GraphHit.Expander -> info.passes[hit.node].id.let { if (!expanded.remove(it)) expanded += it }
            is GraphHit.Stage -> info.passes[hit.node].let { pass ->
                val stage = pass.stages[hit.stage]
                if (stage.canToggle) engine.eventSystem.publish(RenderGraphAction.SetPassEnabled(pass.id, !stage.enabled, stage.id))
            }
            is GraphHit.Pin -> highlighted = hit.resource.takeIf { it != highlighted }
            is GraphHit.Node -> info.passes[hit.node].id.let { selected = it.takeIf { id -> id != selected } }
            null -> highlighted = null
        }
    }

    private fun setPass(pass: PassInfo, enabled: Boolean) {
        if (!pass.required) engine.eventSystem.publish(RenderGraphAction.SetPassEnabled(pass.id, enabled))
    }

    private fun hoverTooltip(info: RenderGraphInfo, hit: GraphHit?) {
        val text = when (hit) {
            is GraphHit.Toggle -> {
                val pass = info.passes[hit.node]
                strings.getString(if (pass.id == PICKING_ID) "tooltip.render_graph.toggle_picking" else "tooltip.render_graph.toggle")
            }
            is GraphHit.Expander -> strings.getString("tooltip.render_graph.expand")
            is GraphHit.Stage -> info.passes[hit.node].stages[hit.stage].let { stage ->
                if (stage.active) strings.getString("tooltip.render_graph.stage") else strings.getString("tooltip.render_graph.stage_inactive")
            }
            is GraphHit.Pin -> {
                val pin = layout?.nodes?.get(hit.node)?.let { n -> (n.inputs + n.outputs).firstOrNull { it.resource == hit.resource && it.isInput == hit.isInput } }
                val name = strings.getString(RenderGraphLayout.resourceKey(hit.resource))
                if (pin != null && !pin.hasWriter) strings.getString("tooltip.render_graph.no_writer", name)
                else strings.getString("tooltip.render_graph.resource", name)
            }
            is GraphHit.Node -> {
                val pass = info.passes[hit.node]
                when {
                    pass.onDemand -> strings.getString("tooltip.render_graph.on_demand")
                    pass.required -> strings.getString("tooltip.render_graph.required")
                    else -> null
                }
            }
            null -> null
        }
        text?.let { ImGui.setTooltip(it) }
    }

    // ── Drawing ───────────────────────────────────────────────────────────────────────────────────────────────────

    private fun drawEdge(dl: ImDrawList, ox: Float, oy: Float, info: RenderGraphInfo, edge: GraphEdge) {
        val sourceOn = info.passes[edge.fromNode].enabled
        val isHighlighted = highlighted == edge.resource
        val alpha = if (highlighted != null && !isHighlighted) 0.35f else 0.9f
        val color = resourceColor(edge.resource, alpha)
        val thickness = (if (isHighlighted) 3f else 1.6f) * view.zoom.coerceAtLeast(0.6f)
        val pts = edge.points.map { (x, y) -> ox + view.toScreenX(x) to oy + view.toScreenY(y) }
        if (edge.lane == 0 && sourceOn) {
            val (ax, ay) = pts.first()
            val (bx, by) = pts.last()
            val horizontal = layout?.orientation != GraphOrientation.VERTICAL
            val d = if (horizontal) abs(bx - ax) * 0.5f else abs(by - ay) * 0.5f
            if (horizontal) dl.addBezierCubic(ax, ay, ax + d, ay, bx - d, by, bx, by, color, thickness)
            else dl.addBezierCubic(ax, ay, ax, ay + d, bx, by - d, bx, by, color, thickness)
            return
        }
        for (k in 0 until pts.size - 1) {
            val (ax, ay) = pts[k]
            val (bx, by) = pts[k + 1]
            if (sourceOn) dl.addLine(ax, ay, bx, by, color, thickness) else dashed(dl, ax, ay, bx, by, color, thickness)
        }
    }

    private fun dashed(dl: ImDrawList, ax: Float, ay: Float, bx: Float, by: Float, color: Int, thickness: Float) {
        val dx = bx - ax
        val dy = by - ay
        val len = kotlin.math.sqrt(dx * dx + dy * dy)
        if (len < 1f) return
        val dash = 6f
        var t = 0f
        while (t < len) {
            val t2 = minOf(t + dash, len)
            dl.addLine(ax + dx * t / len, ay + dy * t / len, ax + dx * t2 / len, ay + dy * t2 / len, color, thickness)
            t += dash * 2
        }
    }

    private fun drawNode(dl: ImDrawList, ox: Float, oy: Float, info: RenderGraphInfo, node: GraphNode) {
        val pass = node.pass
        val z = view.zoom
        fun sx(x: Float) = ox + view.toScreenX(x)
        fun sy(y: Float) = oy + view.toScreenY(y)
        val dim = if (pass.enabled) 1f else 0.45f
        val r = node.rect
        val rounding = 4f * z
        val isSelected = pass.id == selected
        val isHovered = (hovered as? GraphHit.Node)?.node == node.index

        dl.addRectFilled(sx(r.x), sy(r.y), sx(r.right), sy(r.bottom), color(0.16f, 0.17f, 0.20f, 0.95f), rounding)
        dl.addRectFilled(sx(r.x), sy(r.y), sx(r.right), sy(node.header.bottom + 2f), color(0.22f, 0.30f, 0.42f, 0.9f * dim), rounding)
        val border = when {
            isSelected -> color(1f, 0.65f, 0.15f, 1f)
            isHovered -> color(0.75f, 0.8f, 0.9f, 1f)
            else -> color(0.35f, 0.38f, 0.45f, dim)
        }
        dl.addRect(sx(r.x), sy(r.y), sx(r.right), sy(r.bottom), border, rounding, 0, if (isSelected) 2f else 1f)

        val text = color(0.92f, 0.92f, 0.95f, dim)
        val muted = color(0.65f, 0.67f, 0.72f, dim)

        // Header: expander, icon + name, toggle or lock.
        var x = node.header.x
        node.expander?.let {
            text(dl, sx(it.x), sy(node.header.y), if (pass.id in expanded) Icons.ARROW_DOWN else Icons.ARROW_RIGHT, muted)
            x = it.right + 4f
        }
        text(dl, sx(x), sy(node.header.y), "${passIcon(pass.id)} ${strings.getString(pass.labelKey)}", text)
        val toggle = node.toggle
        if (toggle == null) {
            val lockW = ImGui.calcTextSize(Icons.LOCK).x
            text(dl, sx(node.header.right) - lockW * z, sy(node.header.y), Icons.LOCK, muted)
        } else {
            checkbox(dl, sx(toggle.x), sy(toggle.y), toggle.w * z, pass.enabled, (hovered as? GraphHit.Toggle)?.node == node.index)
        }

        // Timing row and share bar.
        val i = node.index
        val timing = when {
            !pass.enabled -> strings.getString("lbl.render_graph.off")
            pass.onDemand -> {
                val last = timings.lastMeasuredNs.getOrNull(i)?.takeIf { it >= 0 && timings.version == info.version }
                strings.getString("lbl.render_graph.on_demand") +
                    (last?.let { "  " + strings.getString("lbl.render_graph.last", PassTimingStats.format(PassTimingStats.toMs(it))) } ?: "")
            }
            else -> strings.getString("lbl.render_graph.timing", PassTimingStats.format(stats.gpuMs(i)), PassTimingStats.format(stats.cpuMs(i)))
        }
        text(dl, sx(node.timingRow.x), sy(node.timingRow.y), timing, if (pass.enabled) text else muted)

        val share = stats.share(info, i)
        val bar = node.barRow
        val barTop = sy(bar.y + bar.h * 0.25f)
        val barBottom = sy(bar.bottom - bar.h * 0.25f)
        dl.addRectFilled(sx(bar.x), barTop, sx(bar.right), barBottom, color(0.10f, 0.10f, 0.12f, 1f), 2f * z)
        if (share >= 0f) {
            val ms = if (stats.frameGpuMs > 0f && stats.gpuMs(i) >= 0f) stats.gpuMs(i) else stats.cpuMs(i)
            val barColor = when (PassTimingStats.budgetLevel(ms)) {
                PassTimingStats.BudgetLevel.LOW -> color(0.35f, 0.75f, 0.40f, 1f)
                PassTimingStats.BudgetLevel.MEDIUM -> color(0.90f, 0.75f, 0.25f, 1f)
                PassTimingStats.BudgetLevel.HIGH -> color(0.90f, 0.35f, 0.30f, 1f)
            }
            dl.addRectFilled(sx(bar.x), barTop, sx(bar.x + bar.w * share), barBottom, barColor, 2f * z)
            val pct = "${(share * 100f).toInt()} %"
            text(dl, sx(bar.right) - ImGui.calcTextSize(pct).x * z, sy(bar.y), pct, muted)
        }

        // Pins.
        node.inputs.forEach { pin ->
            val c = if (pin.hasWriter) resourceColor(pin.resource, dim) else color(0.5f, 0.5f, 0.5f, 0.5f)
            dl.addCircleFilled(sx(pin.x), sy(pin.y), PIN_RADIUS * 0.6f * z.coerceAtLeast(0.6f), c)
            text(dl, sx(pin.labelX), sy(pin.labelY), strings.getString(RenderGraphLayout.resourceKey(pin.resource)), if (pin.hasWriter) text else muted)
        }
        node.outputs.forEach { pin ->
            dl.addCircleFilled(sx(pin.x), sy(pin.y), PIN_RADIUS * 0.6f * z.coerceAtLeast(0.6f), resourceColor(pin.resource, dim))
            val name = strings.getString(RenderGraphLayout.resourceKey(pin.resource))
            text(dl, sx(pin.labelX) - ImGui.calcTextSize(name).x * z, sy(pin.labelY), name, text)
        }

        // Stages (post effects) when expanded.
        node.stageRows.forEachIndexed { s, row ->
            val stage = pass.stages[s]
            val on = stage.enabled && stage.active
            val box = row.h * 0.8f
            checkbox(dl, sx(row.x), sy(row.y + (row.h - box) / 2), box * z, stage.enabled, (hovered as? GraphHit.Stage)?.let { it.node == node.index && it.stage == s } == true)
            text(dl, sx(row.x + box + 6f), sy(row.y), strings.getString(stage.labelKey), if (on && pass.enabled) text else muted)
        }
    }

    private fun checkbox(dl: ImDrawList, x: Float, y: Float, size: Float, checked: Boolean, hot: Boolean) {
        dl.addRectFilled(x, y, x + size, y + size, if (hot) color(0.35f, 0.40f, 0.50f, 1f) else color(0.25f, 0.27f, 0.32f, 1f), 2f)
        if (checked) {
            val p = size * 0.22f
            dl.addLine(x + p, y + size * 0.5f, x + size * 0.42f, y + size - p, color(0.4f, 0.85f, 0.45f, 1f), 2f)
            dl.addLine(x + size * 0.42f, y + size - p, x + size - p, y + p, color(0.4f, 0.85f, 0.45f, 1f), 2f)
        }
    }

    private fun text(dl: ImDrawList, x: Float, y: Float, value: String, col: Int) {
        val size = (fontPx * view.zoom).toInt()
        if (size < 5) return // unreadable: skip, the boxes still show the structure
        val offset = (ImGui.getTextLineHeightWithSpacing() - fontPx) * 0.5f * view.zoom
        dl.addText(ImGui.getFont(), size, x, y + offset, col, value)
    }

    // ── Details panel and status line ─────────────────────────────────────────────────────────────────────────────

    private fun details(info: RenderGraphInfo, i: Int) {
        val pass = info.passes[i]
        ImGui.text("${passIcon(pass.id)} ${strings.getString(pass.labelKey)}")
        ImGui.sameLine()
        if (strings.tippedWith(ImGui.smallButton("${Icons.WINDOW_CLOSE}##rg_details_close"), "tooltip.render_graph.close_details")) selected = null
        ImGui.separator()
        ImGui.textWrapped(strings.getString(pass.descriptionKey))
        ImGui.spacing()

        resourceList("lbl.render_graph.inputs", pass.reads)
        resourceList("lbl.render_graph.outputs", pass.writes)

        ImGui.spacing()
        if (pass.onDemand) MImGui.textDisabled(strings.getString("tooltip.render_graph.on_demand"))
        if (i < stats.size && stats.historyCount > 0 && pass.enabled) {
            val useGpu = stats.frameGpuMs > 0f
            val history = if (useGpu) stats.gpuHistory(i) else stats.cpuHistory(i)
            ImGui.textDisabled(strings.getString(if (useGpu) "lbl.render_graph.history_gpu" else "lbl.render_graph.history_cpu", stats.historyCount))
            ImGui.plotLines(ImGuiIds.stable("", "rg_history"), history, stats.historyCount, if (stats.historyCount < PassTimingStats.HISTORY) 0 else stats.historyOffset)
            ImGui.text(strings.getString("lbl.render_graph.avg_max", PassTimingStats.format(stats.average(history)), PassTimingStats.format(stats.max(history))))
        }

        if (pass.id == SHADOW_ID) {
            ImGui.spacing()
            shadowQuality()
        }
    }

    private fun resourceList(titleKey: String, resources: List<String>) {
        if (resources.isEmpty()) return
        ImGui.textDisabled(strings.getString(titleKey))
        resources.forEach { resource ->
            val name = strings.getString(RenderGraphLayout.resourceKey(resource))
            if (ImGui.selectable("$name##rg_res_$titleKey.$resource", highlighted == resource)) {
                highlighted = resource.takeIf { it != highlighted }
            }
            if (ImGui.isItemHovered()) ImGui.setTooltip(strings.getString("tooltip.render_graph.resource", name))
        }
    }

    /** LOW/MEDIUM/HIGH selector; sets the project's shadow quality (REN-30). */
    private fun shadowQuality() {
        val labels = ShadowQuality.entries.map { strings.getString("settings.graphics.shadow_quality.${it.name.lowercase()}") }.toTypedArray()
        val current = ImInt(ShadowSettings.quality.ordinal)
        ImGui.setNextItemWidth(fontPx * 8f)
        if (ImGui.combo("${strings.getString("lbl.render_graph.shadow_quality")}###rg_shadow_quality", current, labels, labels.size)) {
            engine.eventSystem.publish(ProjectEvent.SetShadowQualityRequested(ShadowQuality.entries[current.get()]))
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip(strings.getString("tooltip.render_graph.shadow_quality"))
    }

    private fun statusLine(info: RenderGraphInfo) {
        MImGui.textDisabled(strings.getString("lbl.render_graph.hint"))
        val off = info.passes.count { !it.enabled && !it.required }
        if (off > 0) {
            ImGui.sameLine()
            MImGui.warningText(strings.getString("lbl.render_graph.passes_off", off))
            ImGui.sameLine()
            if (strings.tipped(ImGui.smallButton("${strings.getString("lbl.render_graph.enable_all")}##rg_enable_all"), "lbl.render_graph.enable_all")) engine.eventSystem.publish(RenderGraphAction.EnableAll)
        }
        if (info.passes.any { it.id == PICKING_ID && !it.enabled }) {
            ImGui.sameLine()
            MImGui.warningText(strings.getString("lbl.render_graph.picking_off"))
        }
    }

    companion object {
        const val NAME = "window.render_graph"
        private const val PICKING_ID = "PickingPass"
        private const val SHADOW_ID = "ShadowPass"
        private const val PIN_RADIUS = 7f
        private const val DETAILS_WIDTH = 280f
        private const val BASE_FONT = 16f

        /** Icon per pass id, with a fallback for passes added later. */
        fun passIcon(passId: String): String = when (passId) {
            "ShadowPass" -> Icons.SUN
            "PickingPass" -> Icons.MOUSE_POINTER
            "GeometryPass" -> Icons.CUBE
            "ParticlePass" -> Icons.MAGIC
            "PostProcessPass" -> Icons.PALETTE
            "DebugPass" -> Icons.RULER
            "UiPass" -> Icons.WINDOW_RESTORE
            else -> Icons.GEAR
        }

        private fun color(r: Float, g: Float, b: Float, a: Float): Int = ImGui.colorConvertFloat4ToU32(r, g, b, a)

        private val RESOURCE_COLORS = listOf(
            floatArrayOf(0.95f, 0.75f, 0.30f), floatArrayOf(0.45f, 0.75f, 0.95f), floatArrayOf(0.70f, 0.55f, 0.95f),
            floatArrayOf(0.45f, 0.85f, 0.55f), floatArrayOf(0.95f, 0.50f, 0.50f),
        )

        /** Stable colour per resource id (edges and pins of one resource share it). */
        fun resourceColorIndex(resource: String): Int = Math.floorMod(resource.hashCode(), RESOURCE_COLORS.size)

        private fun resourceColor(resource: String, alpha: Float): Int =
            RESOURCE_COLORS[resourceColorIndex(resource)].let { color(it[0], it[1], it[2], alpha) }
    }
}
