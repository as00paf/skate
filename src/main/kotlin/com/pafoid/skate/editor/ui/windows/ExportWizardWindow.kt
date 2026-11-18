package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.imgui.Tooltips
import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.imgui.EditorDialog
import com.pafoid.skate.editor.imgui.EmptyState
import com.pafoid.skate.editor.imgui.DialogFooter
import com.pafoid.skate.editor.imgui.FooterButton
import com.pafoid.skate.editor.imgui.GraphicsBackendField
import com.pafoid.skate.editor.imgui.TrailingButtons
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.imgui.label
import com.pafoid.skate.editor.commands.project.TargetFpsChoices
import com.pafoid.skate.editor.project.ExportMemory
import com.pafoid.skate.editor.project.ExportSummary
import com.pafoid.skate.editor.project.ExportWizard
import com.pafoid.skate.editor.project.ExportWizard.Phase
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.editor.systems.EditorSettingsManager
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import com.pafoid.skate.engine.assets.EngineDefaults
import com.pafoid.skate.engine.physics3d.PhysicsBackend
import com.pafoid.skate.engine.utils.AssetsPacker
import com.pafoid.skate.engine.utils.BuildMode
import com.pafoid.skate.engine.utils.ExportNotice
import com.pafoid.skate.engine.utils.ExportProgress
import com.pafoid.skate.engine.utils.ExportTarget
import com.pafoid.skate.engine.utils.GameJarSearch
import com.pafoid.skate.engine.utils.ProjectExporter
import imgui.ImGui
import imgui.type.ImFloat
import imgui.type.ImInt
import imgui.type.ImString
import java.awt.Desktop
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager

/**
 * File > Export Project (EDT-09, EDT-91): Target, Game (icon, splash, graphics and physics backends, frame cap: the
 * project's own settings, changed through undoable commands), Build (Release/Debug, compression, scenes, output) and a
 * Summary (asset count, estimated size, warnings such as a missing icon or splash), then the export's progress. Build
 * choices are remembered per project in the editor settings. The export itself runs in [com.pafoid.skate.editor.ui.handlers.ProjectActionHandler] on an IO thread;
 * progress comes back as [ProjectEvent.ExportProgressed] / [ProjectEvent.ExportFinished].
 */
class ExportWizardWindow(
    engine: Engine,
    private val projectManager: ProjectManager,
    private val settingsManager: EditorSettingsManager,
) : EditorDialog(NAME, engine.stringManager, 600f, 640f) {

    override val icon: String = Icons.EXPORT
    override val relatedWindows = listOf("window.project_settings")

    private val strings = engine.stringManager
    private val eventSystem = engine.eventSystem
    private val logger = engine.logger
    val wizard = ExportWizard(ExportMemory.inSettings({ settingsManager.editorSettings }, settingsManager::update))
    internal val summary = ExportSummary(
        runAsync = { block -> engine.jobSystem.runAsync { block() } },
        runOnMain = { block -> engine.jobSystem.runOnMain { block() } },
    )
    private val splashSeconds = ImFloat()
    private val pathBuffer = ImString(1024)
    private val customFps = ImInt()
    private val outputInput = ImString(1024)
    private var jarSearch = GameJarSearch(null, emptyList())
    private val gameJar: File? get() = jarSearch.jar
    private val jarInput = ImString(1024)
    private val jarLocator = ExportWizard.gameJarLocator { settingsManager.editorSettings.gameJarPath }
    private var lastFrame = Int.MIN_VALUE

    init {
        eventSystem.subscribe<ProjectEvent.ExportProgressed> { wizard.onProgress(it.progress) }
        eventSystem.subscribe<ProjectEvent.ExportFinished> { wizard.onFinished(it.result) }
    }

    /**
     * Standard footer (EDT-51 §2.6). Editing: [Cancel] [Start export]. Running: [Close] (the export goes on).
     * Finished: [Open folder] [Back] [Close].
     */
    override fun footerButtons(): List<FooterButton> {
        val project = projectManager.currentProject ?: return footer(FooterState.NO_PROJECT)
        val state = when {
            wizard.phase == Phase.EDITING -> FooterState.EDITING
            wizard.isBusy -> FooterState.RUNNING
            else -> FooterState.FINISHED
        }
        val outputDir = wizard.result?.outputDir ?: ProjectExporter.outputDir(project, wizard.options(project))
        return footer(
            state,
            canStart = wizard.canStart(project, gameJar, summary.snapshot?.art),
            hasOutputFolder = state == FooterState.FINISHED && outputDir.isDirectory,
            onStart = {
                wizard.start()
                eventSystem.publish(ProjectEvent.ExportRequested(wizard.options(project)))
            },
            onOpenFolder = { openFolder(outputDir) },
            onBack = { wizard.reset(); summary.clear() },
        )
    }

    override fun content() {
        val project = projectManager.currentProject
        if (project == null) {
            ImGui.beginChild("##export_no_project", 0f, ImGui.getContentRegionAvailY() - DialogFooter.height())
            EmptyState.noProject(strings, eventSystem)
            ImGui.endChild()
            return
        }
        val frame = ImGui.getFrameCount()
        if (frame > lastFrame + 1) onOpened(project)
        lastFrame = frame

        if (wizard.phase == Phase.EDITING) options(project) else status()
        wizard.remember() // saves the build choices only when they changed (EDT-91)
    }

    /** The dialog was just opened: rescan scenes and look for the game jar again. */
    private fun onOpened(project: Project) {
        wizard.load(project, ProjectExporter(logger).sceneFiles(project, wizard.options(project)))
        outputInput.set(wizard.outputDir.ifBlank { AssetsPacker.defaultOutputDir(project).path })
        wizard.outputDir = outputInput.get()
        jarInput.set(settingsManager.editorSettings.gameJarPath.orEmpty())
        jarSearch = jarLocator.locate()
    }

    private fun options(project: Project) {
        MImGui.labeledSeparator(strings.getString("lbl.export.target"))
        ExportTarget.entries.forEach { target ->
            val key = "lbl.export.target.${target.name.lowercase()}"
            if (strings.tipped(ImGui.radioButton(strings.label(key), wizard.target == target), key)) {
                wizard.target = target
            }
        }
        if (wizard.target == ExportTarget.WINDOWS) gameJarSection()

        gameSection(project)

        MImGui.labeledSeparator(strings.getString("lbl.export.section.build"))
        MImGui.propertyRow(strings.getString("lbl.export.mode"), strings.getString("lbl.export.mode.tooltip")) {
            BuildMode.entries.forEachIndexed { i, mode ->
                if (i > 0) ImGui.sameLine()
                val key = "lbl.export.mode.${mode.name.lowercase()}"
                if (strings.tipped(ImGui.radioButton(strings.label(key), wizard.mode == mode), key)) wizard.mode = mode
            }
        }
        if (ImGui.checkbox(strings.label("lbl.export.compress"), wizard.compress)) wizard.compress = !wizard.compress
        Tooltips.always(strings.getString("tip.export.compress"))
        if (ImGui.checkbox(strings.label("lbl.export.only_referenced"), wizard.onlyReferenced)) wizard.onlyReferenced = !wizard.onlyReferenced
        Tooltips.always(strings.getString("tip.export.only_referenced"))

        MImGui.labeledSeparator(strings.getString("lbl.export.scenes", wizard.includedSceneCount, wizard.scenes.size))
        sceneList(project)

        MImGui.labeledSeparator(strings.getString("lbl.export.output"))
        val browse = strings.label("btn.export.browse")
        val defaultOutput = strings.label("btn.export.default_output")
        val outputRow = TrailingButtons.begin(browse, defaultOutput)
        if (ImGui.inputText("##export_output", outputInput)) wizard.outputDir = outputInput.get()
        probe("output")
        outputRow.next()
        if (strings.tipped(ImGui.button(browse), "btn.export.browse")) browseForOutput()
        outputRow.next()
        if (strings.tipped(ImGui.button(defaultOutput), "btn.export.default_output")) {
            outputInput.set(AssetsPacker.defaultOutputDir(project).path)
            wizard.outputDir = outputInput.get()
        }

        summarySection(project)

        val issues = wizard.issues(project, gameJar, summary.snapshot?.art)
        if (ExportWizard.Issue.INVALID_OUTPUT in issues) MImGui.errorText(strings.getString("lbl.export.failure.invalid_output"))
        if (ExportWizard.Issue.ICON_UNREADABLE in issues) MImGui.errorText(strings.getString("lbl.export.issue.icon_unreadable"))
    }

    /**
     * The project settings the exported game uses (EDT-91, AST-15 §3). Each change publishes a request the service
     * turns into an undoable command; the export saves the project before packing, so the export and the project agree.
     */
    private fun gameSection(project: Project) {
        MImGui.labeledSeparator(strings.getString("lbl.export.section.game"))
        ImGui.pushTextWrapPos(0f)
        MImGui.textDisabled(strings.getString("lbl.export.game_note"))
        ImGui.popTextWrapPos()

        MImGui.propertyRow(strings.getString("lbl.export.icon"), strings.getString("lbl.export.icon.tooltip")) {
            val change = strings.label("btn.export.icon_change")
            val default = strings.label("btn.export.icon_default")
            val row = TrailingButtons.begin(change, default)
            pathField("##export_icon_path", displayPath(project, project.iconPath, "lbl.export.icon.engine_default"), "lbl.export.icon.tooltip")
            row.next()
            if (strings.tipped(ImGui.button(change), "btn.export.icon_change")) {
                chooseImage(project, "dialog.export.select_icon")?.let { eventSystem.publish(ProjectEvent.SetProjectIconRequested(it)) }
            }
            row.next()
            if (strings.tipped(ImGui.button(default), "btn.export.icon_default")) {
                eventSystem.publish(ProjectEvent.SetProjectIconRequested(EngineDefaults.Textures.APP_ICON))
            }
        }

        MImGui.propertyRow(strings.getString("lbl.export.splash"), strings.getString("lbl.export.splash.tooltip")) {
            val none = project.splashSeconds <= 0f
            val change = strings.label("btn.export.splash_change")
            val noneLabel = strings.label("btn.export.splash_none")
            val default = strings.label("btn.export.splash_default")
            val row = TrailingButtons.begin(change, noneLabel, default)
            pathField(
                "##export_splash_path",
                if (none) strings.getString("lbl.export.splash.none") else displayPath(project, project.splashPath, "lbl.export.splash.engine_default"),
                "lbl.export.splash.tooltip",
            )
            row.next()
            if (strings.tipped(ImGui.button(change), "btn.export.splash_change")) {
                chooseImage(project, "dialog.export.select_splash")?.let { path ->
                    eventSystem.publish(ProjectEvent.SetProjectSplashRequested(path, project.splashSeconds.takeIf { it > 0f } ?: Project.DEFAULT_SPLASH_SECONDS))
                }
            }
            row.next()
            if (strings.tipped(ImGui.button(noneLabel), "btn.export.splash_none")) {
                eventSystem.publish(ProjectEvent.SetProjectSplashRequested(project.splashPath, 0f))
            }
            row.next()
            if (strings.tipped(ImGui.button(default), "btn.export.splash_default")) {
                eventSystem.publish(ProjectEvent.SetProjectSplashRequested("", Project.DEFAULT_SPLASH_SECONDS))
            }
        }
        if (project.splashSeconds > 0f) {
            MImGui.propertyRow(strings.getString("lbl.export.splash_seconds"), strings.getString("lbl.export.splash_seconds.tooltip")) {
                if (!ImGui.isAnyItemActive()) splashSeconds.set(project.splashSeconds)
                ImGui.setNextItemWidth(fit(ImGui.getFontSize() * SLIDER_EMS))
                ImGui.sliderFloat("##export_splash_seconds", splashSeconds.data, 0.5f, Project.MAX_SPLASH_SECONDS, "%.1f s")
                probe("splash_seconds")
                Tooltips.show(strings, "lbl.export.splash_seconds.tooltip")
                if (ImGui.isItemDeactivatedAfterEdit()) {
                    eventSystem.publish(ProjectEvent.SetProjectSplashRequested(project.splashPath, splashSeconds.get()))
                }
            }
        }

        GraphicsBackendField.render(strings, project.graphicsBackend)?.let { eventSystem.publish(ProjectEvent.SetGraphicsBackendRequested(it)) }

        MImGui.propertyRow(strings.getString("lbl.export.physics_backend"), strings.getString("lbl.export.physics_backend.tooltip")) {
            ImGui.setNextItemWidth(fit(comboWidth(PhysicsBackend.entries.map { strings.getString(physicsKey(it)) })))
            if (ImGui.beginCombo("##export_physics_backend", strings.getString(physicsKey(project.physicsBackend)))) {
                PhysicsBackend.entries.forEach { backend ->
                    if (ImGui.selectable(strings.getString(physicsKey(backend)), backend == project.physicsBackend) && backend != project.physicsBackend) {
                        eventSystem.publish(ProjectEvent.SetProjectPhysicsBackendRequested(backend))
                    }
                    Tooltips.show(strings, "lbl.export.physics_backend.tooltip")
                }
                ImGui.endCombo()
            }
            probe("physics_backend")
            Tooltips.show(strings, "lbl.export.physics_backend.tooltip")
        }
        if (project.physicsBackend == PhysicsBackend.JOLT) MImGui.warningText(strings.getString("lbl.export.warning.physics_jolt_fallback"))

        MImGui.propertyRow(strings.getString("lbl.export.target_fps"), strings.getString("lbl.export.target_fps.tooltip")) {
            val custom = !TargetFpsChoices.isPreset(project.targetFps)
            val choices = TargetFpsChoices.PRESETS.map(::fpsLabel) + strings.getString("lbl.export.target_fps.custom")
            ImGui.setNextItemWidth(fit(comboWidth(choices)))
            val shown = if (custom) strings.getString("lbl.export.target_fps.custom") else fpsLabel(project.targetFps)
            if (ImGui.beginCombo("##export_target_fps", shown)) {
                TargetFpsChoices.PRESETS.forEach { fps ->
                    if (ImGui.selectable(fpsLabel(fps), !custom && fps == project.targetFps)) eventSystem.publish(ProjectEvent.SetProjectTargetFpsRequested(fps))
                    Tooltips.show(strings, "lbl.export.target_fps.tooltip")
                }
                if (ImGui.selectable(strings.getString("lbl.export.target_fps.custom"), custom) && !custom) {
                    eventSystem.publish(ProjectEvent.SetProjectTargetFpsRequested(CUSTOM_FPS_START))
                }
                Tooltips.show(strings, "lbl.export.target_fps.tooltip")
                ImGui.endCombo()
            }
            probe("target_fps")
            Tooltips.show(strings, "lbl.export.target_fps.tooltip")
            if (custom) {
                // Next to the combo when it fits, else on the next line (never cut at 150 % or with wide spacing).
                val width = ImGui.getFontSize() * CUSTOM_FPS_EMS
                val right = ImGui.getWindowContentRegionMaxX() + ImGui.getWindowPosX()
                if (TrailingButtons.fitsOnLine(ImGui.getItemRectMaxX(), width, ImGui.getStyle().itemSpacingX, right)) ImGui.sameLine()
                if (!ImGui.isAnyItemActive()) customFps.set(project.targetFps)
                ImGui.setNextItemWidth(fit(width))
                ImGui.inputInt("##export_custom_fps", customFps)
                probe("custom_fps")
                Tooltips.show(strings, "lbl.export.target_fps.tooltip")
                if (ImGui.isItemDeactivatedAfterEdit()) eventSystem.publish(ProjectEvent.SetProjectTargetFpsRequested(customFps.get()))
            }
        }
    }

    /** Asset count, estimated size and warnings, recomputed on a worker when an input changes (EDT-91). */
    private fun summarySection(project: Project) {
        MImGui.labeledSeparator(strings.getString("lbl.export.section.summary"))
        summary.refresh(project, wizard.options(project), gameJar?.takeIf { jarSearch.stale && wizard.target == ExportTarget.WINDOWS })
        val snap = summary.snapshot
        if (snap == null) {
            MImGui.textDisabled(strings.getString("lbl.export.summary.computing"))
            return
        }
        ImGui.text(strings.getString("lbl.export.summary.assets", snap.estimate.assetCount, formatBytes(snap.estimate.bytes, strings)))
        if (summary.computing) {
            ImGui.sameLine()
            MImGui.textDisabled(strings.getString("lbl.export.summary.updating"))
        }
        warningList(snap.warnings, "lbl.export.summary.no_warnings")
    }

    /** Translated warnings (`lbl.export.warning.*`), one per line, or [emptyKey] when there are none. */
    private fun warningList(warnings: List<ExportNotice>, emptyKey: String?) {
        if (warnings.isEmpty()) {
            emptyKey?.let { MImGui.textDisabled(strings.getString(it)) }
            return
        }
        ImGui.pushTextWrapPos(0f)
        warnings.forEach { MImGui.warningText(warningText(strings, it)) }
        ImGui.popTextWrapPos()
    }

    /** A read-only path field (the row's width is set by [TrailingButtons.begin]). */
    private fun pathField(id: String, text: String, tooltipKey: String) {
        pathBuffer.set(text)
        ImGui.inputText(id, pathBuffer, imgui.flag.ImGuiInputTextFlags.ReadOnly)
        probe(id)
        Tooltips.show(strings, tooltipKey)
    }

    /** A combo wide enough for its longest [labels] (text, frame padding and the arrow button), from the font. */
    private fun comboWidth(labels: List<String>): Float =
        (labels.maxOfOrNull { ImGui.calcTextSize(it).x } ?: 0f) + ImGui.getStyle().framePaddingX * 2f + ImGui.getFrameHeight()

    /** [width], but never past what is left on the line. */
    private fun fit(width: Float): Float = width.coerceAtMost(ImGui.getContentRegionAvailX()).coerceAtLeast(1f)

    /** Reports the last item's right edge against the clip rectangle (render tests: nothing is cut). */
    private fun probe(name: String) {
        fieldProbe?.invoke(name, ImGui.getItemRectMaxX(), ImGui.getWindowDrawList().clipRectMaxX)
    }

    private fun fpsLabel(fps: Int): String =
        if (fps <= 0) strings.getString("lbl.export.target_fps.uncapped") else strings.getString("lbl.export.target_fps.value", fps)

    /** [path] for display: project-relative, an `engine:` reference as is, or [defaultKey]'s text when blank. */
    private fun displayPath(project: Project, path: String, defaultKey: String): String = when {
        path.isBlank() -> strings.getString(defaultKey)
        EngineDefaults.isReference(path) -> path
        else -> File(path).let { f -> if (f.isAbsolute) f.relativeToOrSelf(project.getProjectDirectory()).invariantSeparatorsPath else path }
    }

    /** A project image picked with the system dialog (absolute path; saved project-relative), or null. */
    private fun chooseImage(project: Project, titleKey: String): String? = try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        val start = File(project.getProjectDirectory(), "Assets/Textures").takeIf { it.isDirectory } ?: project.getProjectDirectory()
        val chooser = JFileChooser(start)
        chooser.fileSelectionMode = JFileChooser.FILES_ONLY
        chooser.fileFilter = javax.swing.filechooser.FileNameExtensionFilter(strings.getString("dialog.export.images"), "png", "jpg", "jpeg")
        chooser.dialogTitle = strings.getString(titleKey)
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile.absolutePath else null
    } catch (e: Exception) {
        logger.logEditor("Error choosing an image: ${e.message}", LogLevel.ERROR)
        null
    }

    /** Where the game jar was found (or every place searched), a stale-jar warning and the optional override (AST-11). */
    private fun gameJarSection() {
        val jar = gameJar
        if (jar != null && jar.isFile) {
            MImGui.textDisabled(strings.getString("lbl.export.jar_found", jar.path))
            if (jarSearch.stale) MImGui.warningText(strings.getString("lbl.export.jar_stale"))
        } else {
            MImGui.errorText(strings.getString("lbl.export.jar_missing", ProjectExporter.searchedList(jarSearch)))
        }
        ImGui.text(strings.getString("lbl.export.jar_override"))
        val jarBrowse = strings.label("btn.export.jar_browse")
        val jarClear = strings.label("btn.export.jar_clear")
        val jarRow = TrailingButtons.begin(jarBrowse, jarClear)
        ImGui.inputText("##export_game_jar", jarInput)
        probe("game_jar")
        if (ImGui.isItemDeactivatedAfterEdit()) setJarOverride(jarInput.get())
        jarRow.next()
        if (strings.tipped(ImGui.button(jarBrowse), "btn.export.jar_browse")) browseForJar()
        jarRow.next()
        if (strings.tipped(ImGui.button(jarClear), "btn.export.jar_clear")) setJarOverride("")
    }

    private fun setJarOverride(path: String) {
        jarInput.set(path)
        settingsManager.updateGameJarPath(path)
        jarSearch = jarLocator.locate()
    }

    private fun browseForJar() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
            val chooser = JFileChooser(gameJar?.parentFile)
            chooser.fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
            chooser.dialogTitle = strings.getString("dialog.export.select_jar")
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) setJarOverride(chooser.selectedFile.absolutePath)
        } catch (e: Exception) {
            logger.logEditor("Error browsing for the game jar: ${e.message}", LogLevel.ERROR)
        }
    }

    private fun sceneList(project: Project) {
        val projectDir = File(project.projectPath).parentFile
        if (wizard.scenes.isEmpty()) {
            MImGui.textDisabled(strings.getString("lbl.export.scenes.none"))
            return
        }
        ImGui.beginChild("##export_scenes", 0f, 140f, true)
        wizard.scenes.forEach { scene ->
            val name = File(scene).relativeToOrSelf(projectDir).path
            val isDefault = wizard.isDefaultScene(scene)
            ImGui.beginDisabled(isDefault)
            if (ImGui.checkbox("$name###export_scene_$scene", wizard.isIncluded(scene))) {
                wizard.setIncluded(scene, !wizard.isIncluded(scene))
            }
            ImGui.endDisabled()
            if (isDefault) {
                ImGui.sameLine()
                MImGui.textDisabled(strings.getString("lbl.export.scenes.default"))
            }
        }
        ImGui.endChild()
    }

    private fun status() {
        when (wizard.phase) {
            Phase.SAVING -> {
                ImGui.text(strings.getString("lbl.export.saving"))
                ImGui.progressBar(0f, -1f, 0f, "")
            }
            Phase.RUNNING -> wizard.progress?.let { progressView(it) }
            Phase.SUCCEEDED -> {
                val result = wizard.result ?: return
                MImGui.successText(
                    strings.getString("lbl.export.succeeded", result.assetCount, formatBytes(result.packedBytes, strings), result.outputDir.path)
                )
                ImGui.progressBar(1f, -1f, 0f, "100%")
                if (result.warnings.isNotEmpty()) {
                    MImGui.labeledSeparator(strings.getString("lbl.export.warnings"))
                    warningList(result.warnings, null)
                }
            }
            Phase.FAILED -> {
                val result = wizard.result ?: return
                MImGui.errorText(strings.getString("lbl.export.failure.${result.failure?.name?.lowercase()}"))
                result.detail?.let { MImGui.textDisabled(strings.getString(it.key, *result.detailArgs.toTypedArray())) }
            }
            Phase.EDITING -> Unit
        }
    }

    private fun progressView(progress: ExportProgress) {
        ImGui.text(strings.getString("lbl.export.step.${progress.step.name.lowercase()}"))
        ImGui.progressBar(progress.overall, -1f, 0f, "${(progress.overall * 100).toInt()}%")
        if (progress.detail.isNotBlank()) MImGui.textDisabled(progress.detail)
    }

    private fun browseForOutput() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
            val chooser = JFileChooser(outputInput.get().takeIf { File(it).isDirectory })
            chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            chooser.dialogTitle = strings.getString("dialog.export.select_output")
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                outputInput.set(chooser.selectedFile.absolutePath)
                wizard.outputDir = outputInput.get()
            }
        } catch (e: Exception) {
            logger.logEditor("Error browsing for the export folder: ${e.message}", LogLevel.ERROR)
        }
    }

    private fun openFolder(dir: File) {
        try {
            Desktop.getDesktop().open(dir)
        } catch (e: Exception) {
            logger.logEditor("Could not open ${dir.path}: ${e.message}", LogLevel.ERROR)
        }
    }

    /** What the footer offers (EDT-51 §2.6). */
    enum class FooterState { NO_PROJECT, EDITING, RUNNING, FINISHED }

    companion object {
        const val NAME = "window.export_wizard"

        /** Where "Custom" starts in the frame cap combo. */
        const val CUSTOM_FPS_START = 90

        /** Field widths in multiples of the font size (EDT-91 review: no fixed pixels). */
        const val SLIDER_EMS = 8f
        const val CUSTOM_FPS_EMS = 6f

        /** Test hook: (field, its right edge, the clip rectangle's right edge) for every new field drawn. */
        internal var fieldProbe: ((String, Float, Float) -> Unit)? = null

        fun physicsKey(backend: PhysicsBackend): String = "lbl.export.physics.${backend.name.lowercase()}"

        /** An export warning in the editor language, its args filling the key's placeholders. */
        fun warningText(strings: StringManager, notice: ExportNotice): String =
            strings.getString(notice.warning.key, *notice.args.toTypedArray())

        /** No project or running: [Close]. Editing: [Cancel] [Export]. Finished: [Open folder] [Back] [Close]. */
        internal fun footer(
            state: FooterState,
            canStart: Boolean = false,
            hasOutputFolder: Boolean = false,
            onStart: () -> Unit = {},
            onOpenFolder: () -> Unit = {},
            onBack: () -> Unit = {},
        ): List<FooterButton> = when (state) {
            FooterState.NO_PROJECT, FooterState.RUNNING -> listOf(FooterButton("btn.close", FooterButton.Role.CANCEL))
            FooterState.EDITING -> listOf(
                FooterButton("btn.cancel", FooterButton.Role.CANCEL),
                FooterButton("btn.export.start", FooterButton.Role.PRIMARY, enabled = canStart, closes = false, onClick = onStart),
            )
            FooterState.FINISHED -> buildList {
                if (hasOutputFolder) add(FooterButton("btn.export.open_folder", FooterButton.Role.SECONDARY, closes = false, onClick = onOpenFolder))
                add(FooterButton("btn.export.back", FooterButton.Role.SECONDARY, closes = false, onClick = onBack))
                add(FooterButton("btn.close", FooterButton.Role.CANCEL))
            }
        }

        fun formatBytes(bytes: Long, strings: StringManager): String = when {
            bytes >= 1024L * 1024 -> strings.getString("lbl.export.size.mb", bytes / (1024.0 * 1024))
            bytes >= 1024 -> strings.getString("lbl.export.size.kb", bytes / 1024.0)
            else -> strings.getString("lbl.export.size.bytes", bytes)
        }
    }
}
