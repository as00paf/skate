package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.help.HelpArticle
import com.pafoid.skate.editor.help.HelpArticleLoader
import com.pafoid.skate.editor.help.HelpMarkdownRenderer
import com.pafoid.skate.editor.help.HelpNavigation
import com.pafoid.skate.editor.help.HelpTopic
import com.pafoid.skate.editor.help.HelpTopics
import com.pafoid.skate.editor.help.LinkTarget
import com.pafoid.skate.editor.imgui.EditorWindow
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.imgui.Tooltips
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.tippedWith
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui
import imgui.type.ImString
import java.awt.Desktop
import java.net.URI

/**
 * The Help window (EDT-68, docs/design/EDT-68.md): a dockable window with the topic list on the left and the
 * selected Markdown article on the right, with back/forward history. Every window's `?`, its tab menu's Help and the
 * Help menu open it through [com.pafoid.skate.editor.help.HelpRouter].
 *
 * [windowFor] finds the editor window of a topic (`console` -> the Console), for fallback titles and the
 * "related windows" buttons; [openWindow] shows a window by name; [openUrl] opens a web link.
 */
class HelpWindow(
    private val strings: StringManager,
    private val loader: HelpArticleLoader,
    private val windowFor: (topic: String) -> EditorWindow?,
    private val openWindow: (name: String) -> Unit,
    private val openUrl: (String) -> Unit = ::browse,
) : EditorWindow(NAME) {

    override val icon: String = Icons.QUESTION_CIRCLE

    val navigation = HelpNavigation()
    private val search = ImString(128)
    private var focusRequested = false
    private var topicsLocale: String? = null
    private var topicCache: List<HelpTopic> = emptyList()

    private val renderer = HelpMarkdownRenderer(onLink = ::follow, linkTooltip = ::tooltipOf)

    /** Opens the window on [topic], or on the topic list alone when null, and brings it to the front. */
    fun openTopic(topic: String?) {
        navigation.open(topic)
        isOpen.set(true)
        focusRequested = true
    }

    /** The topic list in the current language (cached; rebuilt after a language change). */
    fun topics(): List<HelpTopic> {
        val locale = strings.locale
        if (locale != topicsLocale) {
            loader.clear()
            topicCache = HelpTopics.list(loader, locale) { topic -> windowFor(topic)?.let { strings.getString(it.name) } }
            topicsLocale = locale
        }
        return topicCache
    }

    /** The current topic's article in the current language (English fallback), or null. */
    fun article(): HelpArticle? = navigation.current?.let { topics(); loader.load(it, strings.locale) }

    /** Draws [topic]'s article in the current ImGui window, for modals, which hide this window (EDT-67/68). */
    fun renderInline(topic: String) {
        topics()
        val article = loader.load(topic, strings.locale)
        if (article == null) MImGui.textDisabled(strings.getString("help.window.missing")) else renderer.render(article.blocks)
    }

    override fun imgui() {
        if (!isOpen.get()) return
        if (focusRequested) {
            ImGui.setNextWindowFocus()
            focusRequested = false
        }
        if (beginWindow(strings)) {
            header(strings) { toolbar() }
            val avail = ImGui.getContentRegionAvailX()
            val listWidth = minOf(TOPIC_LIST_WIDTH, avail * 0.4f)
            ImGui.beginChild("##help_topics", listWidth, 0f, true)
            topicList()
            ImGui.endChild()
            ImGui.sameLine()
            ImGui.beginChild("##help_article", 0f, 0f, true)
            articlePane()
            ImGui.endChild()
        }
        ImGui.end()
    }

    private fun toolbar() {
        ImGui.beginDisabled(!navigation.canGoBack)
        if (strings.tippedWith(ImGui.button("${Icons.ARROW_LEFT}##help_back"), "help.window.back.tooltip")) navigation.back()
        ImGui.endDisabled()
        ImGui.sameLine()
        ImGui.beginDisabled(!navigation.canGoForward)
        if (strings.tippedWith(ImGui.button("${Icons.ARROW_RIGHT}##help_forward"), "help.window.forward.tooltip")) navigation.forward()
        ImGui.endDisabled()
        ImGui.sameLine()
        if (strings.tippedWith(ImGui.button("${Icons.LIST}##help_index"), "help.window.index.tooltip")) navigation.open(null)
    }

    private fun topicList() {
        ImGui.setNextItemWidth(-1f)
        ImGui.inputTextWithHint("##help_search", "${Icons.SEARCH} ${strings.getString("help.window.search.hint")}", search)
        Tooltips.show(strings, "help.window.search.tooltip")
        ImGui.spacing()
        HelpTopics.filter(topics(), search.get()).forEach { topic ->
            if (ImGui.selectable("${topic.title}##help_topic_${topic.id}", topic.id == navigation.current)) navigation.open(topic.id)
        }
    }

    private fun articlePane() {
        val current = navigation.current
        if (current == null) {
            MImGui.textDisabled(strings.getString("help.window.pick_topic"))
            return
        }
        val article = article()
        if (article == null) {
            MImGui.textDisabled(strings.getString("help.window.missing"))
            return
        }
        if (article.fallback) {
            MImGui.textDisabled("${Icons.INFO} ${strings.getString("help.window.not_translated")}")
            ImGui.separator()
        }
        renderer.render(article.blocks)
        windowButtons(current)
    }

    /** "Open <window>" for the topic's own window and the windows it names as related. */
    private fun windowButtons(topic: String) {
        val own = windowFor(topic)?.takeIf { it.name != NAME } ?: return
        val names = listOf(own.name) + own.relatedWindows
        ImGui.spacing()
        ImGui.separator()
        names.distinct().forEach { name ->
            val label = "${Icons.EXTERNAL_LINK} ${strings.getString("help.window.open_window", strings.getString(name))}##help_open_$name"
            if (strings.tippedWith(ImGui.smallButton(label), "help.window.open_window.tooltip")) openWindow(name)
            ImGui.sameLine()
        }
        ImGui.newLine()
    }

    private fun follow(target: LinkTarget) {
        when (target) {
            is LinkTarget.Topic -> if (topics().any { it.id == target.id }) navigation.open(target.id)
            is LinkTarget.Web -> openUrl(target.url)
            is LinkTarget.Unsupported -> Unit
        }
    }

    private fun tooltipOf(target: LinkTarget): String? = when (target) {
        is LinkTarget.Topic -> topics().firstOrNull { it.id == target.id }?.title
        is LinkTarget.Web -> target.url
        is LinkTarget.Unsupported -> null
    }

    companion object {
        const val NAME = "window.help"
        private const val TOPIC_LIST_WIDTH = 220f

        private fun browse(url: String) {
            Thread({ runCatching { if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI(url)) } }, "help-browse").apply {
                isDaemon = true
                start()
            }
        }
    }
}
