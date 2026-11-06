package com.pafoid.skate.editor.commands.editor

import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.engine.ecs.systems.PhysicsSystem
import com.pafoid.skate.engine.ecs.systems.SystemManager

class TogglePhysicsDebugCommand(private val systemManager: SystemManager) : ExecuteOnlyCommand {

    override fun execute() {
        systemManager.getSystem<PhysicsSystem>()?.toggleDebug()
    }

    override fun undo() {
        execute()
    }

    override fun getDisplayName(): String = "Toggle Physics Debug"

    /** String key, translated by [com.pafoid.skate.editor.commands.CommandNames.targetName]. */
    override fun getTargetName(): String = "window.game_viewport"
}
