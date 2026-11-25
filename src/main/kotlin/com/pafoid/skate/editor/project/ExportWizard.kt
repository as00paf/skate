package com.pafoid.skate.editor.project

import com.pafoid.skate.editor.settings.EditorSettings
import com.pafoid.skate.engine.utils.BuildMode
import com.pafoid.skate.engine.utils.ExportArt
import com.pafoid.skate.engine.utils.ExportOptions
import com.pafoid.skate.engine.utils.ExportProgress
import com.pafoid.skate.engine.utils.ExportResult
import com.pafoid.skate.engine.utils.ExportTarget
import com.pafoid.skate.engine.utils.GameJarLocator
import com.pafoid.skate.engine.utils.ProjectExporter
import kotlinx.serialization.Serializable
import java.io.File

/**
 * The export choices remembered for one project (AST-29, `docs/design/AST-15.md` §2): build choices, so they live in
 * the editor's settings keyed by project path, not in the `.skateproject`. [excludedScenes] are absolute paths.
 */
@Serializable
data class ExportPreferences(
    val target: ExportTarget = ExportTarget.WINDOWS,
    val mode: BuildMode = BuildMode.RELEASE,
    val compress: Boolean = true,
    val onlyReferenced: Boolean = true,
    val outputDir: String = "",
    val excludedScenes: Set<String> = emptySet(),
)

/** Where [ExportWizard] keeps [ExportPreferences] between sessions. */
interface ExportMemory {
    fun recall(projectPath: String): ExportPreferences?
    fun remember(projectPath: String, preferences: ExportPreferences)

    /** Remembers nothing (tests, or no settings). */
    object None : ExportMemory {
        override fun recall(projectPath: String): ExportPreferences? = null
        override fun remember(projectPath: String, preferences: ExportPreferences) = Unit
    }

    companion object {
        /** Keeps the preferences in [EditorSettings.exportPreferences]; [update] must save the settings. */
        fun inSettings(settings: () -> EditorSettings, update: ((EditorSettings) -> EditorSettings) -> Unit): ExportMemory =
            object : ExportMemory {
                override fun recall(projectPath: String) = settings().exportPreferences[projectPath]
                override fun remember(projectPath: String, preferences: ExportPreferences) {
                    if (settings().exportPreferences[projectPath] == preferences) return
                    update { it.copy(exportPreferences = it.exportPreferences + (projectPath to preferences)) }
                }
            }
    }
}

/**
 * State of the export wizard (EDT-09): the options the user picks and the progress of the running export. It holds
 * no ImGui code so it can be unit tested; [com.pafoid.skate.editor.ui.windows.ExportWizardWindow] renders it.
 */
class ExportWizard(private val memory: ExportMemory = ExportMemory.None) {
    enum class Phase { EDITING, SAVING, RUNNING, SUCCEEDED, FAILED }

    /**
     * Why the current options cannot be exported. [ICON_UNREADABLE]: not even the engine icon can be read (a broken
     * install); a missing project icon is only a warning ([ExportArt.notices]).
     */
    enum class Issue { INVALID_OUTPUT, GAME_JAR_MISSING, ICON_UNREADABLE }

    var target: ExportTarget = ExportTarget.WINDOWS
    var mode: BuildMode = BuildMode.RELEASE
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
     * exist are dropped); another project gets its remembered choices ([ExportMemory]), else the defaults.
     */
    fun load(project: Project, sceneFiles: List<File>) {
        if (project.projectPath != projectPath) {
            projectPath = project.projectPath
            applyPreferences(memory.recall(project.projectPath) ?: ExportPreferences())
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
        ExportOptions(target, excluded.toSet(), compress, onlyReferenced, resolvedOutput(project), mode)

    /** The current choices as remembered per project. */
    fun preferences(): ExportPreferences = ExportPreferences(target, mode, compress, onlyReferenced, outputDir.trim(), excluded.toSet())

    private fun applyPreferences(preferences: ExportPreferences) {
        target = preferences.target
        mode = preferences.mode
        compress = preferences.compress
        onlyReferenced = preferences.onlyReferenced
        outputDir = preferences.outputDir
        excluded.clear()
        excluded += preferences.excludedScenes
    }

    private fun resolvedOutput(project: Project?): String? {
        val text = outputDir.trim().ifBlank { return null }
        val file = File(text)
        if (project == null || file.isAbsolute) return text
        return File(project.getProjectDirectory(), text).path
    }

    /**
     * Every reason the export cannot start, empty when it can. [gameJar] is the jar `gradlew gameJar` built, if any;
     * [art] the icon and splash the export would ship (`ProjectExporter.art`, resolved when the wizard opens).
     */
    fun issues(project: Project, gameJar: File?, art: ExportArt? = null): List<Issue> = buildList {
        // Runs every frame on whatever the user typed: a path the OS rejects (quotes, '<', '|') must not throw.
        val invalid = runCatching { ProjectExporter.validateOutput(project, ProjectExporter.outputDir(project, options(project))) != null }
            .getOrDefault(true)
        if (invalid) add(Issue.INVALID_OUTPUT)
        if (target == ExportTarget.WINDOWS && gameJar?.isFile != true) add(Issue.GAME_JAR_MISSING)
        if (art != null && art.iconPath.isBlank()) add(Issue.ICON_UNREADABLE)
    }

    fun canStart(project: Project, gameJar: File?, art: ExportArt? = null): Boolean = !isBusy && issues(project, gameJar, art).isEmpty()

    /**
     * Remembers the current choices for the loaded project (EDT-91): the dialog calls it every frame, and
     * [ExportMemory] only saves when something changed, so a choice survives Cancel and an editor restart.
     */
    fun remember() {
        projectPath?.let { memory.remember(it, preferences()) }
    }

    /** Called when the export request is sent: the editor saves the project first; the choices are remembered. */
    fun start() {
        remember()
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
