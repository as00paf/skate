package com.pafoid.skate.templates

import com.pafoid.skate.editor.project.ItemType
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.editor.project.ProjectStructureItem
import com.pafoid.skate.engine.assets.PrefabsGenerator
import com.pafoid.skate.engine.assets.ProjectLayout
import com.pafoid.skate.engine.ecs.Scene
import java.io.File

/**
 * A New Project template (EDT-59 §3.1): what a new project starts with. The editor wizard, `skate create` and the MCP
 * server all create projects through [ProjectCreator], which calls these hooks in order: [writeFiles] (file I/O only,
 * safe off the main thread), then [buildScenes] (main thread in the editor, because prefab textures load), then
 * [configure].
 */
interface ProjectTemplate {
    /** Stable id used by `skate create --template` and the MCP tools: "empty", "skate-game", ... */
    val id: String

    /** Display order and grouping of the wizard's cards. */
    val category: TemplateCategory

    /** An `Icons` glyph: the card's thumbnail (EDT-59 decision 4: icons, no screenshots). */
    val icon: String

    /** Translated name: `template.<id>.name` in `editor/strings/tooling.properties`. */
    val nameKey: String get() = "template.$id.name"

    /** Translated one-line description: `template.<id>.description`. */
    val descriptionKey: String get() = "template.$id.description"

    /** Names of the scenes [buildScenes] returns, in order; the first is the default scene. */
    val sceneNames: List<String>

    /** The template's own files under `Assets/`, project-relative with `/` (for the structure preview). */
    val ownFiles: List<String> get() = emptyList()

    /** Writes the template's own files (scripts, sounds, strings). File I/O only. */
    fun writeFiles(ctx: TemplateContext) {}

    /** Builds the scenes, default scene first. [prefabs] loads assets in the editor and keeps paths only headless. */
    fun buildScenes(ctx: TemplateContext, prefabs: PrefabsGenerator): List<Scene>

    /** Template-specific overrides of the new project (description, physics rate...). */
    fun configure(project: Project) {}

    /** The folders and files the template creates, for the wizard's structure preview. */
    fun structure(projectName: String): List<ProjectStructureItem> = buildList {
        add(ProjectStructureItem("${ProjectLayout.ASSETS_DIR}/", ItemType.DIRECTORY))
        ownFiles.sorted().forEach { add(ProjectStructureItem("  ${it.removePrefix("${ProjectLayout.ASSETS_DIR}/")}", ItemType.FILE)) }
        add(ProjectStructureItem("${ProjectLayout.SCENES_DIR}/", ItemType.DIRECTORY))
        sceneNames.forEach { add(ProjectStructureItem("  $it.scene", ItemType.FILE)) }
        add(ProjectStructureItem("${ProjectLayout.BUILDS_DIR}/", ItemType.DIRECTORY))
        add(ProjectStructureItem("$projectName.skateproject", ItemType.FILE))
    }
}

enum class TemplateCategory { EMPTY, GAME, SAMPLE }

/** What a template writes into: the new project's folder, its name and the wizard's settings. */
class TemplateContext(
    projectDir: File,
    val projectName: String,
    val settings: NewProjectSettings,
) {
    val projectDir: File = projectDir.absoluteFile.normalize()
    val assetsDir: File get() = ProjectLayout.assetsDir(projectDir)
    val scenesDir: File get() = ProjectLayout.scenesDir(projectDir)
}
