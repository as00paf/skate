package com.pafoid.skate.editor.project

import com.pafoid.skate.editor.settings.EditorSettings
import com.pafoid.skate.engine.utils.ExportOptions
import com.pafoid.skate.engine.utils.ExportProgress
import com.pafoid.skate.engine.utils.ExportResult
import com.pafoid.skate.engine.utils.ExportTarget
import com.pafoid.skate.engine.utils.GameJarLocator
import com.pafoid.skate.engine.utils.ProjectExporter
import java.io.File

/**
 * State of the export wizard (EDT-09): the options the user picks and the progress of the running export. It holds
 * no ImGui code so it can be unit tested; [com.pafoid.skate.editor.ui.windows.ExportWizardWindow] renders it.
 */
class ExportWizard {
    enum class Phase { EDITING, SAVING, RUNNING, SUCCEEDED, FAILED }

    /** Why the current options cannot be exported. */
    enum class Issue { INVALID_OUTPUT, GAME_JAR_MISSING }

    var target: ExportTarget = ExportTarget.WINDOWS
    var compress: Boolean = true
    var onlyReferenced: Boolean = true

    /** Output folder; blank means `<project>/builds`. */
    var outputDir: String = ""

    /** Absolute paths of the project's scene files, in display order. */
    var scenes: List<String> = emptyList()
        private set
    var defaultScene: String = ""
        private set
    private val excluded = mutableSetOf<String>()
    private var projectPath: String? = null

    var phase: Phase = Phase.EDITING
        private set
    var progress: ExportProgress? = null
        private set
    var result: ExportResult? = null
        private set

    val isBusy: Boolean get() = phase == Phase.SAVING || phase == Phase.RUNNING

    /**
     * Refreshes the scene list when the wizard opens. Choices are kept for the same project (scenes that no longer
     * exist are dropped) and reset when another project is opened.
     */
    fun load(project: Project, sceneFiles: List<File>) {
        if (project.projectPath != projectPath) {
            projectPath = project.projectPath
            excluded.clear()
            outputDir = ""
            if (!isBusy) reset()
        }
        defaultScene = File(project.defaultScene).absolutePath
        scenes = sceneFiles.map { it.absolutePath }
        excluded.retainAll(scenes.toSet())
    }

    fun isDefaultScene(scene: String): Boolean = File(scene).absolutePath == defaultScene

    /** The default scene is always included. */
    fun isIncluded(scene: String): Boolean = isDefaultScene(scene) || scene !in excluded

    fun setIncluded(scene: String, included: Boolean) {
        if (isDefaultScene(scene)) return
        if (included) excluded.remove(scene) else excluded.add(scene)
    }

    val includedSceneCount: Int get() = scenes.count(::isIncluded)

    /**
     * The export settings. With a [project], a relative output folder is taken relative to the project folder
     * (not the editor's working directory, EDT-40).
     */
    fun options(project: Project? = null): ExportOptions =
        ExportOptions(target, excluded.toSet(), compress, onlyReferenced, resolvedOutput(project))

    private fun resolvedOutput(project: Project?): String? {
        val text = outputDir.trim().ifBlank { return null }
        val file = File(text)
        if (project == null || file.isAbsolute) return text
        return File(project.getProjectDirectory(), text).path
    }

    /** Every reason the export cannot start, empty when it can. [gameJar] is the jar `gradlew gameJar` built, if any. */
    fun issues(project: Project, gameJar: File?): List<Issue> = buildList {
        // Runs every frame on whatever the user typed: a path the OS rejects (quotes, '<', '|') must not throw.
        val invalid = runCatching { ProjectExporter.validateOutput(project, ProjectExporter.outputDir(project, options(project))) != null }
            .getOrDefault(true)
        if (invalid) add(Issue.INVALID_OUTPUT)
        if (target == ExportTarget.WINDOWS && gameJar?.isFile != true) add(Issue.GAME_JAR_MISSING)
    }

    fun canStart(project: Project, gameJar: File?): Boolean = !isBusy && issues(project, gameJar).isEmpty()

    /** Called when the export request is sent: the editor saves the project first. */
    fun start() {
        phase = Phase.SAVING
        progress = null
        result = null
    }

    fun onProgress(progress: ExportProgress) {
        if (phase == Phase.SUCCEEDED || phase == Phase.FAILED) return
        this.progress = progress
        phase = Phase.RUNNING
    }

    fun onFinished(result: ExportResult) {
        this.result = result
        phase = if (result.success) Phase.SUCCEEDED else Phase.FAILED
    }

    /** Back to editing the options after a finished export. */
    fun reset() {
        phase = Phase.EDITING
        progress = null
        result = null
    }

    companion object {
        /**
         * The game jar search for this editor (AST-11): next to the editor's own jar or build, then the
         * [EditorSettings.gameJarPath] override, then `-Dskate.gameJar`. Independent of the working directory.
         */
        fun gameJarLocator(configuredPath: () -> String?): GameJarLocator = GameJarLocator(
            editorCodeSource = GameJarLocator.codeSourceOf(ExportWizard::class.java),
            configuredPath = configuredPath,
            editorBuildTime = { GameJarLocator.buildTimeOf(ExportWizard::class.java) },
        )
    }
}
