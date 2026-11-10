package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.events.WindowAction
import com.pafoid.skate.editor.imgui.Tooltips
import com.pafoid.skate.editor.imgui.EditorWindow
import com.pafoid.skate.editor.imgui.EmptyState
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.search.SearchEngine
import com.pafoid.skate.editor.search.data.SearchCategory
import com.pafoid.skate.editor.search.data.SearchResult
import com.pafoid.skate.editor.search.data.SearchResultWithCategory
import com.pafoid.skate.editor.search.history.SearchHistory
import com.pafoid.skate.editor.search.history.SearchHistoryEntry
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui
import imgui.ImVec4
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiFocusedFlags
import imgui.flag.ImGuiInputTextFlags
import imgui.flag.ImGuiKey
import imgui.flag.ImGuiPopupFlags
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import imgui.type.ImString
import kotlinx.coroutines.Job

class SearchEverywhereWindow(
    private val engine: Engine,
    projectDirectory: () -> java.io.File? = { null },
) : EditorWindow("window.search") {
    private val stringManager = engine.stringManager

    override val icon: String = Icons.SEARCH

    override val relatedWindows: List<String> = listOf("window.keybindings")

    private val searchHistory: SearchHistory = SearchHistory(engine.serializer, engine.logger)
    private val searchEngine: SearchEngine = SearchEngine(engine, stringManager, projectDirectory)

    private val searchQuery = ImString(256)
    private var selectedResultIndex = 0
    private var currentResults: Map<SearchCategory, List<SearchResult>> = emptyMap()
    private var isSearching = false
    private var searchJob: Job? = null
    private var recentSearches: List<SearchHistoryEntry> = emptyList()
    private val debounceDelayMs = 50L

    private var lastQueriedText = ""

    private enum class SearchFilter { ALL, GAMEOBJECTS, FILES, ACTIONS }
    private var activeFilter = SearchFilter.ALL

    private var flattenedResults: List<SearchResultWithCategory> = emptyList()

    /** The palette's own commands matching the query (Test Input…), shown with the actions. */
    private var commandResults: List<SearchResult> = emptyList()

    /** Frames since opening: focus is only checked once the window has had a frame to take it. */
    private var framesOpen = 0

    private val focusRequest = SearchFocusRequest()

    fun open() {
        searchQuery.set("")
        selectedResultIndex = 0
        currentResults = emptyMap()
        flattenedResults = emptyList()
        lastQueriedText = ""
        commandResults = emptyList()
        activeFilter = SearchFilter.ALL
        framesOpen = 0
        engine.jobSystem.runOnMain {
            loadRecentSearches()
        }
    }

    fun close(pOpen: ImBoolean?) {
        pOpen?.set(false)
        searchJob?.cancel()
    }

    override fun onClosed() {
        searchJob?.cancel()
        focusRequest.onClosed()
    }

    private suspend fun loadRecentSearches() {
        recentSearches = searchHistory.getRecent(limit = 5)
    }

    /**
     * The command palette (EDT-51 §2.1): a transient popup without a title bar, centred, that closes on Esc, after
     * a choice, or when it loses focus. EDT-48: it takes focus when it opens and the input gets keyboard focus.
     */
    override fun imgui() {
        val justOpened = focusRequest.beginFrame()
        if (justOpened) {
            open()
            // Bring the window to the front so the focused input receives keystrokes.
            ImGui.setNextWindowFocus()
        }

        val viewport = ImGui.getMainViewport()
        ImGui.setNextWindowPos(viewport.workCenterX, viewport.workCenterY, ImGuiCond.Always, 0.5f, 0.5f)
        ImGui.setNextWindowSize(600f, 520f)
        ImGui.setNextWindowBgAlpha(0.95f)

        if (beginWindow(stringManager, PALETTE_FLAGS, closable = false)) {
            header(stringManager) {
                ImGui.textDisabled("${Icons.SEARCH} ${stringManager.getString("search.everywhere.title")}")
            }
            handleKeyboardInput(isOpen)
            renderSearchInput()

            ImGui.separator()
            renderFilterChips()
            ImGui.separator()

            ImGui.beginChild("SearchResults", 0f, 340f)
            if (searchQuery.get().isBlank()) {
                renderRecentSearches()
            } else {
                renderResults(isOpen)
            }
            ImGui.endChild()

            ImGui.separator()
            renderFooter()

            if (PalettePopup.closesOnFocusLoss(
                    framesOpen,
                    focused = ImGui.isWindowFocused(ImGuiFocusedFlags.RootAndChildWindows),
                    popupOpen = ImGui.isPopupOpen("", ImGuiPopupFlags.AnyPopupId),
                )
            ) {
                close(isOpen)
            }
            framesOpen++
        }
        ImGui.end()
    }

    private fun handleKeyboardInput(pOpen: ImBoolean?) {
        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            close(pOpen)
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
                close(pOpen)
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
        // EDT-48: focus once when opened, right before the input it targets (not every frame, not after end()).
        if (focusRequest.consume()) ImGui.setKeyboardFocusHere()
        ImGui.inputTextWithHint(
            "##SearchEverywhere",
            stringManager.getString("search.everywhere.placeholder"),
            searchQuery,
            flags
        )
        val inputEdited = ImGui.isItemEdited()
        ImGui.popItemWidth()
        ImGui.sameLine()

        if (ImGui.button(Icons.WINDOW_CLOSE, buttonSize, buttonSize)) clearQuery()
        Tooltips.text(stringManager.getString("lbl.clear"))

        if (inputEdited) {
            triggerSearch()
        }
    }

    private fun clearQuery() {
        searchQuery.set("")
        currentResults = emptyMap()
        commandResults = emptyList()
        flattenedResults = emptyList()
        selectedResultIndex = 0
        lastQueriedText = ""
        focusRequest.request()
    }

    private fun renderFilterChips() {
        renderFilterChip(SearchFilter.ALL,
            stringManager.getString("lbl.search.filter.all"),
            stringManager.getString("tooltip.search.filter.all"))
        ImGui.sameLine()
        renderFilterChip(SearchFilter.GAMEOBJECTS,
            stringManager.getString("lbl.search.filter.gameobjects"),
            stringManager.getString("tooltip.search.filter.gameobjects"))
        ImGui.sameLine()
        renderFilterChip(SearchFilter.FILES,
            stringManager.getString("lbl.search.filter.assets"),
            stringManager.getString("tooltip.search.filter.assets"))
        ImGui.sameLine()
        renderFilterChip(SearchFilter.ACTIONS,
            stringManager.getString("lbl.search.filter.actions"),
            stringManager.getString("tooltip.search.filter.actions"))
    }

    private fun renderFilterChip(filter: SearchFilter, label: String, tooltip: String) {
        val isActive = activeFilter == filter
        if (isActive) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.5f, 0.8f, 1f)
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.3f, 0.6f, 0.9f, 1f)
        }
        if (ImGui.button(label)) {
            activeFilter = filter
            // Re-flatten results for keyboard nav after filter change
            flattenedResults = flattenResults(getFilteredResults())
            selectedResultIndex = 0
        }
        if (isActive) {
            ImGui.popStyleColor(2)
        }
        Tooltips.text(tooltip)
    }

    private fun getFilteredResults(): Map<SearchCategory, List<SearchResult>> {
        return when (activeFilter) {
            SearchFilter.ALL -> withCommands(currentResults)
            SearchFilter.GAMEOBJECTS -> currentResults.filter { (cat, _) ->
                cat == SearchCategory.GAMEOBJECT
            }
            SearchFilter.FILES -> currentResults.filter { (cat, _) ->
                cat in setOf(
                    SearchCategory.ASSET_TEXTURE,
                    SearchCategory.ASSET_MODEL,
                    SearchCategory.ASSET_ANIMATION,
                    SearchCategory.ASSET_SOUND,
                    SearchCategory.ASSET_PREFAB,
                    SearchCategory.ASSET_SCRIPT,
                    SearchCategory.COMPONENT
                )
            }
            SearchFilter.ACTIONS -> withCommands(currentResults).filter { (cat, _) ->
                cat == SearchCategory.ACTION
            }
        }
    }

    /** The palette's commands first among the actions. */
    private fun withCommands(results: Map<SearchCategory, List<SearchResult>>): Map<SearchCategory, List<SearchResult>> {
        if (commandResults.isEmpty()) return results
        val actions = commandResults + results[SearchCategory.ACTION].orEmpty()
        return results + (SearchCategory.ACTION to actions)
    }

    private fun triggerSearch() {
        val query = searchQuery.get().trim()
        if (query == lastQueriedText) return

        lastQueriedText = query
        commandResults = PaletteCommands.matching(query, stringManager::getString).map { it.toResult(stringManager) }

        if (query.isEmpty()) {
            currentResults = emptyMap()
            flattenedResults = emptyList()
            selectedResultIndex = 0
            return
        }

        searchJob?.cancel()

        isSearching = true
        searchJob = engine.jobSystem.runAsync {
            Thread.sleep(debounceDelayMs)
            if (searchQuery.get().trim() != query) {
                return@runAsync
            }

            val results = searchEngine.search(query)

            engine.jobSystem.runOnMain {
                currentResults = results
                flattenedResults = flattenResults(getFilteredResults())
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

    private fun renderResults(pOpen: ImBoolean?) {
        if (isSearching) {
            ImGui.textColored(0.5f, 0.5f, 0.5f, 1f, stringManager.getString("lbl.search.searching"))
            return
        }

        val filtered = getFilteredResults()

        if (filtered.isEmpty() || filtered.values.all { it.isEmpty() }) {
            EmptyState.noResults(stringManager, ::clearQuery)
            return
        }

        var globalIndex = 0
        filtered.forEach { (category, results) ->
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
                    close(pOpen)
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
            SearchCategory.ASSET_SCRIPT -> Icons.FILE_CODE
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
            SearchCategory.ASSET_SCRIPT -> ImVec4(0.5f, 0.8f, 0.7f, 1f)
            SearchCategory.COMPONENT -> ImVec4(0.7f, 0.7f, 0.3f, 1f)
            SearchCategory.ACTION -> ImVec4(0.6f, 0.6f, 0.6f, 1f)
        }
    }

    private fun selectResult(result: SearchResult) {
        PaletteCommands.byId(result.id)?.let { command ->
            engine.eventSystem.publish(WindowAction.Show(command.window))
            return
        }
        engine.jobSystem.runAsync {
            searchHistory.add(searchQuery.get().trim(), result)
        }

        searchEngine.navigate(result)
    }

    private companion object {
        /** No title bar (the header strip names it), fixed in the centre, never docked or saved in the layout. */
        const val PALETTE_FLAGS = ImGuiWindowFlags.NoTitleBar or ImGuiWindowFlags.NoCollapse or
            ImGuiWindowFlags.NoResize or ImGuiWindowFlags.NoMove or ImGuiWindowFlags.NoDocking or
            ImGuiWindowFlags.NoSavedSettings
    }

    private fun renderFooter() {
        ImGui.spacing()
        ImGui.textColored(
            0.5f, 0.5f, 0.5f, 1f,
            "${stringManager.getString("search.everywhere.shortcut.navigate")}  " +
                    "${stringManager.getString("search.everywhere.shortcut.open")}  " +
                    stringManager.getString("search.everywhere.shortcut.close")
        )
    }
}

/** When the palette closes by itself (EDT-51 §2.1): it lost focus after its first frame, and none of its popups is open. */
object PalettePopup {
    fun closesOnFocusLoss(framesOpen: Int, focused: Boolean, popupOpen: Boolean): Boolean =
        framesOpen > 0 && !focused && !popupOpen
}

/** A command the palette offers itself, on top of the search providers (EDT-52F): it opens [window]. */
data class PaletteCommand(
    val id: String,
    val labelKey: String,
    val keywordsKey: String,
    val icon: String,
    val window: String,
) {
    fun toResult(strings: StringManager) = SearchResult(
        id = id,
        displayName = strings.getString(labelKey),
        category = SearchCategory.ACTION,
        subcategory = "",
        icon = icon,
        relevanceScore = 1f,
    )
}

object PaletteCommands {
    /** Test Input opens Key Bindings on its Test tab (EDT-51 §3.1, EDT-52I). */
    const val TEST_INPUT_WINDOW = KeyBindingsWindow.TEST_TAB

    val TEST_INPUT = PaletteCommand(
        id = "palette.test_input",
        labelKey = "search.command.test_input",
        keywordsKey = "search.command.test_input.keywords",
        icon = Icons.KEYBOARD,
        window = TEST_INPUT_WINDOW,
    )

    val ALL = listOf(TEST_INPUT)

    fun byId(id: String): PaletteCommand? = ALL.firstOrNull { it.id == id }

    /**
     * Commands whose label, keywords (from [text], the current language) or id contain every word of [query],
     * case-insensitive, so "test", "manette" or "gamepad" all find Test Input. A blank query matches nothing.
     */
    fun matching(query: String, text: (String) -> String): List<PaletteCommand> {
        val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return ALL.filter { command ->
            val haystack = listOf(text(command.labelKey), text(command.keywordsKey), command.id).joinToString(" ").lowercase()
            words.all { it in haystack }
        }
    }
}
