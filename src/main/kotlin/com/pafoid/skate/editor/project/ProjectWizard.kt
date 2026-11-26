package com.pafoid.skate.editor.project

import com.pafoid.skate.engine.physics3d.PhysicsBackend
import com.pafoid.skate.engine.render.rhi.GraphicsBackend
import com.pafoid.skate.templates.NewProjectSettings
import com.pafoid.skate.templates.ProjectTemplate
import com.pafoid.skate.templates.TemplateRegistry
import java.io.File

class ProjectWizard {

    companion object {
        private val INVALID_NAME_CHARS = Regex("[<>:\"/\\\\|?*]")
    }

    var projectName: String = ""
        private set

    var projectPath: String = ""
        private set

    /** Backend of the new project's exported games (EDT-43); the editor always uses OpenGL. */
    var graphicsBackend: GraphicsBackend = GraphicsBackend.DEFAULT

    /**
     * EDT-70: copy the engine default assets into the new project (on by default, AST-26 decision 4), or reference
     * them. Applied once when the project is created; the project does not store it (EDT-59 Q2).
     */
    var copyEngineDefaults: Boolean = true

    /** Id of the selected template (EDT-59 §3); Skate Game is preselected. */
    var templateId: String = TemplateRegistry.DEFAULT_ID

    /** Frame cap of the exported game: 0 is uncapped, else [Project.MIN_TARGET_FPS]..[Project.MAX_TARGET_FPS]. */
    var targetFps: Int = 0

    var physicsBackend: PhysicsBackend = PhysicsBackend.BULLET

    /** Optional PNG/JPG splash image; null keeps the engine's. */
    var splashImage: File? = null

    var includeAiFiles: Boolean = false

    var versionControl: Boolean = false

    val template: ProjectTemplate get() = TemplateRegistry.get(templateId) ?: TemplateRegistry.require(TemplateRegistry.DEFAULT_ID)

    /** The wizard's choices as the settings the shared [com.pafoid.skate.templates.ProjectCreator] takes. */
    fun settings(): NewProjectSettings = NewProjectSettings(
        template = templateId,
        targetFps = targetFps,
        physicsBackend = physicsBackend,
        graphicsBackend = graphicsBackend,
        includeAiFiles = includeAiFiles,
        splashImage = splashImage,
        copyEngineDefaults = copyEngineDefaults,
        versionControl = versionControl,
    )

    fun setProjectName(name: String) {
        projectName = name.trim()
    }

    fun setProjectLocation(path: String) {
        projectPath = path.trim()
    }

    fun canCreate(): Boolean {
        return isProjectNameValid() && isProjectPathValid() && !projectFileExists() &&
            NewProjectSettings.isValidTargetFps(targetFps) && NewProjectSettings.isValidSplash(splashImage)
    }

    /** `<name>.skateproject` already exists in the target folder: creation is blocked (EDT-59 answer 6). */
    fun projectFileExists(): Boolean =
        isProjectNameValid() && File(getProjectDirectory(), "$projectName.skateproject").exists()

    /** The target folder exists and holds other files: only a warning (EDT-59 decision 3). */
    fun isTargetFolderNonEmpty(): Boolean =
        isProjectNameValid() && !projectFileExists() && getProjectDirectory().list()?.isNotEmpty() == true

    fun isTargetFpsValid(): Boolean = NewProjectSettings.isValidTargetFps(targetFps)

    fun isSplashValid(): Boolean = NewProjectSettings.isValidSplash(splashImage)

    fun isProjectNameValid(): Boolean {
        if (projectName.isBlank()) return false
        return !INVALID_NAME_CHARS.containsMatchIn(projectName)
    }

    fun isProjectPathValid(): Boolean {
        if (projectPath.isBlank()) return false

        val folder = File(projectPath)
        return folder.exists() && folder.isDirectory && folder.canWrite()
    }

    fun getProjectFilePath(): String {
        return File(projectPath, "$projectName.skateproject").absolutePath
    }

    fun getProjectDirectory(): File {
        return File(projectPath, projectName)
    }

    /** What the selected template creates, for the structure preview. */
    fun getProjectStructureItems(): List<ProjectStructureItem> = template.structure(projectName.ifBlank { "Project" })

    fun reset() {
        projectName = ""
        projectPath = ""
        templateId = TemplateRegistry.DEFAULT_ID
        targetFps = 0
        physicsBackend = PhysicsBackend.BULLET
        splashImage = null
        includeAiFiles = false
        versionControl = false
    }

    fun resetForNewProject() = reset()
}
