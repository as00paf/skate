package com.pafoid.skate.editor.help

import com.pafoid.skate.editor.imgui.EditorWindow
import com.pafoid.skate.editor.imgui.TabMenuItem
import com.pafoid.skate.editor.imgui.WindowHeader
import com.pafoid.skate.editor.ui.menus.HamburgerMenuModel
import com.pafoid.skate.editor.ui.menus.HelpMenuBuilder
import com.pafoid.skate.editor.ui.windows.HelpWindow
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.core.StringManager
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** EDT-68: `?`, the tab menu's Help and the Help menu all open the one Help window, on the right topic. */
class HelpRoutingTest {

    private class TestWindow(name: String) : EditorWindow(name) {
        override fun imgui() {}
    }

    private val opened = mutableListOf<String?>()
    private val savedOpen = HelpRouter.open

    @BeforeEach
    fun capture() {
        HelpRouter.open = { opened += it }
    }

    @AfterEach
    fun restore() {
        HelpRouter.open = savedOpen
    }

    private fun strings(locale: String = "en") = StringManager(mockk(relaxed = true), "en").apply {
        loadStrings(Assets.Strings.EDITOR_STRINGS)
        if (locale != "en") setLocale(locale)
    }

    private fun helpWindow(strings: StringManager, opened: MutableList<String> = mutableListOf()) = HelpWindow(
        strings,
        HelpArticleLoader(HelpArticleSource.Classpath()),
        windowFor = { topic -> if (topic == "console") TestWindow("window.console") else null },
        openWindow = { opened += it },
        openUrl = {},
    )

    @Test
    fun QuestionMarkAndTabMenu_OpenTheWindowsTopic() {
        TestWindow("window.command_history").openHelp()
        WindowHeader.run(TabMenuItem.HELP, TestWindow("window.game_viewport"))
        assertEquals(listOf("command_history", "game_viewport"), opened)
    }

    @Test
    fun HelpMenu_HelpWindowFirst_ThenGeneralTopics_AboutLast() {
        assertEquals(listOf("help_window", "getting_started", "shortcuts", "about"), HamburgerMenuModel.HELP.items.map { it.id })
        assertEquals("", HelpMenuBuilder.topicOf("help_window")) // the topic list, no article
        assertEquals(HelpTopics.GETTING_STARTED, HelpMenuBuilder.topicOf("getting_started"))
        assertEquals(HelpTopics.SHORTCUTS, HelpMenuBuilder.topicOf("shortcuts"))
        assertNull(HelpMenuBuilder.topicOf("about"))
    }

    @Test
    fun HelpWindow_OpenTopicSelectsItAndOpensTheWindow() {
        val window = helpWindow(strings())
        assertFalse(window.isOpen.get())
        window.openTopic("console")
        assertTrue(window.isOpen.get())
        assertEquals("console", window.navigation.current)
        assertEquals("Console", window.article()!!.title)

        window.openTopic(null)
        assertNull(window.navigation.current)
        assertNull(window.article())
        window.navigation.back()
        assertEquals("console", window.navigation.current)
    }

    @Test
    fun HelpWindow_FollowsTheEditorLanguage() {
        val strings = strings()
        val window = helpWindow(strings)
        window.openTopic(HelpTopics.GETTING_STARTED)
        val english = window.article()!!
        assertEquals("en", english.locale)
        assertEquals(HelpTopics.GENERAL, window.topics().take(2).map { it.id })

        strings.setLocale("fr")
        val french = window.article()!!
        assertEquals("fr", french.locale)
        assertFalse(french.fallback)
        assertTrue(french.title != english.title, "${french.title} / ${english.title}")
    }

    @Test
    fun HelpWindow_ListsEveryShippedArticle() {
        val topics = helpWindow(strings()).topics().map { it.id }
        assertTrue(topics.containsAll(listOf("console", "help", "hierarchy", "video_to_animation")), topics.toString())
        assertEquals(topics.size, topics.toSet().size)
    }
}
