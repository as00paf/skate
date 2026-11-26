package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.physics3d.Physics3DFactory
import com.pafoid.skate.engine.physics3d.PhysicsBackend
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PhysicsBackendSettingTest {
    @AfterEach
    fun reset() {
        Physics3DFactory.defaultBackend = PhysicsBackend.BULLET
    }

    @Test
    fun SetProjectPhysicsBackend_AppliesToTheFactoryAndUndoes() {
        val project = Project(name = "P", projectPath = "P/P.skateproject", defaultScene = "Main.scene", scenesPath = emptyList())
        val command = SetProjectPhysicsBackendCommand(project, PhysicsBackend.SIMPLE)

        command.execute()
        assertEquals(PhysicsBackend.SIMPLE, project.physicsBackend)
        assertEquals(PhysicsBackend.SIMPLE, Physics3DFactory.defaultBackend)

        command.undo()
        assertEquals(PhysicsBackend.BULLET, project.physicsBackend)
        assertEquals(PhysicsBackend.BULLET, Physics3DFactory.defaultBackend)
    }
}
