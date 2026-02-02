package com.pafoid.skate.engine.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class StringManagerTest {

    private lateinit var stringManager: StringManager
    private val testPropertiesPath = "/values/test_strings.properties"

    @BeforeEach
    fun setup() {
        stringManager = StringManager(testPropertiesPath)
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
}
