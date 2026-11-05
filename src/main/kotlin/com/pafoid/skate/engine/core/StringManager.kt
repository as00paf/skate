package com.pafoid.skate.engine.core

import com.pafoid.skate.engine.core.LoggerService.LogLevel
import java.util.*

/**
 * Localized text by key. See `docs/design/string-loading.md` for the whole flow.
 *
 * Sources are layered in the order they were added ([loadStrings] first, then each [addStrings]): for every source its
 * default tables, then its translation for the current locale. Later layers win, so a key missing from a translation
 * shows the default-language text (FR falls back to EN). The table is rebuilt and swapped in one step, so readers on
 * another thread never see it half-loaded.
 *
 * The editor and runtime UI use separate managers (`Engine.stringManager`, `Engine.gameStrings`, GUI-10); [logSource]
 * tags their log lines.
 */
class StringManager(
    private val logger: LoggerService,
    private var currentLocale: String = "en",
    private val logSource: String = "editor",
) {
    @Volatile
    private var properties = Properties()
    private var base: StringSource? = null
    private val extras = mutableListOf<StringSource>()
    private val sources: List<StringSource> get() = listOfNotNull(base) + extras
    private val localeListeners = mutableListOf<(String) -> Unit>()

    /** Keys already reported missing (or badly formatted) since the last rebuild: each is logged once, not every frame. */
    private val reported = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    val locale: String get() = currentLocale

    /** Notified after [setLocale] changed the active locale, so UI can refresh text and fonts. */
    fun addLocaleListener(listener: (String) -> Unit) {
        localeListeners.add(listener)
    }

    /**
     * Makes [path] (one table, or a directory of per-area tables, see [StringTables]) the base source, layered under
     * every [addStrings] source and replacing any previous base. Returns whether every default table was read.
     */
    @Synchronized
    fun loadStrings(path: String): Boolean {
        val source = StringSource.files(path)
        extras.removeAll { it.label == source.label }
        base = source
        return rebuild(source)
    }

    /** Adds the tables at [path] (e.g. the game's tables on disk) as a source layered over the loaded ones. */
    fun addStrings(path: String): Boolean = addStrings(StringSource.files(path))

    /**
     * Adds [source] (e.g. game tables read from a pack) layered over the loaded ones; a source with the same label
     * replaces the earlier one in place. Returns whether every default table of [source] was read.
     */
    @Synchronized
    fun addStrings(source: StringSource): Boolean {
        val index = extras.indexOfFirst { it.label == source.label }
        if (index >= 0) extras[index] = source else extras.add(source)
        return rebuild(source)
    }

    /**
     * Switches to [locale] (`fr`; regional forms such as `fr-CA` or `fr_CA` use the `fr` tables). Returns whether any
     * source has a translation for it; without one, the default-language text shows.
     */
    @Synchronized
    fun setLocale(locale: String): Boolean {
        if (currentLocale == locale) return false
        currentLocale = locale
        val result = layer()
        swap(result.table)
        localeListeners.toList().forEach { it(locale) }
        return result.translated
    }

    /** Rebuilds the table and reports whether [added]'s default tables were all read. */
    private fun rebuild(added: StringSource): Boolean {
        val result = layer()
        swap(result.table)
        if (added.baseTables().isEmpty()) logger.log("No string tables found at ${added.label}", LogLevel.ERROR, source = logSource)
        return added.label in result.completeSources
    }

    private fun swap(table: Properties) {
        properties = table
        reported.clear()
    }

    private fun layer(): StringTables.Layered = StringTables.layer(sources, currentLocale) { table, label, e ->
        logger.log("Failed to load strings table $table from $label (locale $currentLocale)", LogLevel.ERROR, source = logSource, throwable = e)
    }.also { result ->
        result.duplicates.forEach { (key, tables) ->
            logger.log("String key $key is defined in several tables: $tables", LogLevel.WARN, source = logSource)
        }
    }

    /** Whether [key] has a value (in the current locale or the default tables), without logging a miss. */
    fun hasString(key: String): Boolean = properties.containsKey(key)

    /** The text for [key], or `!!key!!` (logged once) when no table defines it. */
    fun getString(key: String): String = lookup(key) ?: "!!$key!!"

    /** [getString] formatted with [formatArgs]; a format that does not match its arguments shows unformatted (logged once). */
    fun getString(key: String, vararg formatArgs: Any): String = format(key, lookup(key) ?: "!!$key!!", formatArgs)

    private fun lookup(key: String): String? = properties.getProperty(key).also {
        if (it == null && reported.add(key)) logger.log("Missing string key: $key (locale $currentLocale)", LogLevel.WARN, source = logSource)
    }

    private fun format(key: String, fmt: String, args: Array<out Any>): String = try {
        String.format(fmt, *args)
    } catch (e: java.util.IllegalFormatException) {
        if (reported.add("format:$key")) logger.log("String $key does not match its arguments: '$fmt' (${e.message})", LogLevel.WARN, source = logSource)
        fmt
    }

    /**
     * Picks `key.<category>` by the current locale's plural rule (`zero` wins for 0 when defined), falling back to
     * `key.other`. The first format argument is the quantity.
     */
    fun getQuantityString(key: String, quantity: Int, vararg formatArgs: Any): String {
        val table = properties
        val candidates = listOfNotNull(
            if (quantity == 0) "$key.zero" else null,
            "$key.${PluralRules.category(currentLocale, quantity).suffix}",
            "$key.other",
        )
        val resourceKey = candidates.firstOrNull { table.containsKey(it) } ?: "$key.other"
        return format(resourceKey, lookup(resourceKey) ?: "!!$resourceKey!!", arrayOf(quantity, *formatArgs))
    }
}
