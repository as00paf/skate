package com.pafoid.skate.editor.help

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** EDT-68: the Markdown subset of help articles. */
class HelpMarkdownTest {

    private fun t(text: String, bold: Boolean = false, italic: Boolean = false) = Inline.Text(text, bold, italic)

    @Test
    fun Headings_KeepTheirLevelAndLine() {
        val blocks = HelpMarkdown.parse("# Console\n\n## How to use it\n### Tips ###\n")
        assertEquals(
            listOf(
                Block.Heading(1, listOf(t("Console")), 1),
                Block.Heading(2, listOf(t("How to use it")), 3),
                Block.Heading(3, listOf(t("Tips")), 4),
            ),
            blocks,
        )
        assertEquals("Console", HelpMarkdown.title(blocks))
    }

    @Test
    fun Paragraph_JoinsLinesAndKeepsHardBreaks() {
        val blocks = HelpMarkdown.parse("One line\nand the next.  \nAfter a break.\n\nSecond paragraph.")
        assertEquals(
            listOf(
                Block.Paragraph(listOf(t("One line and the next."), Inline.LineBreak, t("After a break.")), 1),
                Block.Paragraph(listOf(t("Second paragraph.")), 5),
            ),
            blocks,
        )
    }

    @Test
    fun Emphasis_CodeAndEscapes() {
        assertEquals(
            listOf(t("Press "), t("Ctrl+S", bold = true), t(" to "), t("save", italic = true), t(" the "), Inline.Code("*.skscene"), t(" file *now*.")),
            HelpMarkdown.inlines("Press **Ctrl+S** to *save* the `*.skscene` file \\*now\\*."),
        )
    }

    @Test
    fun Underscores_InsideWordsStayText() {
        assertEquals(listOf(t("open asset_browser.md and "), t("this", italic = true)), HelpMarkdown.inlines("open asset_browser.md and _this_"))
    }

    @Test
    fun Links_ToTopicsAndTheWeb() {
        assertEquals(
            listOf(
                t("See "), Inline.Link("Command History", LinkTarget.Topic("command_history")),
                t(" or "), Inline.Link("the site", LinkTarget.Web("https://example.com/a")),
                t(" or "), Inline.Link("https://skate.dev", LinkTarget.Web("https://skate.dev")), t("."),
            ),
            HelpMarkdown.inlines("See [Command History](command_history.md) or [the site](https://example.com/a) or https://skate.dev."),
        )
        assertEquals(LinkTarget.Unsupported("docs/a.md"), LinkTarget.of("docs/a.md"))
        assertEquals(LinkTarget.Unsupported(""), LinkTarget.of(" "))
    }

    @Test
    fun Lists_OrderedUnorderedAndNested() {
        val blocks = HelpMarkdown.parse(
            """
            1. Open the window.
               - nested item
               continues here
            2. Close it.

            - top
            * also top
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                Block.ListItem(0, 1, listOf(t("Open the window.")), 1),
                Block.ListItem(1, null, listOf(t("nested item continues here")), 2),
                Block.ListItem(0, 2, listOf(t("Close it.")), 4),
                Block.ListItem(0, null, listOf(t("top")), 6),
                Block.ListItem(0, null, listOf(t("also top")), 7),
            ),
            blocks,
        )
    }

    @Test
    fun CodeQuoteAndRule() {
        val blocks = HelpMarkdown.parse("```kotlin\n# not a heading\n  indented\n```\n> quoted\n> more\n\n---\n")
        assertEquals(
            listOf(
                Block.Code("# not a heading\n  indented", 1),
                Block.Quote(listOf(t("quoted more")), 5),
                Block.Rule(8),
            ),
            blocks,
        )
    }

    @Test
    fun UnknownSyntax_StaysPlainText() {
        val blocks = HelpMarkdown.parse("| a | b |\n![logo](logo.png)")
        assertEquals(listOf(Block.Paragraph(listOf(t("| a | b | ![logo](logo.png)")), 1)), blocks)
    }

    @Test
    fun Problems_ReportsBrokenArticles() {
        assertEquals(emptyList<String>(), HelpMarkdown.problems("# A\n\nSee [B](b.md), `[x](gone.md)`.\n", setOf("a", "b")))
        assertEquals(listOf("does not start with a '# ' title"), HelpMarkdown.problems("Intro\n\n# Title\n", emptySet()))
        assertEquals(listOf("more than one '# ' title (line 3)"), HelpMarkdown.problems("# A\n\n# B\n", emptySet()))
        assertEquals(listOf("heading deeper than ### (line 3)"), HelpMarkdown.problems("# A\n\n#### Deep\n", emptySet()))
        assertEquals(listOf("unclosed code fence"), HelpMarkdown.problems("# A\n\n```\ncode\n", emptySet()))
        assertEquals(listOf("link to missing topic 'gone.md' (line 3)"), HelpMarkdown.problems("# A\n\nSee [x](gone.md).\n", emptySet()))
        assertEquals(listOf("empty link target (line 3)"), HelpMarkdown.problems("# A\n\nSee [x]().\n", emptySet()))
        assertEquals(listOf("empty article"), HelpMarkdown.problems(" \n", emptySet()))
    }

    @Test
    fun TextFlow_TokensKeepTrailingSpaces() {
        assertEquals(listOf("a  ", "b ", "c"), HelpTextFlow.tokens("a  b c"))
        assertEquals(listOf("  ", "x"), HelpTextFlow.tokens("  x"))
    }
}
