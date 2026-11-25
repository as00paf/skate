package com.pafoid.skate.templates

import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.assets.PrefabsGenerator
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.game.samples.FeatureShowcase

/**
 * The "Feature Showcase" template (TLG-05/TLG-08, EDT-59): a project whose only scene is the [FeatureShowcase]
 * regression scene, one labelled area per engine feature. Since EDT-59 it is no longer added to every new project.
 * See docs/samples.md.
 */
object FeatureShowcaseTemplate : ProjectTemplate {
    const val ID = "feature-showcase"
    const val SCENE_NAME = FeatureShowcase.SCENE_NAME

    override val id = ID
    override val category = TemplateCategory.SAMPLE
    override val icon = Icons.STAR
    override val sceneNames = listOf(SCENE_NAME)
    override val ownFiles = listOf("Assets/Scripts/${FeatureShowcase.SEED_SCRIPT}")

    override fun writeFiles(ctx: TemplateContext) = FeatureShowcase.writeScripts(ctx.assetsDir)

    override fun buildScenes(ctx: TemplateContext, prefabs: PrefabsGenerator): List<Scene> =
        listOf(FeatureShowcase(prefabs).populate(Scene(SCENE_NAME)))

    override fun configure(project: Project) {
        project.description = "One labelled area per engine feature; manual regression suite"
    }
}
