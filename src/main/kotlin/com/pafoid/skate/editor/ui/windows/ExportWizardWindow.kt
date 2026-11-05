package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.imgui.EditorDialog
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.imgui.label
import com.pafoid.skate.editor.project.ExportWizard
import com.pafoid.skate.editor.project.ExportWizard.Phase
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import com.pafoid.skate.engine.utils.AssetsPacker
import com.pafoid.skate.engine.utils.ExportProgress
import com.pafoid.skate.engine.utils.ExportTarget
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
) : EditorDialog(NAME, engine.stringManager, 600f, 640f) {

    private val strings = engine.stringManager
    private val eventSystem = engine.eventSystem
    private val logger = engine.logger
    val wizard = ExportWizard()
    private val outputInput = ImString(1024)
    private var gameJar: File? = null
    private var lastFrame = Int.MIN_VALUE

    init {
        eventSystem.subscribe<ProjectEvent.ExportProgressed> { wizard.onProgress(it.progress) }
        eventSystem.subscribe<ProjectEvent.ExportFinished> { wizard.onFinished(it.result) }
    }

    override fun content() {
        val project = projectManager.currentProject
        if (project == null) {
            MImGui.textDisabled(strings.getString("lbl.export.no_project"))
            return
        }
        val frame = ImGui.getFrameCount()
        if (frame > lastFrame + 1) onOpened(project)
        lastFrame = frame

        if (wizard.phase == Phase.EDITING) options(project) else status(project)
    }

    /** The dialog was just opened: rescan scenes and look for the game jar again. */
    private fun onOpened(project: Project) {
        wizard.load(project, ProjectExporter(logger).sceneFiles(project, wizard.options()))
        outputInput.set(wizard.outputDir.ifBlank { AssetsPacker.defaultOutputDir(project).path })
        wizard.outputDir = outputInput.get()
        gameJar = ProjectExporter.findGameJar()
    }

    private fun options(project: Project) {
        MImGui.labeledSeparator(strings.getString("lbl.export.target"))
        ExportTarget.entries.forEach { target ->
            if (ImGui.radioButton(strings.label("lbl.export.target.${target.name.lowercase()}"), wizard.target == target)) {
                wizard.target = target
            }
        }
        if (wizard.target == ExportTarget.WINDOWS) {
            val jar = gameJar
            if (jar != null && jar.isFile) MImGui.textDisabled(strings.getString("lbl.export.jar_found", jar.path))
            else MImGui.errorText(strings.getString("lbl.export.jar_missing"))
        }

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
        if (ImGui.button(strings.label("btn.export.browse"))) browseForOutput()
        ImGui.sameLine()
        if (ImGui.button(strings.label("btn.export.default_output"))) {
            outputInput.set(AssetsPacker.defaultOutputDir(project).path)
            wizard.outputDir = outputInput.get()
        }

        val issues = wizard.issues(project, gameJar)
        if (ExportWizard.Issue.INVALID_OUTPUT in issues) MImGui.errorText(strings.getString("lbl.export.failure.invalid_output"))

        ImGui.spacing()
        ImGui.beginDisabled(!wizard.canStart(project, gameJar))
        if (ImGui.button(strings.label("btn.export.start"), 160f, 0f)) {
            wizard.start()
            eventSystem.publish(ProjectEvent.ExportRequested(wizard.options()))
        }
        ImGui.endDisabled()
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

    private fun status(project: Project) {
        when (wizard.phase) {
            Phase.SAVING -> {
                ImGui.text(strings.getString("lbl.export.saving"))
                ImGui.progressBar(0f, -1f, 0f, "")
            }
            Phase.RUNNING -> wizard.progress?.let { progressView(it) }
            Phase.SUCCEEDED -> {
                val result = wizard.result ?: return
                MImGui.successText(
                    strings.getString("lbl.export.succeeded", result.assetCount, formatBytes(result.packedBytes), result.outputDir.path)
                )
                ImGui.progressBar(1f, -1f, 0f, "100%")
            }
            Phase.FAILED -> {
                val result = wizard.result ?: return
                MImGui.errorText(strings.getString("lbl.export.failure.${result.failure?.name?.lowercase()}"))
                if (result.detail.isNotBlank()) MImGui.textDisabled(result.detail)
            }
            Phase.EDITING -> Unit
        }
        if (wizard.isBusy) return
        ImGui.spacing()
        val outputDir = wizard.result?.outputDir ?: ProjectExporter.outputDir(project, wizard.options())
        if (outputDir.isDirectory) {
            if (ImGui.button(strings.label("btn.export.open_folder"))) openFolder(outputDir)
            ImGui.sameLine()
        }
        if (ImGui.button(strings.label("btn.export.back"))) wizard.reset()
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

    companion object {
        const val NAME = "window.export_wizard"

        fun formatBytes(bytes: Long): String = when {
            bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
            bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
