package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.data.ToolWindowShortcuts

/** A section of the tool window bars (EDT-84 §1): each side bar has a top and a bottom section. */
enum class BarSection(val labelKey: String, val isLeft: Boolean, val isBottom: Boolean, val zone: DefaultDockLayout.Zone) {
    LEFT_TOP("lbl.toolbars.left_top", isLeft = true, isBottom = false, zone = DefaultDockLayout.Zone.LEFT),
    LEFT_BOTTOM("lbl.toolbars.left_bottom", isLeft = true, isBottom = true, zone = DefaultDockLayout.Zone.BOTTOM),
    RIGHT_TOP("lbl.toolbars.right_top", isLeft = false, isBottom = false, zone = DefaultDockLayout.Zone.RIGHT),
    RIGHT_BOTTOM("lbl.toolbars.right_bottom", isLeft = false, isBottom = true, zone = DefaultDockLayout.Zone.BOTTOM),
}

/** A tool window as its button sees it, from the last frame (EDT-84 §4). */
enum class ToolWindowState {
    CLOSED,
    /** Open, but behind another tab, or not focused. */
    OPEN,
    /** Open, in front (its tab selected, or floating) and focused. */
    FOCUSED,
}

/** What a click on a button (or the window's shortcut) does. */
enum class ToolWindowClick { OPEN, FOCUS, CLOSE }

/**
 * Which tool window button sits where on the side bars (EDT-84 §3, §5, §6), with no ImGui calls. The windows are
 * [ToolWindowShortcuts.WINDOWS]; each is in exactly one [BarSection].
 */
class ToolWindowBars(private val windows: List<String> = ToolWindowShortcuts.WINDOWS) {
    private val sections = BarSection.entries.associateWith { mutableListOf<String>() }

    init {
        reset()
    }

    /** The buttons of [section], top to bottom. */
    fun windows(section: BarSection): List<String> = sections.getValue(section).toList()

    fun sectionOf(window: String): BarSection? = sections.entries.firstOrNull { window in it.value }?.key

    /** Every window back to its default section (§3), in [windows] order. */
    fun reset() {
        sections.values.forEach { it.clear() }
        windows.forEach { w -> defaultSection(w)?.let { sections.getValue(it) += w } }
    }

    /**
     * Moves [window] to [index] of [section] (clamped; an index counts the other buttons). Returns true when its side
     * changed (left/right, top/bottom), so its window should be re-docked (§5, design Q3).
     */
    fun move(window: String, section: BarSection, index: Int): Boolean {
        val from = sectionOf(window) ?: return false
        sections.getValue(from).remove(window)
        val target = sections.getValue(section)
        target.add(index.coerceIn(0, target.size), window)
        return from != section
    }

    /** One line per section: `LEFT_TOP=window.project,window.hierarchy`. */
    fun encode(): String = BarSection.entries.joinToString("\n") { "${it.name}=${sections.getValue(it).joinToString(",")}" }

    companion object {
        /** §3 (design Q2): the window's default dock zone gives its side; bottom-zone windows split by category. */
        fun defaultSection(window: String): BarSection? = when (DefaultDockLayout.zoneOf(window)) {
            DefaultDockLayout.Zone.LEFT -> BarSection.LEFT_TOP
            DefaultDockLayout.Zone.RIGHT -> BarSection.RIGHT_TOP
            DefaultDockLayout.Zone.BOTTOM -> when (WindowCategory.of(window)) {
                WindowCategory.ASSETS, WindowCategory.SCRIPTING -> BarSection.LEFT_BOTTOM
                else -> BarSection.RIGHT_BOTTOM
            }
            DefaultDockLayout.Zone.CENTER, null -> null
        }

        /**
         * Reads [text] saved by [encode] (§6): unknown names are dropped, a window listed twice keeps its first place,
         * and a window missing from the file is appended to its default section. Null or unreadable text gives the
         * default placement.
         */
        fun decode(text: String?, windows: List<String> = ToolWindowShortcuts.WINDOWS): ToolWindowBars {
            val bars = ToolWindowBars(windows)
            if (text.isNullOrBlank()) return bars
            bars.sections.values.forEach { it.clear() }
            val placed = HashSet<String>()
            for (line in text.lines()) {
                val section = BarSection.entries.firstOrNull { it.name == line.substringBefore('=').trim() } ?: continue
                line.substringAfter('=', "").split(',').map { it.trim() }
                    .filter { it in windows && placed.add(it) }
                    .forEach { bars.sections.getValue(section) += it }
            }
            windows.filter { it !in placed }.forEach { w -> defaultSection(w)?.let { bars.sections.getValue(it) += w } }
            return bars
        }

        /** §4: closed → open (and focus); open but not focused in front → focus; focused → close. */
        fun click(state: ToolWindowState): ToolWindowClick = when (state) {
            ToolWindowState.CLOSED -> ToolWindowClick.OPEN
            ToolWindowState.OPEN -> ToolWindowClick.FOCUS
            ToolWindowState.FOCUSED -> ToolWindowClick.CLOSE
        }
    }
}

/**
 * What every window reported about itself in the last frame (EDT-84 §4): whether its `begin` was visible (in front)
 * and whether it had focus. A click on the bar moves ImGui's focus to the host, so the bar reads the frame before.
 */
object ToolWindowStates {
    private data class Report(val visible: Boolean, val focused: Boolean)

    private var current: Map<String, Report> = emptyMap()
    private val next = HashMap<String, Report>()

    /** Called by `EditorWindow.beginWindow`. */
    fun report(window: String, visible: Boolean, focused: Boolean) {
        next[window] = Report(visible, focused)
    }

    /** Called once per frame after every window drew. */
    fun endFrame() {
        current = next.toMap()
        next.clear()
    }

    fun state(window: String, isOpen: Boolean): ToolWindowState {
        if (!isOpen) return ToolWindowState.CLOSED
        val r = current[window] ?: return ToolWindowState.OPEN
        return if (r.visible && r.focused) ToolWindowState.FOCUSED else ToolWindowState.OPEN
    }

    /** In front (its tab selected or floating), focused or not: the button's "open and in front" look. */
    fun inFront(window: String): Boolean = current[window]?.visible == true

    fun reset() {
        current = emptyMap()
        next.clear()
    }
}
