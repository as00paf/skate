package com.pafoid.skate.editor.commands

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.core.StringTables
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

private fun named(name: String): Command = mockk(relaxed = true) { every { getDisplayName() } returns name }

/** EDT-06: Command History shows translated command names. */
class CommandNamesTest {

    private fun strings(locale: String) = StringManager(mockk(relaxed = true), "en").apply {
        loadStrings(Assets.Strings.EDITOR_STRINGS)
        if (locale != "en") setLocale(locale)
    }

    private val editorKeys = StringTables.merged(File(Assets.Strings.EDITOR_STRINGS)).stringPropertyNames()

    /** Same simple class name as a real command, so the lookup key matches. */
    private class SaveSceneCommand : Command by mockk(relaxed = true)
    private class UnkeyedTestCommand : Command by named("Move a → b")

    @Test
    fun KeyedCommand_ShowsTheTranslation() {
        assertEquals("Save Scene", CommandNames.displayName(SaveSceneCommand(), strings("en")))
        assertEquals("Enregistrer la scène", CommandNames.displayName(SaveSceneCommand(), strings("fr")))
    }

    @Test
    fun CommandWithoutKey_FallsBackToItsDisplayName() {
        assertEquals("Move a → b", CommandNames.displayName(UnkeyedTestCommand(), strings("fr")))
    }

    @Test
    fun EveryCommandWithAFixedName_HasAKey() {
        val fixed = Regex("""override fun getDisplayName\(\): String = "[^"$]*"\s*$""", RegexOption.MULTILINE)
        val missing = File("src/main/kotlin/com/pafoid/skate/editor").walkTopDown()
            .filter { it.isFile && it.extension == "kt" && fixed.containsMatchIn(it.readText()) }
            .map { "command.${it.nameWithoutExtension}" }
            .filter { it !in editorKeys }
            .toList()
        assertTrue(missing.isEmpty(), "Add these keys to assets/strings/editor/core.properties (+ _fr): $missing")
    }

    @Test
    fun SearchActions_UseExistingKeys() {
        val src = File("src/main/kotlin/com/pafoid/skate/editor/search/providers/ActionSearchProvider.kt").readText()
        val used = Regex("""(?:displayNameKey|descriptionKey) = "([\w.]+)"""").findAll(src).map { it.groupValues[1] }.toList()
        assertTrue(used.size >= 30)
        assertEquals(emptyList<String>(), used.filterNot { it in editorKeys })
    }
}
