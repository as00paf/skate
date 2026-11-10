package com.pafoid.skate.architecture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

/**
 * AST-35 (from skate-editor): window help lives in Markdown articles (EDT-53, EDT-68), so no editor string table keeps
 * the old `help.<id>.what/how/related` popover text.
 */
class UnusedHelpStringsGuardTest {
    private val popoverKey = Regex("""^help\.[a-z_]+\.(what|how|related)\s*=""", RegexOption.MULTILINE)

    @Test
    fun StringTables_HaveNoPopoverHelpText() {
        val tables = File("src/main/resources/editor/strings").listFiles { f -> f.extension == "properties" }!!
        val hits = tables.filter { popoverKey.containsMatchIn(it.readText()) }.map { it.name }.sorted()
        assertEquals(emptyList<String>(), hits)
    }
}
