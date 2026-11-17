package com.pafoid.skate.editor.ui.windows.assetBrowser

import com.pafoid.skate.engine.assets.AssetFolder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Properties

/** EDT-69: every type folder has a tab, with its label in English and French. */
class AssetBrowserTabsTest {
    private fun strings(name: String) = Properties().apply {
        File("src/main/resources/editor/strings/$name").reader(Charsets.UTF_8).use { load(it) }
    }

    @Test
    fun EveryTypeFolder_HasATab_InFolderOrder() {
        assertEquals(AssetFolder.entries, AssetBrowserTabs.ORDER)
    }

    @Test
    fun EveryTabAndBadge_IsTranslated() {
        for (file in listOf("core.properties", "core_fr.properties")) {
            val table = strings(file)
            val keys = AssetBrowserTabs.ORDER.map(AssetBrowserTabs::labelKey) +
                EngineBadge.entries.flatMap { listOf(it.labelKey, it.tooltipKey) }
            val missing = keys.filterNot { table.containsKey(it) }
            assertTrue(missing.isEmpty(), "$file is missing $missing")
        }
    }
}
