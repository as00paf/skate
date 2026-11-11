package com.pafoid.skate.editor.help

/**
 * A parsed help article. [locale] is the language it was read in; [fallback] is true when the requested language
 * had no file and the English article stands in (the Help window then shows a "not translated" note).
 */
data class HelpArticle(
    val topic: String,
    val locale: String,
    val fallback: Boolean,
    val title: String?,
    val blocks: List<Block>,
)

/** Loads and caches articles from a [HelpArticleSource], with English as the fallback language (EDT-68). */
class HelpArticleLoader(private val source: HelpArticleSource) {
    private val cache = HashMap<Pair<String, String>, HelpArticle?>()

    /** The article for [topic] in [locale] (or English), or null when even English is missing. */
    fun load(topic: String, locale: String): HelpArticle? {
        val language = languageOf(locale)
        return cache.getOrPut(language to topic) {
            read(topic, language, fallback = false) ?: if (language != FALLBACK) read(topic, FALLBACK, fallback = true) else null
        }
    }

    /** Every topic id with an article in [locale] or in English. */
    fun topics(locale: String): Set<String> {
        val language = languageOf(locale)
        return source.topics(FALLBACK) + if (language == FALLBACK) emptySet() else source.topics(language)
    }

    /** Drops the cache, after a language change or to pick up edited articles. */
    fun clear() = cache.clear()

    private fun read(topic: String, locale: String, fallback: Boolean): HelpArticle? {
        val text = source.read(locale, topic) ?: return null
        val blocks = HelpMarkdown.parse(text)
        return HelpArticle(topic, locale, fallback, HelpMarkdown.title(blocks), blocks)
    }

    companion object {
        const val FALLBACK = "en"

        /** `fr_CA` / `fr-CA` / `FR` -> `fr`. */
        fun languageOf(locale: String): String = locale.substringBefore('_').substringBefore('-').lowercase().ifBlank { FALLBACK }
    }
}
