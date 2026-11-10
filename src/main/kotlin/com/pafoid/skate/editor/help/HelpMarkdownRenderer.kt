package com.pafoid.skate.editor.help

import com.pafoid.skate.editor.imgui.Tooltips
import imgui.ImGui
import imgui.ImVec4
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiMouseButton
import imgui.flag.ImGuiMouseCursor
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags

/**
 * Draws parsed help articles with ImGui (EDT-68). The editor has one font, so headings are scaled, **bold** is
 * overstruck by one pixel and *italic* is tinted. Text wraps word by word at the window's right edge.
 * [onLink] runs when a link is clicked; [linkTooltip] gives a link's hover text.
 */
class HelpMarkdownRenderer(
    private val onLink: (LinkTarget) -> Unit,
    private val linkTooltip: (LinkTarget) -> String?,
) {
    private enum class Style { NORMAL, MUTED }

    fun render(blocks: List<Block>) {
        blocks.forEachIndexed { i, block ->
            if (i > 0) {
                ImGui.spacing()
                if (block is Block.Heading) ImGui.spacing()
            }
            when (block) {
                is Block.Heading -> heading(block)
                is Block.Paragraph -> flow(block.inlines, Style.NORMAL)
                is Block.ListItem -> listItem(block)
                is Block.Code -> code(block)
                is Block.Quote -> quote(block)
                is Block.Rule -> ImGui.separator()
            }
        }
    }

    private fun heading(block: Block.Heading) {
        ImGui.setWindowFontScale(HEADING_SCALES.getOrElse(block.level - 1) { 1f })
        flow(block.inlines.map { if (it is Inline.Text) it.copy(bold = true) else it }, Style.NORMAL)
        ImGui.setWindowFontScale(1f)
        if (block.level <= 2) ImGui.separator()
    }

    private fun listItem(block: Block.ListItem) {
        val indent = ImGui.getStyle().indentSpacing * (block.depth + 0.5f)
        ImGui.indent(indent)
        val x0 = ImGui.getCursorPosX()
        if (block.number != null) {
            ImGui.textUnformatted("${block.number}.")
            ImGui.sameLine()
        } else {
            ImGui.bullet()
        }
        // Wrapped lines line up with the text, not the marker.
        val marker = ImGui.getCursorPosX() - x0
        ImGui.indent(marker)
        flow(block.inlines, Style.NORMAL, continuesLine = true)
        ImGui.unindent(marker)
        ImGui.unindent(indent)
    }

    private fun quote(block: Block.Quote) {
        val bar = ImGui.getStyle().indentSpacing * 0.5f
        val x = ImGui.getCursorScreenPosX()
        val top = ImGui.getCursorScreenPosY()
        ImGui.indent(bar + ImGui.getStyle().itemSpacingX)
        flow(block.inlines, Style.MUTED)
        ImGui.unindent(bar + ImGui.getStyle().itemSpacingX)
        val bottom = ImGui.getItemRectMaxY()
        ImGui.getWindowDrawList().addRectFilled(x, top, x + 3f, bottom, ImGui.getColorU32(ImGuiCol.Separator))
    }

    private fun code(block: Block.Code) {
        val lines = block.text.split('\n')
        val style = ImGui.getStyle()
        val pad = style.framePaddingY * 2f
        val height = lines.size * ImGui.getTextLineHeightWithSpacing() + pad * 2f + style.scrollbarSize
        ImGui.pushStyleColor(ImGuiCol.ChildBg, ImGui.getColorU32(ImGuiCol.FrameBg))
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, pad, pad)
        ImGui.beginChild("##help_code_${block.line}", 0f, height, false, ImGuiWindowFlags.HorizontalScrollbar or ImGuiWindowFlags.AlwaysUseWindowPadding)
        lines.forEach { ImGui.textUnformatted(it) }
        ImGui.endChild()
        ImGui.popStyleVar()
        ImGui.popStyleColor()
    }

    /**
     * Inline runs as words that wrap at the right edge. [continuesLine]: the first word goes on the current line
     * (after a list marker).
     */
    private fun flow(inlines: List<Inline>, style: Style, continuesLine: Boolean = false) {
        var lineStart = !continuesLine
        var first = true
        inlines.forEachIndexed { runIndex, run ->
            if (run is Inline.LineBreak) {
                lineStart = true
                first = false
                return@forEachIndexed
            }
            HelpTextFlow.tokens(HelpMarkdown.plain(run)).forEachIndexed { tokenIndex, token ->
                if (!first && !lineStart) {
                    ImGui.sameLine(0f, 0f)
                    if (ImGui.getContentRegionAvailX() < ImGui.calcTextSize(token.trimEnd()).x) {
                        ImGui.newLine()
                        lineStart = true
                    }
                }
                if (first && continuesLine) lineStart = false
                first = false
                if (lineStart && token.isBlank()) return@forEachIndexed
                lineStart = false
                ImGui.pushID("${runIndex}_$tokenIndex")
                draw(run, token, style)
                ImGui.popID()
            }
        }
        // An empty paragraph still takes a line.
        if (first) ImGui.newLine()
    }

    private fun draw(run: Inline, token: String, style: Style) {
        val textColor = ImGui.getStyle().getColor(ImGuiCol.Text)
        val color = when {
            run is Inline.Link -> ImGui.getStyle().getColor(ImGuiCol.CheckMark)
            style == Style.MUTED || (run is Inline.Text && run.italic) ->
                HelpTextFlow.mix(textColor, ImGui.getStyle().getColor(ImGuiCol.TextDisabled), ITALIC_TINT)
            else -> textColor
        }
        if (run is Inline.Code) {
            val x = ImGui.getCursorScreenPosX()
            val y = ImGui.getCursorScreenPosY()
            val size = ImGui.calcTextSize(token)
            ImGui.getWindowDrawList().addRectFilled(x - 1f, y, x + size.x + 1f, y + size.y, ImGui.getColorU32(ImGuiCol.FrameBg), 2f)
        }
        ImGui.pushStyleColor(ImGuiCol.Text, color.x, color.y, color.z, color.w)
        ImGui.textUnformatted(token)
        ImGui.popStyleColor()
        val minX = ImGui.getItemRectMinX()
        val minY = ImGui.getItemRectMinY()
        val colorU32 = ImGui.colorConvertFloat4ToU32(color.x, color.y, color.z, color.w)
        if (run is Inline.Text && run.bold) ImGui.getWindowDrawList().addText(minX + 1f, minY, colorU32, token)
        if (run is Inline.Link) link(run, token, colorU32)
    }

    private fun link(run: Inline.Link, token: String, color: Int) {
        if (!ImGui.isItemHovered()) return
        val maxY = ImGui.getItemRectMaxY()
        val textW = ImGui.calcTextSize(token.trimEnd()).x
        ImGui.getWindowDrawList().addLine(ImGui.getItemRectMinX(), maxY, ImGui.getItemRectMinX() + textW, maxY, color)
        ImGui.setMouseCursor(ImGuiMouseCursor.Hand)
        // Where the link goes is data, like a path: shown even with tooltips off.
        linkTooltip(run.target)?.let { Tooltips.always(it) }
        if (ImGui.isMouseClicked(ImGuiMouseButton.Left)) onLink(run.target)
    }

    private companion object {
        val HEADING_SCALES = listOf(1.5f, 1.25f, 1.1f)
        const val ITALIC_TINT = 0.35f
    }
}

/** The ImGui-free parts of the text flow, unit-tested. */
object HelpTextFlow {
    private val TOKEN = Regex("""\S+\s*|\s+""")

    /** Words with their trailing spaces, so wrapping happens between words: `"a  b"` -> `["a  ", "b"]`. */
    fun tokens(text: String): List<String> = TOKEN.findAll(text).map { it.value }.toList()

    /** [a] moved toward [b] by [t] (0..1), alpha included. */
    fun mix(a: ImVec4, b: ImVec4, t: Float): ImVec4 =
        ImVec4(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t, a.w + (b.w - a.w) * t)
}
