package com.pafoid.skate.cli

import com.pafoid.skate.editor.project.EngineAssetCopier
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.assets.ProjectLayout
import com.pafoid.skate.engine.assets.PrefabsGenerator
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.game.samples.FeatureShowcase
import org.joml.Vector2i
import java.io.File

/**
 * Writes the "Feature showcase" sample project (TLG-05/TLG-08): a project whose only scene is the shared
 * [FeatureShowcase] scene that every new editor project also gets. See docs/samples.md.
 */
class ShowcaseProjectBuilder(
    private val engine: Engine = Engine(),
    private val serializer: Serializer = engine.serializer,
) {

    companion object {
        const val PROJECT_NAME = "FeatureShowcase"
        const val SCENE_NAME = FeatureShowcase.SCENE_NAME
    }

    fun build(parent: File): File {
        val projectDir = File(parent, PROJECT_NAME).also { it.mkdirs() }
        val assetsDir = ProjectLayout.create(projectDir)
        val scenesDir = ProjectLayout.scenesDir(projectDir)
        EngineAssetCopier().copyBundledAssets(projectDir)
        File(assetsDir, "strings.properties").writeText("project.name=$PROJECT_NAME\n")

        // Path references only: the CLI is headless, and the editor reloads every model and texture on open.
        val prefabs = PrefabsGenerator(engine, loadAssets = false).also { it.projectAssetsDir = assetsDir.absolutePath + File.separator }
        val scene = FeatureShowcase(prefabs).populate(Scene(SCENE_NAME))
        val scenePath = File(scenesDir, "$SCENE_NAME.scene").also { it.writeText(serializer.encode(scene)) }.path

        val projectFile = File(projectDir, "$PROJECT_NAME.skateproject")
        val project = Project(
            name = PROJECT_NAME,
            projectPath = projectFile.path,
            description = "One labelled area per engine feature; manual regression suite",
            defaultScene = scenePath,
            scenesPath = listOf(scenePath),
            resolution = Vector2i(1920, 1080),
        )
        projectFile.writeText(serializer.encode(project))
        return projectFile
    }
}
