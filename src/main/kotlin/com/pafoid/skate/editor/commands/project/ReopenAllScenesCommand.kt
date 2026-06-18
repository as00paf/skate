package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.commands.CommandCategory
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.events.SceneAction

class ReopenAllScenesCommand(
    private val sceneManager: SceneManager,
    private val eventSystem: EventSystem,
    private val scenes: List<Scene>,
) : Command {

    private var reopenedScenes: List<Scene>? = null

    override fun execute() {
        reopenedScenes = scenes
        sceneManager.openScenes(scenes)
    }

    override fun undo() {
        eventSystem.publish(SceneAction.CloseAllRequested)
    }

    override fun getDisplayName(): String = "Reopen all scenes requested"

    override fun getTargetName(): String? = null

    override fun getCategory(): CommandCategory = CommandCategory.UNDOABLE
}