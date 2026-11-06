package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.imgui.EditorDialog
import com.pafoid.skate.editor.imgui.EmptyState
import com.pafoid.skate.editor.imgui.DialogFooter
import com.pafoid.skate.editor.imgui.FooterButton
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.imgui.label
import com.pafoid.skate.editor.project.ExportWizard
import com.pafoid.skate.editor.project.ExportWizard.Phase
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.editor.systems.EditorSettingsManager
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import com.pafoid.skate.engine.utils.AssetsPacker
import com.pafoid.skate.engine.utils.ExportProgress
import com.pafoid.skate.engine.utils.ExportTarget
import com.pafoid.skate.engine.utils.GameJarSearch
import com.pafoid.skate.engine.utils.ProjectExporter
import imgui.ImGui
import imgui.type.ImString
import java.awt.Desktop
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager

/**
 * File > Export Project (EDT-09): picks the target, scenes, compression and output folder, then shows the export's
 * progress. The export itself runs in [com.pafoid.skate.editor.ui.handlers.ProjectActionHandler] on an IO thread;
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
    val wizard = ExportWizard()
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
            canStart = wizard.canStart(project, gameJar),
            hasOutputFolder = state == FooterState.FINISHED && outputDir.isDirectory,
            onStart = {
                wizard.start()
                eventSystem.publish(ProjectEvent.ExportRequested(wizard.options(project)))
            },
            onOpenFolder = { openFolder(outputDir) },
            onBack = { wizard.reset() },
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
        backendLine(project)

        MImGui.labeledSeparator(strings.getString("lbl.export.scenes", wizard.includedSceneCount, wizard.scenes.size))
        sceneList(project)

        MImGui.labeledSeparator(strings.getString("lbl.export.options"))
        if (ImGui.checkbox(strings.label("lbl.export.compress"), wizard.compress)) wizard.compress = !wizard.compress
        if (ImGui.isItemHovered()) ImGui.setTooltip(strings.getString("tip.export.compress"))
        if (ImGui.checkbox(strings.label("lbl.export.only_referenced"), wizard.onlyReferenced)) wizard.onlyReferenced = !wizard.onlyReferenced
        if (ImGui.isItemHovered()) ImGui.setTooltip(strings.getString("tip.export.only_referenced"))

        MImGui.labeledSeparator(strings.getString("lbl.export.output"))
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - 200f)
        if (ImGui.inputText("##export_output", outputInput)) wizard.outputDir = outputInput.get()
        ImGui.sameLine()
        if (strings.tipped(ImGui.button(strings.label("btn.export.browse")), "btn.export.browse")) browseForOutput()
        ImGui.sameLine()
        if (strings.tipped(ImGui.button(strings.label("btn.export.default_output")), "btn.export.default_output")) {
            outputInput.set(AssetsPacker.defaultOutputDir(project).path)
            wizard.outputDir = outputInput.get()
        }

        val issues = wizard.issues(project, gameJar)
        if (ExportWizard.Issue.INVALID_OUTPUT in issues) MImGui.errorText(strings.getString("lbl.export.failure.invalid_output"))
    }

    /** The project's graphics backend the exported game starts with (AST-12; chosen in the project settings, EDT-43). */
    private fun backendLine(project: Project) {
        val backend = project.graphicsBackend
        MImGui.textDisabled(strings.getString("lbl.export.backend", strings.getString(backend.labelKey)))
        if (ImGui.isItemHovered()) ImGui.setTooltip(strings.getString("lbl.export.backend.tooltip"))
        if (backend.experimental) MImGui.warningText(strings.getString("lbl.export.backend_experimental"))
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
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - 200f)
        ImGui.inputText("##export_game_jar", jarInput)
        if (ImGui.isItemDeactivatedAfterEdit()) setJarOverride(jarInput.get())
        ImGui.sameLine()
        if (strings.tipped(ImGui.button(strings.label("btn.export.jar_browse")), "btn.export.jar_browse")) browseForJar()
        ImGui.sameLine()
        if (strings.tipped(ImGui.button(strings.label("btn.export.jar_clear")), "btn.export.jar_clear")) setJarOverride("")
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
