package com.pafoid.skate.editor.help

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** EDT-68: the Help window's topic list (order, titles, search) and its back/forward history. */
class HelpWindowModelTest {

    @Test
    fun TopicList_GeneralTopicsFirstThenByTitle(@TempDir dir: File) {
        mapOf(
            "shortcuts" to "# Keyboard shortcuts", "profiler" to "# Profiler", "console" to "# Console",
            "getting_started" to "# Getting started", "asset_browser" to "# Asset Browser", "untitled" to "No heading",
        ).forEach { (id, text) -> File(dir, "en/$id.md").apply { parentFile.mkdirs(); writeText(text) } }
        val loader = HelpArticleLoader(HelpArticleSource.Directory(dir))

        val topics = HelpTopics.list(loader, "en") { id -> if (id == "untitled") "Window Title" else null }
        assertEquals(
            listOf("getting_started", "shortcuts", "asset_browser", "console", "profiler", "untitled"),
            topics.map { it.id },
        )
        assertEquals("Window Title", topics.last().title)
    }

    @Test
    fun TopicList_SortsAccentedTitlesLikeTheLanguage() {
        val topics = listOf(HelpTopic("b", "Éditeur"), HelpTopic("a", "Zone"), HelpTopic("c", "Console"))
        assertEquals(listOf("Console", "Éditeur", "Zone"), HelpTopics.order(topics, "fr").map { it.title })
    }

    @Test
    fun Search_MatchesTitleOrIdIgnoringCaseAndAccents() {
        val topics = listOf(HelpTopic("command_history", "Historique des commandes"), HelpTopic("hierarchy", "Hiérarchie de la scène"))
        assertEquals(listOf("hierarchy"), HelpTopics.filter(topics, "hierarchie scene").map { it.id })
        assertEquals(listOf("command_history"), HelpTopics.filter(topics, "COMMAND").map { it.id })
        assertEquals(topics, HelpTopics.filter(topics, "  "))
        assertEquals(emptyList<HelpTopic>(), HelpTopics.filter(topics, "profiler"))
    }

    @Test
    fun Navigation_BackAndForward() {
        val nav = HelpNavigation()
        assertNull(nav.current)
        assertFalse(nav.canGoBack)
        nav.open("console")
        nav.open("profiler")
        nav.open("profiler") // same topic: no history entry
        nav.back()
        assertEquals("console", nav.current)
        assertTrue(nav.canGoForward)
        nav.forward()
        assertEquals("profiler", nav.current)
        nav.back()
        nav.open("shell") // a new topic drops the forward history
        assertFalse(nav.canGoForward)
        nav.back()
        nav.back()
        assertNull(nav.current) // back to the topic list
        assertFalse(nav.canGoBack)
    }
}
