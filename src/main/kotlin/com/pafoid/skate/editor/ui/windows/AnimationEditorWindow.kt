package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.app.Editor
import com.pafoid.skate.editor.animation.clipeditor.AnimationEditorController
import com.pafoid.skate.editor.animation.clipeditor.AnimationEditorWiring
import com.pafoid.skate.editor.animation.clipeditor.CurveViewport
import com.pafoid.skate.editor.animation.clipeditor.SmoothFilter
import com.pafoid.skate.editor.animation.clipeditor.SkeletonPreview
import com.pafoid.skate.editor.animation.clipeditor.SkinnedPosePreview
import com.pafoid.skate.editor.animation.clipeditor.TimelineView
import com.pafoid.skate.editor.animation.mocap.TargetRig
import com.pafoid.skate.editor.imgui.EditorWindow
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.imgui.Tooltips
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.label
import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.editor.imgui.tippedWith
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.animation.edit.CurveEditing
import com.pafoid.skate.engine.animation.edit.CurveTangent
import com.pafoid.skate.engine.animation.edit.EulerCurves
import com.pafoid.skate.engine.animation.edit.HandleSide
import com.pafoid.skate.engine.animation.edit.KeyRef
import com.pafoid.skate.engine.animation.edit.ReductionTolerance
import com.pafoid.skate.engine.animation.edit.SkClip
import com.pafoid.skate.engine.animation.edit.SkTrack
import com.pafoid.skate.engine.animation.edit.TrackCurveView
import com.pafoid.skate.engine.assets.data.models.animations.AnimationPath
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.getComponent
import imgui.ImGui
import imgui.flag.ImGuiButtonFlags
import imgui.flag.ImGuiKey
import imgui.flag.ImGuiMouseButton
import imgui.flag.ImGuiTabItemFlags
import imgui.flag.ImGuiTreeNodeFlags
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImFloat
import imgui.type.ImInt
import imgui.type.ImString
import org.joml.Quaternionf
import org.joml.Vector3f
import org.lwjgl.util.tinyfd.TinyFileDialogs
import java.io.File
import kotlin.math.abs

/**
 * Animation editor (ANI-14, ANI-12 §2.1): opens a `.skanim` clip; timeline with scrubbing and playback, dope sheet
 * (select, box-select, drag, copy/paste, delete, key selected bones), curve editor with tangent modes (ANI-15), events
 * track, key and clip inspector (value, time, retime, trim, fps, loop), cleanup tools for mocap (resample, reduce,
 * smooth, foot lock, mirror, trim to range, loop seam; ANI-15) and a skeleton preview on the skater or the selected
 * rig. Every edit is undoable.
 * All state and actions live in [AnimationEditorController].
 */
class AnimationEditorWindow(
    private val engine: Engine,
    editor: Editor,
    private val projectManager: ProjectManager,
    private val controller: AnimationEditorController = AnimationEditorWiring.create(engine, editor),
) : EditorWindow("window.animation_editor") {

    override val icon: String get() = Icons.FILM
    override val relatedWindows: List<String> get() = listOf("window.video_to_animation")

    private val strings = engine.stringManager
    private val timeline = TimelineView()
    private val preview = SkeletonPreview()
    /** The pose on the character's skinned mesh, rendered offscreen under the bone overlay (REN-33). */
    private val skinned = SkinnedPosePreview(
        renderer = { runCatching { engine.renderer.renderResources.renderers.skinnedPreview }.getOrNull() },
        modelFor = ::previewModel,
    )
    private var framedFor: Any? = null
    private var fitFor: Any? = null
    private val filter = ImString(128)
    private val eventName = ImString(128)
    private val scaleFactor = ImFloat(1f)
    private val durationField = ImFloat(1f)
    private val fpsField = ImFloat(30f)
    private val valueField = FloatArray(3)
    private val keyFrameField = ImInt(0)
    private var editedKey: KeyRef? = null
    private var boxStart: FloatArray? = null
    private var keyDragStartX = 0f
    private var eventDragging = false
    private var scrubbing = false
    private var pressAt: FloatArray? = null

    // Curve editor (ANI-15).
    /** Tab to bring to front on the next frame: true for Curves, false for the dope sheet. */
    private var requestedTab: Boolean? = null
    private val curveView = CurveViewport()
    private var curveFitFor: Any? = null
    private val showComponent = booleanArrayOf(true, true, true)
    private var curveCache: Pair<List<Any>, TrackCurveView>? = null
    private var curveDragStart: FloatArray? = null
    /** Key time and shown value of the key whose handle is dragged. */
    private var handleAnchor: FloatArray? = null
    private var curveBox: FloatArray? = null

    // Cleanup tools (ANI-15).
    private val resampleFps = ImFloat(30f)
    private val reduceDeg = ImFloat(0.25f)
    private val reduceMm = ImFloat(1f)
    private var smoothFilter = SmoothFilter.GAUSSIAN
    private val smoothStrength = ImFloat(2f)
    private val footBlend = ImFloat(3f)
    private val seamFrames = ImFloat(5f)

    /** Brings the Curves tab (or the dope sheet) to front. */
    fun showCurves(curves: Boolean = true) { requestedTab = curves }

    override fun imgui() {
        if (beginWindow(strings, ImGuiWindowFlags.NoScrollbar)) {
            controller.refresh()
            controller.tick(ImGui.getIO().deltaTime)
            // EDT-101: the toolbar is the header's tools row, with Maximize and `?` at its right end.
            header(strings) { toolbar() }
            controller.error?.let { MImGui.errorText(strings.getString("lbl.animation_editor.error", it)) }
            val clip = controller.clip
            if (clip == null) {
                MImGui.textDisabled(strings.getString("lbl.animation_editor.no_clip"))
            } else {
                shortcuts()
                val avail = ImGui.getContentRegionAvailY()
                val previewH = (avail * 0.4f).coerceIn(140f, 360f)
                val inspectorW = 260f
                if (ImGui.beginChild("##anim_sheet", ImGui.getContentRegionAvailX() - inspectorW - 8f, avail - previewH - 8f, false, ImGuiWindowFlags.NoScrollbar)) {
                    if (ImGui.beginTabBar("##anim_sheet_tabs")) {
                        val pick = requestedTab
                        requestedTab = null
                        val dopeFlags = if (pick == false) ImGuiTabItemFlags.SetSelected else 0
                        val curveFlags = if (pick == true) ImGuiTabItemFlags.SetSelected else 0
                        if (ImGui.beginTabItem(strings.label("lbl.animation_editor.tab_dope"), dopeFlags)) { dopeSheet(clip); ImGui.endTabItem() }
                        if (ImGui.beginTabItem(strings.label("lbl.animation_editor.tab_curves"), curveFlags)) { curveEditor(clip); ImGui.endTabItem() }
                        ImGui.endTabBar()
                    }
                }
                ImGui.endChild()
                ImGui.sameLine()
                if (ImGui.beginChild("##anim_inspector", inspectorW, avail - previewH - 8f, true)) inspector(clip)
                ImGui.endChild()
                if (ImGui.beginChild("##anim_preview", 0f, 0f, true, ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoScrollWithMouse)) previewArea()
                ImGui.endChild()
            }
        }
        ImGui.end()
    }

    // ---- toolbar ---------------------------------------------------------------------------------------------

    private fun toolbar() {
        val clip = controller.clip
        val doc = controller.document
        val current = doc?.let { (if (it.dirty) "* " else "") + it.file.name } ?: strings.getString("lbl.animation_editor.choose_clip")
        ImGui.setNextItemWidth(200f)
        if (ImGui.beginCombo(strings.label("lbl.animation_editor.clip"), current)) {
            val clips = projectClips()
            if (clips.isEmpty()) MImGui.textDisabled(strings.getString("lbl.animation_editor.no_project_clips"))
            for (f in clips) if (ImGui.selectable(f.name, f.absolutePath == doc?.file?.path)) controller.open(f)
            ImGui.endCombo()
        }
        Tooltips.always(strings.getString("lbl.animation_editor.clip.tooltip"))
        ImGui.sameLine()
        if (strings.tippedWith(ImGui.button(Icons.FOLDER_OPEN), "btn.animation_editor.open")) pickClip()?.let { controller.open(it) }
        if (clip == null) return
        ImGui.sameLine()
        ImGui.beginDisabled(doc?.dirty != true)
        if (strings.tippedWith(ImGui.button(Icons.SAVE), "btn.animation_editor.save")) controller.save()
        ImGui.endDisabled()

        ImGui.sameLine(); ImGui.text("|"); ImGui.sameLine()
        if (strings.tippedWith(ImGui.button("${Icons.ARROW_LEFT}${Icons.ARROW_LEFT}"), "btn.animation_editor.to_start")) controller.toStart()
        ImGui.sameLine()
        if (strings.tippedWith(ImGui.button(Icons.ARROW_LEFT), "btn.animation_editor.prev_frame")) controller.stepFrames(-1)
        ImGui.sameLine()
        if (strings.tippedWith(ImGui.button(if (controller.playing) Icons.PAUSE else Icons.PLAY), "btn.animation_editor.play")) controller.togglePlay()
        ImGui.sameLine()
        if (strings.tippedWith(ImGui.button(Icons.ARROW_RIGHT), "btn.animation_editor.next_frame")) controller.stepFrames(1)
        ImGui.sameLine()
        if (strings.tippedWith(ImGui.button("${Icons.ARROW_RIGHT}${Icons.ARROW_RIGHT}"), "btn.animation_editor.to_end")) controller.toEnd()
        ImGui.sameLine()
        ImGui.text(strings.getString("lbl.animation_editor.frame", Math.round(controller.time * clip.fps), Math.round(clip.duration * clip.fps)))

        ImGui.sameLine()
        if (ImGui.checkbox(strings.label("lbl.animation_editor.loop"), clip.loop)) controller.setLoop(!clip.loop)
        Tooltips.always(strings.getString("lbl.animation_editor.loop.tooltip"))
        ImGui.sameLine()
        if (ImGui.checkbox(strings.label("lbl.animation_editor.snap"), controller.snap)) controller.snap = !controller.snap
        Tooltips.always(strings.getString("lbl.animation_editor.snap.tooltip"))
        ImGui.sameLine()
        ImGui.setNextItemWidth(90f)
        val speed = floatArrayOf(controller.speed)
        if (ImGui.sliderFloat(strings.label("lbl.animation_editor.speed"), speed, 0.1f, 2f, "%.2fx")) controller.speed = speed[0]
        Tooltips.always(strings.getString("lbl.animation_editor.speed.tooltip"))
        ImGui.sameLine()
        ImGui.setNextItemWidth(130f)
        val rigs = TargetRig.entries
        val rigKeys = mapOf(TargetRig.SKATER to "lbl.mocap.target.skater", TargetRig.SELECTED to "lbl.mocap.target.selected")
        if (ImGui.beginCombo(strings.label("lbl.animation_editor.rig"), strings.getString(rigKeys.getValue(controller.rig)))) {
            for (r in rigs) if (ImGui.selectable(strings.getString(rigKeys.getValue(r)), r == controller.rig)) controller.rig = r
            ImGui.endCombo()
        }
        Tooltips.always(strings.getString("lbl.animation_editor.rig.tooltip"))
    }

    // ---- dope sheet ------------------------------------------------------------------------------------------

    private data class Row(val bone: String, val path: AnimationPath, val firstOfBone: Boolean)

    private fun rows(clip: SkClip): List<Row> {
        val f = filter.get().trim()
        val tracks = clip.tracks.filter { f.isEmpty() || it.bone.contains(f, ignoreCase = true) }
        var last: String? = null
        return tracks.map { Row(it.bone, it.path, it.bone != last).also { _ -> last = it.bone } }
    }

    private fun dopeSheet(clip: SkClip) {
        ImGui.setNextItemWidth(NAME_W - 8f)
        ImGui.inputTextWithHint("##anim_filter", strings.getString("lbl.animation_editor.filter_hint"), filter)
        ImGui.sameLine()
        if (strings.tipped(ImGui.button(strings.getString("btn.animation_editor.key_bones")), "btn.animation_editor.key_bones")) controller.keySelectedBones()
        ImGui.sameLine()
        if (strings.tippedWith(ImGui.button(Icons.EXPAND), "btn.animation_editor.fit")) fitFor = null

        val draw = ImGui.getWindowDrawList()
        val x0 = ImGui.getCursorScreenPosX()
        val y0 = ImGui.getCursorScreenPosY()
        val width = ImGui.getContentRegionAvailX()
        val keysX = x0 + NAME_W
        val keysW = (width - NAME_W).coerceAtLeast(50f)
        if (fitFor !== controller.document) { timeline.fit(clip.duration, keysW); fitFor = controller.document }
        val mouseX = ImGui.getMousePosX()
        val mouseY = ImGui.getMousePosY()
        val ctrl = ImGui.getIO().keyCtrl
        val shift = ImGui.getIO().keyShift

        ruler(clip, keysX, y0, keysW)
        val endX = timeline.timeToX(clip.duration, keysX)

        // Events row.
        val evY = y0 + RULER_H
        draw.addRectFilled(x0, evY, x0 + width, evY + ROW_H, ImGui.getColorU32(0.13f, 0.13f, 0.15f, 1f))
        draw.addText(x0 + 4f, evY + 2f, ImGui.getColorU32(0.85f, 0.85f, 0.6f, 1f), strings.getString("lbl.animation_editor.events"))
        ImGui.setCursorScreenPos(keysX, evY)
        ImGui.invisibleButton("##anim_events", keysW, ROW_H)
        val eventsHovered = ImGui.isItemHovered()
        var hitEvent = -1
        clip.events.forEachIndexed { i, e ->
            val x = timeline.timeToX(e.time, keysX)
            val selected = controller.selectedEvent == i
            val c = if (selected) ImGui.getColorU32(1f, 0.8f, 0.2f, 1f) else ImGui.getColorU32(0.9f, 0.6f, 0.2f, 1f)
            draw.addTriangleFilled(x - 5f, evY + 3f, x + 5f, evY + 3f, x, evY + ROW_H - 3f, c)
            draw.addText(x + 6f, evY + 2f, ImGui.getColorU32(0.8f, 0.8f, 0.8f, 1f), e.name)
            if (eventsHovered && abs(mouseX - x) <= 6f) hitEvent = i
        }
        if (hitEvent >= 0 && eventsHovered) Tooltips.always(clip.events[hitEvent].name)
        if (ImGui.isItemActivated()) {
            if (hitEvent >= 0) { controller.beginEventDrag(hitEvent); eventDragging = true } else controller.selectEvent(null)
        }
        if (eventDragging) {
            if (ImGui.isMouseDown(ImGuiMouseButton.Left)) controller.dragEventTo(timeline.xToTime(mouseX, keysX))
            else { controller.endDrag(); eventDragging = false }
        }

        // Track rows in a scrolling child.
        val tracksY = evY + ROW_H
        ImGui.setCursorScreenPos(x0, tracksY)
        val rows = rows(clip)
        if (ImGui.beginChild("##anim_tracks", width, 0f, false)) {
            val dl = ImGui.getWindowDrawList()
            val top = ImGui.getCursorScreenPosY()
            val visibleTop = ImGui.getWindowPosY()
            val visibleBottom = visibleTop + ImGui.getWindowHeight()
            ImGui.invisibleButton("##anim_rows", width, (rows.size * ROW_H).coerceAtLeast(ROW_H))
            val hovered = ImGui.isItemHovered()
            var hitKey: KeyRef? = null
            var hitRow = -1
            rows.forEachIndexed { r, row ->
                val y = top + r * ROW_H
                if (y + ROW_H < visibleTop || y > visibleBottom) return@forEachIndexed
                if (r % 2 == 0) dl.addRectFilled(x0, y, x0 + width, y + ROW_H, ImGui.getColorU32(1f, 1f, 1f, 0.03f))
                val boneSelected = row.bone in controller.selectedBones
                val nameColor = if (boneSelected) ImGui.getColorU32(0.4f, 0.8f, 1f, 1f) else ImGui.getColorU32(0.85f, 0.85f, 0.85f, 1f)
                if (row.firstOfBone) dl.addText(x0 + 4f, y + 2f, nameColor, row.bone)
                dl.addText(x0 + NAME_W - 16f, y + 2f, ImGui.getColorU32(0.6f, 0.6f, 0.6f, 1f), PATH_LETTER.getValue(row.path))
                if (hovered && mouseY >= y && mouseY < y + ROW_H) hitRow = r
                val track = clip.track(row.bone, row.path) ?: return@forEachIndexed
                for (k in track.keys) {
                    val x = timeline.timeToX(k.time, keysX)
                    if (x < keysX - 6f || x > keysX + keysW + 6f) continue
                    val ref = KeyRef(row.bone, row.path, k.time)
                    val sel = ref in controller.selection
                    val c = if (sel) ImGui.getColorU32(1f, 0.85f, 0.25f, 1f) else ImGui.getColorU32(0.75f, 0.75f, 0.8f, 1f)
                    val cy = y + ROW_H / 2f
                    dl.addQuadFilled(x, cy - 5f, x + 5f, cy, x, cy + 5f, x - 5f, cy, c)
                    if (hovered && abs(mouseX - x) <= 6f && abs(mouseY - cy) <= ROW_H / 2f) hitKey = ref
                }
            }
            // Clip end and playhead over the rows.
            val bottom = top + rows.size * ROW_H
            dl.addLine(endX, top, endX, bottom, ImGui.getColorU32(0.5f, 0.5f, 0.5f, 1f), 1f)
            interactRows(clip, rows, hovered, hitKey, hitRow, top, keysX, mouseX, mouseY, ctrl, shift)
            boxStart?.let { b -> dl.addRect(b[0], b[1], mouseX, mouseY, ImGui.getColorU32(0.4f, 0.7f, 1f, 1f)) }
            if (hovered && ctrl && ImGui.getIO().mouseWheel != 0f) {
                timeline.zoom(if (ImGui.getIO().mouseWheel > 0f) 1.2f else 1f / 1.2f, mouseX, keysX)
            } else if (hovered && shift && ImGui.getIO().mouseWheel != 0f) {
                timeline.scroll -= ImGui.getIO().mouseWheel * 40f / timeline.pxPerSecond
            }
            timeline.clampScroll(clip.duration, keysW)
        }
        ImGui.endChild()
        // Playhead over ruler, events and rows.
        val px = timeline.timeToX(controller.time, keysX)
        if (px >= keysX && px <= keysX + keysW) {
            draw.addLine(px, y0, px, y0 + ImGui.getWindowHeight(), ImGui.getColorU32(1f, 0.3f, 0.3f, 1f), 1.5f)
        }
    }

    /** Frame ruler with the cleanup tools' range; click or drag to scrub. */
    private fun ruler(clip: SkClip, keysX: Float, y0: Float, keysW: Float) {
        val draw = ImGui.getWindowDrawList()
        draw.addRectFilled(keysX, y0, keysX + keysW, y0 + RULER_H, ImGui.getColorU32(0.16f, 0.16f, 0.18f, 1f))
        controller.range?.let { r ->
            val a = timeline.timeToX(r.start, keysX).coerceIn(keysX, keysX + keysW)
            val b = timeline.timeToX(r.endInclusive, keysX).coerceIn(keysX, keysX + keysW)
            draw.addRectFilled(a, y0, b, y0 + RULER_H, ImGui.getColorU32(0.3f, 0.55f, 0.9f, 0.35f))
        }
        for (frame in timeline.rulerFrames(clip.fps, keysW)) {
            val x = timeline.timeToX(frame / clip.fps, keysX)
            draw.addLine(x, y0 + RULER_H - 6f, x, y0 + RULER_H, ImGui.getColorU32(0.6f, 0.6f, 0.6f, 1f))
            draw.addText(x + 2f, y0 + 1f, ImGui.getColorU32(0.75f, 0.75f, 0.75f, 1f), frame.toString())
        }
        ImGui.setCursorScreenPos(keysX, y0)
        ImGui.invisibleButton("##anim_ruler", keysW, RULER_H)
        if (ImGui.isItemActivated()) scrubbing = true
        if (scrubbing && ImGui.isMouseDown(ImGuiMouseButton.Left)) controller.seek(timeline.xToTime(ImGui.getMousePosX(), keysX)) else scrubbing = false
    }

    private fun interactRows(
        clip: SkClip, rows: List<Row>, hovered: Boolean, hitKey: KeyRef?, hitRow: Int, top: Float, keysX: Float,
        mouseX: Float, mouseY: Float, ctrl: Boolean, shift: Boolean,
    ) {
        if (ImGui.isItemActivated()) {
            when {
                hitKey != null -> {
                    if (ctrl || shift) controller.selectKey(hitKey, additive = true)
                    else if (hitKey !in controller.selection) controller.selectKey(hitKey)
                    controller.beginKeyDrag()
                    keyDragStartX = mouseX
                }
                hitRow >= 0 && mouseX < keysX -> controller.selectBone(rows[hitRow].bone, additive = ctrl)
                else -> boxStart = floatArrayOf(mouseX, mouseY)
            }
        }
        if (controller.dragging && !eventDragging) {
            if (ImGui.isMouseDown(ImGuiMouseButton.Left)) controller.dragKeys((mouseX - keyDragStartX) / timeline.pxPerSecond)
            else controller.endDrag()
        }
        val box = boxStart
        if (box != null && !ImGui.isMouseDown(ImGuiMouseButton.Left)) {
            boxStart = null
            if (abs(mouseX - box[0]) < 3f && abs(mouseY - box[1]) < 3f) {
                // A click on empty sheet: clear the selection and move the playhead there.
                if (!ctrl) controller.clearSelection()
                controller.seek(timeline.xToTime(mouseX, keysX))
            } else {
                val r0 = ((minOf(box[1], mouseY) - top) / ROW_H).toInt().coerceAtLeast(0)
                val r1 = ((maxOf(box[1], mouseY) - top) / ROW_H).toInt().coerceAtMost(rows.size - 1)
                val tracks = if (r0 <= r1) rows.subList(r0, r1 + 1).map { it.bone to it.path } else emptyList()
                controller.selectBox(tracks, timeline.xToTime(box[0], keysX), timeline.xToTime(mouseX, keysX), additive = ctrl)
            }
        }
        if (hovered && hitKey != null) Tooltips.always(strings.getString("lbl.animation_editor.key_tooltip", hitKey.bone, PATH_LETTER.getValue(hitKey.path), Math.round(hitKey.time * clip.fps)))
    }

    private fun shortcuts() {
        if (!ImGui.isWindowFocused(imgui.flag.ImGuiFocusedFlags.RootAndChildWindows) || ImGui.getIO().wantTextInput) return
        val ctrl = ImGui.getIO().keyCtrl
        when {
            ImGui.isKeyPressed(ImGuiKey.Delete) -> if (controller.selectedEvent != null) controller.deleteEvent(controller.selectedEvent!!) else controller.deleteSelectedKeys()
            ctrl && ImGui.isKeyPressed(ImGuiKey.C) -> controller.copySelection()
            ctrl && ImGui.isKeyPressed(ImGuiKey.X) -> controller.cutSelection()
            ctrl && ImGui.isKeyPressed(ImGuiKey.V) -> controller.paste()
            ctrl && ImGui.isKeyPressed(ImGuiKey.A) -> controller.selectAllKeys()
            ImGui.isKeyPressed(ImGuiKey.K) -> controller.keySelectedBones()
            ImGui.isKeyPressed(ImGuiKey.Space) -> controller.togglePlay()
            ImGui.isKeyPressed(ImGuiKey.LeftArrow) -> controller.stepFrames(-1)
            ImGui.isKeyPressed(ImGuiKey.RightArrow) -> controller.stepFrames(1)
            ImGui.isKeyPressed(ImGuiKey.Home) -> controller.toStart()
            ImGui.isKeyPressed(ImGuiKey.End) -> controller.toEnd()
            ImGui.isKeyPressed(ImGuiKey.Escape) -> { controller.cancelDrag(); boxStart = null }
        }
    }

    // ---- inspector -------------------------------------------------------------------------------------------

    private fun inspector(clip: SkClip) {
        val key = controller.singleKey
        if (key != null) keyInspector(clip, key)
        else if (controller.selection.size > 1) {
            ImGui.text(strings.getString("lbl.animation_editor.keys_selected", controller.selection.size))
            ImGui.setNextItemWidth(100f)
            ImGui.inputFloat(strings.label("lbl.animation_editor.scale_factor"), scaleFactor, 0.1f, 0.5f, "%.2f")
            Tooltips.always(strings.getString("lbl.animation_editor.scale_factor.tooltip"))
            ImGui.sameLine()
            if (strings.tipped(ImGui.button(strings.getString("btn.animation_editor.scale")), "btn.animation_editor.scale")) controller.scaleSelection(scaleFactor.get())
        }
        if (controller.selection.isNotEmpty()) {
            if (strings.tippedWith(ImGui.button(Icons.COPY), "btn.animation_editor.copy")) controller.copySelection()
            ImGui.sameLine()
            if (strings.tippedWith(ImGui.button(Icons.TRASH), "btn.animation_editor.delete_keys")) controller.deleteSelectedKeys()
            ImGui.sameLine()
        }
        ImGui.beginDisabled(!controller.canPaste)
        if (strings.tippedWith(ImGui.button(Icons.PASTE), "btn.animation_editor.paste")) controller.paste()
        ImGui.endDisabled()
        ImGui.separator()
        eventInspector(clip)
        ImGui.separator()
        clipInspector(clip)
        cleanupTools(clip)
    }

    private fun keyInspector(clip: SkClip, ref: KeyRef) {
        val track = clip.track(ref.bone, ref.path) ?: return
        val k = track.keys.firstOrNull { abs(it.time - ref.time) < 1e-4f } ?: return
        if (editedKey != ref || !ImGui.isAnyItemActive()) {
            editedKey = ref
            keyFrameField.set(Math.round(ref.time * clip.fps))
            if (ref.path == AnimationPath.ROTATION) {
                val e = EulerCurves.toEulerDeg(Quaternionf(k.value[0], k.value[1], k.value[2], k.value[3]))
                valueField[0] = e.x; valueField[1] = e.y; valueField[2] = e.z
            } else for (i in 0 until 3) valueField[i] = k.value[i]
        }
        ImGui.text("${ref.bone} ${PATH_LETTER.getValue(ref.path)}")
        ImGui.setNextItemWidth(120f)
        ImGui.inputInt(strings.label("lbl.animation_editor.key_frame"), keyFrameField)
        Tooltips.always(strings.getString("lbl.animation_editor.key_frame.tooltip"))
        if (ImGui.isItemDeactivatedAfterEdit()) controller.setKeyTime(ref, keyFrameField.get() / clip.fps)
        ImGui.setNextItemWidth(-1f)
        val valueKey = if (ref.path == AnimationPath.ROTATION) "lbl.animation_editor.key_rotation" else "lbl.animation_editor.key_value"
        ImGui.dragFloat3(strings.label(valueKey), valueField, if (ref.path == AnimationPath.ROTATION) 0.5f else 0.01f)
        Tooltips.always(strings.getString("$valueKey.tooltip"))
        if (ImGui.isItemDeactivatedAfterEdit()) {
            val v = if (ref.path == AnimationPath.ROTATION) {
                val q = EulerCurves.toQuat(Vector3f(valueField[0], valueField[1], valueField[2]))
                floatArrayOf(q.x, q.y, q.z, q.w)
            } else valueField.copyOf()
            controller.setKeyValue(ref, v)
        }
    }

    private fun eventInspector(clip: SkClip) {
        val selected = controller.selectedEvent?.let { clip.events.getOrNull(it) }
        ImGui.setNextItemWidth(140f)
        ImGui.inputTextWithHint("##anim_event_name", strings.getString("lbl.animation_editor.event_hint"), eventName)
        ImGui.sameLine()
        if (strings.tipped(ImGui.button(strings.getString("btn.animation_editor.add_event")), "btn.animation_editor.add_event")) {
            controller.addEvent(eventName.get())
        }
        if (selected != null) {
            val index = controller.selectedEvent!!
            ImGui.text(strings.getString("lbl.animation_editor.event_at", selected.name, Math.round(selected.time * clip.fps)))
            if (strings.tipped(ImGui.button(strings.getString("btn.animation_editor.rename_event")), "btn.animation_editor.rename_event")) controller.renameEvent(index, eventName.get())
            ImGui.sameLine()
            if (strings.tippedWith(ImGui.button(Icons.TRASH), "btn.animation_editor.delete_event")) controller.deleteEvent(index)
        }
    }

    private fun clipInspector(clip: SkClip) {
        if (!ImGui.isAnyItemActive()) { durationField.set(clip.duration); fpsField.set(clip.fps) }
        ImGui.setNextItemWidth(100f)
        ImGui.inputFloat(strings.label("lbl.animation_editor.duration"), durationField, 0.1f, 1f, "%.3f")
        Tooltips.always(strings.getString("lbl.animation_editor.duration.tooltip"))
        if (strings.tipped(ImGui.button(strings.getString("btn.animation_editor.retime")), "btn.animation_editor.retime")) controller.retimeClip(durationField.get())
        ImGui.sameLine()
        if (strings.tipped(ImGui.button(strings.getString("btn.animation_editor.trim")), "btn.animation_editor.trim")) controller.setDuration(durationField.get())
        ImGui.setNextItemWidth(100f)
        ImGui.inputFloat(strings.label("lbl.animation_editor.fps"), fpsField, 1f, 10f, "%.0f")
        Tooltips.always(strings.getString("lbl.animation_editor.fps.tooltip"))
        if (ImGui.isItemDeactivatedAfterEdit()) controller.setFps(fpsField.get())
    }

    // ---- curve editor (ANI-15) -------------------------------------------------------------------------------

    private fun curveEditor(clip: SkClip) {
        val track = controller.shownCurveTrack()
        curveToolbar(track)
        val draw = ImGui.getWindowDrawList()
        val x0 = ImGui.getCursorScreenPosX()
        val y0 = ImGui.getCursorScreenPosY()
        val width = ImGui.getContentRegionAvailX()
        val height = ImGui.getContentRegionAvailY()
        val keysX = x0 + AXIS_W
        val keysW = (width - AXIS_W).coerceAtLeast(50f)
        if (fitFor !== controller.document) { timeline.fit(clip.duration, keysW); fitFor = controller.document }
        ruler(clip, keysX, y0, keysW)

        val top = y0 + RULER_H
        val plotH = (height - RULER_H).coerceAtLeast(40f)
        ImGui.setCursorScreenPos(x0, top)
        ImGui.invisibleButton("##anim_curves", width.coerceAtLeast(1f), plotH, ImGuiButtonFlags.MouseButtonLeft or ImGuiButtonFlags.MouseButtonMiddle)
        val hovered = ImGui.isItemHovered()
        draw.pushClipRect(x0, top, x0 + width, top + plotH, true)
        draw.addRectFilled(x0, top, x0 + width, top + plotH, ImGui.getColorU32(0.11f, 0.11f, 0.13f, 1f))
        if (track == null) {
            draw.addText(keysX + 8f, top + 8f, ImGui.getColorU32(0.6f, 0.6f, 0.6f, 1f), strings.getString("lbl.animation_editor.no_curve_track"))
            draw.popClipRect()
            return
        }
        val visible = (0 until CurveEditing.COMPONENTS).filter { showComponent[it] }.toSet()
        val view = curveData(track, timeline.xToTime(keysX, keysX), timeline.xToTime(keysX + keysW, keysX), (keysW / 3f).toInt())
        val trackId = Triple(controller.document, track.bone, track.path)
        if (curveFitFor != trackId) { frameCurves(view, visible, plotH); curveFitFor = trackId }

        // Value grid, labelled in the left gutter.
        for (v in curveView.gridValues(plotH)) {
            val y = curveView.valueToY(v, top, plotH)
            draw.addLine(keysX, y, x0 + width, y, ImGui.getColorU32(1f, 1f, 1f, if (v == 0f) 0.18f else 0.06f))
            draw.addText(x0 + 2f, y - 7f, ImGui.getColorU32(0.6f, 0.6f, 0.6f, 1f), "%.${decimals(v)}f".format(v))
        }
        val endX = timeline.timeToX(clip.duration, keysX)
        draw.addLine(endX, top, endX, top + plotH, ImGui.getColorU32(0.5f, 0.5f, 0.5f, 1f))
        controller.range?.let { r ->
            draw.addRectFilled(timeline.timeToX(r.start, keysX), top, timeline.timeToX(r.endInclusive, keysX), top + plotH, ImGui.getColorU32(0.3f, 0.55f, 0.9f, 0.08f))
        }

        // Curves, keys and (cubic translation/scale tracks) the handles of the selected keys.
        for (c in visible) {
            val color = AXIS_COLORS[c]
            val xs = view.sampleTimes; val ys = view.sampleValues[c]
            for (j in 1 until xs.size) {
                draw.addLine(timeline.timeToX(xs[j - 1], keysX), curveView.valueToY(ys[j - 1], top, plotH),
                    timeline.timeToX(xs[j], keysX), curveView.valueToY(ys[j], top, plotH), ImGui.getColorU32(color[0], color[1], color[2], 0.9f), 1.5f)
            }
        }
        val mouseX = ImGui.getMousePosX()
        val mouseY = ImGui.getMousePosY()
        val selected = controller.selectedIndices(track)
        var hitKey = -1
        var hitComponent = -1
        var hitHandle: Triple<Int, Int, HandleSide>? = null
        val handleTime = HANDLE_PX / timeline.pxPerSecond
        val showHandles = track.path != AnimationPath.ROTATION && selected.size <= MAX_HANDLES
        for (i in track.keys.indices) {
            val x = timeline.timeToX(view.keyTimes[i], keysX)
            if (x < keysX - 6f || x > keysX + keysW + 6f) continue
            val handles = if (showHandles && i in selected) CurveEditing.handles(track, i) else null
            for (c in visible) {
                val y = curveView.valueToY(view.keyValues[c][i], top, plotH)
                if (handles != null) for (side in HandleSide.entries) {
                    val slope = if (side == HandleSide.IN) handles.inTangent[c] else handles.outTangent[c]
                    val sign = if (side == HandleSide.IN) -1f else 1f
                    val hx = timeline.timeToX(view.keyTimes[i] + sign * handleTime, keysX)
                    val hy = curveView.valueToY(view.keyValues[c][i] + sign * handleTime * slope, top, plotH)
                    draw.addLine(x, y, hx, hy, ImGui.getColorU32(0.8f, 0.8f, 0.8f, 0.7f))
                    draw.addCircleFilled(hx, hy, 3.5f, ImGui.getColorU32(0.9f, 0.9f, 0.9f, 1f))
                    if (hovered && hitHandle == null && abs(mouseX - hx) <= 5f && abs(mouseY - hy) <= 5f) hitHandle = Triple(i, c, side)
                }
                val color = if (i in selected) ImGui.getColorU32(1f, 0.85f, 0.25f, 1f) else ImGui.getColorU32(AXIS_COLORS[c][0], AXIS_COLORS[c][1], AXIS_COLORS[c][2], 1f)
                draw.addQuadFilled(x, y - 4f, x + 4f, y, x, y + 4f, x - 4f, y, color)
                if (hovered && hitKey < 0 && abs(mouseX - x) <= 6f && abs(mouseY - y) <= 6f) { hitKey = i; hitComponent = c }
            }
        }
        interactCurves(clip, track, view, visible, hitKey, hitComponent, hitHandle, hovered, keysX, keysW, top, plotH)
        curveBox?.let { b -> draw.addRect(b[0], b[1], mouseX, mouseY, ImGui.getColorU32(0.4f, 0.7f, 1f, 1f)) }
        val px = timeline.timeToX(controller.time, keysX)
        if (px >= keysX && px <= keysX + keysW) draw.addLine(px, y0, px, top + plotH, ImGui.getColorU32(1f, 0.3f, 0.3f, 1f), 1.5f)
        draw.popClipRect()

        if (hitKey >= 0 && curveDragStart == null) {
            Tooltips.always(strings.getString("lbl.animation_editor.curve_key_tooltip", track.bone, PATH_LETTER.getValue(track.path),
                AXIS_NAMES[hitComponent], view.keyValues[hitComponent][hitKey], Math.round(view.keyTimes[hitKey] * clip.fps)))
        }
    }

    private fun curveToolbar(track: SkTrack?) {
        val offered = controller.curveTracks()
        val shown = track?.let { it.bone to it.path }
        ImGui.setNextItemWidth(NAME_W)
        if (ImGui.beginCombo("##anim_curve_track", shown?.let { "${it.first} ${PATH_LETTER.getValue(it.second)}" } ?: "")) {
            for (t in offered) if (ImGui.selectable("${t.first} ${PATH_LETTER.getValue(t.second)}", t == shown)) controller.curveTrack = t
            ImGui.endCombo()
        }
        Tooltips.always(strings.getString("lbl.animation_editor.curve_track.tooltip"))
        for (c in 0 until CurveEditing.COMPONENTS) {
            ImGui.sameLine()
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, AXIS_COLORS[c][0], AXIS_COLORS[c][1], AXIS_COLORS[c][2], 1f)
            if (ImGui.checkbox(strings.label(AXIS_KEYS[c]), showComponent[c])) showComponent[c] = !showComponent[c]
            ImGui.popStyleColor()
            Tooltips.always(strings.getString("${AXIS_KEYS[c]}.tooltip"))
        }
        ImGui.sameLine()
        if (strings.tippedWith(ImGui.button("${Icons.EXPAND}##fit_curves"), "btn.animation_editor.fit_curves")) curveFitFor = null
        // Tangent modes of the selected keys; the current one is highlighted.
        val current = track?.let { t -> controller.selectedIndices(t).takeIf { it.isNotEmpty() }?.let { CurveEditing.currentTangent(t, it) } }
        ImGui.beginDisabled(controller.selection.isEmpty())
        for ((tangent, key) in TANGENT_KEYS) {
            ImGui.sameLine()
            val active = tangent == current
            if (active) ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, ImGui.getStyle().getColor(imgui.flag.ImGuiCol.ButtonActive))
            if (strings.tipped(ImGui.button(strings.label(key)), key)) controller.setTangent(tangent)
            if (active) ImGui.popStyleColor()
        }
        ImGui.endDisabled()
    }

    private fun interactCurves(
        clip: SkClip, track: SkTrack, view: TrackCurveView, visible: Set<Int>, hitKey: Int, hitComponent: Int,
        hitHandle: Triple<Int, Int, HandleSide>?, hovered: Boolean, keysX: Float, keysW: Float, top: Float, plotH: Float,
    ) {
        val io = ImGui.getIO()
        val mouseX = ImGui.getMousePosX()
        val mouseY = ImGui.getMousePosY()
        if (ImGui.isItemActivated() && ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            curveDragStart = floatArrayOf(mouseX, mouseY)
            when {
                hitHandle != null -> {
                    val (i, c, side) = hitHandle
                    handleAnchor = floatArrayOf(view.keyTimes[i], view.keyValues[c][i])
                    controller.beginHandleDrag(i, c, side)
                }
                hitKey >= 0 -> {
                    val ref = KeyRef(track.bone, track.path, track.keys[hitKey].time)
                    if (io.keyCtrl) controller.selectKey(ref, additive = true)
                    else if (ref !in controller.selection) controller.selectKey(ref)
                    controller.beginCurveDrag(hitComponent)
                }
                else -> { curveBox = floatArrayOf(mouseX, mouseY); curveDragStart = null }
            }
        }
        val start = curveDragStart
        if (start != null) {
            if (ImGui.isMouseDown(ImGuiMouseButton.Left)) {
                val anchor = handleAnchor
                if (anchor != null) {
                    val dt = timeline.xToTime(mouseX, keysX) - anchor[0]
                    if (abs(dt) > 1e-4f) controller.dragHandle((curveView.yToValue(mouseY, top, plotH) - anchor[1]) / dt)
                } else {
                    var dx = mouseX - start[0]
                    var dy = start[1] - mouseY
                    // Shift keeps the move on one axis: time or value.
                    if (io.keyShift) { if (abs(dx) >= abs(dy)) dy = 0f else dx = 0f }
                    controller.dragCurveKeys(dx / timeline.pxPerSecond, dy / curveView.pxPerUnit)
                }
            } else {
                controller.endDrag()
                curveDragStart = null
                handleAnchor = null
            }
        }
        val box = curveBox
        if (box != null && !ImGui.isMouseDown(ImGuiMouseButton.Left)) {
            curveBox = null
            if (abs(mouseX - box[0]) < 3f && abs(mouseY - box[1]) < 3f) {
                if (!io.keyCtrl) controller.clearSelection()
                controller.seek(timeline.xToTime(mouseX, keysX))
            } else {
                val t0 = timeline.xToTime(minOf(box[0], mouseX), keysX); val t1 = timeline.xToTime(maxOf(box[0], mouseX), keysX)
                val v0 = curveView.yToValue(maxOf(box[1], mouseY), top, plotH); val v1 = curveView.yToValue(minOf(box[1], mouseY), top, plotH)
                val refs = track.keys.indices.filter { i ->
                    view.keyTimes[i] in t0..t1 && visible.any { c -> view.keyValues[c][i] in v0..v1 }
                }.map { KeyRef(track.bone, track.path, track.keys[it].time) }.toSet()
                controller.selectKeys(refs, additive = io.keyCtrl)
            }
        }
        if (ImGui.isItemActive() && ImGui.isMouseDragging(ImGuiMouseButton.Middle, 1f)) {
            curveView.pan(io.mouseDeltaY)
            timeline.scroll -= io.mouseDeltaX / timeline.pxPerSecond
        }
        if (hovered && io.mouseWheel != 0f) {
            val factor = if (io.mouseWheel > 0f) 1.2f else 1f / 1.2f
            when {
                io.keyCtrl -> timeline.zoom(factor, mouseX, keysX)
                io.keyShift -> timeline.scroll -= io.mouseWheel * 40f / timeline.pxPerSecond
                else -> curveView.zoom(factor, mouseY, top, plotH)
            }
        }
        timeline.clampScroll(clip.duration, keysW)
    }

    /** Curves of [track] over the visible time, recomputed only when the clip, track or view changes. */
    private fun curveData(track: SkTrack, t0: Float, t1: Float, samples: Int): TrackCurveView {
        val key = listOf(controller.document?.revision ?: -1L, track.bone, track.path, t0, t1, samples)
        curveCache?.let { (k, v) -> if (k == key) return v }
        return CurveEditing.view(track, t0, t1, samples).also { curveCache = key to it }
    }

    private fun frameCurves(view: TrackCurveView, visible: Set<Int>, plotH: Float) {
        val (lo, hi) = view.range(visible) ?: return
        curveView.fit(lo, hi, plotH)
    }

    private fun decimals(step: Float): Int = when {
        step == 0f || abs(step) >= 10f -> 0
        abs(step) >= 1f -> 1
        abs(step) >= 0.1f -> 2
        else -> 3
    }

    // ---- cleanup tools (ANI-15) ------------------------------------------------------------------------------

    private fun cleanupTools(clip: SkClip) {
        if (!ImGui.collapsingHeader(strings.label("lbl.animation_editor.cleanup"), ImGuiTreeNodeFlags.DefaultOpen)) return
        val fps = clip.fps
        val r = controller.toolRange()
        ImGui.text(strings.getString("lbl.animation_editor.range", Math.round(r.start * fps), Math.round(r.endInclusive * fps)))
        Tooltips.always(strings.getString("lbl.animation_editor.range.tooltip"))
        if (strings.tipped(ImGui.button(strings.label("btn.animation_editor.range_in")), "btn.animation_editor.range_in")) controller.setRangeStartAtPlayhead()
        ImGui.sameLine()
        if (strings.tipped(ImGui.button(strings.label("btn.animation_editor.range_out")), "btn.animation_editor.range_out")) controller.setRangeEndAtPlayhead()
        ImGui.beginDisabled(controller.selection.isEmpty())
        if (strings.tipped(ImGui.button(strings.label("btn.animation_editor.range_selection")), "btn.animation_editor.range_selection")) controller.setRangeFromSelection()
        ImGui.endDisabled()
        ImGui.sameLine()
        ImGui.beginDisabled(controller.range == null)
        if (strings.tipped(ImGui.button(strings.label("btn.animation_editor.range_clear")), "btn.animation_editor.range_clear")) controller.clearRange()
        ImGui.endDisabled()
        val bones = controller.selectedBones.size
        MImGui.textDisabled(if (bones > 0) strings.getString("lbl.animation_editor.cleanup_scope_bones", bones) else strings.getString("lbl.animation_editor.cleanup_scope_all"))
        ImGui.separator()

        ImGui.setNextItemWidth(70f)
        ImGui.inputFloat(strings.label("lbl.animation_editor.resample_fps"), resampleFps, 0f, 0f, "%.0f")
        Tooltips.always(strings.getString("lbl.animation_editor.resample_fps.tooltip"))
        ImGui.sameLine()
        if (strings.tipped(ImGui.button(strings.label("btn.animation_editor.resample")), "btn.animation_editor.resample")) controller.resample(resampleFps.get())

        ImGui.setNextItemWidth(70f)
        ImGui.inputFloat(strings.label("lbl.animation_editor.reduce_deg"), reduceDeg, 0f, 0f, "%.2f")
        Tooltips.always(strings.getString("lbl.animation_editor.reduce_deg.tooltip"))
        ImGui.setNextItemWidth(70f)
        ImGui.inputFloat(strings.label("lbl.animation_editor.reduce_mm"), reduceMm, 0f, 0f, "%.1f")
        Tooltips.always(strings.getString("lbl.animation_editor.reduce_mm.tooltip"))
        if (strings.tipped(ImGui.button(strings.label("btn.animation_editor.reduce")), "btn.animation_editor.reduce")) {
            val mm = reduceMm.get().coerceAtLeast(0f) / 1000f
            controller.reduceKeys(ReductionTolerance(translationMetres = mm, rotationDeg = reduceDeg.get().coerceAtLeast(0f), scale = mm))
        }
        ImGui.separator()

        ImGui.setNextItemWidth(110f)
        val filterKeys = mapOf(SmoothFilter.GAUSSIAN to "lbl.animation_editor.smooth_gaussian", SmoothFilter.ONE_EURO to "lbl.animation_editor.smooth_one_euro")
        if (ImGui.beginCombo(strings.label("lbl.animation_editor.smooth_filter"), strings.getString(filterKeys.getValue(smoothFilter)))) {
            for ((f, k) in filterKeys) if (ImGui.selectable(strings.getString(k), f == smoothFilter)) smoothFilter = f
            ImGui.endCombo()
        }
        Tooltips.always(strings.getString("lbl.animation_editor.smooth_filter.tooltip"))
        ImGui.setNextItemWidth(70f)
        ImGui.inputFloat(strings.label("lbl.animation_editor.smooth_strength"), smoothStrength, 0f, 0f, "%.1f")
        Tooltips.always(strings.getString("lbl.animation_editor.smooth_strength.tooltip"))
        ImGui.sameLine()
        if (strings.tipped(ImGui.button(strings.label("btn.animation_editor.smooth")), "btn.animation_editor.smooth")) controller.smooth(smoothFilter, smoothStrength.get())
        ImGui.separator()

        ImGui.setNextItemWidth(70f)
        ImGui.inputFloat(strings.label("lbl.animation_editor.foot_blend"), footBlend, 0f, 0f, "%.0f")
        Tooltips.always(strings.getString("lbl.animation_editor.foot_blend.tooltip"))
        val left = controller.legChain(left = true)
        val right = controller.legChain(left = false)
        ImGui.beginDisabled(left == null)
        if (strings.tipped(ImGui.button(strings.label("btn.animation_editor.foot_lock_left")), "btn.animation_editor.foot_lock_left")) controller.footLock(true, footBlend.get())
        ImGui.endDisabled()
        ImGui.sameLine()
        ImGui.beginDisabled(right == null)
        if (strings.tipped(ImGui.button(strings.label("btn.animation_editor.foot_lock_right")), "btn.animation_editor.foot_lock_right")) controller.footLock(false, footBlend.get())
        ImGui.endDisabled()
        if (left == null || right == null) MImGui.textDisabled(strings.getString("lbl.animation_editor.no_legs"))
        ImGui.separator()

        ImGui.beginDisabled(controller.previewRig() == null)
        if (strings.tipped(ImGui.button(strings.label("btn.animation_editor.mirror")), "btn.animation_editor.mirror")) controller.mirror()
        ImGui.endDisabled()
        ImGui.sameLine()
        ImGui.beginDisabled(controller.range == null)
        if (strings.tipped(ImGui.button(strings.label("btn.animation_editor.trim_range")), "btn.animation_editor.trim_range")) controller.trimToRange()
        ImGui.endDisabled()
        ImGui.setNextItemWidth(70f)
        ImGui.inputFloat(strings.label("lbl.animation_editor.seam_frames"), seamFrames, 0f, 0f, "%.0f")
        Tooltips.always(strings.getString("lbl.animation_editor.seam_frames.tooltip"))
        ImGui.sameLine()
        if (strings.tipped(ImGui.button(strings.label("btn.animation_editor.loop_seam")), "btn.animation_editor.loop_seam")) controller.loopSeam(seamFrames.get())
    }

    // ---- preview ---------------------------------------------------------------------------------------------

    private fun previewArea() {
        val (rig, locals) = controller.pose() ?: run {
            MImGui.textDisabled(strings.getString("lbl.animation_editor.no_rig"))
            return
        }
        val w = ImGui.getContentRegionAvailX()
        val h = ImGui.getContentRegionAvailY()
        val ox = ImGui.getCursorScreenPosX()
        val oy = ImGui.getCursorScreenPosY()
        ImGui.invisibleButton("##anim_view", w.coerceAtLeast(1f), h.coerceAtLeast(1f))
        val hovered = ImGui.isItemHovered()
        val pressed = ImGui.isItemActivated()
        val released = ImGui.isItemDeactivated()
        val joints = preview.joints(rig, locals)
        if (framedFor !== rig) { preview.frame(joints); framedFor = rig }
        if (ImGui.isItemActive() && ImGui.isMouseDragging(ImGuiMouseButton.Left, 2f)) {
            preview.orbit(-ImGui.getIO().mouseDeltaX * 0.4f, ImGui.getIO().mouseDeltaY * 0.4f)
        }
        if (hovered && ImGui.getIO().mouseWheel != 0f) preview.zoom(if (ImGui.getIO().mouseWheel > 0f) 0.9f else 1.1f)

        val draw = ImGui.getWindowDrawList()
        draw.addRectFilled(ox, oy, ox + w, oy + h, ImGui.getColorU32(0.1f, 0.1f, 0.12f, 1f))
        // The character in the pose (REN-33); the grid and joints are drawn on top of it
        val character = skinned.draw(rig, locals, preview, w.toInt(), h.toInt())
        if (character != 0) draw.addImage(character.toLong(), ox, oy, ox + w, oy + h, 0f, 1f, 1f, 0f)
        // Ground grid (1 m cells around the origin).
        val gridColor = ImGui.getColorU32(0.3f, 0.3f, 0.35f, 1f)
        val lines = (-5..5).flatMap { i -> listOf(Vector3f(i.toFloat(), 0f, -5f) to Vector3f(i.toFloat(), 0f, 5f), Vector3f(-5f, 0f, i.toFloat()) to Vector3f(5f, 0f, i.toFloat())) }
        val gridScreen = preview.project(lines.flatMap { listOf(it.first, it.second) }.toTypedArray(), w, h)
        for (i in lines.indices) {
            val a = gridScreen[i * 2] ?: continue
            val b = gridScreen[i * 2 + 1] ?: continue
            draw.addLine(ox + a[0], oy + a[1], ox + b[0], oy + b[1], gridColor)
        }
        val screen = preview.project(joints, w, h)
        for (i in 0 until rig.boneCount) {
            val p = rig.parents[i]
            val a = screen[i] ?: continue
            if (p >= 0) screen[p]?.let { b -> draw.addLine(ox + a[0], oy + a[1], ox + b[0], oy + b[1], ImGui.getColorU32(0.85f, 0.85f, 0.9f, 1f), 2f) }
        }
        val mx = ImGui.getMousePosX() - ox
        val my = ImGui.getMousePosY() - oy
        val hit = if (hovered) preview.pick(screen, mx, my) else -1
        for (i in 0 until rig.boneCount) {
            val a = screen[i] ?: continue
            val selected = rig.names[i] in controller.selectedBones
            val c = when {
                selected -> ImGui.getColorU32(0.3f, 0.8f, 1f, 1f)
                i == hit -> ImGui.getColorU32(1f, 1f, 0.5f, 1f)
                else -> ImGui.getColorU32(0.95f, 0.6f, 0.2f, 1f)
            }
            draw.addCircleFilled(ox + a[0], oy + a[1], if (selected || i == hit) 5f else 3f, c)
        }
        if (pressed) pressAt = floatArrayOf(mx, my)
        if (hit >= 0) Tooltips.always(rig.names[hit])
        // A click (not an orbit drag) on a joint selects that bone and its keys.
        val press = pressAt
        if (released && press != null) {
            pressAt = null
            val joint = preview.pick(screen, press[0], press[1])
            if (joint >= 0 && abs(mx - press[0]) < 3f && abs(my - press[1]) < 3f) controller.selectBone(rig.names[joint], additive = ImGui.getIO().keyCtrl)
        }
        ImGui.setCursorScreenPos(ox + 6f, oy + 6f)
        if (strings.tippedWith(ImGui.button(Icons.EXPAND), "btn.animation_editor.frame_view")) framedFor = null
    }

    /** The skinned mesh of the preview rig: the skater's model, or the selected object's (REN-33). */
    private fun previewModel(): `3dModel`? = when (controller.rig) {
        TargetRig.SKATER -> engine.assetsManager.getModel(Assets.Models.JAMES)
        TargetRig.SELECTED -> engine.sceneManager.currentScene?.selectedGameObject?.getComponent<RenderComponent>()?.model
    }

    // ---- files -----------------------------------------------------------------------------------------------

    private fun projectClips(): List<File> {
        val dir = projectManager.getProjectDirectory()?.let { File(it, "Assets") } ?: return emptyList()
        return dir.walkTopDown().filter { it.isFile && it.extension.equals(SkClip.EXTENSION, ignoreCase = true) }.sortedBy { it.name.lowercase() }.take(500).toList()
    }

    private fun pickClip(): File? {
        val stack = org.lwjgl.system.MemoryStack.stackPush()
        try {
            val patterns = stack.mallocPointer(5)
            listOf("*.skanim", "*.glb", "*.gltf", "*.fbx", "*.dae").forEach { patterns.put(stack.UTF8(it)) }
            patterns.flip()
            val start = projectManager.getProjectDirectory()?.let { File(it, "Assets").absolutePath + File.separator } ?: ""
            val path = TinyFileDialogs.tinyfd_openFileDialog(strings.getString("dialog.animation_editor.open_title"), start, patterns,
                strings.getString("dialog.animation_editor.open_filter"), false)
            return path?.let(::File)
        } finally {
            stack.pop()
        }
    }

    companion object {
        private const val NAME_W = 180f
        private const val RULER_H = 20f
        private const val ROW_H = 20f
        private val PATH_LETTER = mapOf(AnimationPath.TRANSLATION to "T", AnimationPath.ROTATION to "R", AnimationPath.SCALE to "S")
        /** Width of the curve editor's value labels. */
        private const val AXIS_W = 48f
        /** Shown length of a tangent handle, in pixels along time. */
        private const val HANDLE_PX = 40f
        /** Handles are drawn for at most this many selected keys (a whole mocap track selected would be noise). */
        private const val MAX_HANDLES = 32
        private val AXIS_COLORS = arrayOf(floatArrayOf(0.95f, 0.4f, 0.4f), floatArrayOf(0.45f, 0.9f, 0.45f), floatArrayOf(0.45f, 0.6f, 1f))
        private val AXIS_NAMES = arrayOf("X", "Y", "Z")
        private val AXIS_KEYS = arrayOf("lbl.animation_editor.curve_x", "lbl.animation_editor.curve_y", "lbl.animation_editor.curve_z")
        private val TANGENT_KEYS = linkedMapOf(
            CurveTangent.AUTO to "btn.animation_editor.tangent_auto",
            CurveTangent.LINEAR to "btn.animation_editor.tangent_linear",
            CurveTangent.FLAT to "btn.animation_editor.tangent_flat",
            CurveTangent.FREE to "btn.animation_editor.tangent_free",
            CurveTangent.BROKEN to "btn.animation_editor.tangent_broken",
            CurveTangent.STEP to "btn.animation_editor.tangent_step",
        )
    }
}
