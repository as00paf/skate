package com.pafoid.skate.editor.commands.scene

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.systems.GameObjectManager

/**
 * Adds an object that [build] creates once, on the first execute (EDT-10). Undo removes that object; redo adds the
 * same object back, so later commands that refer to it keep working.
 */
abstract class AddBuiltObjectCommand(
    protected val scene: Scene,
    private val gameObjectManager: GameObjectManager,
) : Command {
    private var built: GameObject? = null

    /** The object, once executed. */
    val gameObject: GameObject? get() = built

    protected abstract fun build(): GameObject

    /** Where the object goes; the scene (top-level) by default. */
    protected open fun parent(): GameObject = scene

    override fun execute() {
        val obj = built ?: build().also { built = it }
        gameObjectManager.addGameObject(obj, parent())
        scene.selectedGameObject = obj
        scene.isDirty = true
    }

    override fun undo() {
        val obj = built ?: return
        gameObjectManager.removeGameObject(obj)
        scene.removeFromSelection(obj)
        scene.isDirty = true
    }
}
