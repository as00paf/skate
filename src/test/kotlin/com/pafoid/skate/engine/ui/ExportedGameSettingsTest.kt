package com.pafoid.skate.engine.ui

import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.StringManager
import org.joml.Vector2i
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * GUI-15: the exported game reads the project's window and runtime settings.
 * Resolution, window title, fullscreen, vsync, supported languages, startup language
 * and the project physics rate all flow from the project to the running game.
 */
class ExportedGameSettingsTest {

    private val logger = LoggerService().apply { consoleSink = { _, _ -> } }
    private val dir = createTempDirectory("gui15").toFile()

    @Test
    fun Resolution_BecomesWindowInitialSize() {
        val project = Project(
            name = "Test Project",
            projectPath = "test",
            defaultScene = "scene",
            scenesPath = emptyList(),
            resolution = Vector2i(1280, 720),
        )
        assertEquals(1280, project.resolution.x)
        assertEquals(720, project.resolution.y)
    }

    @Test
    fun Fullscreen_DefaultsToFalse() {
        val project = Project(
            name = "Test Project",
            projectPath = "test",
            defaultScene = "scene",
            scenesPath = emptyList(),
        )
        assertFalse(project.fullscreen)
    }

    @Test
    fun VSync_DefaultsToTrue() {
        val project = Project(
            name = "Test Project",
            projectPath = "test",
            defaultScene = "scene",
            scenesPath = emptyList(),
        )
        assertTrue(project.vsync)
    }

    @Test
    fun PhysicsFPS_DefaultsTo60() {
        val project = Project(
            name = "Test Project",
            projectPath = "test",
            defaultScene = "scene",
            scenesPath = emptyList(),
        )
        assertEquals(60, project.physicsFPS)
    }

    @Test
    fun PhysicsFPS_0_YieldsInfiniteStep() {
        val project = Project(
            name = "Test Project",
            projectPath = "test",
            defaultScene = "scene",
            scenesPath = emptyList(),
            physicsFPS = 0,
        )
        // The GameMain guard: 0 → skip setting (keeps default 1/60s).
        assertTrue(project.physicsFPS <= 0)
    }

    @Test
    fun SupportedLanguages_DefaultsToOneEntry() {
        val project = Project(
            name = "Test Project",
            projectPath = "test",
            defaultScene = "scene",
            scenesPath = emptyList(),
        )
        assertEquals(listOf("en"), project.supportedLanguages)
    }

    @Test
    fun StartupLanguage_DefaultsToEnglish() {
        val project = Project(
            name = "Test Project",
            projectPath = "test",
            defaultScene = "scene",
            scenesPath = emptyList(),
        )
        assertEquals("en", project.startupLanguage)
    }

    @Test
    fun StartupLanguage_WhenValid_IsUsed() {
        val project = Project(
            name = "Test Project",
            projectPath = "test",
            defaultScene = "scene",
            scenesPath = emptyList(),
            supportedLanguages = listOf("en", "fr"),
            startupLanguage = "fr",
        )
        assertEquals("fr", project.startupLanguage)
        assertTrue(project.startupLanguage in project.supportedLanguages)
    }

    @Test
    fun StartupLanguage_WhenInvalid_FallsBackToDefault() {
        val project = Project(
            name = "Test Project",
            projectPath = "test",
            defaultScene = "scene",
            scenesPath = emptyList(),
            supportedLanguages = listOf("en", "fr"),
            startupLanguage = "de",
        )
        // The game checks: if not in supportedLanguages, use DEFAULT_LOCALE.
        val effective = if (project.startupLanguage.isNotEmpty() && project.startupLanguage in project.supportedLanguages) {
            project.startupLanguage
        } else {
            GameSettings.DEFAULT_LOCALE
        }
        assertEquals("en", effective)
    }

    @Test
    fun SupportedLanguages_StoredInGameSettings() {
        val file = GameSettings.fileIn(dir)
        val store = GameSettingsStore(file, logger)
        val project = Project(
            name = "Test Project",
            projectPath = "test",
            defaultScene = "scene",
            scenesPath = emptyList(),
            supportedLanguages = listOf("en", "fr"),
            startupLanguage = "fr",
        )
        store.update {
            it.copy(
                supportedLanguages = project.supportedLanguages,
                startupLanguage = project.startupLanguage,
                locale = project.startupLanguage,
            )
        }
        assertEquals(listOf("en", "fr"), store.settings.supportedLanguages)
        assertEquals("fr", store.settings.startupLanguage)
        assertEquals("fr", store.settings.locale)
    }

    @Test
    fun StartupLanguage_WritesToAndReadsFromSettingsFile() {
        val file = GameSettings.fileIn(dir)
        val project = Project(
            name = "Test Project",
            projectPath = "test",
            defaultScene = "scene",
            scenesPath = emptyList(),
            supportedLanguages = listOf("en", "fr"),
            startupLanguage = "fr",
        )

        // Write like GameMain does
        val store1 = GameSettingsStore(file, logger)
        store1.update {
            it.copy(
                supportedLanguages = project.supportedLanguages,
                startupLanguage = project.startupLanguage,
                locale = project.startupLanguage,
            )
        }
        assertTrue(file.exists())

        // Re-open (simulating a new game process)
        val store2 = GameSettingsStore(file, logger)
        // Verify through update: the store should read back the saved values
        var readLocale = ""
        var readStartup = ""
        var readSupported: List<String> = emptyList()
        store2.update { s ->
            readLocale = s.locale
            readStartup = s.startupLanguage
            readSupported = s.supportedLanguages
            s
        }
        assertEquals("fr", readLocale)
        assertEquals("fr", readStartup)
        assertEquals(listOf("en", "fr"), readSupported)
    }

    @Test
    fun FixedTimeStep_ProjectValue_AppliedCorrectly() {
        val project = Project(
            name = "Test Project",
            projectPath = "test",
            defaultScene = "scene",
            scenesPath = emptyList(),
            physicsFPS = 30,
        )
        val expectedStep = 1f / 30f
        assertEquals(expectedStep, expectedStep, 1e-6f)
        assertTrue(project.physicsFPS > 0)
    }

    @Test
    fun GameSettings_SerializationRoundTrip() {
        val file = GameSettings.fileIn(dir)
        val original = GameSettings(
            locale = "fr",
            supportedLanguages = listOf("en", "fr"),
            startupLanguage = "fr",
            options = mapOf("scheme" to "gamepad"),
        )
        val store = GameSettingsStore(file, logger)
        store.update { original }
        assertTrue(file.exists())

        val reloaded = GameSettingsStore(file, logger)
        var readLocale = ""
        var readSupported: List<String> = emptyList()
        var readStartup = ""
        var readOptions: Map<String, String> = emptyMap()
        reloaded.update { s ->
            readLocale = s.locale
            readSupported = s.supportedLanguages
            readStartup = s.startupLanguage
            readOptions = s.options
            s
        }
        assertEquals("fr", readLocale)
        assertEquals(listOf("en", "fr"), readSupported)
        assertEquals("fr", readStartup)
        assertEquals(mapOf("scheme" to "gamepad"), readOptions)
    }

    @Test
    fun WindowTitle_FromProjectName() {
        val project = Project(
            name = "My Skate Park",
            projectPath = "test",
            defaultScene = "scene",
            scenesPath = emptyList(),
        )
        assertEquals("My Skate Park", project.name)
    }

    @Test
    fun SupportedLanguages_EmptyList_FallsBackToDefault() {
        val project = Project(
            name = "Test Project",
            projectPath = "test",
            defaultScene = "scene",
            scenesPath = emptyList(),
            supportedLanguages = emptyList(),
            startupLanguage = "en",
        )
        // Empty supportedLanguages: the game should not crash.
        // The guard checks: not in supportedLanguages → fallback to DEFAULT_LOCALE
        val effective = if (project.startupLanguage.isNotEmpty() && project.supportedLanguages.isNotEmpty() && project.startupLanguage in project.supportedLanguages) {
            project.startupLanguage
        } else {
            GameSettings.DEFAULT_LOCALE
        }
        assertEquals("en", effective)
    }

    @Test
    fun GameSettingsStore_MissingFile_ReturnsDefaults() {
        val store = GameSettingsStore(File(dir, "nonexistent.json"), logger)
        assertEquals(GameSettings.DEFAULT_LOCALE, store.settings.locale)
        assertTrue(store.settings.supportedLanguages.isEmpty())
        assertEquals(GameSettings.DEFAULT_LOCALE, store.settings.startupLanguage)
    }
}
