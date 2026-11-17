package com.pafoid.skate.editor.ui.windows.assetBrowser

import com.pafoid.skate.editor.imgui.Tooltips
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui

/** Draws an entry's engine badges under its tile or name (AST-26 §8), each with its explanation as a tooltip. */
object EngineBadges {
    fun render(strings: StringManager, entry: BrowserEntry) {
        for (badge in entry.badges) {
            val (r, g, b) = color(badge)
            ImGui.textColored(r, g, b, 1f, strings.getString(badge.labelKey))
            Tooltips.always(strings.getString(badge.tooltipKey))
        }
    }

    /** One line of text for a list row (the Sounds table): the badge labels joined. */
    fun inline(strings: StringManager, entry: BrowserEntry): String =
        entry.badges.joinToString(" · ") { strings.getString(it.labelKey) }

    private fun color(badge: EngineBadge): Triple<Float, Float, Float> = when (badge) {
        EngineBadge.ENGINE -> Triple(0.45f, 0.70f, 1f)
        EngineBadge.ENGINE_COPY -> Triple(0.65f, 0.65f, 0.65f)
        EngineBadge.MODIFIED -> Triple(1f, 0.70f, 0.30f)
        EngineBadge.UPDATE_AVAILABLE -> Triple(0.45f, 0.85f, 0.45f)
    }
}
