package com.pafoid.skate.engine.input

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProjectActionSetTest {

    private val fire = ProjectAction(
        "fire", "Fire", category = "Combat",
        defaults = listOf(ActionBinding.Key(70), ActionBinding.GamepadButton(2)), events = true,
    )
    private val steer = ProjectAction(
        "steer", "Steer", context = "vehicle", kind = ActionKind.AXIS,
        defaults = listOf(ActionBinding.Key(68), ActionBinding.Key(65, -1f), ActionBinding.GamepadAxis(0)),
    )
    private val vehicle = ProjectContext("vehicle", "Vehicle", priority = 10, blocking = ContextBlocking.CONSUME_BOUND)

    @Test
    fun ProjectActionSet_JsonRoundTrip() {
        val set = ProjectActionSet(contexts = listOf(vehicle), actions = listOf(fire, steer))
        assertEquals(set, ProjectActionSet.fromJson(set.toJson()))
    }

    @Test
    fun ProjectActionSet_ReadsDesignNoteFormat() {
        val text = """
            { "version": 1,
              "contexts": [ { "id": "vehicle", "name": "Vehicle", "priority": 10, "blocking": "consume", "activeAtStart": false } ],
              "actions": [
                { "id": "fire", "name": "Fire", "context": "gameplay", "category": "Combat", "kind": "button",
                  "defaults": [ { "type": "key", "key": 70 }, { "type": "pad", "button": 2 } ], "events": true },
                { "id": "steer", "name": "Steer", "context": "vehicle", "kind": "axis",
                  "defaults": [ { "type": "key", "key": 68 }, { "type": "key", "key": 65, "scale": -1.0 }, { "type": "axis", "axis": 0 } ] }
              ] }
        """.trimIndent()
        assertEquals(ProjectActionSet(contexts = listOf(vehicle), actions = listOf(fire, steer)), ProjectActionSet.fromJson(text))
    }

    @Test
    fun ActionBinding_WritesShortNamesAndOmitsUnitScale() {
        val text = Json.encodeToString(ActionBindingSerializer, ActionBinding.Key(70))
        assertEquals("""{"type":"key","key":70}""", text)
        assertEquals(
            """{"type":"axis","axis":1,"scale":-1.0}""",
            Json.encodeToString(ActionBindingSerializer, ActionBinding.GamepadAxis(1, -1f)),
        )
    }

    @Test
    fun BindingProfile_DecodesLegacyClassNames() {
        val legacy = """
            { "name": "Old", "version": 1, "overrides": { "jump": [
                { "type": "com.pafoid.skate.engine.input.ActionBinding.Key", "key": 70, "scale": 1.0 },
                { "type": "com.pafoid.skate.engine.input.ActionBinding.MouseButton", "button": 1, "scale": 1.0 },
                { "type": "com.pafoid.skate.engine.input.ActionBinding.GamepadButton", "button": 3, "scale": 1.0 },
                { "type": "com.pafoid.skate.engine.input.ActionBinding.GamepadAxis", "axis": 2, "scale": -1.0 } ] } }
        """.trimIndent()
        val expected = listOf(
            ActionBinding.Key(70), ActionBinding.MouseButton(1), ActionBinding.GamepadButton(3), ActionBinding.GamepadAxis(2, -1f),
        )
        val profile = BindingProfile.fromJson(legacy)
        assertEquals(expected, profile.overrides["jump"])
        assertEquals(profile, BindingProfile.fromJson(profile.toJson()))
    }

    @Test
    fun ProjectActionSet_ValidationSkipsBadEntriesAndKeepsTheRest() {
        val set = ProjectActionSet(
            contexts = listOf(vehicle, ProjectContext("Bad Id"), vehicle.copy(name = "again")),
            actions = listOf(
                fire,
                ProjectAction("Fire!"),
                fire.copy(name = "dup"),
                ProjectAction("jump"),
                ProjectAction("open_map", context = "nowhere"),
                ProjectAction("ride", context = "skate"),
                steer,
            ),
        )
        val result = set.validate(codeIds = setOf("jump"), codeContexts = setOf("skate"))

        assertEquals(listOf("fire", "ride", "steer"), result.valid.actions.map { it.id })
        assertEquals(listOf(vehicle), result.valid.contexts)
        assertEquals(
            listOf(
                ProjectActionIssue("Bad Id", ProjectActionProblem.INVALID_ID, isContext = true),
                ProjectActionIssue("vehicle", ProjectActionProblem.DUPLICATE_ID, isContext = true),
                ProjectActionIssue("Fire!", ProjectActionProblem.INVALID_ID),
                ProjectActionIssue("fire", ProjectActionProblem.DUPLICATE_ID),
                ProjectActionIssue("jump", ProjectActionProblem.CODE_ID),
                ProjectActionIssue("open_map", ProjectActionProblem.UNKNOWN_CONTEXT),
            ),
            result.issues,
        )
    }

    @Test
    fun ProjectActionSet_IdProblemForLiveValidation() {
        assertNull(ProjectActionSet.idProblem("open_map.2", emptySet(), emptySet()))
        assertEquals(ProjectActionProblem.INVALID_ID, ProjectActionSet.idProblem("", emptySet(), emptySet()))
        assertEquals(ProjectActionProblem.INVALID_ID, ProjectActionSet.idProblem("2fire", emptySet(), emptySet()))
        assertEquals(ProjectActionProblem.CODE_ID, ProjectActionSet.idProblem("jump", setOf("jump"), emptySet()))
        assertEquals(ProjectActionProblem.DUPLICATE_ID, ProjectActionSet.idProblem("fire", emptySet(), setOf("fire")))
    }

    @Test
    fun ProjectActionSet_WithAndWithoutEntries() {
        val set = ProjectActionSet(actions = listOf(fire)).withAction(steer).withAction(fire.copy(name = "Shoot"))
        assertEquals(listOf("Shoot", "Steer"), set.actions.map { it.name })
        assertEquals(listOf("steer"), set.withoutAction("fire").actions.map { it.id })
        val ctx = set.withContext(vehicle).withContext(vehicle.copy(priority = 3))
        assertEquals(listOf(3), ctx.contexts.map { it.priority })
        assertTrue(ctx.withoutContext("vehicle").contexts.isEmpty())
    }

    @Test
    fun ProjectAction_DefinitionCarriesOriginKindAndName() {
        val def = steer.toDefinition()
        assertEquals(ActionOrigin.PROJECT, def.origin)
        assertEquals(ActionKind.AXIS, def.kind)
        assertEquals("Steer", def.displayName)
        assertEquals(InputContext("vehicle"), def.context)
        assertFalse(def.publishEvents)
        assertTrue(fire.toDefinition().publishEvents)
        assertNull(ProjectAction("x").toDefinition().displayName)
    }
}
