package com.pafoid.skate.engine.utils

import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class ScreenshotUtilsTest {

    @Test
    fun `buildScreenshotFileName generates unique names within same timestamp`() {
        val now = LocalDateTime.of(2026, 5, 18, 15, 30, 0, 0)

        val first = ScreenshotUtils.buildScreenshotFileName(now)
        val second = ScreenshotUtils.buildScreenshotFileName(now)

        assertNotEquals(first, second)
        assertTrue(first.startsWith("screenshot_2026-05-18_15-30-00-000_"))
        assertTrue(second.startsWith("screenshot_2026-05-18_15-30-00-000_"))
        assertTrue(first.endsWith(".png"))
        assertTrue(second.endsWith(".png"))
    }
}
