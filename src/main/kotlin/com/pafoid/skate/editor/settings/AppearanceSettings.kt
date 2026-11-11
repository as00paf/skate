package com.pafoid.skate.editor.settings

import com.pafoid.skate.editor.imgui.data.Style
import kotlinx.serialization.Serializable
import org.joml.Vector2f

/**
 * The editor's spacing, in pixels before the UI scale (EDT-76): the padding inside windows and popups (menus),
 * inside frames (buttons, fields) and the spacing between items (menu items get half of [itemSpacingY] above and
 * below their highlight).
 */
@Serializable
data class Spacing(
    val windowPaddingX: Float,
    val windowPaddingY: Float,
    val framePaddingX: Float,
    val framePaddingY: Float,
    val itemSpacingX: Float,
    val itemSpacingY: Float,
) {
    /** Values clamped to [MIN]..[MAX] (a hand-edited config can hold anything). */
    fun sanitized() = Spacing(
        windowPaddingX.coerceIn(MIN, MAX), windowPaddingY.coerceIn(MIN, MAX),
        framePaddingX.coerceIn(MIN, MAX), framePaddingY.coerceIn(MIN, MAX),
        itemSpacingX.coerceIn(MIN, MAX), itemSpacingY.coerceIn(MIN, MAX),
    )

    companion object {
        const val MIN = 0f
        const val MAX = 24f
    }
}

/** Spacing presets (EDT-76). [THEME] keeps the theme file's own values; [CUSTOM] uses the user's values. */
enum class Density(val labelKey: String, val spacing: Spacing?) {
    THEME("settings.editor.interface.density.theme", null),
    COMPACT("settings.editor.interface.density.compact", Spacing(6f, 6f, 4f, 2f, 6f, 3f)),
    COMFORTABLE("settings.editor.interface.density.comfortable", Spacing(10f, 8f, 8f, 5f, 8f, 6f)),
    SPACIOUS("settings.editor.interface.density.spacious", Spacing(14f, 12f, 12f, 7f, 12f, 9f)),
    CUSTOM("settings.editor.interface.density.custom", null),
}

/**
 * Editor Settings › Interface › Appearance (EDT-76), applied over the theme by `ImGuiStyleManager` and saved with
 * the editor settings. The default, [Density.COMFORTABLE], gives menus and buttons room around their text.
 * [rounding] null keeps the theme's corner rounding. The font size is [EditorSettings.fontSize] (the UI scale).
 */
@Serializable
data class AppearanceSettings(
    val density: Density = Density.COMFORTABLE,
    /** The values [Density.CUSTOM] uses; starts from the default preset. */
    val custom: Spacing = Density.COMFORTABLE.spacing!!,
    val rounding: Float? = null,
) {
    /** The spacing in effect, or null for the theme's own. */
    val spacing: Spacing? get() = if (density == Density.CUSTOM) custom else density.spacing

    /** Switches to [density]; picking a preset copies it into [custom], so tuning starts from what is showing. */
    fun withDensity(density: Density): AppearanceSettings = copy(density = density, custom = density.spacing ?: custom)

    /** Edits one value: the current spacing (or [themeSpacing] when on the theme's) becomes custom, then [change]. */
    fun withCustom(themeSpacing: Spacing, change: (Spacing) -> Spacing): AppearanceSettings =
        copy(density = Density.CUSTOM, custom = change(spacing ?: themeSpacing).sanitized())

    fun sanitized() = copy(custom = custom.sanitized(), rounding = rounding?.coerceIn(MIN_ROUNDING, MAX_ROUNDING))

    companion object {
        const val MIN_ROUNDING = 0f
        const val MAX_ROUNDING = 12f

        /** The theme's values alone (tests, and code that has no editor settings). */
        val THEME_ONLY = AppearanceSettings(density = Density.THEME)

        /** A theme's spacing, as the starting point of custom values when the density is [Density.THEME]. */
        fun spacingOf(style: Style) = Spacing(
            style.windowPadding.x, style.windowPadding.y, style.framePadding.x, style.framePadding.y,
            style.itemSpacing.x, style.itemSpacing.y,
        )

        /** [style] with [appearance]'s spacing and rounding over it; colours and other sizes stay the theme's. */
        fun apply(style: Style, appearance: AppearanceSettings): Style {
            val spacing = appearance.spacing
            return style.copy(
                rounding = appearance.rounding ?: style.rounding,
                windowPadding = spacing?.let { Vector2f(it.windowPaddingX, it.windowPaddingY) } ?: style.windowPadding,
                framePadding = spacing?.let { Vector2f(it.framePaddingX, it.framePaddingY) } ?: style.framePadding,
                itemSpacing = spacing?.let { Vector2f(it.itemSpacingX, it.itemSpacingY) } ?: style.itemSpacing,
            )
        }
    }
}
