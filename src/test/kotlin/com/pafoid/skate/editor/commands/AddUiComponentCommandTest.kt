package com.pafoid.skate.editor.commands

import com.pafoid.skate.editor.commands.objects.AddComponentCommand
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.ComponentType
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.ui.UIButtonComponent
import com.pafoid.skate.engine.ui.UICanvasComponent
import com.pafoid.skate.engine.ui.UIElementComponent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Inspector Add Component on a plain GameObject brings in what the UI component needs, and undo takes it back. */
class AddUiComponentCommandTest {
    private val logger = LoggerService().apply { consoleSink = { _, _ -> } }

    private fun add(go: GameObject, type: ComponentType) = AddComponentCommand(go, type, logger).also { it.execute() }

    @Test
    fun AddButtonToPlainObject_AddsRectAndCanvas_UndoRemovesAll() {
        val go = GameObject("Empty").apply { addComponent(Transform()) }
        val cmd = add(go, ComponentType.UI_BUTTON)

        assertNotNull(go.getComponent<UIButtonComponent>())
        assertNotNull(go.getComponent<UIElementComponent>())
        assertNotNull(go.getComponent<UICanvasComponent>())

        cmd.undo()
        assertEquals(1, go.components.size)
        assertNull(go.getComponent<UIElementComponent>())
        assertNull(go.getComponent<UICanvasComponent>())
    }

    @Test
    fun AddCanvasThenButton_ButtonOnlyAddsRect() {
        val go = GameObject("Empty")
        add(go, ComponentType.UI_CANVAS)
        assertNull(go.getComponent<UIElementComponent>())

        val cmd = add(go, ComponentType.UI_BUTTON)
        assertNotNull(go.getComponent<UIElementComponent>())

        cmd.undo()
        assertNull(go.getComponent<UIElementComponent>())
        assertNotNull(go.getComponent<UICanvasComponent>())
    }

    @Test
    fun AddButtonUnderCanvasParent_DoesNotAddSecondCanvas() {
        val canvas = GameObject("Canvas")
        add(canvas, ComponentType.UI_CANVAS)
        val child = GameObject("Child")
        canvas.addChild(child)

        add(child, ComponentType.UI_BUTTON)

        assertNotNull(child.getComponent<UIElementComponent>())
        assertNull(child.getComponent<UICanvasComponent>())
    }
}
