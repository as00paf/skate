package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.app.Editor
import com.pafoid.skate.editor.animation.mocap.MocapPhase
import com.pafoid.skate.editor.animation.mocap.SetupHint
import com.pafoid.skate.editor.animation.mocap.TargetRig
import com.pafoid.skate.editor.animation.mocap.VideoToAnimationController
import com.pafoid.skate.editor.animation.mocap.VideoToAnimationWiring
import com.pafoid.skate.editor.events.FileSystemEvent
import com.pafoid.skate.editor.imgui.EditorWindow
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.imgui.Tooltips
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.label
import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.ui.windows.assetBrowser.TextureThumbnail
import com.pafoid.skate.editor.ui.windows.assetBrowser.ThumbnailPixels
import com.pafoid.skate.editor.ui.windows.assetBrowser.ThumbnailScaler
import com.pafoid.skate.engine.animation.mocap.PoseLandmark
import com.pafoid.skate.engine.animation.mocap.Smoothing
import com.pafoid.skate.engine.assets.loaders.TextureLoader
import com.pafoid.skate.engine.core.Engine
import imgui.ImGui
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
    private val previewFrame = intArrayOf(0)
    private var thumbnail: TextureThumbnail? = null
    private var thumbnailFile: File? = null
    private var settingsLoaded = false

    override fun imgui() {
        if (beginWindow(strings)) {
            header(strings)
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
        ImGui.end()
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
            preview(info.thumbnail, info.width, info.height)
        }
    }

    /** The video's thumbnail, with the detected pose drawn over it once landmarks exist. */
    private fun preview(file: File?, width: Int, height: Int) {
        val tex = thumbnailFor(file) ?: return
        val maxW = ImGui.getContentRegionAvailX().coerceAtMost(420f)
        val aspect = if (width > 0 && height > 0) height.toFloat() / width else tex.height.toFloat() / tex.width
        val w = maxW
        val h = w * aspect
        ImGui.image(tex.texId, w, h, 0f, 0f, 1f, 1f)
        val x0 = ImGui.getItemRectMinX()
        val y0 = ImGui.getItemRectMinY()
        val seq = controller.landmarks ?: return
        if (seq.frames.isEmpty()) return
        previewFrame[0] = previewFrame[0].coerceIn(0, seq.frames.size - 1)
        val points = seq.frames[previewFrame[0]].image
        if (points != null) {
            val draw = ImGui.getWindowDrawList()
            val color = ImGui.getColorU32(0.2f, 1f, 0.4f, 1f)
            for ((a, b) in PoseLandmark.SKELETON_LINES) {
                draw.addLine(x0 + points[a * 2] * w, y0 + points[a * 2 + 1] * h, x0 + points[b * 2] * w, y0 + points[b * 2 + 1] * h, color, 2f)
            }
        }
        ImGui.setNextItemWidth(w)
        ImGui.sliderInt(strings.label("lbl.mocap.preview_frame"), previewFrame, 0, seq.frames.size - 1)
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
            MocapPhase.EXTRACTING, MocapPhase.SOLVING -> {
                val label = if (controller.phase == MocapPhase.SOLVING) strings.getString("lbl.mocap.solving")
                else strings.getString("lbl.mocap.extracting", (controller.progress * 100).toInt())
                ImGui.progressBar(controller.progress, ImGui.getContentRegionAvailX().coerceAtMost(420f), 0f, label)
                if (controller.phase == MocapPhase.EXTRACTING && strings.tipped(ImGui.button(strings.getString("btn.mocap.cancel")), "btn.mocap.cancel")) controller.cancel()
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
        if (controller.checkingSetup) { ImGui.sameLine(); MImGui.textDisabled(strings.getString("lbl.mocap.setup_checking")) }
        controller.setup?.let { s ->
            if (s.ok) {
                MImGui.successText(strings.getString("lbl.mocap.setup_ok", s.python ?: "?", s.mediapipe ?: "?", s.api ?: "?"))
            } else {
                MImGui.errorText(s.problem ?: "")
                val hint = when (s.hint) {
                    SetupHint.INSTALL_PYTHON -> "hint.mocap.install_python"
                    SetupHint.INSTALL_MEDIAPIPE -> "hint.mocap.install_mediapipe"
                    SetupHint.MODEL_FILE -> "hint.mocap.model_file"
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
            thumbnail = runCatching { ThumbnailScaler.upload(decode(file)) }.getOrNull()
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
        const val PREVIEW_SIZE = 512
        val VIDEO_PATTERNS = listOf("*.mp4", "*.mov", "*.avi", "*.mkv", "*.webm", "*.m4v")
        val IMPORT_PATTERNS = listOf("*.bvh", "*.fbx", "*.gltf", "*.glb")
    }
}
