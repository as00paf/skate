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

    override fun getTargetName(): String = "Game Viewport"
}
