package com.pafoid.skate.templates

import com.pafoid.skate.engine.assets.PrefabsGenerator
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.editor.project.Project
import java.io.File

/**
 * What `PrefabsGenerator.createDefaultScenes` used to give a new project (EDT-114): the Skate Game `MainScene` and the
 * `FeatureShowcase`, built by their templates and saved into [scenesDir] (a project's `Scenes/` folder).
 */
fun PrefabsGenerator.createDefaultScenes(serializer: Serializer, scenesDir: File): List<Scene> {
    val projectDir = scenesDir.absoluteFile.parentFile
    val ctx = TemplateContext(projectDir, projectDir.name, NewProjectSettings())
    val files = ProjectFiles(serializer, projectDir)
    scenesDir.mkdirs()
    return files.inProject {
        listOf(SkateGameTemplate, FeatureShowcaseTemplate).flatMap { it.buildScenes(ctx, this) }
    }.onEach { files.writeScene(scenesDir, it.name, it) }
}

/** A new Skate Game project plus the Feature Showcase scene (its own template since EDT-59), reopened. */
fun ProjectManager.createWithShowcase(
    engine: Engine, serializer: Serializer, name: String, parent: File, settings: NewProjectSettings = NewProjectSettings(),
): Result<Project> {
    val created = createProject(name, parent, settings)
    if (created.isFailure) return created
    val project = created.getOrThrow()
    val projectDir = project.getProjectDirectory()
    val ctx = TemplateContext(projectDir, name, settings)
    FeatureShowcaseTemplate.writeFiles(ctx)
    val files = ProjectFiles(serializer, projectDir)
    val showcase = files.inProject { FeatureShowcaseTemplate.buildScenes(ctx, engine.prefabsGenerator) }.single()
    val path = files.writeScene(ctx.scenesDir, showcase.name, showcase).path
    files.writeProject(project.copy(scenesPath = project.scenesPath + path))
    closeProject()
    return if (openProjectFile(project.getProjectFile())) Result.success(currentProject!!)
    else Result.failure(IllegalStateException("could not reopen $name"))
}
