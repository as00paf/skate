package com.pafoid.skate.editor.imgui.theme

import com.pafoid.skate.editor.imgui.data.Style
import com.pafoid.skate.editor.imgui.data.Theme
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.joml.Vector2f
import org.joml.Vector4f

/** An editor theme loaded from a `*.theme.json` file (EDT-07): colors, sizes and font scale. */
data class EditorTheme(
    val id: String,
    /** String key of the name shown in the theme picker. */
    val nameKey: String,
    val style: Style,
    /** ImGui global font scale. */
    val fontScale: Float = 1f,
) {
    companion object {
        const val DEFAULT_ID = "dark"

        /** The built-in look, used when no theme file can be read. */
        val BUILT_IN = EditorTheme(DEFAULT_ID, "settings.theme.dark", Style())
    }
}

class ThemeParseException(message: String) : IllegalArgumentException(message)

/**
 * Reads theme files. Every field except `id` and `nameKey` is optional and falls back to the built-in look; unknown
 * color names and malformed values are errors, so a typo never passes silently.
 *
 * ```json
 * { "id": "dark", "nameKey": "settings.theme.dark", "font": { "scale": 1.0 },
 *   "sizes": { "rounding": 6, "windowPadding": [8, 8] },
 *   "colors": { "text": "#D9D9D9", "accent": "#4296FAFF" } }
 * ```
 * Colors are `#RRGGBB` or `#RRGGBBAA`; size pairs are `[x, y]`.
 */
object ThemeParser {
    @Serializable
    private class FontSpec(val scale: Float = 1f)

    @Serializable
    private class SizeSpec(
        val rounding: Float? = null,
        val windowPadding: List<Float>? = null,
        val framePadding: List<Float>? = null,
        val itemSpacing: List<Float>? = null,
        val itemInnerSpacing: List<Float>? = null,
        val touchExtraPadding: List<Float>? = null,
        val indentSpacing: Float? = null,
        val scrollbarSize: Float? = null,
        val grabMinSize: Float? = null,
        val windowBorderSize: Float? = null,
        val frameBorderSize: Float? = null,
    )

    @Serializable
    private class ThemeFile(
        val id: String,
        val nameKey: String,
        val font: FontSpec = FontSpec(),
        val sizes: SizeSpec = SizeSpec(),
        val colors: Map<String, String> = emptyMap(),
    )

    private val json = Json { ignoreUnknownKeys = false }

    /** Names accepted under `colors`, one per [Theme] property. */
    val COLOR_NAMES: List<String> = colorMap(Theme()).keys.toList()

    fun parse(text: String): EditorTheme {
        val file = try {
            json.decodeFromString(ThemeFile.serializer(), text)
        } catch (e: Exception) {
            throw ThemeParseException("Invalid theme file: ${e.message}")
        }
        if (file.id.isBlank()) throw ThemeParseException("Theme id is empty")
        if (file.font.scale !in 0.5f..3f) throw ThemeParseException("font.scale must be in 0.5..3, was ${file.font.scale}")
        val unknown = file.colors.keys - COLOR_NAMES.toSet()
        if (unknown.isNotEmpty()) throw ThemeParseException("Unknown colors in theme '${file.id}': $unknown")
        val colors = colorMap(Theme()).toMutableMap()
        file.colors.forEach { (name, hex) -> colors[name] = parseColor(hex) }
        val d = Style()
        val sz = file.sizes
        val style = Style(
            rounding = sz.rounding ?: d.rounding,
            windowPadding = pair(sz.windowPadding, "windowPadding") ?: d.windowPadding,
            framePadding = pair(sz.framePadding, "framePadding") ?: d.framePadding,
            itemSpacing = pair(sz.itemSpacing, "itemSpacing") ?: d.itemSpacing,
            itemInnerSpacing = pair(sz.itemInnerSpacing, "itemInnerSpacing") ?: d.itemInnerSpacing,
            touchExtraPadding = pair(sz.touchExtraPadding, "touchExtraPadding") ?: d.touchExtraPadding,
            indentSpacing = sz.indentSpacing ?: d.indentSpacing,
            scrollbarSize = sz.scrollbarSize ?: d.scrollbarSize,
            grabMinSize = sz.grabMinSize ?: d.grabMinSize,
            windowBorderSize = sz.windowBorderSize ?: d.windowBorderSize,
            frameBorderSize = sz.frameBorderSize ?: d.frameBorderSize,
            theme = themeOf(colors),
        )
        return EditorTheme(file.id, file.nameKey, style, file.font.scale)
    }

    /** `#RRGGBB` or `#RRGGBBAA` to 0..1 RGBA. */
    fun parseColor(hex: String): Vector4f {
        val h = hex.trim().removePrefix("#")
        if ((h.length != 6 && h.length != 8) || h.any { it.digitToIntOrNull(16) == null }) {
            throw ThemeParseException("Invalid color '$hex' (expected #RRGGBB or #RRGGBBAA)")
        }
        fun c(i: Int) = h.substring(i, i + 2).toInt(16) / 255f
        return Vector4f(c(0), c(2), c(4), if (h.length == 8) c(6) else 1f)
    }

    private fun pair(v: List<Float>?, name: String): Vector2f? {
        if (v == null) return null
        if (v.size != 2) throw ThemeParseException("$name must be [x, y], was $v")
        return Vector2f(v[0], v[1])
    }

    private fun colorMap(t: Theme): Map<String, Vector4f> = linkedMapOf(
        "text" to t.text, "textDisabled" to t.textDisabled, "background" to t.background, "border" to t.border,
        "borderShadow" to t.borderShadow, "accent" to t.accent, "accentHover" to t.accentHover,
        "widgetBg" to t.widgetBg, "widgetHover" to t.widgetHover, "widgetActive" to t.widgetActive,
        "header" to t.header, "headerHover" to t.headerHover, "tabActive" to t.tabActive, "tabHover" to t.tabHover,
        "tabInactive" to t.tabInactive, "scrollbarGrab" to t.scrollbarGrab, "scrollbarGrabHover" to t.scrollbarGrabHover,
        "scrollbarGrabActive" to t.scrollbarGrabActive, "selection" to t.selection, "navHighlight" to t.navHighlight,
        "navWindowingHighlight" to t.navWindowingHighlight, "navWindowingDimBg" to t.navWindowingDimBg,
        "modalDimBg" to t.modalDimBg, "success" to t.success, "warning" to t.warning, "danger" to t.danger,
    )

    private fun themeOf(c: Map<String, Vector4f>) = Theme(
        text = c.getValue("text"), textDisabled = c.getValue("textDisabled"), background = c.getValue("background"),
        border = c.getValue("border"), borderShadow = c.getValue("borderShadow"), accent = c.getValue("accent"),
        accentHover = c.getValue("accentHover"), widgetBg = c.getValue("widgetBg"), widgetHover = c.getValue("widgetHover"),
        widgetActive = c.getValue("widgetActive"), header = c.getValue("header"), headerHover = c.getValue("headerHover"),
        tabActive = c.getValue("tabActive"), tabHover = c.getValue("tabHover"), tabInactive = c.getValue("tabInactive"),
        scrollbarGrab = c.getValue("scrollbarGrab"), scrollbarGrabHover = c.getValue("scrollbarGrabHover"),
        scrollbarGrabActive = c.getValue("scrollbarGrabActive"), selection = c.getValue("selection"),
        navHighlight = c.getValue("navHighlight"), navWindowingHighlight = c.getValue("navWindowingHighlight"),
        navWindowingDimBg = c.getValue("navWindowingDimBg"), modalDimBg = c.getValue("modalDimBg"),
        success = c.getValue("success"), warning = c.getValue("warning"), danger = c.getValue("danger"),
    )
}

/** The themes found in a directory (`*.theme.json`), sorted by file name; unreadable files are skipped and reported. */
class ThemeLibrary(val themes: List<EditorTheme>, val errors: List<String> = emptyList()) {

    /** [id]'s theme, else the default one, else the built-in look. */
    fun resolve(id: String?): EditorTheme =
        themes.firstOrNull { it.id == id } ?: themes.firstOrNull { it.id == EditorTheme.DEFAULT_ID } ?: EditorTheme.BUILT_IN

    companion object {
        const val SUFFIX = ".theme.json"

        fun load(dir: java.io.File): ThemeLibrary {
            val files = dir.listFiles { f -> f.isFile && f.name.endsWith(SUFFIX) }.orEmpty().sortedBy { it.name }
            val errors = mutableListOf<String>()
            val themes = files.mapNotNull { f ->
                try {
                    ThemeParser.parse(f.readText())
                } catch (e: Exception) {
                    errors += "${f.name}: ${e.message}"
                    null
                }
            }.distinctBy { it.id }
            return ThemeLibrary(themes, errors)
        }
    }
}
