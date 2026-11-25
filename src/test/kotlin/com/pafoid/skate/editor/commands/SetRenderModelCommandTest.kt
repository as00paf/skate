package com.pafoid.skate.editor.commands

import com.pafoid.skate.editor.commands.objects.SetRenderModelCommand
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.ecs.components.RenderComponent
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SetRenderModelCommandTest {
    @Test
    fun RenderModel_ChangeKeepsMaterialOverrideAndUndoRestores() {
        val old = `3dModel`(path = "a.glb")
        val next = `3dModel`(path = "b.glb")
        val override = Material()
        val component = RenderComponent(model = old, material = override, materialPath = "m.skmat")
        val command = SetRenderModelCommand(component, next)

        command.execute()
        assertSame(next, component.model)
        assertSame(override, component.material)
        assertTrue(component.materialPath == "m.skmat")

        command.undo()
        assertSame(old, component.model)
        assertSame(override, component.material)
    }

    @Test
    fun RenderModel_SamePathIsNoOp() {
        val component = RenderComponent(model = `3dModel`(path = "a.glb"))
        assertTrue(SetRenderModelCommand(component, `3dModel`(path = "a.glb")).isNoOp)
        assertFalse(SetRenderModelCommand(component, `3dModel`(path = "b.glb")).isNoOp)
    }
}
