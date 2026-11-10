package com.pafoid.skate.editor.help

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.URLClassLoader
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

/** EDT-68: help articles are discovered and loaded from disk, the classpath or a jar, with English as fallback. */
class HelpArticleLoaderTest {

    private fun write(root: File, locale: String, topic: String, text: String) =
        File(root, "$locale/$topic.md").apply { parentFile.mkdirs(); writeText(text) }

    @Test
    fun Directory_ListsAndReadsTopics(@TempDir dir: File) {
        write(dir, "en", "console", "# Console\n")
        write(dir, "en", "Not-A-Topic", "# x\n")
        File(dir, "en/notes.txt").writeText("x")
        val source = HelpArticleSource.Directory(dir)
        assertEquals(setOf("console"), source.topics("en"))
        assertEquals("# Console\n", source.read("en", "console"))
        assertNull(source.read("fr", "console"))
        assertNull(source.read("en", "../en/console"))
    }

    @Test
    fun Loader_FallsBackToEnglishAndMarksIt(@TempDir dir: File) {
        write(dir, "en", "console", "# Console\n\nLogs.")
        write(dir, "en", "profiler", "# Profiler\n")
        write(dir, "fr", "console", "# Console FR\n")
        val loader = HelpArticleLoader(HelpArticleSource.Directory(dir))

        val fr = loader.load("console", "fr")!!
        assertEquals("Console FR", fr.title)
        assertEquals("fr", fr.locale)
        assertFalse(fr.fallback)

        val fallback = loader.load("profiler", "fr_CA")!!
        assertEquals("en", fallback.locale)
        assertTrue(fallback.fallback)
        assertEquals("Profiler", fallback.title)

        assertNull(loader.load("missing", "fr"))
        assertEquals(setOf("console", "profiler"), loader.topics("fr"))
    }

    @Test
    fun Loader_CachesUntilCleared(@TempDir dir: File) {
        val file = write(dir, "en", "console", "# Old\n")
        val loader = HelpArticleLoader(HelpArticleSource.Directory(dir))
        assertEquals("Old", loader.load("console", "en")!!.title)
        file.writeText("# New\n")
        assertEquals("Old", loader.load("console", "en")!!.title)
        loader.clear()
        assertEquals("New", loader.load("console", "en")!!.title)
    }

    @Test
    fun Classpath_FindsTheShippedArticles() {
        val source = HelpArticleSource.Classpath()
        val en = source.topics("en")
        assertTrue(en.containsAll(listOf("console", "help", HelpTopics.GETTING_STARTED, HelpTopics.SHORTCUTS)), en.toString())
        assertEquals(en, source.topics("fr"))
        assertTrue(source.read("fr", "console")!!.startsWith("# "))
    }

    @Test
    fun Classpath_ReadsArticlesFromAJar(@TempDir dir: File) {
        val jar = File(dir, "editor.jar")
        JarOutputStream(jar.outputStream()).use { out ->
            // Directory entries first, as Gradle's jar task writes them.
            listOf("editor/", "editor/help/", "editor/help/en/", "editor/help/en/sub/", "editor/help/fr/").forEach {
                out.putNextEntry(JarEntry(it))
                out.closeEntry()
            }
            listOf("editor/help/en/console.md" to "# Console\n", "editor/help/en/sub/deep.md" to "# x\n", "editor/help/fr/console.md" to "# Console\n")
                .forEach { (name, text) ->
                    out.putNextEntry(JarEntry(name))
                    out.write(text.toByteArray())
                    out.closeEntry()
                }
        }
        URLClassLoader(arrayOf(jar.toURI().toURL()), null).use { loader ->
            val source = HelpArticleSource.Classpath(classLoader = loader)
            assertEquals(setOf("console"), source.topics("en"))
            assertEquals("# Console\n", source.read("en", "console"))
            assertEquals(emptySet<String>(), source.topics("de"))
        }
    }

    @Test
    fun Default_PrefersTheSourceTreeInDevRuns(@TempDir dir: File) {
        assertTrue(HelpArticleSource.default(dir) is HelpArticleSource.Classpath)
        File(dir, HelpArticleSource.DEV_ROOT).mkdirs()
        assertTrue(HelpArticleSource.default(dir) is HelpArticleSource.Directory)
    }

    @Test
    fun Decode_RejectsInvalidUtf8() {
        assertEquals("é", HelpArticleSource.decode("é".toByteArray()))
        assertNull(HelpArticleSource.decode(byteArrayOf(0xC3.toByte(), 0x28)))
    }

    @Test
    fun LanguageOf_KeepsTheLanguagePart() {
        assertEquals("fr", HelpArticleLoader.languageOf("fr_CA"))
        assertEquals("fr", HelpArticleLoader.languageOf("FR-ca"))
        assertEquals("en", HelpArticleLoader.languageOf(""))
    }
}
