package com.pafoid.skate.engine.core

import com.pafoid.skate.engine.core.LoggerService.LogLevel

/**
 * Localized text by key. See `docs/design/string-loading.md` for the whole flow.
 *
 * Sources are layered in the order they were added ([loadStrings] first, then each [addStrings]): for every source its
 * default tables, then its translation for the current locale. Later layers win, so a key missing from a translation
 * shows the default-language text (FR falls back to EN).
 *
 * Threading (GUI-11): reloads ([loadStrings], [addStrings], [setLocale]) may run on any thread and are serialized;
 * each builds a complete new table and publishes it with one atomic swap. Lookups ([getString], [hasString], ...) read
 * the published snapshot without locking, so a reader sees either the old table or the new one, never a mix.
 *
 * The editor and runtime UI use separate managers (`Engine.stringManager`, `Engine.gameStrings`, GUI-10); [logSource]
 * tags their log lines.
 */
class StringManager(
    private val logger: LoggerService,
    initialLocale: String = "en",
    private val logSource: String = "editor",
) {
    /**
     * Everything a lookup reads, published as one immutable value (GUI-11). Writers build a new one off to the side
     * and swap it in with [current]; readers take one volatile read and never lock.
     */
    private class Snapshot(val locale: String, val table: Map<String, String>) {
        /** Placeholders handed out for missing keys; the first miss of a key is logged, later ones reuse the string. */
        val missing = java.util.concurrent.ConcurrentHashMap<String, String>()
        /** Keys whose format already failed (logged once). */
        val badFormats: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    }

    private val current = java.util.concurrent.atomic.AtomicReference(Snapshot(initialLocale, emptyMap()))

    // Writer-side state, guarded by this manager's monitor (loadStrings / addStrings / setLocale).
    private var targetLocale = initialLocale
    private var base: StringSource? = null
    private val extras = mutableListOf<StringSource>()
    private val sources: List<StringSource> get() = listOfNotNull(base) + extras
    private val localeListeners = java.util.concurrent.CopyOnWriteArrayList<(String) -> Unit>()

    val locale: String get() = current.get().locale

    /** Notified after [setLocale] changed the active locale, so UI can refresh text and fonts. */
    fun addLocaleListener(listener: (String) -> Unit) {
        localeListeners.add(listener)
    }

    /**
     * Makes [path] (one table, or a directory of per-area tables, see [StringTables]) the base source, layered under
     * every [addStrings] source and replacing any previous base. Returns whether every default table was read.
     * Reads files on the calling thread (the editor calls it from the IO thread); lookups meanwhile keep the old table.
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
    fun setLocale(locale: String): Boolean {
        val translated = synchronized(this) {
            if (targetLocale == locale) return false
            targetLocale = locale
            publish(layer()).translated
        }
        localeListeners.forEach { it(locale) } // outside the lock: listeners may read strings or switch again
        return translated
    }

    /** Rebuilds the table and reports whether [added]'s default tables were all read. Caller holds the monitor. */
    private fun rebuild(added: StringSource): Boolean {
        val result = publish(layer())
        if (added.baseTables().isEmpty()) logger.log("No string tables found at ${added.label}", LogLevel.ERROR, source = logSource)
        return added.label in result.completeSources
    }

    private fun publish(result: StringTables.Layered): StringTables.Layered {
        val table = HashMap<String, String>(result.table.size * 2)
        result.table.stringPropertyNames().forEach { table[it] = result.table.getProperty(it) }
        current.set(Snapshot(targetLocale, table))
        return result
    }

    private fun layer(): StringTables.Layered = StringTables.layer(sources, targetLocale) { table, label, e ->
        logger.log("Failed to load strings table $table from $label (locale $targetLocale)", LogLevel.ERROR, source = logSource, throwable = e)
    }.also { result ->
        result.duplicates.forEach { (key, tables) ->
            logger.log("String key $key is defined in several tables: $tables", LogLevel.WARN, source = logSource)
        }
    }

    /** Whether [key] has a value (in the current locale or the default tables), without logging a miss. */
    fun hasString(key: String): Boolean = current.get().table.containsKey(key)

    /**
     * The text for [key], or `!!key!!` (logged once) when no table defines it. Lock-free and allocation-free for a
     * defined key (and for a missing key after its first miss).
     */
    fun getString(key: String): String = lookup(current.get(), key)

    /** [getString] formatted with [formatArgs]; a format that does not match its arguments shows unformatted (logged once). */
    fun getString(key: String, vararg formatArgs: Any): String {
        val snap = current.get()
        return format(snap, key, lookup(snap, key), formatArgs)
    }

    private fun lookup(snap: Snapshot, key: String): String =
        snap.table[key] ?: snap.missing[key] ?: missing(snap, key)

    private fun missing(snap: Snapshot, key: String): String {
        val placeholder = "!!$key!!"
        if (snap.missing.putIfAbsent(key, placeholder) == null) {
            logger.log("Missing string key: $key (locale ${snap.locale})", LogLevel.WARN, source = logSource)
        }
        return placeholder
    }

    private fun format(snap: Snapshot, key: String, fmt: String, args: Array<out Any>): String = try {
        String.format(fmt, *args)
    } catch (e: java.util.IllegalFormatException) {
        if (snap.badFormats.add(key)) logger.log("String $key does not match its arguments: '$fmt' (${e.message})", LogLevel.WARN, source = logSource)
        fmt
    }

    /**
     * Picks `key.<category>` by the current locale's plural rule (`zero` wins for 0 when defined), falling back to
     * `key.other`. The first format argument is the quantity.
     */
    fun getQuantityString(key: String, quantity: Int, vararg formatArgs: Any): String {
        val snap = current.get()
        val table = snap.table
        val candidates = listOfNotNull(
            if (quantity == 0) "$key.zero" else null,
            "$key.${PluralRules.category(snap.locale, quantity).suffix}",
            "$key.other",
        )
        val resourceKey = candidates.firstOrNull { table.containsKey(it) } ?: "$key.other"
        return format(snap, resourceKey, lookup(snap, resourceKey), arrayOf(quantity, *formatArgs))
    }
}
