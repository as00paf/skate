package com.pafoid.skate.engine.input

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProjectActionsTest {
    private val files = HashMap<String, String>()
    private val warnings = ArrayList<String>()
    private val map = UiNavigationActions.register(ActionMap())
        .define(ActionDefinition("jump", InputContext.GAMEPLAY, "Movement", defaults = listOf(ActionBinding.Key(Keys.SPACE))))
    private val actions = ProjectActions(map, files::get, warnings::add)

    private val fire = ProjectAction("fire", "Fire", defaults = listOf(ActionBinding.Key(70)))
    private val path = ProjectActionSet.DEFAULT_PATH

    @Test
    fun ProjectActions_LoadDefinesActionsFromTheAsset() {
        files[path] = ProjectActionSet(actions = listOf(fire)).toJson()
        actions.load(path)

        val def = map.definition("fire")!!
        assertEquals(ActionOrigin.PROJECT, def.origin)
        assertEquals(listOf(ActionBinding.Key(70)), map.bindings("fire"))
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun ProjectActions_NullPathOrMissingFileDefinesNothing() {
        val before = map.names
        actions.load(null)
        actions.load(path)
        assertEquals(before, map.names)
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun ProjectActions_UnreadableFileIsReportedAndLoadsNothing() {
        files[path] = "{ not json"
        actions.load(path)
        assertNull(map.definition("fire"))
        assertEquals(1, warnings.size)
    }

    @Test
    fun ProjectActions_ReapplyRemovesActionsThatAreGone() {
        actions.apply(ProjectActionSet(actions = listOf(fire, ProjectAction("open_map"))))
        actions.apply(ProjectActionSet(actions = listOf(fire.copy(name = "Shoot"))))

        assertNull(map.definition("open_map"))
        assertEquals("Shoot", map.definition("fire")!!.displayName)
        actions.apply(ProjectActionSet())
        assertNull(map.definition("fire"))
    }

    @Test
    fun ProjectActions_CodeIdWinsAndIsNeverRemoved() {
        actions.apply(ProjectActionSet(actions = listOf(ProjectAction("jump", defaults = listOf(ActionBinding.Key(70))))))
        assertEquals(ActionOrigin.CODE, map.definition("jump")!!.origin)
        assertEquals(listOf(ActionBinding.Key(Keys.SPACE)), map.bindings("jump"))
        assertEquals(listOf(ProjectActionIssue("jump", ProjectActionProblem.CODE_ID)), actions.issues)

        actions.apply(ProjectActionSet())
        assertTrue(map.definition("jump") != null)
        assertTrue("jump" in actions.codeIds())
        assertTrue(UiNavigationActions.CONFIRM in actions.codeIds())
    }

    @Test
    fun ProjectActions_StartContextsArePushedAndPopped() {
        val stack = InputContextStack.withDefaults()
        actions.apply(
            ProjectActionSet(
                contexts = listOf(
                    ProjectContext("vehicle", priority = 10, blocking = ContextBlocking.ALL, activeAtStart = true),
                    ProjectContext("map_screen"),
                ),
            ),
        )
        actions.pushStartContexts(stack)
        actions.pushStartContexts(stack) // held once only
        val pushed = stack.entries().filter { it.owner == ProjectActions.OWNER }
        assertEquals(listOf(InputContext("vehicle")), pushed.map { it.context })
        assertEquals(10, pushed.single().priority)
        assertEquals(ContextBlocking.ALL, pushed.single().blocking)
        assertTrue(actions.holdsStartContexts)
        assertEquals(10, actions.context("vehicle")!!.priority)

        actions.popStartContexts(stack)
        assertFalse(stack.isActive(InputContext("vehicle")))
        assertFalse(actions.holdsStartContexts)
    }
}

class ActionMapRemoveTest {
    @Test
    fun ActionMap_RemoveKeepsOverrideAsideUntilRedefined() {
        val def = ActionDefinition("fire", InputContext.GAMEPLAY, "Combat", defaults = listOf(ActionBinding.Key(70)))
        val map = ActionMap().define(def)
        map.rebind("fire", 0, ActionBinding.Key(71))
        val revision = map.revision

        assertTrue(map.remove("fire"))
        assertFalse(map.remove("fire"))
        assertTrue(map.revision > revision)
        assertNull(map.definition("fire"))
        assertEquals(listOf(ActionBinding.Key(71)), map.toProfile().overrides["fire"])

        map.define(def)
        assertEquals(listOf(ActionBinding.Key(71)), map.bindings("fire"))
    }
}
