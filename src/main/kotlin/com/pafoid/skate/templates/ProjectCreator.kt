package com.pafoid.skate.templates

import com.pafoid.skate.editor.project.EngineAssetCopier
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.editor.project.ProjectWizard
import com.pafoid.skate.editor.vcs.JGitVcsService
import com.pafoid.skate.editor.vcs.VcsAuthor
import com.pafoid.skate.editor.vcs.VcsService
import com.pafoid.skate.engine.assets.AssetFolder
import com.pafoid.skate.engine.assets.EngineDefaults
import com.pafoid.skate.engine.assets.PrefabsGenerator
import com.pafoid.skate.engine.assets.ProjectLayout
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.random.RandomService
import org.joml.Vector2i
import java.io.File

class ProjectCreationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The one way a project is created (EDT-59 §3.3): the editor, `skate create`/`skate sample` and the MCP server all go
 * through [prepare] then [finish], so they cannot drift. [prepare] only writes files and is safe on a background
 * thread; [finish] builds the scenes (main thread in the editor) and writes the `.skateproject`.
 */
class ProjectCreator(
    private val serializer: Serializer,
    private val copier: EngineAssetCopier = EngineAssetCopier(),
    private val vcsFor: (File) -> VcsService = { JGitVcsService(it) },
    private val author: () -> VcsAuthor = ::defaultAuthor,
) {
    companion object {
        const val SPLASH_NAME = "splash"
        const val FIRST_COMMIT_MESSAGE = "Create project from the %s template"

        fun projectFile(projectDir: File, name: String): File = File(projectDir, "$name.skateproject")

        /** The OS user, like the VCS panel's fallback when Git has no `user.name` (EDT-60). */
        fun defaultAuthor(): VcsAuthor {
            val user = System.getProperty("user.name")?.takeIf { it.isNotBlank() } ?: "Skate"
            return VcsAuthor(user, "$user@users.noreply.skate")
        }
    }

    /** Why [name] in [parent] with [settings] cannot be created; empty when it can. */
    fun problems(name: String, parent: File, settings: NewProjectSettings): List<String> = buildList {
        val wizard = ProjectWizard().apply {
            setProjectName(name)
            setProjectLocation(parent.absolutePath)
        }
        if (!wizard.isProjectNameValid()) add("Invalid project name '$name'")
        if (!wizard.isProjectPathValid()) add("Folder '${parent.path}' does not exist or is not writable")
        else if (projectFile(File(parent, name.trim()), name.trim()).exists()) {
            add("Project '${name.trim()}' already exists in ${parent.path}")
        }
        addAll(settings.problems())
    }

    /**
     * Background-safe step: the project folder and its typed folders, the strings file, the engine defaults (copy
     * or reference mode), the template's own files, the splash image and the AI files. Returns the project folder.
     */
    fun prepare(name: String, parent: File, settings: NewProjectSettings): Result<File> = runCatching {
        val problems = problems(name, parent, settings)
        if (problems.isNotEmpty()) throw ProjectCreationException(problems.joinToString("; "))
        val projectName = name.trim()
        val template = TemplateRegistry.require(settings.template)
        val projectDir = File(parent, projectName).absoluteFile.normalize().also { it.mkdirs() }

        val assetsDir = ProjectLayout.create(projectDir)
        File(assetsDir, ProjectLayout.STRINGS_FILE).writeText("project.name=$projectName\n")
        copier.copyEngineDefaults(projectDir, copyAll = settings.copyEngineDefaults).onFailure { e ->
            throw ProjectCreationException(
                "Could not copy the engine assets into ${projectDir.path} (run from the engine folder): ${e.message}", e,
            )
        }
        template.writeFiles(TemplateContext(projectDir, projectName, settings))
        settings.splashImage?.let { copySplash(projectDir, it) }
        if (settings.includeAiFiles) AiFiles.write(projectDir, projectName, template.id)
        projectDir
    }

    /**
     * Builds the template's scenes into `Scenes/`, then writes `<name>.skateproject`, and runs `git init` when
     * [NewProjectSettings.versionControl]. The returned [Project] holds absolute paths; the file holds them
     * project-relative.
     */
    fun finish(projectDir: File, name: String, settings: NewProjectSettings, prefabs: PrefabsGenerator): Result<Project> =
        runCatching {
            val projectName = name.trim()
            val template = TemplateRegistry.require(settings.template)
            val ctx = TemplateContext(projectDir, projectName, settings)
            val files = ProjectFiles(serializer, ctx.projectDir)
            prefabs.projectAssetsDir = ctx.assetsDir.absolutePath + File.separator

            val scenes = files.inProject { template.buildScenes(ctx, prefabs) }
            if (scenes.isEmpty()) throw ProjectCreationException("Template '${template.id}' built no scene")
            val scenesDir = ctx.scenesDir.also { it.mkdirs() }
            val scenePaths = scenes.map { files.writeScene(scenesDir, it.name, it).path }

            val project = Project(
                name = projectName,
                projectPath = projectFile(ctx.projectDir, projectName).path,
                iconPath = EngineDefaults.Textures.APP_ICON,
                defaultScene = scenePaths.first(),
                scenesPath = scenePaths,
                resolution = Vector2i(1920, 1080),
                graphicsBackend = settings.graphicsBackend,
                physicsBackend = settings.physicsBackend,
                targetFps = settings.targetFps,
                splashPath = settings.splashImage?.let { splashTarget(ctx.projectDir, it) }?.takeIf { it.isFile }?.path.orEmpty(),
                randomSeed = RandomService.newSeed(),
            )
            template.configure(project)
            files.writeProject(project)
            if (settings.versionControl) {
                vcsFor(ctx.projectDir).init(author(), FIRST_COMMIT_MESSAGE.format(template.id))
            }
            project
        }

    /** [prepare] then [finish]: for callers that do both on one thread (CLI, MCP, tests). */
    fun create(name: String, parent: File, settings: NewProjectSettings, prefabs: PrefabsGenerator): Result<Project> =
        prepare(name, parent, settings).mapCatching { dir -> finish(dir, name, settings, prefabs).getOrThrow() }

    /** Copies [source] to [splashTarget] unless an identical copy is already there. */
    private fun copySplash(projectDir: File, source: File): File =
        splashTarget(projectDir, source).also { if (!it.exists()) source.copyTo(it) }

    /**
     * Where [source] goes: `Assets/Textures/splash.<ext>`, or `splash_2`, `_3`... when a different file holds the name
     * (AST-26 §3.1). An identical file is reused, so [finish] finds the copy [prepare] made.
     */
    private fun splashTarget(projectDir: File, source: File): File {
        val dir = ProjectLayout.typeDir(projectDir, AssetFolder.TEXTURES).also { it.mkdirs() }
        val ext = source.extension.lowercase()
        val bytes = source.readBytes()
        var target = File(dir, "$SPLASH_NAME.$ext")
        var n = 2
        while (target.exists() && !target.readBytes().contentEquals(bytes)) target = File(dir, "${SPLASH_NAME}_${n++}.$ext")
        return target
    }
}
