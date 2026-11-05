package com.pafoid.skate.engine.core

import com.pafoid.skate.engine.assets.Assets
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Guards the bundled string tables: the editor merges both sources, so keys must be unique across all of them. */
class StringTablesTest {

    private val sources = listOf(File(Assets.Strings.EDITOR_STRINGS), File(Assets.Strings.GAME_STRINGS))
    private val tables = sources.flatMap(StringTables::baseTables)
    private val placeholder = Regex("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[a-zA-Z%]""")

    @Test
    fun everySourceHasTables() {
        sources.forEach { assertTrue(StringTables.baseTables(it).isNotEmpty(), "no tables in $it") }
    }

    @Test
    fun noKeyIsDefinedInTwoTables() {
        val duplicates = StringTables.duplicateKeys(tables)
        assertTrue(duplicates.isEmpty(), "keys defined in several tables (keep each key in one area file):\n" +
            duplicates.entries.joinToString("\n") { (k, files) -> "$k -> $files" })
    }

    @Test
    fun noFrenchKeyIsDefinedInTwoTables() {
        val french = tables.map { StringTables.localized(it, "fr") }.filter { it.exists() }
        assertTrue(StringTables.duplicateKeys(french).isEmpty(), StringTables.duplicateKeys(french).toString())
    }

    @Test
    fun everyKeyHasAFrenchTranslationInTheMatchingFile() {
        val missing = tables.flatMap { table ->
            val fr = StringTables.localized(table, "fr")
            val frKeys = if (fr.exists()) StringTables.read(fr).stringPropertyNames() else emptySet()
            (StringTables.read(table).stringPropertyNames() - frKeys).map { "${fr.name}: $it" }
        }
        assertTrue(missing.isEmpty(), "${missing.size} keys missing in French:\n" + missing.sorted().joinToString("\n"))
    }

    @Test
    fun frenchFilesHaveNoStaleKeysAndNoOrphans() {
        val stale = tables.flatMap { table ->
            val fr = StringTables.localized(table, "fr")
            if (!fr.exists()) emptyList()
            else (StringTables.read(fr).stringPropertyNames() - StringTables.read(table).stringPropertyNames()).map { "${fr.name}: $it" }
        }
        assertTrue(stale.isEmpty(), "French keys with no English key in the same area:\n" + stale.joinToString("\n"))
        val orphans = sources.flatMap { dir ->
            dir.listFiles { f -> f.name.endsWith("_fr.properties") }.orEmpty()
                .filter { fr -> !File(dir, fr.name.removeSuffix("_fr.properties") + ".properties").exists() }
        }
        assertTrue(orphans.isEmpty(), "translation files without an English table: $orphans")
    }

    @Test
    fun translationsKeepTheSamePlaceholders() {
        val mismatches = tables.flatMap { table ->
            val fr = StringTables.localized(table, "fr").takeIf { it.exists() }?.let(StringTables::read) ?: return@flatMap emptyList()
            val en = StringTables.read(table)
            en.stringPropertyNames().filter { fr.containsKey(it) }.mapNotNull { key ->
                val a = placeholder.findAll(en.getProperty(key)).map { it.value }.sorted().toList()
                val b = placeholder.findAll(fr.getProperty(key)).map { it.value }.sorted().toList()
                if (a == b) null else "$key: $a vs $b"
            }
        }
        assertTrue(mismatches.isEmpty(), "placeholder mismatch:\n" + mismatches.joinToString("\n"))
    }

    @TempDir
    lateinit var dir: File

    private fun write(name: String, text: String) = File(dir, name).also { it.writeText(text, Charsets.UTF_8) }

    @Test
    fun stringManagerMergesADirectoryOfTablesAndTheirTranslations() {
        write("core.properties", "a=Undo\nb=Save\n")
        write("core_fr.properties", "a=Rétablir\n")
        write("audio.properties", "c=Volume\n")
        write("audio_fr.properties", "c=Volume sonore\n")
        val sm = StringManager(mockk(relaxed = true), "en")
        sm.loadStrings(dir.path)
        assertEquals("Undo", sm.getString("a"))
        assertEquals("Volume", sm.getString("c"))

        sm.setLocale("fr")
        assertEquals("Rétablir", sm.getString("a"), "UTF-8 accents survive")
        assertEquals("Save", sm.getString("b"), "untranslated key falls back")
        assertEquals("Volume sonore", sm.getString("c"))
    }

    @Test
    fun localizedFilesAreNotTreatedAsTables() {
        write("core.properties", "a=A\n")
        write("core_fr.properties", "a=Aa\n")
        write("game_ui.properties", "b=B\n") // area names ending in _xx would be mistaken for translations
        assertEquals(listOf("core.properties"), StringTables.baseTables(dir).map { it.name })
    }

    @Test
    fun duplicateKeysAreReported() {
        val a = write("a.properties", "k=1\nonly.a=1\n")
        val b = write("b.properties", "k=2\n")
        assertEquals(mapOf("k" to listOf("a.properties", "b.properties")), StringTables.duplicateKeys(listOf(a, b)))
    }
}

class StringTablesEditorLoadTest {
    @Test
    fun editorStartupLoadResolvesEditorAndGameKeysInBothLocales() {
        // Same calls as Editor.init: editor tables in the editor's manager, game tables in the runtime one (GUI-10).
        val editor = StringManager(mockk(relaxed = true), "en")
        val game = StringManager(mockk(relaxed = true), "en", logSource = "game")
        assertTrue(editor.loadStrings(Assets.Strings.EDITOR_STRINGS))
        assertTrue(game.addStrings(Assets.Strings.GAME_STRINGS))
        assertEquals("Redo", editor.getString("menu.edit.redo"))
        assertTrue(!game.getString("showcase.title").startsWith("!!"), "game key resolves for runtime UI")
        assertTrue(!editor.hasString("showcase.title"), "game text is not in the editor's table")

        editor.setLocale("fr")
        assertEquals("Rétablir", editor.getString("menu.edit.redo"), "French accents read as UTF-8")
        assertTrue(!editor.getString("component.Transform.translation").startsWith("!!"))
        assertEquals("en", game.locale, "the editor language does not change the game's")
    }
}
