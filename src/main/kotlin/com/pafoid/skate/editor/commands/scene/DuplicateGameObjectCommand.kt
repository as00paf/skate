package com.pafoid.skate.editor.commands.scene

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.GameObjectManager
import com.pafoid.skate.engine.getComponent

class DuplicateGameObjectCommand(
    private val original: GameObject,
    scene: Scene,
    gameObjectManager: GameObjectManager,
) : AddBuiltObjectCommand(scene, gameObjectManager) {
    override fun build(): GameObject {
        val gameObject = original
        val duplicated = GameObject("${gameObject.name} (Copy)")
        val originalTransform = gameObject.getComponent<Transform>()
        val newTransform = Transform()
        originalTransform?.let { orig ->
            newTransform.copyFrom(orig)
        }
        newTransform.translation.add(1f, 0f, 0f)
        duplicated.addComponent(newTransform)

        val originalRender = gameObject.getComponent<RenderComponent>()
        if (originalRender != null) {
            // A fresh id for the copy; the original keeps its own (EDT-10: it used to be the one renumbered).
            val newRender = originalRender.copy().also { it.uId = -1 }
            duplicated.addComponent(newRender)
        }

        return duplicated
    }

    /** A sibling of the original: top-level stays top-level, a child stays under the same parent. */
    override fun parent(): GameObject = original.parent ?: scene

    override fun getDisplayName(): String = "Duplicate ${original.name}"
    override fun getTargetName(): String = "${original.name} (Copy)"
}
