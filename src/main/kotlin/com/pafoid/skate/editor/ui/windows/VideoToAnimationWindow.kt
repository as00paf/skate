package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.app.Editor
import com.pafoid.skate.editor.animation.mocap.MocapPhase
import com.pafoid.skate.editor.animation.mocap.SetupHint
import com.pafoid.skate.editor.animation.mocap.TargetRig
import com.pafoid.skate.editor.animation.mocap.VideoPlayer
import com.pafoid.skate.editor.animation.mocap.VideoToAnimationController
import com.pafoid.skate.editor.animation.mocap.VideoToAnimationWiring
import com.pafoid.skate.editor.events.FileSystemEvent
import com.pafoid.skate.editor.imgui.EditorWindow
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.imgui.Tooltips
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.label
import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.editor.imgui.tippedWith
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.ui.windows.assetBrowser.TextureThumbnail
import com.pafoid.skate.editor.ui.windows.assetBrowser.ThumbnailPixels
import com.pafoid.skate.editor.ui.windows.assetBrowser.ThumbnailScaler
import com.pafoid.skate.engine.animation.mocap.PoseLandmark
import com.pafoid.skate.engine.animation.mocap.Smoothing
import com.pafoid.skate.engine.assets.loaders.TextureLoader
import com.pafoid.skate.engine.core.Engine
import imgui.ImGui
import imgui.flag.ImGuiFocusedFlags
import imgui.flag.ImGuiKey
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImInt
import imgui.type.ImString
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.tinyfd.TinyFileDialogs
import java.io.File

/**
 * Video to Animation (ANI-17): choose a local video, see a preview, click Create: the pose estimator runs, the
 * motion is solved, retargeted onto the rig and saved as a `.skanim` that is then revealed in the Project window.
 * Also imports BVH/FBX/glTF mocap files from other tools. Python + MediaPipe are set up once (Setup section,
 * docs/mocap-setup.md). All state and work live in [VideoToAnimationController].
 */
class VideoToAnimationWindow(
    private val engine: Engine,
    editor: Editor,
    private val projectManager: ProjectManager,
    private val controller: VideoToAnimationController = VideoToAnimationWiring.create(engine, editor, projectManager),
) : EditorWindow("window.video_to_animation") {

    override val icon: String get() = Icons.FILM

    private val strings = engine.stringManager
    private val settingsManager = editor.settingsManager
    private val pythonPath = ImString(512)
    private val modelPath = ImString(1024)
    private var thumbnail: TextureThumbnail? = null
    private var thumbnailFile: File? = null
    private var settingsLoaded = false

    /** Uploads a preview frame; replaced by tests (no GPU). */
    internal var uploadPreview: (File) -> TextureThumbnail = { ThumbnailScaler.upload(decode(it)) }

    override fun imgui() {
        if (beginWindow(strings)) {
            header(strings)
            // ANI-22: the body scrolls in its own child. Scrolling the window itself does not work below the header:
            // the header keeps its row inside the clip rectangle (HelpButtonLayout.row), so it moved back down with
            // everything after it and the scroll snapped back.
            if (ImGui.beginChild(BODY_ID, 0f, 0f, false, ImGuiWindowFlags.HorizontalScrollbar)) body()
            ImGui.endChild()
        }
        ImGui.end()
    }

    private fun body() {
        if (projectManager.getProjectDirectory() == null) {
            MImGui.textDisabled(strings.getString("lbl.mocap.no_project"))
        } else {
            videoStep()
            ImGui.separator()
            optionsStep()
            ImGui.separator()
            createStep()
            ImGui.separator()
            if (strings.tipped(ImGui.button(strings.getString("btn.mocap.import")), "btn.mocap.import")) pickImportFile()?.let(controller::importFile)
        }
        setupSection()
    }

    override fun onClosed() {
        thumbnail?.texture?.close()
        thumbnail = null
        thumbnailFile = null
    }

    private fun videoStep() {
        if (strings.tipped(ImGui.button(strings.getString("btn.mocap.choose_video")), "btn.mocap.choose_video") && !controller.busy) {
            pickVideo()?.let(controller::selectVideo)
        }
        ImGui.sameLine()
        val video = controller.video
        if (video == null) MImGui.textDisabled(strings.getString("lbl.mocap.no_video")) else ImGui.text(video.name)
        if (controller.phase == MocapPhase.PROBING) MImGui.textDisabled(strings.getString("lbl.mocap.probing"))
        controller.info?.let { info ->
            MImGui.textDisabled(strings.getString("lbl.mocap.video_info", info.width, info.height, info.fps, info.duration))
            controller.tick(ImGui.getIO().deltaTime)
            preview(controller.previewImage() ?: info.thumbnail, info.width, info.height)
            controller.player?.let { playback(it) }
        }
    }

    /** The current video frame, with the detected pose drawn over it once landmarks exist for that frame. */
    private fun preview(file: File?, width: Int, height: Int) {
        val tex = thumbnailFor(file) ?: return
        val w = ImGui.getContentRegionAvailX().coerceAtMost(PREVIEW_WIDTH)
        val aspect = if (width > 0 && height > 0) height.toFloat() / width else tex.height.toFloat() / tex.width
        val h = w * aspect
        ImGui.image(tex.texId, w, h, 0f, 0f, 1f, 1f)
        val x0 = ImGui.getItemRectMinX()
        val y0 = ImGui.getItemRectMinY()
        val frame = controller.player?.frame ?: return
        val index = controller.landmarkIndexFor(frame) ?: return
        val points = controller.landmarks?.frames?.get(index)?.image ?: return
        val draw = ImGui.getWindowDrawList()
        val color = ImGui.getColorU32(0.2f, 1f, 0.4f, 1f)
        for ((a, b) in PoseLandmark.SKELETON_LINES) {
            draw.addLine(x0 + points[a * 2] * w, y0 + points[a * 2 + 1] * h, x0 + points[b * 2] * w, y0 + points[b * 2 + 1] * h, color, 2f)
        }
    }

    /** Play/pause, frame step, scrub bar with the in/out markers, and the range buttons (ANI-20). */
    private fun playback(player: VideoPlayer) {
        val busy = controller.busy
        if (strings.tippedWith(ImGui.button(Icons.ARROW_LEFT), "btn.mocap.prev_frame")) player.step(-1)
        ImGui.sameLine()
        ImGui.beginDisabled(busy)
        if (strings.tippedWith(ImGui.button(if (player.playing) Icons.PAUSE else Icons.PLAY), "btn.mocap.play")) player.togglePlay()
        ImGui.endDisabled()
        ImGui.sameLine()
        if (strings.tippedWith(ImGui.button(Icons.ARROW_RIGHT), "btn.mocap.next_frame")) player.step(1)
        ImGui.sameLine()
        ImGui.text(strings.getString("lbl.mocap.frame", player.frame, player.frameCount - 1, player.time))
        controller.previewProgress?.let { ImGui.sameLine(); MImGui.textDisabled(strings.getString("lbl.mocap.preview_loading", (it * 100).toInt())) }

        // Scrub bar: the whole video, the kept range highlighted, the playhead in red. Click or drag to scrub.
        val w = ImGui.getContentRegionAvailX().coerceAtMost(PREVIEW_WIDTH)
        val x0 = ImGui.getCursorScreenPosX()
        val y0 = ImGui.getCursorScreenPosY()
        val h = 18f
        ImGui.invisibleButton("##mocap_scrub", w, h)
        Tooltips.always(strings.getString("lbl.mocap.preview_frame.tooltip"))
        val last = (player.frameCount - 1).coerceAtLeast(1)
        fun xOf(frame: Int) = x0 + w * frame / last
        if (ImGui.isItemActive()) {
            player.pause()
            player.seek(Math.round((ImGui.getMousePosX() - x0) / w * last))
        }
        val draw = ImGui.getWindowDrawList()
        draw.addRectFilled(x0, y0 + 4f, x0 + w, y0 + h - 4f, ImGui.getColorU32(0.25f, 0.25f, 0.28f, 1f))
        draw.addRectFilled(xOf(player.inFrame), y0 + 2f, xOf(player.outFrame), y0 + h - 2f, ImGui.getColorU32(0.3f, 0.6f, 0.95f, 0.8f))
        draw.addLine(xOf(player.frame), y0, xOf(player.frame), y0 + h, ImGui.getColorU32(1f, 0.3f, 0.3f, 1f), 2f)

        if (strings.tipped(ImGui.button(strings.getString("btn.mocap.mark_in")), "btn.mocap.mark_in")) player.markIn()
        ImGui.sameLine()
        if (strings.tipped(ImGui.button(strings.getString("btn.mocap.mark_out")), "btn.mocap.mark_out")) player.markOut()
        ImGui.sameLine()
        ImGui.beginDisabled(!player.trimmed)
        if (strings.tipped(ImGui.button(strings.getString("btn.mocap.clear_range")), "btn.mocap.clear_range")) player.clearRange()
        ImGui.endDisabled()
        ImGui.sameLine()
        ImGui.text(strings.getString("lbl.mocap.range", player.inFrame, player.outFrame, player.rangeSeconds))

        if (ImGui.isWindowFocused(ImGuiFocusedFlags.RootAndChildWindows) && !ImGui.getIO().wantTextInput) {
            when {
                ImGui.isKeyPressed(ImGuiKey.Space) && !busy -> player.togglePlay()
                ImGui.isKeyPressed(ImGuiKey.LeftArrow) -> player.step(-1)
                ImGui.isKeyPressed(ImGuiKey.RightArrow) -> player.step(1)
                ImGui.isKeyPressed(ImGuiKey.I) -> player.markIn()
                ImGui.isKeyPressed(ImGuiKey.O) -> player.markOut()
            }
        }
    }

    private fun optionsStep() {
        if (!ImGui.collapsingHeader(strings.label("lbl.mocap.options"))) return
        var o = controller.options
        val targets = TargetRig.entries
        val targetKeys = mapOf(TargetRig.SKATER to "lbl.mocap.target.skater", TargetRig.SELECTED to "lbl.mocap.target.selected")
        if (ImGui.beginCombo(strings.label("lbl.mocap.target"), strings.getString(targetKeys.getValue(o.target)))) {
            for (t in targets) if (ImGui.selectable(strings.getString(targetKeys.getValue(t)), t == o.target)) o = o.copy(target = t)
            ImGui.endCombo()
        }
        tooltip("lbl.mocap.target")
        val smoothing = ImInt(o.smoothing.ordinal)
        val smoothingNames = Smoothing.entries.map { strings.getString("lbl.mocap.smoothing.${it.name.lowercase()}") }.toTypedArray()
        if (ImGui.combo(strings.label("lbl.mocap.smoothing"), smoothing, smoothingNames)) o = o.copy(smoothing = Smoothing.entries[smoothing.get()])
        tooltip("lbl.mocap.smoothing")
        if (ImGui.checkbox(strings.label("lbl.mocap.root_motion"), o.rootMotion)) o = o.copy(rootMotion = !o.rootMotion)
        tooltip("lbl.mocap.root_motion")
        if (o.rootMotion) {
            if (ImGui.checkbox(strings.label("lbl.mocap.foot_lock"), o.footLock)) o = o.copy(footLock = !o.footLock)
            tooltip("lbl.mocap.foot_lock")
        }
        controller.options = o
    }

    private fun createStep() {
        when (controller.phase) {
            MocapPhase.DOWNLOADING_MODEL, MocapPhase.EXTRACTING, MocapPhase.SOLVING -> {
                val percent = (controller.progress * 100).toInt()
                val label = when (controller.phase) {
                    MocapPhase.SOLVING -> strings.getString("lbl.mocap.solving")
                    MocapPhase.DOWNLOADING_MODEL -> strings.getString("lbl.mocap.downloading_model", percent)
                    else -> strings.getString("lbl.mocap.extracting", percent)
                }
                ImGui.progressBar(controller.progress, ImGui.getContentRegionAvailX().coerceAtMost(420f), 0f, label)
                if (controller.phase != MocapPhase.SOLVING && strings.tipped(ImGui.button(strings.getString("btn.mocap.cancel")), "btn.mocap.cancel")) controller.cancel()
                return
            }
            else -> Unit
        }
        ImGui.beginDisabled(!controller.canCreate)
        if (strings.tipped(ImGui.button(strings.getString("btn.mocap.create"), 220f, 0f), "btn.mocap.create")) controller.create()
        ImGui.endDisabled()

        controller.result?.takeIf { controller.phase == MocapPhase.DONE }?.let { file ->
            MImGui.successText(strings.getString("lbl.mocap.done", file.name))
            if (controller.coverage < 0.8f) MImGui.warningText(strings.getString("lbl.mocap.low_coverage", controller.coverage * 100f))
            if (strings.tipped(ImGui.button(strings.getString("btn.mocap.show_in_project")), "btn.mocap.show_in_project")) {
                engine.eventSystem.publish(FileSystemEvent.RevealInProject(file.absolutePath))
            }
        }
        controller.error?.let { MImGui.errorText(strings.getString("lbl.mocap.failed", it)) }
    }

    private fun setupSection() {
        if (!settingsLoaded) {
            val s = settingsManager.editorSettings.mocap
            pythonPath.set(s.pythonPath)
            modelPath.set(s.modelPath ?: "")
            settingsLoaded = true
        }
        if (!ImGui.collapsingHeader(strings.label("lbl.mocap.setup"))) return
        MImGui.textDisabled(strings.getString("lbl.mocap.setup_doc"))
        var changed = ImGui.inputText(strings.label("lbl.mocap.python_path"), pythonPath)
        tooltip("lbl.mocap.python_path")
        changed = ImGui.inputText(strings.label("lbl.mocap.model_path"), modelPath) || changed
        tooltip("lbl.mocap.model_path")
        ImGui.sameLine()
        if (strings.tipped(ImGui.button(strings.getString("btn.mocap.browse_model")), "btn.mocap.browse_model")) {
            pickModel()?.let { modelPath.set(it); changed = true }
        }
        if (changed) {
            val current = settingsManager.editorSettings.mocap
            settingsManager.updateMocapSettings(current.copy(pythonPath = pythonPath.get(), modelPath = modelPath.get()))
        }
        ImGui.beginDisabled(controller.checkingSetup)
        if (strings.tipped(ImGui.button(strings.getString("btn.mocap.check_setup")), "btn.mocap.check_setup")) controller.checkSetup()
        ImGui.endDisabled()
        if (controller.checkingSetup) {
            ImGui.sameLine()
            val download = controller.setupDownload
            MImGui.textDisabled(if (download != null) strings.getString("lbl.mocap.downloading_model", (download * 100).toInt())
            else strings.getString("lbl.mocap.setup_checking"))
        }
        controller.setup?.let { s ->
            if (s.ok) {
                val api = strings.getString(if (s.api == "tasks") "lbl.mocap.api.tasks" else "lbl.mocap.api.solutions")
                MImGui.successText(strings.getString("lbl.mocap.setup_ok", s.python ?: "?", s.mediapipe ?: "?", api))
                s.model?.let { MImGui.textDisabled(strings.getString("lbl.mocap.setup_model", File(it).name)); Tooltips.always(it) }
                s.fallback?.let { MImGui.warningText(strings.getString("lbl.mocap.setup_fallback", it)) }
            } else {
                MImGui.errorText(s.problem ?: "")
                val hint = when (s.hint) {
                    SetupHint.INSTALL_PYTHON -> "hint.mocap.install_python"
                    SetupHint.INSTALL_MEDIAPIPE -> "hint.mocap.install_mediapipe"
                    SetupHint.MODEL_FILE -> "hint.mocap.model_file"
                    SetupHint.UPDATE_MEDIAPIPE -> "hint.mocap.update_mediapipe"
                    else -> "hint.mocap.other"
                }
                ImGui.textWrapped(strings.getString(hint))
            }
        }
    }

    private fun tooltip(key: String) {
        Tooltips.always(strings.getString("$key.tooltip"))
    }

    private fun thumbnailFor(file: File?): TextureThumbnail? {
        if (file == null || !file.isFile) return null
        if (file != thumbnailFile) {
            thumbnail?.texture?.close()
            thumbnail = runCatching { uploadPreview(file) }.getOrNull()
            thumbnailFile = file
        }
        return thumbnail
    }

    /** Like [ThumbnailScaler.decode], at a size readable in the window. */
    private fun decode(file: File): ThumbnailPixels {
        val loader = TextureLoader()
        val data = loader.decodeFromFile(file.absolutePath)
        try {
            val (w, h) = ThumbnailScaler.fit(data.width, data.height, PREVIEW_SIZE)
            val out = MemoryUtil.memAlloc(w * h * 4)
            ThumbnailScaler.downscale(data.pixels, data.width, data.height, out, w, h)
            return ThumbnailPixels(w, h, data.width, data.height, out)
        } finally {
            loader.discard(data)
        }
    }

    private fun pickVideo() = openDialog("dialog.mocap.video_title", "dialog.mocap.video_filter", VIDEO_PATTERNS)?.let(::File)
    private fun pickImportFile() = openDialog("dialog.mocap.import_title", "dialog.mocap.import_filter", IMPORT_PATTERNS)?.let(::File)
    private fun pickModel() = openDialog("dialog.mocap.model_title", "dialog.mocap.model_filter", listOf("*.task"))

    private fun openDialog(titleKey: String, filterKey: String, patterns: List<String>): String? {
        val start = projectManager.getProjectDirectory()?.path?.plus(File.separator)
        val buffers = patterns.map { MemoryUtil.memUTF8(it) }
        val filters = MemoryUtil.memAllocPointer(buffers.size)
        buffers.forEachIndexed { i, b -> filters.put(i, b) }
        return try {
            TinyFileDialogs.tinyfd_openFileDialog(strings.getString(titleKey), start, filters, strings.getString(filterKey), false)
        } finally {
            buffers.forEach { MemoryUtil.memFree(it) }
            MemoryUtil.memFree(filters)
        }
    }

    private companion object {
        const val BODY_ID = "##mocap_body"
        const val PREVIEW_SIZE = 512
        const val PREVIEW_WIDTH = 420f
        val VIDEO_PATTERNS = listOf("*.mp4", "*.mov", "*.avi", "*.mkv", "*.webm", "*.m4v")
        val IMPORT_PATTERNS = listOf("*.bvh", "*.fbx", "*.gltf", "*.glb")
    }
}
