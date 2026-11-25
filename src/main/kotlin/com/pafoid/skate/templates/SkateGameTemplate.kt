package com.pafoid.skate.templates

import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.EngineDefaults
import com.pafoid.skate.engine.assets.PrefabsGenerator
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneSun
import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.components.GridLines
import com.pafoid.skate.engine.ecs.components.ScenePhysicsComponent

/**
 * The "Skate Game" template (EDT-59, preselected in the wizard): the skate `MainScene` every new project used to get
 * from `PrefabsGenerator.createDefaultScenes` (GPL-23 park, skater, board, cameras, HUD), without the Feature
 * Showcase, which is its own template now.
 */
object SkateGameTemplate : ProjectTemplate {
    const val ID = "skate-game"
    const val SCENE_NAME = "MainScene"

    override val id = ID
    override val category = TemplateCategory.GAME
    override val icon = Icons.USER
    override val sceneNames = listOf(SCENE_NAME)

    override fun buildScenes(ctx: TemplateContext, prefabs: PrefabsGenerator): List<Scene> {
        val scene = Scene(SCENE_NAME)
        // The sky is a path reference: the editor resolves textures when it opens the saved scene, and the CLI has no GL.
        scene.addComponent(ScenePhysicsComponent())
            .addComponent(GridLines())
            .addComponent(EnvironmentComponent(skyTexture = Texture(filePath = EngineDefaults.Textures.SKY_HDRI)))
            .addComponent(DayNightCycleComponent())
        SceneSun.addDefaults(scene) // the "Sun" entity, driven by the day/night cycle (REN-26)
        prefabs.spawnDefaultsSync().forEach { scene.addChild(it) }
        return listOf(scene)
    }
}
