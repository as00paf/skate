package com.pafoid.skate.editor.commands.scene

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.systems.GameObjectManager

class DeleteGameObjectCommand(
    private val gameObject: GameObject,
    private val scene: Scene,
    private val gameObjectManager: GameObjectManager,
) : Command {

    /** Where the object was, so undo puts it back at the same place (top-level or nested). */
    private var parent: GameObject = scene
    private var index: Int = -1

    override fun execute() {
        parent = gameObject.parent ?: scene
        index = parent.children.indexOf(gameObject)
        gameObjectManager.removeGameObject(gameObject)
        scene.removeFromSelection(gameObject)
        scene.isDirty = true
    }

    override fun undo() {
        gameObjectManager.addGameObject(gameObject, parent, index)
        scene.addToSelection(gameObject)
    }

    override fun getDisplayName(): String = "Delete GameObject"
    override fun getTargetName(): String = gameObject.name
}