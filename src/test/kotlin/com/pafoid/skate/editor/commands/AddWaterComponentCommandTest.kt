package com.pafoid.skate.editor.commands

import com.pafoid.skate.editor.commands.objects.AddComponentCommand
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.ComponentType
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.components.WaterComponent
import com.pafoid.skate.engine.getComponent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** ECS-16: the inspector's Add Component offers Water and adds it to a GameObject; undo removes it. */
class AddWaterComponentCommandTest {
    private val logger = LoggerService().apply { consoleSink = { _, _ -> } }

    @Test
    fun AddComponent_WaterIsListedAndAddedToGameObject_UndoRemovesIt() {
        val go = GameObject("Pool").apply { addComponent(Transform()) }

        val cmd = AddComponentCommand(go, ComponentType.WATER, logger).also { it.execute() }

        assertNotNull(go.getComponent<WaterComponent>())
        cmd.undo()
        assertNull(go.getComponent<WaterComponent>())
        assertEquals(1, go.components.size)
    }
}
