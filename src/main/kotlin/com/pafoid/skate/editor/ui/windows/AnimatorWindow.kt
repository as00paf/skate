package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.app.Editor
import com.pafoid.skate.editor.animation.AnimatorControllerCatalog
import com.pafoid.skate.editor.animation.clipeditor.OpenAnimationClip
import com.pafoid.skate.editor.animation.controllereditor.AnimatorWindowController
import com.pafoid.skate.editor.animation.controllereditor.AnimatorLayout
import com.pafoid.skate.editor.animation.controllereditor.AnimatorWindowWiring
import com.pafoid.skate.editor.animation.controllereditor.ClipOption
import com.pafoid.skate.editor.animation.controllereditor.ClipSource
import com.pafoid.skate.editor.animation.controllereditor.ControllerGraphView
import com.pafoid.skate.editor.animation.controllereditor.ControllerGraphView.Companion.NODE_H
import com.pafoid.skate.editor.animation.controllereditor.ControllerGraphView.Companion.NODE_W
import com.pafoid.skate.editor.imgui.EditorWindow
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.imgui.Tooltips
import com.pafoid.skate.editor.imgui.TrailingButtons
import com.pafoid.skate.editor.imgui.WindowHeader
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.label
import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.editor.imgui.tippedWith
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.animation.edit.SkAnimFormat
import com.pafoid.skate.engine.animation.graph.AnimationControllerAsset.Companion.ANY_STATE_NODE
import com.pafoid.skate.engine.animation.graph.ConditionOp
import com.pafoid.skate.engine.animation.graph.NodePos
import com.pafoid.skate.engine.animation.graph.ParameterDef
import com.pafoid.skate.engine.animation.graph.ParameterType
import com.pafoid.skate.engine.animation.graph.StateDef
import com.pafoid.skate.engine.animation.graph.TransitionCondition
import com.pafoid.skate.engine.animation.graph.TransitionDef
import com.pafoid.skate.engine.assets.AssetPaths
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.getComponent
import imgui.ImGui
import imgui.flag.ImGuiButtonFlags
import imgui.flag.ImGuiDragDropFlags
import imgui.flag.ImGuiFocusedFlags
import imgui.flag.ImGuiKey
import imgui.flag.ImGuiMouseButton
import imgui.flag.ImGuiSelectableFlags
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImString
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animator window (ANI-21): builds and edits animator controllers (`*.controller.json`) as a state graph. Parameters
 * and states on the left, the graph in the middle, the inspector of the selection on the right. Every edit is
 * undoable; Save writes the file and rebuilds the characters that use it. In play mode the graph shows the preview
 * character's active state, crossfades and events, and its parameters can be changed live.
 * All state and actions live in [AnimatorWindowController]; this class only draws and forwards input.
 */
class AnimatorWindow(
    private val engine: Engine,
    editor: Editor,
    private val projectManager: ProjectManager,
    private val controller: AnimatorWindowController = AnimatorWindowWiring.create(engine, editor, projectManager),
) : EditorWindow("window.animator") {

    override val icon: String get() = Icons.PROJECT_DIAGRAM
    override val relatedWindows: List<String> get() = listOf("window.animation_editor", "window.properties")

    private val strings = engine.stringManager
    private val view get() = controller.view

    // In-progress text and number edits: committed on Enter / when the field loses focus.
    private val texts = HashMap<String, ImString>()
    private val numbers = HashMap<String, FloatArray>()
    private var activeEdit: String? = null

    // Canvas gestures.
    private var pressNode: String? = null
    private var pressX = 0f
    private var pressY = 0f
    private var boxStart: Pair<Float, Float>? = null
    private var contextNode: String? = null
    private var contextArrow: ControllerGraphView.Arrow? = null
    private var contextAt = NodePos(0f, 0f)
    private var renaming: String? = null
    private var frameRequest = false
    private var canvasW = 640f
    private var canvasH = 360f

    // Play mode: events flash on their state for a moment.
    private val flashes = HashMap<String, Float>()

    override fun imgui() {
        // The window scrolls vertically (its scrollbar) when a small window cannot hold the panels; the wheel stays
        // with the graph's zoom and the side panels' own scrolling.
        if (beginWindow(strings, ImGuiWindowFlags.NoScrollWithMouse)) {
            header(strings)
            toolbar()
            controller.message?.let { wrapped { MImGui.errorText(it) } }
            val doc = controller.document
            if (doc == null) {
                emptyPage()
            } else {
                if (doc.readOnly) readOnlyBanner()
                shortcuts()
                panels()
            }
            probe(PANEL_WINDOW)
        }
        ImGui.end()
    }

    /** Parameters and states | graph | inspector, sized from the font and shared out of the window's width (ANI-25). */
    private fun panels() {
        val widths = AnimatorLayout.panelWidths(ImGui.getContentRegionAvailX(), ImGui.getFontSize().toFloat(), ImGui.getStyle().itemSpacingX)
        val height = ImGui.getContentRegionAvailY().coerceAtLeast(ImGui.getFontSize() * AnimatorLayout.MIN_PANEL_HEIGHT_EMS)
        if (ImGui.beginChild("##animator_left", widths.left, height, true)) { parametersPanel(); statesList(); probe(PANEL_LEFT) }
        ImGui.endChild()
        ImGui.sameLine()
        if (ImGui.beginChild("##animator_canvas_area", widths.canvas, height, true, ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoScrollWithMouse)) canvas()
        ImGui.endChild()
        ImGui.sameLine()
        if (ImGui.beginChild("##animator_inspector", widths.right, height, true)) { inspector(); probe(PANEL_INSPECTOR) }
        ImGui.endChild()
    }

    // ---- toolbar ----------------------------------------------------------------------------------------------

    private fun toolbar() {
        val doc = controller.document
        val current = doc?.let { (if (it.dirty) "* " else "") + AnimatorControllerCatalog.displayName(it.path) }
            ?: strings.getString("lbl.animator_window.choose")
        val newLabel = fit("${Icons.PLUS} ${strings.getString("btn.animator_window.new")}")
        val saveLabel = fit("${Icons.SAVE} ${strings.getString("btn.animator_window.save")}")
        val buttons = if (doc == null) listOf(newLabel) else listOf(newLabel, saveLabel)
        val comboLabel = strings.label("lbl.animator_window.controller")
        val row = trailing(buttons.map(::buttonWidth), strings.getString("lbl.animator_window.controller"), maxField = em(14f))
        if (ImGui.beginCombo(comboLabel, current)) {
            for (path in AnimatorControllerCatalog.shared.options(doc?.path)) {
                if (ImGui.selectable("${AnimatorControllerCatalog.displayName(path)}###animator_open:$path", path == doc?.path)) controller.open(path)
                Tooltips.always(path)
            }
            ImGui.endCombo()
        }
        Tooltips.always(strings.getString("lbl.animator_window.controller.tooltip"))
        row.next()
        if (strings.tipped(ImGui.button(newLabel), "btn.animator_window.new")) controller.newController()
        if (doc == null) return
        row.next()
        ImGui.beginDisabled(!doc.dirty || doc.readOnly)
        if (strings.tipped(ImGui.button(saveLabel), "btn.animator_window.save")) controller.save()
        ImGui.endDisabled()

        val preview = controller.preview?.gameObject?.name ?: strings.getString("lbl.animator_window.preview_none")
        val previewText = strings.getString("lbl.animator_window.preview", preview)
        flow(ImGui.calcTextSize(previewText).x)
        ImGui.text(previewText)
        val useSelected = fit(strings.getString("btn.animator_window.use_selected"))
        flow(buttonWidth(useSelected))
        if (strings.tipped(ImGui.button(useSelected), "btn.animator_window.use_selected")) {
            engine.sceneManager.currentScene?.selectedGameObject?.getComponent<Animator>()?.let { controller.preview = it }
        }
        if (controller.canUseOnPreview) {
            val useOn = fit(strings.getString("btn.animator_window.use_on_preview", preview))
            flow(buttonWidth(useOn))
            if (strings.tipped(ImGui.button(useOn), "btn.animator_window.use_on_preview")) controller.usePreviewOnDocument()
        }
        if (controller.liveMachine != null) {
            val live = strings.getString("lbl.animator_window.live")
            flow(ImGui.calcTextSize(live).x)
            MImGui.successText(live)
            Tooltips.always(strings.getString("lbl.animator_window.live.tooltip"))
        }
    }

    private fun emptyPage() {
        ImGui.spacing()
        textWrapped(strings.getString("lbl.animator_window.empty"))
        if (strings.tipped(ImGui.button(fit(strings.getString("btn.animator_window.new"))), "btn.animator_window.new")) controller.newController()
    }

    private fun readOnlyBanner() {
        wrapped { MImGui.warningText(strings.getString("lbl.animator_window.read_only")) }
        val copy = fit(strings.getString("btn.animator_window.make_copy"))
        flow(buttonWidth(copy))
        if (strings.tipped(ImGui.button(copy), "btn.animator_window.make_copy")) controller.makeProjectCopy()
    }

    private fun shortcuts() {
        if (!ImGui.isWindowFocused(ImGuiFocusedFlags.RootAndChildWindows) || ImGui.getIO().wantTextInput) return
        val ctrl = ImGui.getIO().keyCtrl
        when {
            ImGui.isKeyPressed(ImGuiKey.Escape) -> controller.cancelConnect()
            ImGui.isKeyPressed(ImGuiKey.Delete) -> controller.deleteSelection()
            ctrl && ImGui.isKeyPressed(ImGuiKey.D) -> controller.duplicateSelection()
            ctrl && ImGui.isKeyPressed(ImGuiKey.C) -> controller.copySelection()
            ctrl && ImGui.isKeyPressed(ImGuiKey.V) -> controller.paste()
            !ctrl && ImGui.isKeyPressed(ImGuiKey.F) -> frameRequest = true
        }
    }

    // ---- left panel -------------------------------------------------------------------------------------------

    private fun parametersPanel() {
        val doc = controller.document ?: return
        section(strings.getString("lbl.animator_window.parameters"))
        ImGui.beginDisabled(doc.readOnly)
        ParameterType.entries.forEachIndexed { i, type ->
            val key = "btn.animator_window.add_${type.name.lowercase()}"
            val label = fit("${Icons.PLUS} ${strings.getString(key)}")
            if (i > 0) flow(buttonWidth(label))
            if (strings.tipped(iconLabelButton(label), key)) controller.addParameter(type)
        }
        ImGui.endDisabled()
        val params = controller.parameters()
        if (params.isEmpty()) wrapped { MImGui.textDisabled(strings.getString("lbl.animator_window.no_parameters")) }
        val live = controller.liveMachine != null
        for (p in params) {
            ImGui.pushID("param:${p.name}")
            ImGui.separator()
            ImGui.beginDisabled(doc.readOnly)
            val row = trailing(listOf(iconButtonWidth()))
            textCommit("param_name:${p.name}", "##name", p.name)?.let { controller.renameParameter(p.name, it) }
            Tooltips.always(strings.getString("lbl.animator_window.parameter_name.tooltip"))
            row.next()
            if (strings.tippedWith(iconButton(Icons.TRASH), "btn.animator_window.remove_parameter.tooltip")) controller.removeParameter(p.name)
            ImGui.endDisabled()
            val tag = when {
                controller.isRequest(p) -> "lbl.animator_window.request" to strings.getString("lbl.animator_window.request.tooltip", p.name)
                !controller.isDeclared(p.name) -> "lbl.animator_window.inferred" to strings.getString("lbl.animator_window.inferred.tooltip")
                else -> null
            }
            ImGui.beginDisabled(doc.readOnly)
            val typeWidth = AnimatorLayout.share(ImGui.getContentRegionAvailX(), em(7f))
            ImGui.setNextItemWidth(typeWidth)
            if (ImGui.beginCombo("##type", typeName(p.type))) {
                for (t in ParameterType.entries) if (ImGui.selectable(typeName(t), t == p.type)) controller.setParameter(p.name, t, p.default)
                ImGui.endCombo()
            }
            Tooltips.always(strings.getString("lbl.animator_window.parameter_type.tooltip"))
            ImGui.endDisabled()
            flow(em(AnimatorLayout.MIN_VALUE_EMS))
            if (live) liveValue(p) else defaultValue(p, doc.readOnly)
            tag?.let { (key, tooltip) ->
                val text = strings.getString(key)
                flow(ImGui.calcTextSize(text).x)
                MImGui.textDisabled(text)
                Tooltips.always(tooltip)
            }
            ImGui.popID()
        }
    }

    private fun defaultValue(p: ParameterDef, readOnly: Boolean) {
        ImGui.beginDisabled(readOnly)
        when (p.type) {
            ParameterType.FLOAT -> {
                ImGui.setNextItemWidth(ImGui.getContentRegionAvailX().coerceAtMost(em(6f)))
                floatCommit("param_default:${p.name}", "##default", p.default, 0.05f, -1e6f, 1e6f, "%.2f")
                    ?.let { controller.setParameter(p.name, p.type, it) }
            }
            ParameterType.BOOL -> if (ImGui.checkbox("##default", p.default != 0f)) controller.setParameter(p.name, p.type, if (p.default != 0f) 0f else 1f)
            ParameterType.TRIGGER -> MImGui.textDisabled(strings.getString("lbl.animator_window.trigger_default"))
        }
        Tooltips.always(strings.getString("lbl.animator_window.parameter_default.tooltip"))
        ImGui.endDisabled()
    }

    private fun liveValue(p: ParameterDef) {
        val value = controller.liveValue(p) ?: return
        when (p.type) {
            ParameterType.FLOAT -> {
                val buf = floatArrayOf(value)
                ImGui.setNextItemWidth(ImGui.getContentRegionAvailX().coerceAtMost(em(6f)))
                if (ImGui.dragFloat("##live", buf, 0.05f)) controller.setLive(p, buf[0])
            }
            ParameterType.BOOL -> if (ImGui.checkbox("##live", value != 0f)) controller.setLive(p, if (value != 0f) 0f else 1f)
            ParameterType.TRIGGER -> if (strings.tipped(iconLabelButton(strings.getString("btn.animator_window.fire")), "btn.animator_window.fire")) controller.setLive(p, 1f)
        }
        Tooltips.always(strings.getString("lbl.animator_window.live_value.tooltip"))
    }

    private fun statesList() {
        val doc = controller.document ?: return
        val asset = doc.asset
        ImGui.spacing()
        section(strings.getString("lbl.animator_window.states"))
        ImGui.beginDisabled(doc.readOnly)
        if (strings.tipped(iconLabelButton("${Icons.PLUS} ${strings.getString("btn.animator_window.add_state")}"), "btn.animator_window.add_state")) {
            val (cx, cy) = view.toCanvas(canvasW / 2f, canvasH / 2f, 0f, 0f)
            controller.addState(NodePos(cx - NODE_W / 2, cy - NODE_H / 2))
        }
        ImGui.endDisabled()
        val active = controller.liveMachine?.currentState
        for (s in asset.states) {
            val marks = buildString {
                if (s.name == asset.defaultState) append("${Icons.PLAY} ")
                append(s.name)
                if (s.motion.isEmpty()) append("  (${strings.getString("lbl.animator_window.no_clip")})")
                if (s.name == active) append("  ${strings.getString("lbl.animator_window.active")}")
            }
            if (wrappedSelectable("###state:${s.name}", marks, s.name in controller.selectedNodes)) {
                controller.select(s.name, ImGui.getIO().keyCtrl)
            }
        }
    }

    // ---- canvas -----------------------------------------------------------------------------------------------

    private fun canvas() {
        val asset = controller.asset ?: return
        val ox = ImGui.getCursorScreenPosX()
        val oy = ImGui.getCursorScreenPosY()
        canvasW = ImGui.getContentRegionAvailX().coerceAtLeast(50f)
        canvasH = ImGui.getContentRegionAvailY().coerceAtLeast(50f)
        if (frameRequest) { frameRequest = false; view.frameAll(controller.positions(), canvasW, canvasH) }

        ImGui.invisibleButton("##animator_canvas", canvasW, canvasH,
            ImGuiButtonFlags.MouseButtonLeft or ImGuiButtonFlags.MouseButtonRight or ImGuiButtonFlags.MouseButtonMiddle)
        val hovered = ImGui.isItemHovered()
        dropTarget(ox, oy)

        val positions = controller.positions()
        val arrows = controller.arrows()
        val io = ImGui.getIO()
        val mx = ImGui.getMousePosX()
        val my = ImGui.getMousePosY()
        val (cx, cy) = view.toCanvas(mx, my, ox, oy)
        val hoverNode = if (hovered) ControllerGraphView.nodeAt(positions, cx, cy) else null
        val hoverArrow = if (hovered && hoverNode == null) ControllerGraphView.arrowAt(arrows, cx, cy, 6f / view.zoom) else null

        if (hovered && io.mouseWheel != 0f) view.zoomAt(if (io.mouseWheel > 0f) 1.1f else 1f / 1.1f, mx, my, ox, oy)
        if (ImGui.isItemActive() && ImGui.isMouseDragging(ImGuiMouseButton.Middle, 0f)) view.panBy(io.mouseDeltaX, io.mouseDeltaY)

        if (hovered && ImGui.isMouseClicked(ImGuiMouseButton.Left)) leftClick(hoverNode, hoverArrow, cx, cy)
        if (hovered && hoverNode != null && hoverNode != ANY_STATE_NODE && ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left)) {
            renaming = hoverNode
            pressNode = null
            ImGui.openPopup("##animator_rename")
        }
        pressNode?.let { node ->
            if (ImGui.isMouseDragging(ImGuiMouseButton.Left, 3f)) {
                if (!controller.dragging) controller.beginDrag(node)
                controller.dragTo(cx - pressX, cy - pressY)
            }
        }
        if (ImGui.isMouseReleased(ImGuiMouseButton.Left)) {
            if (controller.dragging) controller.endDrag()
            pressNode = null
            boxStart?.let { (bx, by) ->
                if (kotlin.math.abs(cx - bx) > 3f || kotlin.math.abs(cy - by) > 3f) controller.selectNodes(ControllerGraphView.nodesIn(positions, bx, by, cx, cy))
            }
            boxStart = null
        }
        if (hovered && ImGui.isMouseClicked(ImGuiMouseButton.Right)) {
            contextNode = hoverNode
            contextArrow = hoverArrow
            contextAt = NodePos(cx, cy)
            hoverNode?.let { if (it !in controller.selectedNodes) controller.select(it) }
            hoverArrow?.let { controller.selectTransition(it.indices.first()) }
            ImGui.openPopup("##animator_context")
        }

        draw(ox, oy, positions, arrows, hoverArrow, cx, cy)
        hoverArrow?.let { Tooltips.always(it.indices.joinToString("\n") { i -> sentence(asset.transitions[i]) }) }
        if (hoverArrow == null && hoverNode == null && hovered && controller.connectFrom == null) {
            Tooltips.always(strings.getString("lbl.animator_window.canvas.tooltip"))
        }
        contextMenu()
        renamePopup()
    }

    private fun leftClick(node: String?, arrow: ControllerGraphView.Arrow?, cx: Float, cy: Float) {
        if (controller.connectFrom != null) {
            if (node != null) controller.connectTo(node) else controller.cancelConnect()
            return
        }
        when {
            node != null -> {
                if (ImGui.getIO().keyCtrl) controller.select(node, additive = true)
                else {
                    if (node !in controller.selectedNodes) controller.select(node)
                    pressNode = node
                    pressX = cx
                    pressY = cy
                }
            }
            arrow != null -> controller.selectTransition(arrow.indices.first())
            else -> {
                controller.clearSelection()
                boxStart = cx to cy
            }
        }
    }

    private fun dropTarget(ox: Float, oy: Float) {
        if (!ImGui.beginDragDropTarget()) return
        val (cx, cy) = view.toCanvas(ImGui.getMousePosX(), ImGui.getMousePosY(), ox, oy)
        val at = NodePos(cx - NODE_W / 2, cy - NODE_H / 2)
        ImGui.acceptDragDropPayload<Animation>("ANIMATION", ImGuiDragDropFlags.None)?.let { anim ->
            controller.addStateFromClip(optionFor(anim.name, anim.path), at)
        }
        ImGui.acceptDragDropPayload<String>("FILE_PATH", ImGuiDragDropFlags.None)?.let { path ->
            if (SkAnimFormat.isSkAnim(path)) {
                runCatching { SkAnimFormat.decode(File(path).readText()).name }.getOrNull()?.let { controller.addStateFromClip(optionFor(it, path), at) }
            }
        }
        ImGui.endDragDropTarget()
    }

    /** A clip dropped on the canvas: as the picker lists it, else a project file when it lies in the project. */
    private fun optionFor(name: String, path: String?): ClipOption {
        controller.clipOptions().firstOrNull { it.name == name }?.let { return it }
        val root = projectManager.getProjectDirectory()
        val rel = path?.let { AssetPaths.relativize(File(it).absolutePath, root) }?.takeIf { root != null && !AssetPaths.isAbsolute(it) }
        return if (rel != null) ClipOption(name, ClipSource.PROJECT, rel) else ClipOption(name, ClipSource.CHARACTER)
    }

    private fun draw(
        ox: Float, oy: Float, positions: Map<String, NodePos>, arrows: List<ControllerGraphView.Arrow>,
        hoverArrow: ControllerGraphView.Arrow?, mouseCx: Float, mouseCy: Float,
    ) {
        val asset = controller.asset ?: return
        val dl = ImGui.getWindowDrawList()
        dl.pushClipRect(ox, oy, ox + canvasW, oy + canvasH, true)
        dl.addRectFilled(ox, oy, ox + canvasW, oy + canvasH, ImGui.getColorU32(0.13f, 0.13f, 0.15f, 1f))
        val zoom = view.zoom
        fun sx(x: Float) = ox + (x - view.panX) * zoom
        fun sy(y: Float) = oy + (y - view.panY) * zoom

        // Grid.
        val step = 50f
        val grid = ImGui.getColorU32(1f, 1f, 1f, 0.05f)
        var gx = kotlin.math.floor(view.panX / step) * step
        while (sx(gx) < ox + canvasW) { dl.addLine(sx(gx), oy, sx(gx), oy + canvasH, grid); gx += step }
        var gy = kotlin.math.floor(view.panY / step) * step
        while (sy(gy) < oy + canvasH) { dl.addLine(ox, sy(gy), ox + canvasW, sy(gy), grid); gy += step }

        val live = controller.liveMachine
        updateFlashes(live)

        // Arrows.
        val selected = controller.selectedTransition
        for (a in arrows) {
            val isSelected = selected != null && selected in a.indices
            val color = when {
                isSelected -> ImGui.getColorU32(1f, 0.75f, 0.2f, 1f)
                a === hoverArrow -> ImGui.getColorU32(0.95f, 0.95f, 0.95f, 1f)
                else -> ImGui.getColorU32(0.7f, 0.72f, 0.78f, 1f)
            }
            arrow(dl, sx(a.x0), sy(a.y0), sx(a.x1), sy(a.y1), color, if (isSelected) 3f else 2f)
            if (a.indices.size > 1) {
                val mx = (sx(a.x0) + sx(a.x1)) / 2f
                val my = (sy(a.y0) + sy(a.y1)) / 2f
                dl.addCircleFilled(mx, my, 8f, ImGui.getColorU32(0.25f, 0.25f, 0.3f, 1f))
                dl.addText(mx - 4f, my - 7f, color, a.indices.size.toString())
            }
            if (live != null && live.nextState == a.to && (a.from == live.currentState || a.from == ANY_STATE_NODE)) {
                val t = live.fadeProgress
                dl.addLine(sx(a.x0), sy(a.y0), sx(a.x0) + (sx(a.x1) - sx(a.x0)) * t, sy(a.y0) + (sy(a.y1) - sy(a.y0)) * t, ImGui.getColorU32(0.3f, 0.9f, 0.4f, 1f), 4f)
            }
        }

        // Making a transition: a line from the source box to the mouse.
        controller.connectFrom?.let { from ->
            positions[from]?.let { p ->
                arrow(dl, sx(p.x + NODE_W / 2), sy(p.y + NODE_H / 2), sx(mouseCx), sy(mouseCy), ImGui.getColorU32(1f, 0.75f, 0.2f, 1f), 2f)
            }
        }

        // Boxes.
        val states = asset.states.associateBy { it.name }
        for ((name, p) in positions) {
            val x0 = sx(p.x); val y0 = sy(p.y); val x1 = sx(p.x + NODE_W); val y1 = sy(p.y + NODE_H)
            val any = name == ANY_STATE_NODE
            val isActive = live?.currentState == name
            val fill = when {
                isActive -> ImGui.getColorU32(0.2f, 0.45f, 0.25f, 1f)
                any -> ImGui.getColorU32(0.3f, 0.25f, 0.4f, 1f)
                name == asset.defaultState -> ImGui.getColorU32(0.35f, 0.3f, 0.18f, 1f)
                else -> ImGui.getColorU32(0.24f, 0.26f, 0.3f, 1f)
            }
            dl.addRectFilled(x0, y0, x1, y1, fill, 6f * zoom)
            val border = if (name in controller.selectedNodes) ImGui.getColorU32(1f, 0.75f, 0.2f, 1f) else ImGui.getColorU32(0.5f, 0.52f, 0.58f, 1f)
            dl.addRect(x0, y0, x1, y1, border, 6f * zoom, 0, if (name in controller.selectedNodes) 2.5f else 1f)
            val label = (if (name == asset.defaultState) "${Icons.PLAY} " else "") +
                if (any) strings.getString("lbl.animator_window.any_state") else name
            val tw = ImGui.calcTextSize(label).x
            dl.pushClipRect(x0 + 2f, y0, x1 - 2f, y1, true)
            dl.addText((x0 + x1) / 2f - tw / 2f, (y0 + y1) / 2f - ImGui.getTextLineHeight() / 2f, ImGui.getColorU32(0.95f, 0.95f, 0.95f, 1f), label)
            dl.popClipRect()
            if (states[name]?.motion?.isEmpty() == true) dl.addCircleFilled(x1 - 7f, y0 + 7f, 4f, ImGui.getColorU32(1f, 0.6f, 0.1f, 1f))
            if (isActive) {
                dl.addRectFilled(x0 + 4f, y1 - 5f, x0 + 4f + (x1 - x0 - 8f) * live!!.normalizedTime, y1 - 2f, ImGui.getColorU32(0.4f, 1f, 0.5f, 1f))
            }
            flashes[name]?.let { f -> dl.addRect(x0 - 3f, y0 - 3f, x1 + 3f, y1 + 3f, ImGui.getColorU32(0.4f, 0.8f, 1f, f / FLASH_SECONDS), 8f * zoom, 0, 3f) }
        }

        boxStart?.let { (bx, by) ->
            dl.addRect(sx(bx), sy(by), sx(mouseCx), sy(mouseCy), ImGui.getColorU32(0.4f, 0.7f, 1f, 0.9f))
            dl.addRectFilled(sx(bx), sy(by), sx(mouseCx), sy(mouseCy), ImGui.getColorU32(0.4f, 0.7f, 1f, 0.15f))
        }
        if (controller.connectFrom != null) {
            dl.addText(ox + 8f, oy + 6f, ImGui.getColorU32(1f, 0.8f, 0.3f, 1f), strings.getString("lbl.animator_window.connecting"))
        }
        dl.popClipRect()
    }

    private fun arrow(dl: imgui.ImDrawList, x0: Float, y0: Float, x1: Float, y1: Float, color: Int, thickness: Float) {
        dl.addLine(x0, y0, x1, y1, color, thickness)
        val angle = atan2(y1 - y0, x1 - x0)
        val size = 9f
        dl.addTriangleFilled(
            x1, y1,
            x1 - size * cos(angle - 0.45f), y1 - size * sin(angle - 0.45f),
            x1 - size * cos(angle + 0.45f), y1 - size * sin(angle + 0.45f),
            color,
        )
    }

    private fun updateFlashes(live: com.pafoid.skate.engine.animation.graph.AnimationStateMachine?) {
        val dt = ImGui.getIO().deltaTime
        val it = flashes.entries.iterator()
        while (it.hasNext()) { val e = it.next(); e.setValue(e.value - dt); if (e.value <= 0f) it.remove() }
        live?.firedEvents?.forEach { flashes[it.state] = FLASH_SECONDS }
    }

    private fun contextMenu() {
        if (!ImGui.beginPopup("##animator_context")) return
        val doc = controller.document ?: run { ImGui.endPopup(); return }
        val asset = doc.asset
        val node = contextNode
        val arrow = contextArrow
        ImGui.beginDisabled(doc.readOnly)
        when {
            node != null -> {
                if (menu("menu.animator_window.make_transition")) controller.startConnect(node)
                if (ImGui.beginMenu(strings.label("menu.animator_window.request_transition"))) {
                    for (s in asset.states) if (s.name != node && ImGui.menuItem("${s.name}###req:${s.name}")) controller.addRequestTransition(node, s.name)
                    ImGui.endMenu()
                }
                Tooltips.always(strings.getString("menu.animator_window.request_transition.tooltip"))
                if (node != ANY_STATE_NODE) {
                    ImGui.separator()
                    if (menu("menu.animator_window.set_default")) controller.setDefault(node)
                    if (menu("menu.animator_window.rename")) { renaming = node; ImGui.closeCurrentPopup(); ImGui.endDisabled(); ImGui.endPopup(); ImGui.openPopup("##animator_rename"); return }
                    if (menu("menu.animator_window.duplicate")) controller.duplicateSelection()
                    if (menu("menu.animator_window.duplicate_with_transitions")) controller.duplicateSelection(withTransitions = true)
                    if (menu("menu.animator_window.copy")) controller.copySelection()
                }
                ImGui.separator()
                if (menu("menu.animator_window.delete")) controller.deleteSelection()
            }
            arrow != null -> {
                if (menu("menu.animator_window.delete_transition")) controller.deleteSelection()
            }
            else -> {
                if (menu("menu.animator_window.add_state")) controller.addState(NodePos(contextAt.x - NODE_W / 2, contextAt.y - NODE_H / 2))
                ImGui.beginDisabled(!controller.canPaste)
                if (menu("menu.animator_window.paste")) controller.paste()
                ImGui.endDisabled()
                if (asset.transitions.none { it.from == null } && ImGui.beginMenu(strings.label("menu.animator_window.any_state_transition"))) {
                    for (s in asset.states) if (ImGui.menuItem("${s.name}###anyreq:${s.name}")) controller.addRequestTransition(ANY_STATE_NODE, s.name)
                    ImGui.endMenu()
                }
                Tooltips.always(strings.getString("menu.animator_window.any_state_transition.tooltip"))
            }
        }
        ImGui.endDisabled()
        ImGui.separator()
        if (menu("menu.animator_window.frame_all")) frameRequest = true
        ImGui.endPopup()
    }

    private fun menu(key: String): Boolean = strings.tipped(ImGui.menuItem(strings.getString(key)), key)

    private fun renamePopup() {
        if (!ImGui.beginPopup("##animator_rename")) return
        val node = renaming
        if (node == null) { ImGui.closeCurrentPopup(); ImGui.endPopup(); return }
        if (ImGui.isWindowAppearing()) ImGui.setKeyboardFocusHere()
        ImGui.setNextItemWidth(em(12f))
        textCommit("rename:$node", strings.label("lbl.animator_window.name"), node)?.let {
            controller.renameState(node, it)
            renaming = null
            ImGui.closeCurrentPopup()
        }
        ImGui.endPopup()
    }

    // ---- inspector --------------------------------------------------------------------------------------------

    private fun inspector() {
        val doc = controller.document ?: return
        ImGui.beginDisabled(doc.readOnly)
        val transition = controller.selectedTransition
        val state = controller.selectedState
        when {
            transition != null -> transitionInspector(transition)
            state != null -> stateInspector(state)
            controller.selectedNodes.singleOrNull() == ANY_STATE_NODE -> anyStateInspector()
            controller.selectedNodes.size > 1 -> wrapped { MImGui.textDisabled(strings.getString("lbl.animator_window.multi", controller.selectedNodes.size)) }
            else -> controllerInspector()
        }
        ImGui.endDisabled()
    }

    private fun controllerInspector() {
        val asset = controller.asset ?: return
        section(strings.getString("lbl.animator_window.controller_settings"))
        textCommit("controller_name", labeled("lbl.animator_window.controller_name"), asset.name)?.let { controller.setControllerName(it) }
        Tooltips.always(strings.getString("lbl.animator_window.controller_name.tooltip"))
        if (ImGui.beginCombo(labeled("lbl.animator_window.default_state"), asset.defaultState)) {
            for (s in asset.states) if (ImGui.selectable("${s.name}###default:${s.name}", s.name == asset.defaultState)) controller.setDefault(s.name)
            ImGui.endCombo()
        }
        Tooltips.always(strings.getString("lbl.animator_window.default_state.tooltip"))
        problems()
        if (asset.clips.isNotEmpty()) {
            section(strings.getString("lbl.animator_window.clip_files"))
            for (c in asset.clips) textWrapped(c)
        }
        if (asset.poses.isNotEmpty() || asset.clipFits.isNotEmpty() || asset.motions.isNotEmpty()) {
            section(strings.getString("lbl.animator_window.read_only_parts"))
            if (asset.poses.isNotEmpty()) textWrapped(strings.getString("lbl.animator_window.poses", asset.poses.keys.joinToString()))
            if (asset.motions.isNotEmpty()) textWrapped(strings.getString("lbl.animator_window.blend_trees", asset.motions.keys.joinToString()))
            if (asset.clipFits.isNotEmpty()) textWrapped(strings.getString("lbl.animator_window.clip_fits", asset.clipFits.keys.joinToString()))
        }
        ImGui.spacing()
        textWrapped(strings.getString("lbl.animator_window.how_to"))
    }

    private fun problems() {
        val errors = controller.errors()
        val warnings = controller.warnings()
        if (errors.isEmpty() && warnings.isEmpty()) {
            if (controller.preview != null) wrapped { MImGui.successText(strings.getString("lbl.animator_window.no_problems")) }
            return
        }
        section(strings.getString("lbl.animator_window.problems"))
        wrapped {
            errors.forEach { MImGui.errorText(it) }
            warnings.forEach { MImGui.warningText(it) }
        }
    }

    private fun stateInspector(state: StateDef) {
        val asset = controller.asset ?: return
        ImGui.pushID("state:${state.name}")
        section(strings.getString("lbl.animator_window.state"))
        textCommit("state_name:${state.name}", labeled("lbl.animator_window.name"), state.name)?.let { controller.renameState(state.name, it) }
        Tooltips.always(strings.getString("lbl.animator_window.name.tooltip"))
        if (labeledCheckbox("lbl.animator_window.is_default", state.name == asset.defaultState) && state.name != asset.defaultState) controller.setDefault(state.name)

        val options = controller.clipOptions()
        val clipLabel = when {
            state.motion.isEmpty() -> strings.getString("lbl.animator_window.no_clip_rest")
            state.motion in asset.motions -> strings.getString("lbl.animator_window.blend_tree", state.motion)
            else -> state.motion
        }
        val editPath = options.firstOrNull { it.name == state.motion }?.path
        val clipRow = if (editPath != null) labeledWithButtons("lbl.animator_window.clip", listOf(iconButtonWidth())) else null
        val clipCombo = if (clipRow != null) clipRow.second else labeled("lbl.animator_window.clip")
        if (ImGui.beginCombo(clipCombo, clipLabel)) {
            if (ImGui.selectable("${strings.getString("lbl.animator_window.no_clip_rest")}###clip_none", state.motion.isEmpty())) controller.setClip(state.name, null)
            var group: ClipSource? = null
            for (o in options) {
                if (o.source != group) { group = o.source; MImGui.textDisabled(strings.getString("lbl.animator_window.clip_source.${o.source.name.lowercase()}")) }
                if (ImGui.selectable("${o.name}###clip:${o.source}:${o.name}", o.name == state.motion)) controller.setClip(state.name, o)
                o.path?.let { Tooltips.always(it) }
            }
            for (key in asset.motions.keys) {
                if (ImGui.selectable("${strings.getString("lbl.animator_window.blend_tree", key)}###clip_tree:$key", key == state.motion)) controller.setClip(state.name, ClipOption(key, ClipSource.CHARACTER))
            }
            ImGui.endCombo()
        }
        Tooltips.always(strings.getString("lbl.animator_window.clip.tooltip"))
        if (clipRow != null && editPath != null) {
            clipRow.first.next()
            if (strings.tippedWith(iconButton(Icons.EDIT), "btn.animator_window.edit_clip.tooltip")) {
                val file = projectManager.getProjectDirectory()?.let { File(it, editPath) }?.takeIf { it.isFile } ?: File(editPath)
                engine.eventSystem.publish(OpenAnimationClip(file.absolutePath))
            }
        }
        if (state.motion.isEmpty()) wrapped { MImGui.warningText(strings.getString("lbl.animator_window.no_clip_warning")) }

        floatCommit("state_speed:${state.name}", labeled("lbl.animator_window.speed"), state.speed, 0.01f, -10f, 10f, "%.2f")
            ?.let { v -> controller.updateState(state.name) { it.copy(speed = v) } }
        Tooltips.always(strings.getString("lbl.animator_window.speed.tooltip"))
        if (labeledCheckbox("lbl.animator_window.loop", state.loop)) controller.updateState(state.name) { it.copy(loop = !it.loop) }

        section(strings.getString("lbl.animator_window.events"))
        state.events.forEachIndexed { i, e ->
            ImGui.pushID(i)
            ImGui.setNextItemWidth(AnimatorLayout.share(ImGui.getContentRegionAvailX(), em(AnimatorLayout.MIN_VALUE_EMS)))
            floatCommit("event_time:${state.name}:$i", "##event_time", e.time * 100f, 0.5f, 0f, 100f, "%.0f %%")
                ?.let { controller.updateEvent(state.name, i, e.copy(time = it / 100f)) }
            Tooltips.always(strings.getString("lbl.animator_window.event_time.tooltip"))
            flow(em(TrailingButtons.MIN_FIELD_EMS) + iconButtonWidth() + ImGui.getStyle().itemSpacingX)
            val row = trailing(listOf(iconButtonWidth()))
            textCommit("event_name:${state.name}:$i", "##event_name", e.name)?.let { controller.updateEvent(state.name, i, e.copy(name = it)) }
            Tooltips.always(strings.getString("lbl.animator_window.event_name.tooltip"))
            row.next()
            if (strings.tippedWith(iconButton(Icons.TRASH), "btn.animator_window.remove_event.tooltip")) controller.removeEvent(state.name, i)
            ImGui.popID()
        }
        if (strings.tipped(iconLabelButton("${Icons.PLUS} ${strings.getString("btn.animator_window.add_event")}"), "btn.animator_window.add_event")) {
            controller.addEvent(state.name, 0.5f, strings.getString("lbl.animator_window.new_event"))
        }

        outgoing(state.name)

        ImGui.spacing()
        buttonRow(
            "btn.animator_window.make_transition" to { controller.startConnect(state.name) },
            "btn.animator_window.duplicate" to { controller.duplicateSelection() },
            "btn.animator_window.delete" to { controller.deleteSelection() },
        )
        ImGui.popID()
    }

    private fun anyStateInspector() {
        section(strings.getString("lbl.animator_window.any_state"))
        textWrapped(strings.getString("lbl.animator_window.any_state_help"))
        outgoing(ANY_STATE_NODE)
        buttonRow("btn.animator_window.make_transition" to { controller.startConnect(ANY_STATE_NODE) })
    }

    /** Transitions leaving [node] in the order they are checked, with priority arrows. */
    private fun outgoing(node: String) {
        val asset = controller.asset ?: return
        section(strings.getString("lbl.animator_window.transitions_out"))
        val list = controller.transitionsFrom(node)
        if (list.isEmpty()) MImGui.textDisabled(strings.getString("lbl.animator_window.no_transitions"))
        list.forEachIndexed { pos, index ->
            ImGui.pushID("out:$index")
            ImGui.beginDisabled(pos == 0)
            if (strings.tippedWith(iconButton(Icons.ARROW_UP), "btn.animator_window.priority_up.tooltip")) controller.moveTransition(index, -1)
            ImGui.endDisabled()
            flow(iconButtonWidth())
            ImGui.beginDisabled(pos == list.lastIndex)
            if (strings.tippedWith(iconButton(Icons.ARROW_DOWN), "btn.animator_window.priority_down.tooltip")) controller.moveTransition(index, 1)
            ImGui.endDisabled()
            flow(em(AnimatorLayout.MIN_TEXT_EMS))
            if (wrappedSelectable("###sel", sentence(asset.transitions[index]), false)) controller.selectTransition(index)
            ImGui.popID()
        }
    }

    private fun transitionInspector(index: Int) {
        val asset = controller.asset ?: return
        val t = asset.transitions.getOrNull(index) ?: return
        ImGui.pushID("transition:$index")
        val from = t.from ?: strings.getString("lbl.animator_window.any_state")
        section(strings.getString("lbl.animator_window.transition", from, t.to))
        val siblings = asset.transitions.indices.filter { asset.transitions[it].from == t.from && asset.transitions[it].to == t.to }
        if (siblings.size > 1) {
            siblings.forEachIndexed { n, i ->
                val label = "${n + 1}"
                if (n > 0) flow(ImGui.getFrameHeight() + ImGui.getStyle().itemInnerSpacingX + ImGui.calcTextSize(label).x)
                if (ImGui.radioButton("$label###sib:$i", i == index)) controller.selectTransition(i)
            }
            Tooltips.always(strings.getString("lbl.animator_window.siblings.tooltip"))
        }

        section(strings.getString("lbl.animator_window.conditions"))
        val params = controller.parameters()
        t.conditions.forEachIndexed { ci, c -> conditionRow(index, ci, c, params) }
        if (t.conditions.isEmpty()) MImGui.textDisabled(strings.getString("lbl.animator_window.no_conditions"))
        if (strings.tipped(iconLabelButton("${Icons.PLUS} ${strings.getString("btn.animator_window.add_condition")}"), "btn.animator_window.add_condition")) controller.addCondition(index)

        section(strings.getString("lbl.animator_window.timing"))
        if (labeledCheckbox("lbl.animator_window.exit_time", t.hasExitTime)) controller.updateTransition(index) { it.copy(hasExitTime = !it.hasExitTime) }
        if (t.hasExitTime) {
            floatCommit("exit:$index", labeled("lbl.animator_window.exit_at"), t.exitTime * 100f, 0.5f, 0f, 500f, "%.0f %%")
                ?.let { v -> controller.updateTransition(index) { it.copy(exitTime = v / 100f) } }
            Tooltips.always(strings.getString("lbl.animator_window.exit_at.tooltip"))
        }
        floatCommit("blend:$index", labeled("lbl.animator_window.blend"), t.duration, 0.01f, 0f, 10f, "%.2f s")
            ?.let { v -> controller.updateTransition(index) { it.copy(duration = v) } }
        Tooltips.always(strings.getString("lbl.animator_window.blend.tooltip"))
        if (t.conditions.isEmpty() && !t.hasExitTime) wrapped { MImGui.warningText(strings.getString("lbl.animator_window.never_fires")) }

        if (t.from == null) {
            section(strings.getString("lbl.animator_window.except_from"))
            for (s in asset.states) {
                if (dataCheckbox("except:${s.name}", s.name, s.name in t.exceptFrom, "lbl.animator_window.except_from.tooltip")) {
                    controller.updateTransition(index) { it.copy(exceptFrom = if (s.name in it.exceptFrom) it.exceptFrom - s.name else it.exceptFrom + s.name) }
                }
            }
        }

        ImGui.spacing()
        buttonRow("btn.animator_window.delete_transition" to { controller.removeTransition(index) })
        ImGui.popID()
    }

    /** One condition: the parameter and Remove on the first line, then the test and the value (ANI-25: they wrap). */
    private fun conditionRow(index: Int, ci: Int, c: TransitionCondition, params: List<ParameterDef>) {
        ImGui.pushID("cond:$ci")
        if (ci > 0) ImGui.separator()
        val type = params.firstOrNull { it.name == c.parameter }?.type
        val row = trailing(listOf(iconButtonWidth()))
        if (ImGui.beginCombo("##param", c.parameter)) {
            for (p in params) if (ImGui.selectable("${p.name}###p:${p.name}", p.name == c.parameter)) controller.setCondition(index, ci, c.copy(parameter = p.name))
            ImGui.endCombo()
        }
        Tooltips.always(strings.getString("lbl.animator_window.condition_parameter.tooltip"))
        row.next()
        if (strings.tippedWith(iconButton(Icons.TRASH), "btn.animator_window.remove_condition.tooltip")) controller.removeCondition(index, ci)
        val numeric = c.op == ConditionOp.GREATER || c.op == ConditionOp.LESS || c.op == ConditionOp.EQUALS
        val avail = ImGui.getContentRegionAvailX()
        val valueWidth = em(AnimatorLayout.MIN_VALUE_EMS)
        val opWidth = if (numeric && avail - valueWidth - ImGui.getStyle().itemSpacingX >= em(TrailingButtons.MIN_FIELD_EMS)) {
            avail - valueWidth - ImGui.getStyle().itemSpacingX
        } else avail
        ImGui.setNextItemWidth(opWidth)
        if (ImGui.beginCombo("##op", opName(c.op))) {
            for (op in AnimatorWindowController.operatorsFor(type)) if (ImGui.selectable("${opName(op)}###op:${op.name}", op == c.op)) controller.setCondition(index, ci, c.copy(op = op))
            ImGui.endCombo()
        }
        Tooltips.always(strings.getString("lbl.animator_window.condition_op.tooltip"))
        if (numeric) {
            flow(valueWidth)
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX().coerceAtMost(valueWidth).coerceAtLeast(1f))
            floatCommit("threshold:$index:$ci", "##threshold", c.threshold, 0.05f, -1e6f, 1e6f, "%.2f")
                ?.let { controller.setCondition(index, ci, c.copy(threshold = it)) }
            Tooltips.always(strings.getString("lbl.animator_window.threshold.tooltip"))
        }
        if (type != null && c.op !in AnimatorWindowController.operatorsFor(type)) wrapped { MImGui.warningText(strings.getString("lbl.animator_window.op_mismatch")) }
        ImGui.popID()
    }

    // ---- wording ----------------------------------------------------------------------------------------------

    private fun typeName(t: ParameterType) = strings.getString("lbl.animator_window.type.${t.name.lowercase()}")
    private fun opName(op: ConditionOp) = strings.getString("lbl.animator_window.op.${op.name.lowercase()}")

    /** "walking → running when speed is greater than 2 (blend 0.20 s)" (ANI-21 §3.3). */
    private fun sentence(t: TransitionDef): String {
        val from = t.from ?: strings.getString("lbl.animator_window.any_state")
        val parts = t.conditions.map { c ->
            when (c.op) {
                ConditionOp.GREATER, ConditionOp.LESS, ConditionOp.EQUALS -> "${c.parameter} ${opName(c.op)} ${"%.2f".format(c.threshold)}"
                else -> "${c.parameter} ${opName(c.op)}"
            }
        } + listOfNotNull(if (t.hasExitTime) strings.getString("lbl.animator_window.at_exit", (t.exitTime * 100).toInt()) else null)
        val condition = if (parts.isEmpty()) strings.getString("lbl.animator_window.never") else parts.joinToString(strings.getString("lbl.animator_window.and"))
        return strings.getString("lbl.animator_window.sentence", from, t.to, condition, t.duration)
    }

    // ---- layout helpers (ANI-25: sized from the style, rows wrap instead of running off the panel) ---------------

    private fun em(n: Float) = ImGui.getFontSize() * n

    /** The right edge of the current panel's content, in screen pixels. */
    private fun rowRight() = ImGui.getWindowPosX() + ImGui.getWindowContentRegionMaxX()

    /**
     * Before an item [width] pixels wide that follows another one: on the same line when it fits, else on the next
     * (EDT-86's rule, [TrailingButtons.fitsOnLine]).
     */
    private fun flow(width: Float) {
        if (TrailingButtons.fitsOnLine(ImGui.getItemRectMaxX(), width, ImGui.getStyle().itemSpacingX, rowRight())) ImGui.sameLine()
    }

    private fun buttonWidth(label: String) = TrailingButtons.buttonWidth(ImGui.calcTextSize(label, true).x, ImGui.getStyle().framePaddingX)

    /**
     * EDT-86's [TrailingButtons] for buttons of known [widths] (our icon buttons are wider than their glyph): sets the
     * next field's width so they fit after it, or they wrap to the next line. [fieldLabel] is the field's visible label.
     */
    private fun trailing(widths: List<Float>, fieldLabel: String? = null, maxField: Float = Float.MAX_VALUE): TrailingButtons.Row {
        val st = ImGui.getStyle()
        val labelText = fieldLabel?.let { ImGui.calcTextSize(it, true).x } ?: 0f
        val labelWidth = if (labelText > 0f) labelText + st.itemInnerSpacingX else 0f
        val available = kotlin.math.floor(ImGui.getContentRegionAvailX())
        val layout = TrailingButtons.layout(available, widths.map { kotlin.math.ceil(it) }, kotlin.math.ceil(labelWidth), st.itemSpacingX, em(TrailingButtons.MIN_FIELD_EMS))
        val sized = layout.copy(fieldWidth = kotlin.math.floor(if (layout.wrapped) layout.fieldWidth else layout.fieldWidth.coerceAtMost(maxField)).coerceAtLeast(1f))
        ImGui.setNextItemWidth(sized.fieldWidth)
        return TrailingButtons.Row(widths, sized, ImGui.getCursorScreenPosX() + available)
    }

    /**
     * The label to give the next field for the string [key]: beside the field when both fit, else on its own (wrapped)
     * line above and the field takes the whole width. The ID stays `###key` either way.
     */
    private fun labeled(key: String): String {
        val text = strings.getString(key)
        val labelWidth = kotlin.math.ceil(ImGui.calcTextSize(text, true).x + ImGui.getStyle().itemInnerSpacingX)
        val avail = kotlin.math.floor(ImGui.getContentRegionAvailX())
        return if (avail - labelWidth >= em(TrailingButtons.MIN_FIELD_EMS)) {
            ImGui.setNextItemWidth(avail - labelWidth)
            "$text###$key"
        } else {
            textWrapped(text)
            ImGui.setNextItemWidth(avail.coerceAtLeast(1f))
            "###$key"
        }
    }

    /** [labeled] with buttons of [widths] after the field: returns their row and the field's label. */
    private fun labeledWithButtons(key: String, widths: List<Float>): Pair<TrailingButtons.Row, String> {
        val text = strings.getString(key)
        val st = ImGui.getStyle()
        val labelWidth = ImGui.calcTextSize(text, true).x + st.itemInnerSpacingX
        val buttons = widths.sumOf { (it + st.itemSpacingX).toDouble() }.toFloat()
        return if (ImGui.getContentRegionAvailX() - labelWidth - buttons >= em(TrailingButtons.MIN_FIELD_EMS)) {
            trailing(widths, text) to "$text###$key"
        } else {
            textWrapped(text)
            trailing(widths) to "###$key"
        }
    }

    /** A checkbox whose label (the string [key]) wraps next to the box; its tooltip is `[key].tooltip`. */
    private fun labeledCheckbox(key: String, value: Boolean): Boolean = dataCheckbox(key, strings.getString(key), value, "$key.tooltip")

    /** A checkbox with the label [text] (data or translated) wrapping next to the box. */
    private fun dataCheckbox(id: String, text: String, value: Boolean, tooltipKey: String): Boolean {
        val clicked = ImGui.checkbox("###$id", value)
        Tooltips.always(strings.getString(tooltipKey))
        ImGui.sameLine(0f, ImGui.getStyle().itemInnerSpacingX)
        textWrapped(text)
        Tooltips.always(strings.getString(tooltipKey))
        if (ImGui.isItemClicked()) return true
        return clicked
    }

    /** A section title: EDT's separator with text when it fits, else the wrapped text over a plain separator. */
    private fun section(title: String) {
        val needed = ImGui.calcTextSize(title).x + ImGui.getStyle().separatorTextPaddingX * 2f + em(1f)
        if (needed <= ImGui.getContentRegionAvailX()) MImGui.labeledSeparator(title)
        else { textWrapped(title); ImGui.separator() }
    }

    /**
     * Text items in [block] (coloured or not) wrap at the panel's edge. One pixel short of it: ImGui rounds a text's
     * width up, so a line wrapped exactly at the edge can end a pixel past it.
     */
    private inline fun wrapped(block: () -> Unit) {
        ImGui.pushTextWrapPos(ImGui.getWindowContentRegionMaxX() - 1f)
        block()
        ImGui.popTextWrapPos()
    }

    private fun textWrapped(text: String) = wrapped { ImGui.textUnformatted(text) }

    /** A full-width selectable row showing [text] wrapped onto as many lines as it needs. */
    private fun wrappedSelectable(id: String, text: String, selected: Boolean): Boolean {
        val width = kotlin.math.floor(ImGui.getContentRegionAvailX()).coerceAtLeast(1f)
        val x = ImGui.getCursorPosX()
        val y = ImGui.getCursorPosY()
        val height = ImGui.calcTextSize(text, false, width - 1f).y
        val clicked = ImGui.selectable(id, selected, ImGuiSelectableFlags.AllowOverlap, width, height)
        val after = ImGui.getCursorPosY()
        ImGui.setCursorPos(x, y)
        ImGui.pushTextWrapPos(x + width - 1f)
        ImGui.textUnformatted(text)
        ImGui.popTextWrapPos()
        ImGui.setCursorPosY(maxOf(after, ImGui.getCursorPosY()))
        return clicked
    }

    /** Translated buttons that wrap as a row; each is `strings.tipped` with its key. */
    private fun buttonRow(vararg buttons: Pair<String, () -> Unit>) {
        buttons.forEachIndexed { i, (key, action) ->
            val label = fit(strings.getString(key))
            if (i > 0) flow(buttonWidth(label))
            if (strings.tipped(ImGui.button(label), key)) action()
        }
    }

    /**
     * An icon-only button sized like the header's `?` (EDT-67/76): at least square and tall enough for the icon glyph
     * at any font scale or frame padding. `smallButton` has no vertical padding and cut the icon off (ANI-24).
     */
    private fun iconButton(icon: String): Boolean {
        val size = WindowHeader.buttonSize()
        return ImGui.button(icon, size.width, size.height)
    }

    private fun iconButtonWidth() = WindowHeader.buttonSize().width

    /**
     * A text button (with an icon) at least as tall as [iconButton], so rows of mixed buttons line up and nothing is
     * cut; a label too wide for the panel goes on several lines ([fit]).
     */
    private fun iconLabelButton(label: String): Boolean {
        val text = fit(label)
        val height = maxOf(WindowHeader.buttonSize().height, ImGui.calcTextSize(text, true).y + ImGui.getStyle().framePaddingY * 2f)
        return ImGui.button(text, 0f, height)
    }

    /** The panel's content width (a full line). */
    private fun fullWidth() = ImGui.getWindowContentRegionMaxX() - ImGui.getWindowContentRegionMinX()

    /** [label] broken onto several lines when the button would be wider than the panel ([AnimatorLayout.wrapWords]). */
    private fun fit(label: String): String =
        AnimatorLayout.wrapWords(label, fullWidth() - ImGui.getStyle().framePaddingX * 2f) { ImGui.calcTextSize(it, true).x }

    // ---- commit-on-release fields ----------------------------------------------------------------------------

    /** A text field showing [current] (its width set before); returns the new text once, when the edit is confirmed. */
    private fun textCommit(key: String, label: String, current: String): String? {
        val buf = texts.getOrPut(key) { ImString(current, 256) }
        if (activeEdit != key && buf.get() != current) buf.set(current)
        ImGui.inputText(label, buf)
        if (ImGui.isItemActivated()) activeEdit = key
        val done = ImGui.isItemDeactivatedAfterEdit()
        if (ImGui.isItemDeactivated() && activeEdit == key) activeEdit = null
        return if (done && buf.get() != current) buf.get() else null
    }

    /** A drag field showing [current] (its width set before); returns the value once, on release or Enter. */
    private fun floatCommit(key: String, label: String, current: Float, speed: Float, min: Float, max: Float, format: String): Float? {
        val buf = numbers.getOrPut(key) { floatArrayOf(current) }
        if (activeEdit != key) buf[0] = current
        ImGui.dragFloat(label, buf, speed, min, max, format)
        if (ImGui.isItemActivated()) activeEdit = key
        val done = ImGui.isItemDeactivatedAfterEdit()
        if (ImGui.isItemDeactivated() && activeEdit == key) activeEdit = null
        return if (done && buf[0] != current) buf[0] else null
    }

    /**
     * Test hook (ANI-25): at the end of each frame, how far each panel's laid-out content reaches past its visible
     * width (`ImGui.getScrollMaxX`, pixels; 0 when everything fits). Nothing scrolls sideways, so more is unreachable.
     */
    internal var layoutProbe: ((panel: String, overflowX: Float) -> Unit)? = null

    private fun probe(panel: String) { layoutProbe?.invoke(panel, ImGui.getScrollMaxX()) }

    internal companion object {
        const val PANEL_WINDOW = "window"
        const val PANEL_LEFT = "left"
        const val PANEL_INSPECTOR = "inspector"
        const val FLASH_SECONDS = 0.6f
    }
}
