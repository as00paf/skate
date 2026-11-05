package com.pafoid.skate.editor.project

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.core.StringManager
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** EDT-06: "last opened" follows the editor language, with plural forms. */
class RecentProjectDisplayInfoTest {
    private val now = 10_000_000_000L
    private fun info(agoMs: Long) = RecentProjectDisplayInfo("p", "p", now - agoMs, true)
    private fun strings(locale: String) = StringManager(mockk(relaxed = true), "en").apply {
        loadStrings(Assets.Strings.EDITOR_STRINGS)
        if (locale != "en") setLocale(locale)
    }

    @Test
    fun English() {
        val en = strings("en")
        assertEquals("Just now", info(5_000).getLastOpenedString(en, now))
        assertEquals("1 minute ago", info(60_000).getLastOpenedString(en, now))
        assertEquals("5 minutes ago", info(300_000).getLastOpenedString(en, now))
        assertEquals("2 hours ago", info(7_200_000).getLastOpenedString(en, now))
        assertEquals("3 days ago", info(3 * 86_400_000L).getLastOpenedString(en, now))
    }

    @Test
    fun French() {
        val fr = strings("fr")
        assertEquals("À l'instant", info(5_000).getLastOpenedString(fr, now))
        assertEquals("il y a 1 minute", info(60_000).getLastOpenedString(fr, now))
        assertEquals("il y a 2 heures", info(7_200_000).getLastOpenedString(fr, now))
        assertEquals("il y a 1 jour", info(86_400_000).getLastOpenedString(fr, now))
    }
}
