package com.pafoid.skate.editor.systems

import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ui.GameLocale
import com.pafoid.skate.engine.ui.GameSettings
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** EDT-39: the game locale follows the current project's game.settings.json, including newly created projects. */
class GameSettingsBindingTest {

    @TempDir
    lateinit var dir: File

    private val logger = mockk<LoggerService>(relaxed = true)
    private val events = EventSystem(logger)
    private val locale = GameLocale(StringManager(logger)).also { GameSettingsBinding.install(events, it, logger) }

    private fun project(name: String): Project {
        val folder = File(dir, name).apply { mkdirs() }
        return Project(name = name, projectPath = File(folder, "$name.skateproject").path, defaultScene = "", scenesPath = emptyList())
    }

    private fun settingsOf(p: Project) = GameSettings.fileIn(p.getProjectDirectory())

    @Test
    fun Opened_AppliesTheProjectsSavedLocale() {
        val a = project("A")
        settingsOf(a).writeText("""{"locale":"fr"}""")
        events.publish(ProjectEvent.Opened(a))
        assertEquals("fr", locale.strings.locale)
        assertEquals(settingsOf(a), locale.store?.file)
    }

    @Test
    fun Created_AttachesTheNewProject_SoChangesNeverLandInThePreviousOne() {
        val a = project("A")
        events.publish(ProjectEvent.Opened(a))
        locale.set("fr")
        assertTrue(settingsOf(a).readText().contains("\"fr\""))

        val b = project("B")
        events.publish(ProjectEvent.Created(b))
        assertEquals(GameSettings.DEFAULT_LOCALE, locale.strings.locale, "a new project starts in the default locale")
        locale.set("fr")
        assertTrue(settingsOf(b).isFile)

        locale.set("en")
        assertTrue(settingsOf(a).readText().contains("\"fr\""), "project A's file is untouched")
    }

    @Test
    fun Closed_Detaches() {
        val a = project("A")
        events.publish(ProjectEvent.Opened(a))
        locale.set("fr")
        events.publish(ProjectEvent.Closed(a))
        assertNull(locale.store)
        assertEquals(GameSettings.DEFAULT_LOCALE, locale.strings.locale)
        locale.set("en")
        assertTrue(settingsOf(a).readText().contains("\"fr\""))
        assertFalse(locale.store != null)
    }
}
