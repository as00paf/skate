package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.commands.input.BindingOperation
import com.pafoid.skate.editor.events.KeyBindingsAction
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.input.ActionBinding
import com.pafoid.skate.engine.input.ActionMap
import com.pafoid.skate.engine.input.ProjectAction
import com.pafoid.skate.engine.input.ProjectActionSet
import com.pafoid.skate.engine.input.ProjectActions
import com.pafoid.skate.engine.input.UiNavigationActions
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class KeyBindingsProjectActionsTest {
    @TempDir
    lateinit var dir: File

    private val events = EventSystem()
    private val map = UiNavigationActions.register(ActionMap())
    private val project = Project(name = "p", projectPath = "", defaultScene = "", scenesPath = emptyList())
    private val mappings get() = project.gameplaySettings.inputMappings
    private val asset get() = File(dir, ProjectActionSet.DEFAULT_PATH)

    private lateinit var undo: UndoRedoManager
    private lateinit var actions: ProjectActions

    private fun start() {
        val projectManager = mockk<ProjectManager>(relaxed = true)
        every { projectManager.currentProject } returns project
        every { projectManager.getProjectDirectory() } returns dir
        undo = UndoRedoManager(events, LoggerService())
        actions = ProjectActions(map, { path -> File(dir, path).takeIf { it.exists() }?.readText() })
        KeyBindingsActionHandler(events, projectManager, undo, { actions }, { map })
    }

    private fun edit(after: ProjectActionSet, key: String = "command.project_actions.add") =
        events.publish(KeyBindingsAction.EditProjectActions(after, key, listOf("fire")))

    @Test
    fun KeyBindingsProject_AddRenameAndRemoveAnActionWithUndo() {
        start()
        val fire = ProjectAction("fire", "Fire", defaults = listOf(ActionBinding.Key(70)))

        edit(ProjectActionSet(actions = listOf(fire)))
        assertEquals(listOf(ActionBinding.Key(70)), map.bindings("fire"))
        assertEquals("Fire", ProjectActionSet.fromJson(asset.readText()).action("fire")?.name)

        edit(actions.current.withAction(fire.copy(name = "Shoot")), "command.project_actions.edit")
        assertEquals("Shoot", ProjectActionSet.fromJson(asset.readText()).action("fire")?.name)

        edit(actions.current.withoutAction("fire"), "command.project_actions.delete")
        assertNull(map.definition("fire"))

        undo.undo()
        assertNotNull(map.definition("fire"))
        undo.undo()
        assertEquals("Fire", ProjectActionSet.fromJson(asset.readText()).action("fire")?.name)
        undo.undo()
        assertNull(map.definition("fire"))
    }

    @Test
    fun KeyBindingsProject_RebindingAProjectActionEditsItsDefaultBindings() {
        start()
        edit(ProjectActionSet(actions = listOf(ProjectAction("fire", "Fire", defaults = listOf(ActionBinding.Key(70))))))

        events.publish(KeyBindingsAction.Apply(BindingOperation.Rebind("fire", 0, ActionBinding.Key(71))))

        assertEquals(listOf(ActionBinding.Key(71)), ProjectActionSet.fromJson(asset.readText()).action("fire")?.defaults)
        assertFalse(mappings.bindings.overrides.containsKey("fire"))

        undo.undo()
        assertEquals(listOf(ActionBinding.Key(70)), map.bindings("fire"))
    }
}
