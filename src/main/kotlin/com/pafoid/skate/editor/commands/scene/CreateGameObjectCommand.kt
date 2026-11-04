package com.pafoid.skate.editor.commands.scene

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.systems.GameObjectManager

/** Adds [gameObject] under [parent]: the scene (top-level) by default, or an object for a child. */
class CreateGameObjectCommand(
    private val gameObject: GameObject,
    private val scene: Scene,
    private val gameObjectManager: GameObjectManager,
    private val parent: GameObject = scene,
) : Command {

    override fun execute() {
        gameObjectManager.addGameObject(gameObject, parent)
        scene.selectedGameObject = gameObject
        scene.isDirty = true
    }

    override fun undo() {
        gameObjectManager.removeGameObject(gameObject)
        scene.removeFromSelection(gameObject)
    }

    override fun getDisplayName(): String = "Create GameObject"
    override fun getTargetName(): String = gameObject.name
}