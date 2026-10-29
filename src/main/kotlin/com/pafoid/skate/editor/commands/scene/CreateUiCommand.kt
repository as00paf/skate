package com.pafoid.skate.editor.commands.scene

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.ui.UICanvasComponent
import com.pafoid.skate.engine.ui.UiFactory
import com.pafoid.skate.engine.ui.UiKind

/**
 * Creates a UI canvas or element. Elements go under [preferredParent] when it is part of a UI tree, otherwise under
 * the scene's first canvas, which is created on demand.
 */
class CreateUiCommand(
    private val kind: UiKind,
    private val scene: Scene,
    private val preferredParent: GameObject?,
) : Command {
    private var created: GameObject? = null
    private var createdCanvas: GameObject? = null
    private var parent: GameObject? = null
    private var previousSelection: GameObject? = null

    override fun execute() {
        previousSelection = scene.selectedGameObject
        val target: GameObject = if (kind == UiKind.CANVAS) scene else resolveParent()
        val obj = created ?: UiFactory.create(kind).also { it.addComponent(Transform()); created = it }
        parent = target
        target.addChild(obj)
        scene.selectedGameObject = obj
        scene.isDirty = true
    }

    private fun resolveParent(): GameObject {
        preferredParent?.takeIf { UiFactory.isUi(it) }?.let { return it }
        findCanvas(scene)?.let { return it }
        val canvas = createdCanvas ?: UiFactory.create(UiKind.CANVAS).also { it.addComponent(Transform()); createdCanvas = it }
        scene.addChild(canvas)
        return canvas
    }

    private fun findCanvas(node: GameObject): GameObject? {
        if (node.getComponent<UICanvasComponent>() != null) return node
        node.children.forEach { child -> findCanvas(child)?.let { return it } }
        return null
    }

    override fun undo() {
        val obj = created ?: return
        parent?.removeChild(obj)
        createdCanvas?.let { scene.removeChild(it) }
        scene.selectedGameObject = previousSelection
        scene.isDirty = true
    }

    override fun getDisplayName(): String = "Create UI ${kind.name.lowercase().replace('_', ' ')}"
    override fun getTargetName(): String? = created?.name
}
