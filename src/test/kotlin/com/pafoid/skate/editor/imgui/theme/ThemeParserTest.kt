package com.pafoid.skate.editor.imgui.theme

import com.pafoid.skate.editor.imgui.ImGuiStyleManager
import com.pafoid.skate.editor.imgui.data.Style
import com.pafoid.skate.editor.imgui.data.Theme
import com.pafoid.skate.engine.assets.Assets
import org.joml.Vector2f
import org.joml.Vector4f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.util.Properties
import kotlin.io.path.createTempDirectory

/** EDT-07: theme files are parsed strictly, the shipped themes load, and the effective theme follows preview/saved. */
class ThemeParserTest {

    private val dir: File = createTempDirectory("edt07").toFile()

    @AfterEach
    fun cleanup() {
        dir.deleteRecursively()
        ImGuiStyleManager.preview = null
        ImGuiStyleManager.library = ThemeLibrary(emptyList())
    }

    private fun near(expected: Vector4f, actual: Vector4f) =
        listOf(expected.x to actual.x, expected.y to actual.y, expected.z to actual.z, expected.w to actual.w)
            .all { (e, a) -> kotlin.math.abs(e - a) <= 1f / 255f + 1e-4f }

    @Test
    fun ParseColor_RgbAndRgba() {
        assertEquals(Vector4f(1f, 0f, 0f, 1f), ThemeParser.parseColor("#FF0000"))
        val c = ThemeParser.parseColor("#00FF0080")
        assertEquals(0f, c.x); assertEquals(1f, c.y); assertEquals(128 / 255f, c.w)
        assertEquals(Vector4f(0f, 0f, 1f, 1f), ThemeParser.parseColor("0000ff"))
        listOf("#FFF", "#GG0000", "", "#FF00000").forEach { bad ->
            assertThrows<ThemeParseException>("'$bad' accepted") { ThemeParser.parseColor(bad) }
        }
    }

    @Test
    fun Parse_MissingFieldsFallBackToTheBuiltInLook() {
        val t = ThemeParser.parse("""{ "id": "mini", "nameKey": "k", "colors": { "accent": "#112233" } }""")
        assertEquals("mini", t.id)
        assertEquals(1f, t.fontScale)
        assertEquals(Style().rounding, t.style.rounding)
        assertEquals(Theme().text, t.style.theme.text)
        assertEquals(ThemeParser.parseColor("#112233"), t.style.theme.accent)
    }

    @Test
    fun Parse_SizesAndFont() {
        val t = ThemeParser.parse(
            """{ "id": "x", "nameKey": "k", "font": { "scale": 1.25 },
                 "sizes": { "rounding": 0, "framePadding": [6, 4], "windowBorderSize": 2, "frameBorderSize": 1 } }"""
        )
        assertEquals(1.25f, t.fontScale)
        assertEquals(0f, t.style.rounding)
        assertEquals(Vector2f(6f, 4f), t.style.framePadding)
        assertEquals(2f, t.style.windowBorderSize)
        assertEquals(1f, t.style.frameBorderSize)
    }

    @Test
    fun Parse_Errors() {
        val cases = mapOf(
            "not json" to "{",
            "missing id" to """{ "nameKey": "k" }""",
            "blank id" to """{ "id": " ", "nameKey": "k" }""",
            "unknown color" to """{ "id": "x", "nameKey": "k", "colors": { "txet": "#FFFFFF" } }""",
            "bad color" to """{ "id": "x", "nameKey": "k", "colors": { "text": "white" } }""",
            "bad pair" to """{ "id": "x", "nameKey": "k", "sizes": { "itemSpacing": [1, 2, 3] } }""",
            "unknown size" to """{ "id": "x", "nameKey": "k", "sizes": { "roundness": 3 } }""",
            "font scale" to """{ "id": "x", "nameKey": "k", "font": { "scale": 10 } }""",
        )
        cases.forEach { (what, text) -> assertThrows<ThemeParseException>(what) { ThemeParser.parse(text) } }
    }

    @Test
    fun ShippedThemes_LoadWithoutErrors_AndHaveNamesInBothLanguages() {
        val lib = ThemeLibrary.load(File(Assets.Themes.DIR))
        assertTrue(lib.errors.isEmpty(), "theme errors: ${lib.errors}")
        assertEquals(setOf("dark", "high_contrast", "light"), lib.themes.map { it.id }.toSet())
        lib.themes.forEach { t ->
            // Every color is set explicitly in a shipped theme.
            val text = File(Assets.Themes.DIR, "${t.id}${ThemeLibrary.SUFFIX}").readText()
            ThemeParser.COLOR_NAMES.forEach { assertTrue(text.contains("\"$it\""), "${t.id} lacks color $it") }
        }
        val strings = listOf("gameui.properties", "gameui_fr.properties").map { name ->
            Properties().apply { File(Assets.Strings.EDITOR_STRINGS, name).reader(Charsets.UTF_8).use { load(it) } }
        }
        lib.themes.forEach { t -> strings.forEach { assertTrue(it.containsKey(t.nameKey), "missing string ${t.nameKey}") } }
    }

    @Test
    fun DarkTheme_IsTheCurrentLook() {
        val dark = ThemeLibrary.load(File(Assets.Themes.DIR)).resolve("dark")
        val built = Theme()
        assertTrue(near(built.background, dark.style.theme.background))
        assertTrue(near(built.accent, dark.style.theme.accent))
        assertTrue(near(built.text, dark.style.theme.text))
        assertEquals(Style().rounding, dark.style.rounding)
    }

    @Test
    fun Library_SkipsBrokenFiles_AndResolvesWithFallback() {
        File(dir, "a.theme.json").writeText("""{ "id": "dark", "nameKey": "k" }""")
        File(dir, "b.theme.json").writeText("""{ "id": "light", "nameKey": "k" }""")
        File(dir, "broken.theme.json").writeText("""{ "id": """)
        File(dir, "notes.json").writeText("ignored")
        val lib = ThemeLibrary.load(dir)
        assertEquals(listOf("dark", "light"), lib.themes.map { it.id })
        assertEquals(1, lib.errors.size)
        assertTrue(lib.errors.single().startsWith("broken.theme.json"))
        assertEquals("light", lib.resolve("light").id)
        assertEquals("dark", lib.resolve("Islands Dark").id, "unknown ids fall back to the default theme")
        assertSame(EditorTheme.BUILT_IN, ThemeLibrary(emptyList()).resolve("light"))
    }

    @Test
    fun EffectiveTheme_PreviewWinsUntilCleared() {
        ImGuiStyleManager.library = ThemeLibrary.load(File(Assets.Themes.DIR))
        assertEquals("dark", ImGuiStyleManager.effective("dark").id)
        ImGuiStyleManager.preview = "high_contrast"
        assertEquals("high_contrast", ImGuiStyleManager.effective("dark").id)
        ImGuiStyleManager.preview = null
        assertEquals("light", ImGuiStyleManager.effective("light").id)
    }
}
