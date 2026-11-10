package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.app.Editor
import com.pafoid.skate.editor.animation.clipeditor.AnimationEditorController
import com.pafoid.skate.editor.animation.clipeditor.AnimationEditorWiring
import com.pafoid.skate.editor.animation.clipeditor.SkeletonPreview
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
import com.pafoid.skate.engine.animation.edit.EulerCurves
import com.pafoid.skate.engine.animation.edit.KeyRef
import com.pafoid.skate.engine.animation.edit.SkClip
import com.pafoid.skate.engine.assets.data.models.animations.AnimationPath
import com.pafoid.skate.engine.core.Engine
import imgui.ImGui
import imgui.flag.ImGuiKey
import imgui.flag.ImGuiMouseButton
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
 * (select, box-select, drag, copy/paste, delete, key selected bones), events track, key and clip inspector (value,
 * time, retime, trim, fps, loop) and a skeleton preview on the skater or the selected rig. Every edit is undoable.
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

    override fun imgui() {
        if (beginWindow(strings, ImGuiWindowFlags.NoScrollbar)) {
            header(strings)
            controller.refresh()
            controller.tick(ImGui.getIO().deltaTime)
            toolbar()
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
                    dopeSheet(clip)
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

        // Ruler: click or drag to scrub.
        draw.addRectFilled(keysX, y0, keysX + keysW, y0 + RULER_H, ImGui.getColorU32(0.16f, 0.16f, 0.18f, 1f))
        for (frame in timeline.rulerFrames(clip.fps, keysW)) {
            val x = timeline.timeToX(frame / clip.fps, keysX)
            draw.addLine(x, y0 + RULER_H - 6f, x, y0 + RULER_H, ImGui.getColorU32(0.6f, 0.6f, 0.6f, 1f))
            draw.addText(x + 2f, y0 + 1f, ImGui.getColorU32(0.75f, 0.75f, 0.75f, 1f), frame.toString())
        }
        val endX = timeline.timeToX(clip.duration, keysX)
        ImGui.setCursorScreenPos(keysX, y0)
        ImGui.invisibleButton("##anim_ruler", keysW, RULER_H)
        if (ImGui.isItemActivated()) scrubbing = true
        if (scrubbing && ImGui.isMouseDown(ImGuiMouseButton.Left)) controller.seek(timeline.xToTime(mouseX, keysX)) else scrubbing = false

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
    }
}
