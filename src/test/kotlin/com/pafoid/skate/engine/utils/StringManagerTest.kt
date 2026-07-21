package com.pafoid.skate.engine.utils

import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.StringManager
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class StringManagerTest {

    private lateinit var stringManager: StringManager

    @BeforeEach
    fun setup() {
        stopKoin()
        startKoin {
            modules(
                module {
                    single { LoggerService() }
                }
            )
        }
        stringManager = StringManager(mockk(), "test_strings", "en") // Start with English
    }

    @AfterEach
    fun teardown() {
        stopKoin()
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
