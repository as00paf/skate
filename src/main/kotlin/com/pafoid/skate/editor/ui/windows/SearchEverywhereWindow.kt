package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.imgui.IWindow
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.search.SearchEngine
import com.pafoid.skate.editor.search.data.SearchCategory
import com.pafoid.skate.editor.search.data.SearchResult
import com.pafoid.skate.editor.search.data.SearchResultWithCategory
import com.pafoid.skate.editor.search.history.SearchHistory
import com.pafoid.skate.editor.search.history.SearchHistoryEntry
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.utils.IJobSystem
import imgui.ImGui
import imgui.ImVec4
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiInputTextFlags
import imgui.flag.ImGuiKey
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import imgui.type.ImString
import kotlinx.coroutines.Job
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Search Everywhere overlay window providing global search across all editor resources.
 *
 * This modal overlay allows users to search GameObjects, assets, components, and actions
 * from a single unified interface. Features include real-time search with debouncing,
 * keyboard navigation, recent searches, and category-based result grouping.
 *
 * Usage:
 * - Open with Ctrl+P (handled by input handler)
 * - Type to search (150ms debounce)
 * - Navigate with ↑↓ arrows
 * - Select with Enter or click
 * - Close with Esc or X button
 */
class SearchEverywhereWindow(private val searchHistory: SearchHistory) : IWindow, KoinComponent {

    private val searchEngine: SearchEngine by inject()
    private val stringManager: StringManager by inject()
    private val jobSystem: IJobSystem by inject()

    private var isOpen = false
    private val searchQuery = ImString(256)
    private var selectedResultIndex = 0
    private var currentResults: Map<SearchCategory, List<SearchResult>> = emptyMap()
    private var isSearching = false
    private var searchJob: Job? = null
    private var recentSearches: List<SearchHistoryEntry> = emptyList()
    private var debounceDelayMs = 50L  // Reduced from 150ms for faster results

    // Track last query to avoid redundant searches
    private var lastQueriedText = ""

    // Result flattening for keyboard navigation
    private var flattenedResults: List<SearchResultWithCategory> = emptyList()

    /**
     * Opens the search overlay.
     */
    fun open() {
        isOpen = true
        searchQuery.set("")
        selectedResultIndex = 0
        currentResults = emptyMap()
        lastQueriedText = ""
        jobSystem.runOnMain {
            loadRecentSearches()
        }
    }

    /**
     * Closes the search overlay.
     */
    fun close() {
        isOpen = false
        searchJob?.cancel()
    }

    /**
     * Toggles the search overlay visibility.
     */
    fun toggle() {
        if (isOpen) close() else open()
    }

    /**
     * Checks if the window is currently open.
     */
    fun isOpen(): Boolean = isOpen

    private suspend fun loadRecentSearches() {
        recentSearches = searchHistory.getRecent(limit = 10)
    }

    override fun imgui(pOpen: ImBoolean?) {
        if (!isOpen) return

        val viewport = ImGui.getMainViewport()
        val centerX = viewport.workCenterX
        val centerY = viewport.workCenterY

        // Set up modal overlay window
        ImGui.setNextWindowPos(centerX, centerY, ImGuiCond.Always, 0.5f, 0.5f)
        ImGui.setNextWindowSize(600f, 500f)
        ImGui.setNextWindowBgAlpha(0.95f)

        val windowFlags = ImGuiWindowFlags.Modal or
                ImGuiWindowFlags.NoCollapse or
                ImGuiWindowFlags.NoResize or
                ImGuiWindowFlags.NoMove

        ImGui.begin(stringManager.getString("search.everywhere.title"), null, windowFlags)

        handleKeyboardInput()
        renderSearchInput()

        ImGui.separator()

        ImGui.beginChild("SearchResults", 0f, 400f)
        if (searchQuery.get().isBlank()) {
            renderRecentSearches()
        } else {
            renderResults()
        }
        ImGui.endChild()

        ImGui.separator()

        renderFooter()

        ImGui.end()

        // Auto-focus search input when opened
        if (searchQuery.get().isEmpty() && lastQueriedText.isEmpty()) {
            ImGui.setKeyboardFocusHere(0)
        }
    }

    private fun handleKeyboardInput() {
        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            close()
            return
        }

        if (ImGui.isKeyPressed(ImGuiKey.UpArrow)) {
            selectedResultIndex = (selectedResultIndex - 1).coerceAtLeast(0)
        }

        if (ImGui.isKeyPressed(ImGuiKey.DownArrow)) {
            val maxIndex = flattenedResults.size - 1
            selectedResultIndex = (selectedResultIndex + 1).coerceAtMost(maxIndex)
        }

        if (ImGui.isKeyPressed(ImGuiKey.Enter) && flattenedResults.isNotEmpty()) {
            if (selectedResultIndex in flattenedResults.indices) {
                val result = flattenedResults[selectedResultIndex].result
                selectResult(result)
            }
        }
    }

    private fun renderSearchInput() {
        val buttonSize = ImGui.getFrameHeight()
        val spacing = ImGui.getStyle().itemSpacingX

        ImGui.textColored(0.4f, 0.4f, 0.4f, 1f, Icons.SEARCH)
        ImGui.sameLine()

        val inputWidth = ImGui.getContentRegionAvailX() - buttonSize - spacing
        ImGui.pushItemWidth(inputWidth)
        val flags = ImGuiInputTextFlags.AutoSelectAll
        ImGui.inputTextWithHint(
            "##SearchEverywhere",
            stringManager.getString("search.everywhere.placeholder"),
            searchQuery,
            flags
        )
        val inputEdited = ImGui.isItemEdited()  // True whenever text changes (not just Enter)
        ImGui.popItemWidth()
        ImGui.sameLine()

        if (ImGui.button(Icons.WINDOW_CLOSE, buttonSize, buttonSize)) {
            searchQuery.set("")
            currentResults = emptyMap()
            flattenedResults = emptyList()
            selectedResultIndex = 0
            lastQueriedText = ""
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(stringManager.getString("lbl.clear"))
        }

        if (inputEdited) {
            triggerSearch()
        }
    }

    private fun triggerSearch() {
        val query = searchQuery.get().trim()
        if (query == lastQueriedText) return

        lastQueriedText = query

        if (query.isEmpty()) {
            currentResults = emptyMap()
            flattenedResults = emptyList()
            selectedResultIndex = 0
            return
        }

        searchJob?.cancel()

        isSearching = true
        searchJob = jobSystem.runAsync {
            Thread.sleep(debounceDelayMs)
            if (searchQuery.get().trim() != query) {
                return@runAsync
            }

            val results = searchEngine.search(query)

            jobSystem.runOnMain {
                currentResults = results
                flattenedResults = flattenResults(results)
                selectedResultIndex = 0
                isSearching = false
            }
        }
    }

    private fun flattenResults(results: Map<SearchCategory, List<SearchResult>>): List<SearchResultWithCategory> {
        val flattened = mutableListOf<SearchResultWithCategory>()
        results.forEach { (category, categoryResults) ->
            categoryResults.forEach { result ->
                flattened.add(SearchResultWithCategory(category, result))
            }
        }
        return flattened
    }

    private fun renderRecentSearches() {
        if (recentSearches.isEmpty()) {
            ImGui.textColored(0.5f, 0.5f, 0.5f, 1f, stringManager.getString("search.everywhere.recent"))
            return
        }

        ImGui.textColored(getCategoryColor(SearchCategory.ACTION), stringManager.getString("search.everywhere.recent"))
        ImGui.separator()

        recentSearches.forEachIndexed { index, entry ->
            val isSelected = (index == selectedResultIndex)
            if (isSelected) {
                ImGui.pushStyleColor(ImGuiCol.Header, 0.2f, 0.4f, 0.6f, 1f)
            }

            val label = "${Icons.CLOCK} ${entry.query}"
            if (ImGui.selectable(label, isSelected)) {
                searchQuery.set(entry.query)
                triggerSearch()
            }

            if (isSelected) {
                ImGui.popStyleColor()
            }
        }
    }

    private fun renderResults() {
        if (isSearching) {
            ImGui.textColored(0.5f, 0.5f, 0.5f, 1f, stringManager.getString("lbl.search.searching"))
            return
        }

        if (currentResults.isEmpty()) {
            ImGui.textColored(0.5f, 0.5f, 0.5f, 1f, stringManager.getString("lbl.search.no_results"))
            return
        }

        var globalIndex = 0
        currentResults.forEach { (category, results) ->
            if (results.isEmpty()) return@forEach

            val categoryColor = getCategoryColor(category)
            ImGui.textColored(categoryColor, "${stringManager.getString(category.displayNameKey)} (${results.size})")
            ImGui.separator()

            results.forEach { result ->
                val isSelected = (globalIndex == selectedResultIndex)
                if (isSelected) {
                    ImGui.pushStyleColor(ImGuiCol.Header, 0.2f, 0.4f, 0.6f, 1f)
                }

                val icon = result.icon ?: getDefaultIcon(category)
                val label = buildString {
                    append(icon)
                    append(" ")
                    append(result.displayName)
                    if (result.subcategory.isNotEmpty() && result.subcategory != stringManager.getString(category.displayNameKey)) {
                        append(" (")
                        append(result.subcategory)
                        append(")")
                    }
                }

                if (ImGui.selectable(label, isSelected)) {
                    selectResult(result)
                }

                if (isSelected) {
                    ImGui.popStyleColor()
                }

                globalIndex++
            }

            ImGui.separator()
        }
    }

    private fun getDefaultIcon(category: SearchCategory): String {
        return when (category) {
            SearchCategory.GAMEOBJECT -> Icons.CUBE
            SearchCategory.ASSET_TEXTURE -> Icons.SUN
            SearchCategory.ASSET_MODEL -> Icons.CUBE
            SearchCategory.ASSET_ANIMATION -> Icons.ARROW_ROTATE
            SearchCategory.ASSET_SOUND -> Icons.MUSIC
            SearchCategory.ASSET_PREFAB -> Icons.COPY
            SearchCategory.COMPONENT -> Icons.GEAR
            SearchCategory.ACTION -> Icons.ATOM
        }
    }

    private fun getCategoryColor(category: SearchCategory): ImVec4 {
        return when (category) {
            SearchCategory.GAMEOBJECT -> ImVec4(0.2f, 0.6f, 0.9f, 1f)
            SearchCategory.ASSET_TEXTURE -> ImVec4(0.9f, 0.6f, 0.2f, 1f)
            SearchCategory.ASSET_MODEL -> ImVec4(0.6f, 0.8f, 0.3f, 1f)
            SearchCategory.ASSET_ANIMATION -> ImVec4(0.8f, 0.4f, 0.8f, 1f)
            SearchCategory.ASSET_SOUND -> ImVec4(0.9f, 0.4f, 0.4f, 1f)
            SearchCategory.ASSET_PREFAB -> ImVec4(0.4f, 0.7f, 0.9f, 1f)
            SearchCategory.COMPONENT -> ImVec4(0.7f, 0.7f, 0.3f, 1f)
            SearchCategory.ACTION -> ImVec4(0.6f, 0.6f, 0.6f, 1f)
        }
    }

    private fun selectResult(result: SearchResult) {
        jobSystem.runAsync {
            searchHistory.add(searchQuery.get().trim(), result)
        }

        searchEngine.navigate(result)
        close()
    }

    private fun renderFooter() {
        ImGui.spacing()
        ImGui.textColored(0.5f, 0.5f, 0.5f, 1f,
            "${stringManager.getString("search.everywhere.shortcut.navigate")}  " +
            "${stringManager.getString("search.everywhere.shortcut.open")}  " +
            "${stringManager.getString("search.everywhere.shortcut.close")}"
        )
    }

}

