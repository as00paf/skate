package com.pafoid.skate.editor.ui.windows

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** GPL-26: the Properties window edits character configs and animator controllers as fields, with Apply/Revert. */
class ConfigFileDraftTest {

    private val character = File("Assets/Data/skater.character.json")
    private val controller = File("Assets/Animations/skater.controller.json")
    private val characterText = """{"startMounted":false,"push":{"acceleration":4.0,"maxSpeed":8},"mount":{"targetName":"Skateboard"}}"""
    private val controllerText = """{"name":"skater","defaultState":"idle","states":[{"name":"idle","loop":true},{"name":"jump","loop":false}]}"""

    @Test
    fun handlesCharacterConfigsAndControllersOnly() {
        assertTrue(ConfigFileDraft.handles(character))
        assertTrue(ConfigFileDraft.handles(controller))
        assertTrue(ConfigFileDraft.handles(File("My.Character.JSON")))
        assertFalse(ConfigFileDraft.handles(File("Assets/Data/strings.json")))
        assertFalse(ConfigFileDraft.handles(File("Assets/Scenes/Main.scene")))
    }

    @Test
    fun everyLeafIsAFieldWithItsKindAndSection() {
        val draft = ConfigFileDraft().apply { show(character, characterText) }

        val byPath = draft.fields.associateBy { it.path }
        assertEquals(listOf("startMounted", "push.acceleration", "push.maxSpeed", "mount.targetName"), draft.fields.map { it.path })
        assertEquals(ConfigField.Kind.BOOL, byPath.getValue("startMounted").kind)
        assertEquals(ConfigField.Kind.NUMBER, byPath.getValue("push.acceleration").kind)
        assertEquals(ConfigField.Kind.INT, byPath.getValue("push.maxSpeed").kind)
        assertEquals(ConfigField.Kind.TEXT, byPath.getValue("mount.targetName").kind)
        assertEquals("push", byPath.getValue("push.acceleration").section)
        assertEquals("acceleration", byPath.getValue("push.acceleration").label)
        assertEquals("startMounted", byPath.getValue("startMounted").label)
    }

    @Test
    fun controllerStatesAreLabelledByName() {
        val draft = ConfigFileDraft().apply { show(controller, controllerText) }

        val loop = draft.fields.single { it.path == "states.1.loop" }
        assertEquals("states", loop.section)
        assertEquals("jump › loop", loop.label)
    }

    @Test
    fun editApplyAndRevert() {
        val draft = ConfigFileDraft().apply { show(character, characterText) }

        draft.set("push.acceleration", JsonPrimitive(6.5))
        assertTrue(draft.dirty)
        draft.revert()
        assertFalse(draft.dirty)

        draft.set("push.acceleration", JsonPrimitive(6.5))
        val saved = Json.parseToJsonElement(draft.text()).jsonObject
        assertEquals("6.5", saved.getValue("push").jsonObject.getValue("acceleration").toString())
        assertEquals("Skateboard", (saved.getValue("mount").jsonObject.getValue("targetName") as JsonPrimitive).content, "untouched values kept")
        draft.applied()
        assertFalse(draft.dirty)
    }

    @Test
    fun pendingEditsSurviveARefresh_ButAnotherFileReloads() {
        val draft = ConfigFileDraft().apply { show(character, characterText) }
        draft.set("startMounted", JsonPrimitive(true))

        draft.show(character, characterText) // same file re-read while editing: edits kept
        assertTrue(draft.dirty)

        draft.show(controller, controllerText)
        assertFalse(draft.dirty)
        assertEquals(controller, draft.file)
    }

    @Test
    fun invalidJson_IsReportedAndHasNoFields() {
        val draft = ConfigFileDraft().apply { show(character, "{not json") }

        assertNotNull(draft.error)
        assertEquals(emptyList<ConfigField>(), draft.fields)
        assertNull(draft.value)
    }
}
