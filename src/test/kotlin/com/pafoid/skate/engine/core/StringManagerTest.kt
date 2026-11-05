package com.pafoid.skate.engine.core

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.createTempDirectory

/** GUI-08: layering, fallback, missing keys and reload behaviour of [StringManager]. */
class StringManagerTest {
    private val logger = mockk<LoggerService>(relaxed = true)
    private val dir: File = createTempDirectory("gui08").toFile()

    @AfterEach
    fun cleanup() {
        dir.deleteRecursively()
    }

    private fun tables(name: String, vararg files: Pair<String, String>): String {
        val d = File(dir, name).apply { mkdirs() }
        files.forEach { (file, text) -> File(d, file).writeText(text, Charsets.UTF_8) }
        return d.path
    }

    private fun memory(label: String, vararg files: Pair<String, String>) =
        StringSource.memory(label, files.associate { it.first to it.second.toByteArray(Charsets.UTF_8) })

    @Test
    fun LaterSourcesWin_TheSameWhateverTheCallOrder() {
        val base = tables("base", "t.properties" to "k=base en\n", "t_fr.properties" to "k=base fr\n")
        val extra = memory("extra", "x.properties" to "k=extra en\n")

        val a = StringManager(logger).apply { loadStrings(base); addStrings(extra); setLocale("fr") }
        val b = StringManager(logger).apply { setLocale("fr"); loadStrings(base); addStrings(extra) }
        val c = StringManager(logger).apply { addStrings(extra); loadStrings(base); setLocale("fr") }

        // The extra source has no French text for k, so its English default layers over the base translation.
        listOf(a, b, c).forEach { assertEquals("extra en", it.getString("k")) }
    }

    @Test
    fun MissingTranslation_FallsBackToTheDefaultLanguage() {
        val sm = StringManager(logger).apply {
            loadStrings(tables("t", "ui.properties" to "a=Play\nb=Quit\n", "ui_fr.properties" to "a=Jouer\n"))
        }
        assertTrue(sm.setLocale("fr"))
        assertEquals("Jouer", sm.getString("a"))
        assertEquals("Quit", sm.getString("b"))
        verify(exactly = 0) { logger.log(match { it.startsWith("Missing") }, any(), any(), any()) }
    }

    @Test
    fun RegionalLocale_UsesTheLanguageTables_UnknownLocaleShowsDefaults() {
        val sm = StringManager(logger).apply {
            loadStrings(tables("t", "ui.properties" to "a=Play\n", "ui_fr.properties" to "a=Jouer\n"))
        }
        assertTrue(sm.setLocale("fr-CA"))
        assertEquals("Jouer", sm.getString("a"))
        assertTrue(sm.setLocale("fr_CA"))
        assertFalse(sm.setLocale("de"), "no German tables")
        assertEquals("Play", sm.getString("a"))
    }

    @Test
    fun SetLocale_ReportsTranslationsFromAddedSourcesToo() {
        // Runtime UI only adds sources (pack or game tables), it never calls loadStrings.
        val sm = StringManager(logger, logSource = "game")
        sm.addStrings(memory("pack", "hud.properties" to "t=Paused\n", "hud_fr.properties" to "t=Pause\n"))
        assertTrue(sm.setLocale("fr"))
        assertEquals("Pause", sm.getString("t"))
    }

    @Test
    fun MissingKey_IsLoggedOnce_WithTheManagersSource() {
        val sm = StringManager(logger, logSource = "game")
        sm.addStrings(memory("pack", "hud.properties" to "t=Paused\n"))
        repeat(100) { assertEquals("!!nope!!", sm.getString("nope")) } // runtime UI asks every frame
        verify(exactly = 1) { logger.log(match { it.startsWith("Missing string key: nope") }, LogLevel.WARN, "game", any()) }
    }

    @Test
    fun BadFormat_ShowsTheRawText_InsteadOfThrowing() {
        val sm = StringManager(logger).apply { addStrings(memory("m", "a.properties" to "score=Score: %d\n")) }
        repeat(3) { assertEquals("Score: %d", sm.getString("score", "not a number")) }
        assertEquals("Score: 5", sm.getString("score", 5))
        verify(exactly = 1) { logger.log(match { it.startsWith("String score does not match") }, LogLevel.WARN, any(), any()) }
    }

    @Test
    fun QuantityString_PicksThePluralAndLogsAMissOnce() {
        val sm = StringManager(logger).apply {
            addStrings(memory("m", "a.properties" to "n.one=%d item\nn.other=%d items\n", "a_fr.properties" to "n.one=%d objet\nn.other=%d objets\n"))
        }
        assertEquals("1 item", sm.getQuantityString("n", 1))
        assertEquals("2 items", sm.getQuantityString("n", 2))
        sm.setLocale("fr")
        assertEquals("0 objet", sm.getQuantityString("n", 0), "French uses 'one' for 0")
        repeat(5) { assertEquals("!!m.other!!", sm.getQuantityString("m", 3)) }
        verify(exactly = 1) { logger.log(match { it.startsWith("Missing string key: m.other") }, any(), any(), any()) }
    }

    @Test
    fun LoadStrings_KeepsSourcesAddedBefore() {
        val sm = StringManager(logger)
        sm.addStrings(memory("game", "g.properties" to "g=Game\n"))
        sm.loadStrings(tables("ed", "e.properties" to "e=Editor\n"))
        assertEquals("Game", sm.getString("g"))
        assertEquals("Editor", sm.getString("e"))
    }

    @Test
    fun SwitchingLanguage_NeverShowsAHalfLoadedTable() {
        val sm = StringManager(logger).apply { loadStrings(Assets.Strings.EDITOR_STRINGS) }
        val stop = AtomicBoolean(false)
        val seen = AtomicReference<String?>(null)
        val reader = Thread {
            while (!stop.get()) {
                val v = sm.getString("menu.edit.redo")
                if (v.startsWith("!!")) seen.compareAndSet(null, v)
            }
        }.apply { start() }
        repeat(40) { i -> sm.setLocale(if (i % 2 == 0) "fr" else "en") }
        stop.set(true)
        reader.join()
        assertEquals(null, seen.get(), "a reader saw a missing key while the language was switching")
    }

    @Test
    fun MergedHelper_MatchesTheManager() {
        val sm = StringManager(logger).apply { loadStrings(Assets.Strings.EDITOR_STRINGS); setLocale("fr") }
        val merged = StringTables.merged(File(Assets.Strings.EDITOR_STRINGS), "fr")
        merged.stringPropertyNames().forEach { assertEquals(merged.getProperty(it), sm.getString(it), it) }
    }
}
