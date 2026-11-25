package com.pafoid.skate.editor.commands

import com.pafoid.skate.editor.commands.input.BindingOperation
import com.pafoid.skate.editor.commands.input.EditProjectActionsCommand
import com.pafoid.skate.engine.gameplay.CharacterActions
import com.pafoid.skate.engine.input.ActionBinding
import com.pafoid.skate.engine.input.ActionMap
import com.pafoid.skate.engine.input.ConflictResolution
import com.pafoid.skate.engine.input.InputMappings
import com.pafoid.skate.engine.input.Keys
import com.pafoid.skate.engine.input.ProjectAction
import com.pafoid.skate.engine.input.ProjectActionSet
import com.pafoid.skate.engine.input.ProjectActions
import com.pafoid.skate.engine.input.RebindResult
import com.pafoid.skate.engine.input.UiNavigationActions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class EditProjectActionsCommandTest {
    private val map = CharacterActions.register(UiNavigationActions.register(ActionMap()))
    private val mappings = InputMappings()
    private val files = HashMap<String, String>()
    private var failWrites = false
    private val actions = ProjectActions(map, files::get)
    private val path = ProjectActionSet.DEFAULT_PATH

    private val fire = ProjectAction("fire", "Fire", defaults = listOf(ActionBinding.Key(70)))

    private fun write(p: String, text: String) {
        if (failWrites) throw IOException("disk full")
        files[p] = text
    }

    private fun cmd(after: ProjectActionSet, profile: com.pafoid.skate.engine.input.BindingProfile? = null) =
        EditProjectActionsCommand(map, mappings, actions, ::write, after, "command.project_actions.add", listOf("fire"), profile)

    @Test
    fun EditProjectActions_FirstActionCreatesAssetAndUndoRestores() {
        val c = cmd(ProjectActionSet(actions = listOf(fire)))
        c.execute()

        assertTrue(c.wasSuccessful())
        assertEquals(path, mappings.actionsPath)
        assertEquals(listOf("fire"), ProjectActionSet.fromJson(files[path]!!).actions.map { it.id })
        assertEquals(listOf(ActionBinding.Key(70)), map.bindings("fire"))

        c.undo()
        assertNull(mappings.actionsPath)
        assertNull(map.definition("fire"))

        c.execute() // redo
        assertTrue(map.definition("fire") != null)
    }

    @Test
    fun EditProjectActions_UndoRewritesThePreviousAsset() {
        cmd(ProjectActionSet(actions = listOf(fire))).execute()
        val rename = cmd(actions.current.withAction(fire.copy(name = "Shoot")))
        rename.execute()
        assertEquals("Shoot", ProjectActionSet.fromJson(files[path]!!).action("fire")!!.name)

        rename.undo()
        assertEquals("Fire", ProjectActionSet.fromJson(files[path]!!).action("fire")!!.name)
        assertEquals("Fire", map.definition("fire")!!.displayName)
    }

    @Test
    fun EditProjectActions_FailedWriteChangesNothing() {
        failWrites = true
        val c = cmd(ProjectActionSet(actions = listOf(fire)))
        c.execute()

        assertFalse(c.wasSuccessful())
        assertTrue(c.getFailureReason()!!.contains("disk full"))
        assertNull(mappings.actionsPath)
        assertNull(map.definition("fire"))
        assertTrue(files.isEmpty())
    }

    @Test
    fun EditProjectActions_NoOpWhenNothingChanges() {
        cmd(ProjectActionSet(actions = listOf(fire))).execute()
        assertTrue(cmd(actions.current).isNoOp())
        assertFalse(cmd(actions.current.withoutAction("fire")).isNoOp())
    }

    @Test
    fun Bake_RebindOfProjectActionEditsItsDefaultsNotAnOverride() {
        cmd(ProjectActionSet(actions = listOf(fire))).execute()
        val baked = EditProjectActionsCommand.bake(map, actions.current, "Default", BindingOperation.Rebind("fire", 0, ActionBinding.Key(71)))!!
        assertEquals(RebindResult.Success, baked.result)
        assertEquals(listOf(ActionBinding.Key(71)), baked.set.action("fire")!!.defaults)
        assertFalse("fire" in baked.profile!!.overrides)

        val c = cmd(baked.set, baked.profile)
        c.execute()
        assertEquals(listOf(ActionBinding.Key(71)), map.bindings("fire"))
        assertFalse(map.isCustomized("fire"))
        c.undo()
        assertEquals(listOf(ActionBinding.Key(70)), map.bindings("fire"))
    }

    @Test
    fun Bake_SwapWithCodeActionKeepsCodeOverrideInProfile() {
        cmd(ProjectActionSet(actions = listOf(fire))).execute()
        val jumpKey = map.bindings(CharacterActions.JUMP).first { it is ActionBinding.Key }
        val baked = EditProjectActionsCommand.bake(
            map, actions.current, "Default", BindingOperation.Rebind("fire", 0, jumpKey, ConflictResolution.SWAP),
        )!!
        assertEquals(listOf(jumpKey), baked.set.action("fire")!!.defaults)
        assertTrue(ActionBinding.Key(70) in baked.profile!!.overrides[CharacterActions.JUMP]!!)
    }

    @Test
    fun Bake_IgnoresOperationsOnCodeActionsOnly() {
        cmd(ProjectActionSet(actions = listOf(fire))).execute()
        assertNull(EditProjectActionsCommand.bake(map, actions.current, "Default", BindingOperation.Rebind(CharacterActions.JUMP, 0, ActionBinding.Key(Keys.J))))
    }

    @Test
    fun Bake_RejectedConflictOnProjectActionIsReported() {
        cmd(ProjectActionSet(actions = listOf(fire))).execute()
        val jumpKey = map.bindings(CharacterActions.JUMP).first { it is ActionBinding.Key }
        val baked = EditProjectActionsCommand.bake(map, actions.current, "Default", BindingOperation.Rebind("fire", 0, jumpKey))!!
        assertTrue(baked.result is RebindResult.Conflict)
    }
}
