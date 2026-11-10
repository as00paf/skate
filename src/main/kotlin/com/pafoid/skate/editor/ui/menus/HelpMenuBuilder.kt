package com.pafoid.skate.editor.ui.menus

import com.pafoid.skate.editor.help.HelpRouter
import com.pafoid.skate.editor.help.HelpTopics
import com.pafoid.skate.editor.imgui.HamburgerMenuRenderer
import com.pafoid.skate.editor.imgui.MenuAction
import com.pafoid.skate.engine.core.StringManager

/**
 * Help (EDT-68 decision 6): Help Window… (the topic list), Getting started and Keyboard shortcuts (the Help window on
 * those topics), About Skate.
 */
class HelpMenuBuilder(
    private val stringManager: StringManager,
    private val aboutModal: AboutModal,
) {
    fun render() = HamburgerMenuRenderer.menu(stringManager, HamburgerMenuModel.HELP, ::action)

    private fun action(id: String): MenuAction? = topicOf(id)?.let { topic -> MenuAction { HelpRouter.open(topic.ifEmpty { null }) } }
        ?: if (id == "about") MenuAction { aboutModal.open() } else null

    companion object {
        /** The Help window topic a Help menu item opens: "" for the topic list alone, null for other items. */
        fun topicOf(itemId: String): String? = when (itemId) {
            "help_window" -> ""
            "getting_started" -> HelpTopics.GETTING_STARTED
            "shortcuts" -> HelpTopics.SHORTCUTS
            else -> null
        }
    }
}
