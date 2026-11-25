package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.engine.gameplay.CharacterActions
import com.pafoid.skate.engine.input.ActionMap
import com.pafoid.skate.engine.input.ActionOrigin
import com.pafoid.skate.engine.input.InputContext
import com.pafoid.skate.engine.input.ProjectAction
import com.pafoid.skate.engine.input.ProjectActionSet
import com.pafoid.skate.engine.input.ProjectActions
import com.pafoid.skate.engine.input.ProjectContext
import com.pafoid.skate.engine.input.UiNavigationActions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** INP-11: the pure parts of the Key Bindings project action UI. */
class ProjectActionsModelTest {
    private val map = CharacterActions.register(UiNavigationActions.register(ActionMap()))
    private val set = ProjectActionSet(
        contexts = listOf(ProjectContext("vehicle", "Vehicle")),
        actions = listOf(ProjectAction("fire", "Fire", category = "Combat"), ProjectAction("steer", context = "vehicle")),
    )

    init {
        ProjectActions(map, { null }).apply(set)
    }

    private fun none(@Suppress("UNUSED_PARAMETER") key: String): String? = null

    @Test
    fun ProjectActionsModel_RowsCarryOriginAndPlainName() {
        val rows = ActionBindingsModel.groups(map, "", { ActionBindingsModel.actionName(it, ::none) }, { it.category })
            .flatMap { it.rows }
        val fire = rows.single { it.definition.id == "fire" }
        assertEquals(ActionOrigin.PROJECT, fire.definition.origin)
        assertEquals("Fire", fire.name)
        assertEquals("steer", rows.single { it.definition.id == "steer" }.name)
        assertEquals(ActionOrigin.CODE, rows.single { it.definition.id == CharacterActions.JUMP }.definition.origin)
    }

    @Test
    fun ProjectActionsModel_IdErrorKeys() {
        val code = setOf(CharacterActions.JUMP)
        assertNull(ActionBindingsModel.idErrorKey("open_map", code, setOf("fire")))
        assertEquals("msg.keybindings.id_invalid", ActionBindingsModel.idErrorKey("Open Map", code, emptySet()))
        assertEquals("msg.keybindings.id_code", ActionBindingsModel.idErrorKey(CharacterActions.JUMP, code, emptySet()))
        assertEquals("msg.keybindings.id_taken", ActionBindingsModel.idErrorKey("fire", code, setOf("fire")))
    }

    @Test
    fun ProjectActionsModel_ContextChoicesBuiltInThenProject() {
        assertEquals(listOf("gameplay", "ui", "editor", "vehicle"), ActionBindingsModel.contextChoices(map, set))
        assertEquals("Vehicle", ActionBindingsModel.contextLabel(InputContext("vehicle"), ::none, set))
        assertEquals("other", ActionBindingsModel.contextLabel(InputContext("other"), ::none, set))
    }

    @Test
    fun ProjectActionsModel_DuplicateChangeIdAndContextUsers() {
        assertEquals("fire_copy", ActionBindingsModel.duplicateId("fire", setOf("fire")))
        assertEquals("fire_copy3", ActionBindingsModel.duplicateId("fire", setOf("fire", "fire_copy", "fire_copy2")))
        assertEquals(listOf("shoot", "steer"), ActionBindingsModel.changeId(set, "fire", "shoot").actions.map { it.id })
        assertEquals(listOf("steer"), ActionBindingsModel.contextUsers(set, "vehicle"))
        assertEquals("Combat", ActionBindingsModel.categorySuggestions(map).first { it == "Combat" })
    }
}
