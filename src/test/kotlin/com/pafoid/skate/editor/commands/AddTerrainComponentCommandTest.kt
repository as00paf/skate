package com.pafoid.skate.editor.commands

import com.pafoid.skate.editor.commands.objects.AddComponentCommand
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.ComponentType
import com.pafoid.skate.engine.ecs.components.TerrainComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/** ECS-17: the inspector's Add Component adds Terrain to a GameObject; undo removes it. */
class AddTerrainComponentCommandTest {
    private val logger = LoggerService().apply { consoleSink = { _, _ -> } }

    @Test
    fun AddComponent_TerrainIsAddedToGameObject_UndoRemovesIt() {
        val go = GameObject("Ground").apply { addComponent(Transform()) }

        val cmd = AddComponentCommand(go, ComponentType.TERRAIN, logger).also { it.execute() }

        val terrain = go.getComponent<TerrainComponent>()
        assertNotNull(terrain)
        assertSame(go, terrain!!.gameObject)
        cmd.undo()
        assertNull(go.getComponent<TerrainComponent>())
        assertEquals(1, go.components.size)
    }
}
