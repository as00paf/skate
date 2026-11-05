package com.pafoid.skate.editor.imgui

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.core.StringTables
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** EDT-38: ImGui IDs of translated labels must not change with the editor language. */
class ImGuiIdsTest {

    private fun strings(locale: String) = StringManager(mockk(relaxed = true), "en").apply {
        loadStrings(Assets.Strings.EDITOR_STRINGS)
        addStrings(Assets.Strings.GAME_STRINGS)
        if (locale != "en") setLocale(locale)
    }

    private val windowKeys = StringTables.merged(File(Assets.Strings.EDITOR_STRINGS)).stringPropertyNames()
        .filter { it.matches(Regex("""window\.[a-z_]+""")) }.sorted()

    @Test
    fun HashedPart_IsTheStableIdOrTheWholeLabel() {
        assertEquals("###window.console", ImGuiIds.hashedPart("Console###window.console"))
        assertEquals("Console##x", ImGuiIds.hashedPart("Console##x")) // '##' hides text but hashes all of it
        assertEquals("Console", ImGuiIds.hashedPart("Console"))
    }

    @Test
    fun TranslatedTitles_ChangedIdWithTheLanguage_WhichIsTheBug() {
        val en = strings("en")
        val fr = strings("fr")
        val changed = windowKeys.filter { ImGuiIds.hashedPart(en.getString(it)) != ImGuiIds.hashedPart(fr.getString(it)) }
        assertTrue(changed.isNotEmpty(), "Plain translated titles hash differently in EN and FR (e.g. window.hierarchy)")
    }

    @Test
    fun StableLabels_KeepTheirIdAcrossLanguages_AndShowTheTranslation() {
        val en = strings("en")
        val fr = strings("fr")
        assertTrue(windowKeys.size > 10)
        windowKeys.forEach { key ->
            assertEquals(ImGuiIds.hashedPart(en.label(key)), ImGuiIds.hashedPart(fr.label(key)), key)
            assertTrue(fr.label(key).startsWith(fr.getString(key)), key)
        }
        assertNotEquals(en.label("window.hierarchy"), fr.label("window.hierarchy"))
    }

    @Test
    fun SwitchingLocaleAtRuntime_KeepsWindowIds() {
        val sm = strings("en")
        val before = windowKeys.associateWith { ImGuiIds.hashedPart(sm.label(it)) }
        sm.setLocale("fr")
        val after = windowKeys.associateWith { ImGuiIds.hashedPart(sm.label(it)) }
        assertEquals(before, after)
    }

    @Test
    fun WindowTitle_UsesTheWindowNameAsIdEvenWithAnotherTextKey() {
        val window = object : EditorWindow("window.project_switcher") { override fun imgui() {} }
        val sm = strings("fr")
        assertEquals("###window.project_switcher", ImGuiIds.hashedPart(window.title(sm, textKey = "window.switch_project")))
        assertEquals("###window.project_switcher", ImGuiIds.hashedPart(window.title(sm)))
    }

    @Test
    fun LayoutStore_IgnoresAnIniWithoutStableIds(@TempDir dir: File) {
        val ini = File(dir, "imgui.ini")
        val store = EditorLayoutStore(ini, File(dir, "imgui.windows"))
        ini.writeText("[Window][Console]\nPos=0,0\nDockId=0x1,0\n")
        assertFalse(store.hasSavedDockLayout())
        ini.writeText("[Window][Console###window.console]\nPos=0,0\nDockId=0x1,0\n")
        assertTrue(store.hasSavedDockLayout())
    }
}
