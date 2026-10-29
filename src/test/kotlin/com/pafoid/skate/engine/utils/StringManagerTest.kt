package com.pafoid.skate.engine.utils

import com.pafoid.skate.engine.core.StringManager
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
class StringManagerTest {

    private lateinit var stringManager: StringManager

    @BeforeEach
    fun setup() {
        stringManager = StringManager(mockk(relaxed = true), "en")
        stringManager.loadStrings("src/test/resources/values/test_strings.properties")
    }

    @Test
    fun `getString should return correct value for existing key`() {
        val result = stringManager.getString("test.hello")
        assertEquals("Hello World", result)
    }

    @Test
    fun `getString should return fallback for missing key`() {
        val result = stringManager.getString("test.missing")
        assertEquals("!!test.missing!!", result)
    }

    @Test
    fun `getString with format args should return correctly formatted string`() {
        val result = stringManager.getString("test.formatted", "User", 5)
        assertEquals("Hello, User! You have 5 new messages.", result)
    }
    
    @Test
    fun `getString with format args should return fallback for missing key`() {
        val result = stringManager.getString("test.missing.formatted", "Arg1")
        assertEquals("!!test.missing.formatted!!", result)
    }

    @Test
    fun `getQuantityString should return singular for quantity 1`() {
        val result = stringManager.getQuantityString("test.tricks", 1)
        assertEquals("1 Trick", result)
    }

    @Test
    fun `getQuantityString should return plural for quantity 0`() {
        val result = stringManager.getQuantityString("test.tricks", 0)
        assertEquals("0 Tricks", result)
    }

    @Test
    fun `getQuantityString should return plural for quantity 5`() {
        val result = stringManager.getQuantityString("test.tricks", 5)
        assertEquals("5 Tricks", result)
    }

    @Test
    fun `setLocale should switch language correctly`() {
        // Verify initial English
        assertEquals("Hello World", stringManager.getString("test.hello"))

        // Switch to French
        stringManager.setLocale("fr")
        assertEquals("Bonjour le monde", stringManager.getString("test.hello"))
        assertEquals("Bonjour, User! Vous avez 5 nouveaux messages.", stringManager.getString("test.formatted", "User", 5))
        assertEquals("1 Tour", stringManager.getQuantityString("test.tricks", 1))
        assertEquals("5 Tours", stringManager.getQuantityString("test.tricks", 5))

        // Switch back to English
        stringManager.setLocale("en")
        assertEquals("Hello World", stringManager.getString("test.hello"))
    }
}

class StringManagerLocaleTest {
    @org.junit.jupiter.api.io.TempDir
    lateinit var dir: java.io.File

    private fun write(name: String, text: String) = java.io.File(dir, name).also { it.writeText(text) }.path

    @Test
    fun StringManager_LocaleFallsBackToDefaultAndNotifiesListeners() {
        val base = write("s.properties", "a=A\nb=B\nn.one=%d item\nn.other=%d items\n")
        write("s_fr.properties", "a=Aa\nn.one=%d objet\nn.other=%d objets\n")
        val sm = StringManager(mockk(relaxed = true), "en")
        sm.loadStrings(base)
        val seen = mutableListOf<String>()
        sm.addLocaleListener { seen += it }

        sm.setLocale("fr")
        assertEquals("Aa", sm.getString("a"))
        assertEquals("B", sm.getString("b"), "untranslated key falls back to default")
        assertEquals("0 objet", sm.getQuantityString("n", 0), "French treats 0 as singular")
        assertEquals("2 objets", sm.getQuantityString("n", 2))
        sm.setLocale("de")
        assertEquals("A", sm.getString("a"), "missing locale file keeps defaults")
        assertEquals(listOf("fr", "de"), seen)
        assertEquals("de", sm.locale)
    }

    @Test
    fun StringManager_ExplicitZeroFormWinsForZero() {
        val base = write("z.properties", "n.zero=none\nn.other=%d things\n")
        val sm = StringManager(mockk(relaxed = true), "en")
        sm.loadStrings(base)
        assertEquals("none", sm.getQuantityString("n", 0))
        assertEquals("3 things", sm.getQuantityString("n", 3))
    }
}
