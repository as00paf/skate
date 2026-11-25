package com.pafoid.skate.templates

import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.engine.assets.PrefabsGenerator
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneSun

/** The "Empty" template (EDT-59): one lit `MainScene`, as `SceneManager.createNewScene` makes it. */
object EmptyTemplate : ProjectTemplate {
    const val ID = "empty"
    const val SCENE_NAME = "MainScene"

    override val id = ID
    override val category = TemplateCategory.EMPTY
    override val icon = Icons.FILE_TEXT
    override val sceneNames = listOf(SCENE_NAME)

    override fun buildScenes(ctx: TemplateContext, prefabs: PrefabsGenerator): List<Scene> =
        listOf(SceneSun.addDefaults(Scene(SCENE_NAME)))
}
