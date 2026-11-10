package com.pafoid.skate.editor.help

import java.text.Collator
import java.util.Locale

/** A topic of the Help window's list: its id (`console`) and its title in the current language. */
data class HelpTopic(val id: String, val title: String)

/**
 * The Help window's topic list (EDT-68): the general topics first (Getting started, Keyboard shortcuts), then every
 * other topic by title in the current language. A topic's title is its article's `#` heading, else [fallbackTitle]
 * (the window's translated title), else its id.
 */
object HelpTopics {
    const val GETTING_STARTED = "getting_started"
    const val SHORTCUTS = "shortcuts"

    /** Topics opened from the Help menu rather than from a window's `?`, in list order. */
    val GENERAL = listOf(GETTING_STARTED, SHORTCUTS)

    fun list(
        loader: HelpArticleLoader,
        locale: String,
        fallbackTitle: (String) -> String? = { null },
    ): List<HelpTopic> {
        val topics = loader.topics(locale).map { id ->
            HelpTopic(id, loader.load(id, locale)?.title ?: fallbackTitle(id) ?: id)
        }
        return order(topics, locale)
    }

    fun order(topics: List<HelpTopic>, locale: String): List<HelpTopic> {
        val collator = Collator.getInstance(Locale.forLanguageTag(HelpArticleLoader.languageOf(locale))).apply { strength = Collator.PRIMARY }
        val (general, rest) = topics.partition { it.id in GENERAL }
        return general.sortedBy { GENERAL.indexOf(it.id) } + rest.sortedWith { a, b -> collator.compare(a.title, b.title) }
    }

    /** Topics whose title or id contains every word of [query], ignoring case and accents. */
    fun filter(topics: List<HelpTopic>, query: String): List<HelpTopic> {
        val words = normalize(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return topics
        return topics.filter { topic ->
            val hay = normalize(topic.title) + " " + topic.id.replace('_', ' ')
            words.all { it in hay }
        }
    }

    private fun normalize(s: String): String =
        java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
}

/**
 * What the Help window shows and its back/forward history (decision 5 of EDT-68). [current] null is the topic list
 * alone, as the Help menu opens it.
 */
class HelpNavigation {
    var current: String? = null
        private set
    private val back = ArrayDeque<String?>()
    private val forward = ArrayDeque<String?>()

    val canGoBack: Boolean get() = back.isNotEmpty()
    val canGoForward: Boolean get() = forward.isNotEmpty()

    /** Shows [topic] (null: no article, the list only); the old one goes to the back history. */
    fun open(topic: String?) {
        if (topic == current) return
        back.addLast(current)
        if (back.size > MAX_HISTORY) back.removeFirst()
        forward.clear()
        current = topic
    }

    fun back() {
        if (!canGoBack) return
        forward.addLast(current)
        current = back.removeLast()
    }

    fun forward() {
        if (!canGoForward) return
        back.addLast(current)
        current = forward.removeLast()
    }

    private companion object {
        const val MAX_HISTORY = 100
    }
}

/**
 * Routes "show help" from anywhere in the editor to the Help window (EDT-68): a window's `?`, its tab menu's Help,
 * the Help menu. [WindowRegistry][com.pafoid.skate.editor.imgui.WindowRegistry] connects it to the window.
 */
object HelpRouter {
    /** Opens the Help window on a topic, or on the topic list when null. */
    var open: (topic: String?) -> Unit = {}

    /** Draws a topic's article inline in the current ImGui window (modals, which hide every other window). */
    var renderInline: (topic: String) -> Unit = {}
}
