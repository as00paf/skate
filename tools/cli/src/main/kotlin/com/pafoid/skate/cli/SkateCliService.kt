package com.pafoid.skate.cli

import com.pafoid.skate.editor.project.EngineAssetCopier
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.editor.project.ProjectWizard
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.utils.AssetsPacker
import org.joml.Vector2i
import java.io.File

class CliException(message: String) : Exception(message)

data class ValidationReport(val errors: List<String>, val warnings: List<String>, val sceneCount: Int) {
    val isValid get() = errors.isEmpty()
}

data class RunStats(
    val sceneName: String,
    val frames: Int,
    val simulatedSeconds: Float,
    val wallMillis: Double,
    val gameObjects: Int,
    val components: Int,
)

/** Headless project operations. Everything here reuses engine/editor services and never opens a window. */
class SkateCliService(
    private val engine: Engine = Engine(),
    private val serializer: Serializer = engine.serializer,
    private val out: (String) -> Unit = ::println,
) {

    fun createProject(name: String, parent: File): File {
        val wizard = ProjectWizard().apply {
            setProjectName(name)
            setProjectLocation(parent.absolutePath)
        }
        if (!wizard.isProjectNameValid()) throw CliException("Invalid project name '$name'")
        if (!wizard.isProjectPathValid()) throw CliException("Folder '${parent.path}' does not exist or is not writable")

        val projectDir = wizard.getProjectDirectory()
        val projectFile = File(projectDir, "$name.skateproject")
        if (projectFile.exists()) throw CliException("Project '$name' already exists in ${parent.path}")

        val assetsDir = File(projectDir, "Assets").also { it.mkdirs() }
        val scenesDir = File(projectDir, "Scenes").also { it.mkdirs() }
        File(projectDir, "Builds").mkdirs()
        File(assetsDir, "strings.properties").writeText("project.name=$name")
        EngineAssetCopier().copyBundledAssets(projectDir)

        val sceneName = "MainScene"
        engine.sceneManager.createNewScene(sceneName, scenesDir.path)
        val scenePath = File(scenesDir, "$sceneName.scene").path
        val project = Project(
            name = name,
            projectPath = projectFile.path,
            defaultScene = scenePath,
            scenesPath = listOf(scenePath),
            resolution = Vector2i(1920, 1080),
        )
        projectFile.writeText(serializer.encode(project))
        out("Created project '$name' at ${projectFile.path}")
        return projectFile
    }

    fun loadProject(path: String): Project {
        val file = resolveProjectFile(path)
        val project = try {
            serializer.decode<Project>(file.readText())
        } catch (e: Exception) {
            throw CliException("Could not read project ${file.path}: ${e.message}")
        }
        // The stored path may be stale when the project folder was moved
        project.projectPath = file.absolutePath
        return project
    }

    fun validate(path: String): ValidationReport {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val project = try {
            loadProject(path)
        } catch (e: CliException) {
            return ValidationReport(listOf(e.message.orEmpty()), emptyList(), 0)
        }
        val dir = project.getProjectDirectory()

        if (project.name.isBlank()) errors += "Project name is blank"
        if (!File(dir, "Assets").isDirectory) warnings += "Missing Assets folder"
        if (project.scenesPath.isEmpty()) errors += "Project has no scenes"

        val sceneFiles = project.scenesPath.map { locate(it, dir) }
        sceneFiles.forEachIndexed { i, file ->
            if (!file.exists()) {
                errors += "Scene file not found: ${project.scenesPath[i]}"
                return@forEachIndexed
            }
            try {
                serializer.decode<Scene>(file.readText())
            } catch (e: Exception) {
                errors += "Scene ${file.name} is not valid: ${e.message?.lineSequence()?.firstOrNull()}"
            }
        }

        val defaultScene = locate(project.defaultScene, dir)
        if (!defaultScene.exists()) errors += "Default scene not found: ${project.defaultScene}"
        else if (sceneFiles.none { it.name == defaultScene.name }) warnings += "Default scene is not listed in scenesPath"

        return ValidationReport(errors, warnings, sceneFiles.count { it.exists() })
    }

    fun pack(path: String): File {
        val project = loadProject(path)
        val buildsDir = File(project.getProjectDirectory(), "builds").also { it.mkdirs() }
        if (!AssetsPacker(engine.logger).pack(project)) throw CliException("Packing failed")
        val bin = File(buildsDir, AssetsPacker.BIN_FILE)
        if (!bin.exists()) throw CliException("Packing did not produce ${bin.path}")
        out("Packed assets into ${bin.path} (${bin.length()} bytes)")
        return bin
    }

    fun export(path: String, output: File): File {
        val report = validate(path)
        if (!report.isValid) throw CliException("Project is invalid:\n" + report.errors.joinToString("\n") { "  - $it" })
        val bin = pack(path)
        output.mkdirs()
        val target = File(output, bin.name)
        bin.copyTo(target, overwrite = true)
        out("Exported to ${target.path}")
        return target
    }

    val logs get() = engine.logger.logs

    fun loadScenes(project: Project): List<Scene> {
        val dir = project.getProjectDirectory()
        return project.scenesPath.map { locate(it, dir) }.filter { it.exists() }.map {
            try {
                serializer.decode<Scene>(it.readText())
            } catch (e: Exception) {
                throw CliException("Scene ${it.name} is not valid: ${e.message?.lineSequence()?.firstOrNull()}")
            }
        }
    }

    fun run(path: String, sceneName: String?, frames: Int, dt: Float): RunStats {
        val project = loadProject(path)
        val scenes = loadScenes(project)
        if (scenes.isEmpty()) throw CliException("Project has no loadable scenes")
        val defaultName = File(project.defaultScene.replace('\\', '/')).nameWithoutExtension
        val scene = if (sceneName != null) {
            scenes.firstOrNull { it.name == sceneName } ?: throw CliException("Scene '$sceneName' not found")
        } else {
            scenes.firstOrNull { it.name == defaultName } ?: scenes.first()
        }

        engine.sceneManager.openScene(scene, forceSingle = true)
        scene.start()
        val start = System.nanoTime()
        repeat(frames) { scene.update(dt) }
        val wallMillis = (System.nanoTime() - start) / 1_000_000.0

        val stats = RunStats(scene.name, frames, frames * dt, wallMillis, countObjects(scene) - 1, countComponents(scene))
        engine.sceneManager.closeAllScenes()
        out(
            "Scene '${stats.sceneName}': ${stats.frames} frames (${"%.2f".format(stats.simulatedSeconds)}s simulated) " +
                "in ${"%.2f".format(stats.wallMillis)} ms, ${stats.gameObjects} objects, ${stats.components} components"
        )
        return stats
    }

    private fun countObjects(obj: GameObject): Int = 1 + obj.children.sumOf { countObjects(it) }

    private fun countComponents(obj: GameObject): Int = obj.components.size + obj.children.sumOf { countComponents(it) }

    private fun resolveProjectFile(path: String): File {
        val file = File(path)
        if (file.isDirectory) {
            return file.listFiles { f -> f.extension == "skateproject" }?.firstOrNull()
                ?: throw CliException("No .skateproject file found in ${file.path}")
        }
        if (!file.exists()) throw CliException("Project not found: $path")
        return file
    }

    // Stored scene paths are absolute and may use Windows separators; fall back to <project>/Scenes/<file>
    private fun locate(stored: String, projectDir: File): File {
        val direct = File(stored)
        if (direct.exists()) return direct
        val fileName = stored.substringAfterLast('\\').substringAfterLast('/')
        return File(File(projectDir, "Scenes"), fileName)
    }
}
