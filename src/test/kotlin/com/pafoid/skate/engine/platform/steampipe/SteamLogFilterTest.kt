package com.pafoid.skate.engine.platform.steampipe

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SteamLogFilterTest {

    @Test
    fun SteamLogFilter_MasksTheAccountNameCaseInsensitively() {
        val filter = SteamLogFilter("builder_bot")
        assertEquals("Logging in user 'bu***' to Steam Public...", filter.filter("Logging in user 'Builder_Bot' to Steam Public..."))
    }

    @Test
    fun SteamLogFilter_RedactsTokens() {
        val token = "a1B2c3D4e5F6a7B8c9D0e1F2a3B4c5D6e7F8"
        assertEquals("token ${SteamLogFilter.REDACTED} ok", SteamLogFilter(null).filter("token $token ok"))
    }

    @Test
    fun SteamLogFilter_KeepsOrdinaryLines() {
        val line = "Uploading content... 45% (1.2 MB / 2.7 MB)"
        assertEquals(line, SteamLogFilter("someone").filter(line))
    }
}
