package com.pafoid.skate.editor.ui.windows.assetBrowser

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.core.StringTables
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** EDT-52 C: empty states and empty-space menus of the Project window and the Asset Browser (EDT-51 §2.3, §2.5). */
class BrowserMenusTest {

    @Test
    fun EmptyState_DependsOnTheListAndTheSearch() {
        assertEquals(BrowserEmptyState.NONE, BrowserEmptyState.of(total = 3, shown = 1))
        assertEquals(BrowserEmptyState.NOTHING_YET, BrowserEmptyState.of(total = 0, shown = 0))
        assertEquals(BrowserEmptyState.NO_RESULTS, BrowserEmptyState.of(total = 3, shown = 0))
    }

    @Test
    fun ProjectMenu_CreatesFirstThenFolderActionsThenCopy() {
        val items = EmptySpaceItem.PROJECT
        assertEquals(
            listOf(EmptySpaceItem.Group.CREATE, EmptySpaceItem.Group.MAIN, EmptySpaceItem.Group.COPY),
            items.map { it.group }.distinct(),
        )
        assertEquals(items.map { it.group }.sorted(), items.map { it.group }, "groups are contiguous and in order")
        val separators = items.indices.filter { EmptySpaceItem.separatorBefore(items, it) }
        assertEquals(listOf(items.indexOf(EmptySpaceItem.REFRESH), items.indexOf(EmptySpaceItem.COPY_FOLDER_PATH)), separators)
    }

    @Test
    fun AssetTabMenu_HasFolderActionsOnlyForFolders() {
        assertEquals(listOf(EmptySpaceItem.REFRESH, EmptySpaceItem.SHOW_FOLDER, EmptySpaceItem.COPY_FOLDER_PATH), EmptySpaceItem.assetTab(true))
        assertEquals(emptyList<EmptySpaceItem>(), EmptySpaceItem.assetTab(false), "the prefab catalog has nothing to act on")
        assertEquals(EmptySpaceItem.NEW_SHADER, EmptySpaceItem.assetTab(true, hasProject = true).first(), "EDT-80: New Shader with a project")
        assertEquals(emptyList<EmptySpaceItem>(), EmptySpaceItem.assetTab(false, hasProject = true))
    }

    @Test
    fun EveryMenuItem_HasALabelAndATooltip() {
        val keys = StringTables.merged(File(Assets.Strings.EDITOR_STRINGS)).stringPropertyNames()
        val missing = EmptySpaceItem.entries.flatMap { listOf(it.labelKey, "${it.labelKey}.tooltip") }.filterNot { it in keys }
        assertTrue(missing.isEmpty(), missing.toString())
    }
}
