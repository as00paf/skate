package com.pafoid.skate.editor.windows

import com.pafoid.skate.editor.imgui.IWindow
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.input.EditorInputMappings
import com.pafoid.skate.engine.utils.UnitSystem
import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiSelectableFlags
import imgui.flag.ImGuiWindowFlags
import imgui.internal.ImGui.begin
import imgui.internal.ImGui.beginChild
import imgui.internal.ImGui.button
import imgui.internal.ImGui.checkbox
import imgui.internal.ImGui.combo
import imgui.internal.ImGui.dragInt
import imgui.internal.ImGui.end
import imgui.internal.ImGui.endChild
import imgui.internal.ImGui.inputText
import imgui.internal.ImGui.sameLine
import imgui.internal.ImGui.setNextWindowPos
import imgui.internal.ImGui.setNextWindowSize
import imgui.internal.ImGui.sliderFloat
import imgui.type.ImBoolean
import imgui.type.ImInt
import imgui.type.ImString
import org.lwjgl.glfw.GLFW

/**
 * Editor settings window with IntelliJ-style left/right split layout.
 *
 * Left pane: Category list with search filter
 * Right pane: Settings content for selected category
 */
class EditorSettingsWindow(
    private val settingsManager: SettingsManager,
    private val stringManager: StringManager
) : IWindow {

    private data class Category(
        val id: String,
        val labelKey: String,
        val searchTerms: Array<String>,
        val render: () -> Unit
    )

    private val searchBuffer = ImString("", 128)
    private var selectedCategoryId = "general"
    private var hasPendingChanges = false

    private var tempVSync = true
    private var tempMSAA = 4
    private var tempLanguage = "en"
    private var tempTheme = "Islands Dark"
    private var tempUnitSystem = UnitSystem.METRIC
    private var tempShowOverlay = true
    private var tempOverlaySize = 0.225f
    private var tempAutoSaveEnabled = true
    private var tempAutoSaveInterval = 5

    private val tempVSyncBool = ImBoolean(true)
    private val tempShowOverlayBool = ImBoolean(true)

    private val categories: List<Category> by lazy {
        listOf(
            Category("general", "settings.editor.general", arrayOf("general", "language")) { renderGeneral() },
            Category("keybindings", "settings.editor.keybindings", arrayOf("key", "binding", "gizmo", "translate", "rotate", "scale", "select", "measure", "deselect")) { renderKeyBindings() },
            Category("display", "settings.editor.display", arrayOf("display", "vsync", "msaa", "sync", "anti-aliasing")) { renderDisplay() },
            Category("interface", "settings.editor.interface", arrayOf("interface", "theme", "unit", "overlay", "gamepad")) { renderInterface() },
            Category("autosave", "settings.editor.autosave", arrayOf("auto", "save", "autosave", "interval")) { renderAutoSave() }
        )
    }

    override fun imgui(pOpen: ImBoolean?) {
        if (pOpen?.get() == false) return
        if (!hasPendingChanges) syncTempSettings()

        val viewport = ImGui.getMainViewport()
        val defaultWidth = viewport.workSizeX * 0.5f
        setNextWindowPos(viewport.centerX, viewport.centerY, ImGuiCond.FirstUseEver, 0.5f, 0.5f)
        setNextWindowSize(defaultWidth, 550f, ImGuiCond.FirstUseEver)

        if (begin(stringManager.getString("window.editor_settings"), pOpen, ImGuiWindowFlags.NoDocking)) {
            val avail = imgui.ImVec2()
            ImGui.getContentRegionAvail(avail)
            val windowW = avail.x
            val leftW = windowW * 0.3f
            val rightW = (windowW - leftW) - ImGui.getStyle().itemSpacingX
            val contentH = avail.y - 45f

            beginChild("leftPane", leftW, contentH)
            ImGui.pushItemWidth(-1f)
            inputText(stringManager.getString("settings.search.placeholder"), searchBuffer)
            ImGui.popItemWidth()
            ImGui.spacing()

            val query = searchBuffer.get().lowercase()
            val filtered = categories.filter { cat ->
                matchesSearch(query, *cat.searchTerms)
            }

            for (cat in filtered) {
                val isSelected = cat.id == selectedCategoryId
                if (isSelected) {
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.Header, 0.25f, 0.45f, 0.75f, 1f)
                }
                val label = stringManager.getString(cat.labelKey)
                if (ImGui.selectable(label, isSelected, ImGuiSelectableFlags.SpanAllColumns)) {
                    selectedCategoryId = cat.id
                    hasPendingChanges = false
                }
                if (isSelected) {
                    ImGui.popStyleColor()
                }
            }
            endChild()

            ImGui.sameLine()

            beginChild("rightPane", rightW, contentH)
            val selected = categories.find { it.id == selectedCategoryId }
            selected?.render()
            endChild()

            ImGui.spacing()
            val btnW = 100f
            if (button("OK", btnW, 0f)) {
                saveSettings()
                pOpen?.set(false)
            }
            ImGui.sameLine()
            if (button("Cancel", btnW, 0f)) {
                syncTempSettings()
                hasPendingChanges = false
                pOpen?.set(false)
            }
            ImGui.sameLine()
            ImGui.beginDisabled(!hasPendingChanges)
            if (button("Apply", btnW, 0f)) {
                saveSettings()
            }
            ImGui.endDisabled()
        }
        end()
    }

    private fun syncTempSettings() {
        if (!hasPendingChanges) {
            val hw = settingsManager.getCurrentHardware()
            val editor = settingsManager.engine.editor
            val autoSave = settingsManager.engine.autoSave
            tempVSync = hw.display.vsync
            tempVSyncBool.set(tempVSync)
            tempMSAA = hw.graphics.msaa
            tempLanguage = editor.language
            tempTheme = editor.theme
            tempUnitSystem = editor.unitSystem
            tempShowOverlay = editor.showGamepadOverlay
            tempShowOverlayBool.set(tempShowOverlay)
            tempOverlaySize = editor.gamepadOverlaySize
            tempAutoSaveEnabled = autoSave.enabled
            tempAutoSaveInterval = autoSave.intervalMinutes
        }
    }

    private fun renderGeneral() {
        val languages = arrayOf(
            stringManager.getString("settings.editor.language.english"),
            stringManager.getString("settings.editor.language.french")
        )
        val langCodes = arrayOf("en", "fr")
        val currentIdx = langCodes.indexOf(tempLanguage).coerceAtLeast(0)
        val selector = ImInt(currentIdx)
        MImGui.propertyRow(
            label = stringManager.getString("settings.editor.language"),
            onReset = { tempLanguage = "en"; hasPendingChanges = true }
        ) {
            if (combo("##language", selector, languages, languages.size)) {
                tempLanguage = langCodes[selector.get()]
                hasPendingChanges = true
            }
        }
    }

    private fun renderKeyBindings() {
        val mappings = EditorInputMappings()
        val actions = listOf(
            stringManager.getString("settings.editor.keybindings.gizmo_translate") to GLFW.glfwGetKeyName(mappings.gizmoTranslate.keyboardKey, 0),
            stringManager.getString("settings.editor.keybindings.gizmo_rotate") to GLFW.glfwGetKeyName(mappings.gizmoRotate.keyboardKey, 0),
            stringManager.getString("settings.editor.keybindings.gizmo_scale") to GLFW.glfwGetKeyName(mappings.gizmoScale.keyboardKey, 0),
            stringManager.getString("settings.editor.keybindings.gizmo_select") to GLFW.glfwGetKeyName(mappings.gizmoSelect.keyboardKey, 0),
            stringManager.getString("settings.editor.keybindings.measure_tool") to GLFW.glfwGetKeyName(mappings.measureTool.keyboardKey, 0),
            stringManager.getString("settings.editor.keybindings.deselect") to GLFW.glfwGetKeyName(mappings.deselectAll.keyboardKey, 0),
        )
        if (ImGui.beginTable("##keybindings_table", 2)) {
            ImGui.tableSetupColumn(stringManager.getString("settings.editor.keybindings.action"))
            ImGui.tableSetupColumn(stringManager.getString("settings.editor.keybindings.bound_key"))
            ImGui.tableHeadersRow()
            for ((action, keyName) in actions) {
                ImGui.tableNextRow()
                ImGui.tableSetColumnIndex(0)
                ImGui.text(action)
                ImGui.tableSetColumnIndex(1)
                ImGui.text(keyName ?: "Unknown")
            }
            ImGui.endTable()
        }
        MImGui.textDisabled(stringManager.getString("settings.editor.keybindings.readonly_note"))
    }

    private fun renderDisplay() {
        MImGui.propertyRow(
            label = stringManager.getString("settings.editor.display.vsync"),
            helpTooltip = stringManager.getString("settings.editor.display.vsync.desc"),
            onReset = { tempVSync = true; tempVSyncBool.set(true); hasPendingChanges = true }
        ) {
            if (ImGui.checkbox("##vsync", tempVSyncBool)) {
                tempVSync = tempVSyncBool.get()
                hasPendingChanges = true
            }
        }
        val msaaOptions = arrayOf(
            stringManager.getString("settings.editor.display.msaa.none"),
            "2", "4", "8"
        )
        val msaaValues = arrayOf(0, 2, 4, 8)
        val msaaIdx = msaaValues.indexOf(tempMSAA).coerceAtLeast(0)
        val msaaSel = ImInt(msaaIdx)
        MImGui.propertyRow(
            label = stringManager.getString("settings.editor.display.msaa"),
            helpTooltip = stringManager.getString("settings.editor.display.msaa.desc"),
            onReset = { tempMSAA = 4; hasPendingChanges = true }
        ) {
            if (combo("##msaa", msaaSel, msaaOptions, msaaOptions.size)) {
                tempMSAA = msaaValues[msaaSel.get()]
                hasPendingChanges = true
            }
        }
        MImGui.textDisabled(stringManager.getString("settings.restart_note"))
    }

    private fun renderInterface() {
        val themeOptions = arrayOf(
            stringManager.getString("settings.theme.islands_dark"),
            stringManager.getString("settings.theme.default")
        )
        val themeValues = arrayOf("Islands Dark", "Default")
        val themeIdx = themeValues.indexOf(tempTheme).coerceAtLeast(0)
        val themeSel = ImInt(themeIdx)
        MImGui.propertyRow(
            label = stringManager.getString("settings.editor.interface.theme"),
            helpTooltip = stringManager.getString("settings.editor.interface.theme.desc"),
            onReset = { tempTheme = "Islands Dark"; hasPendingChanges = true }
        ) {
            if (combo("##theme", themeSel, themeOptions, themeOptions.size)) {
                tempTheme = themeValues[themeSel.get()]
                hasPendingChanges = true
            }
        }

        val unitOptions = UnitSystem.entries.map { it.name }.toTypedArray()
        val unitSel = ImInt(tempUnitSystem.ordinal)
        MImGui.propertyRow(
            label = stringManager.getString("settings.editor.interface.unit_system"),
            helpTooltip = stringManager.getString("settings.editor.interface.unit_system.desc"),
            onReset = { tempUnitSystem = UnitSystem.METRIC; hasPendingChanges = true }
        ) {
            if (combo("##unit", unitSel, unitOptions, unitOptions.size)) {
                tempUnitSystem = UnitSystem.entries[unitSel.get()]
                hasPendingChanges = true
            }
        }

        MImGui.propertyRow(
            label = stringManager.getString("settings.editor.interface.show_overlay"),
            helpTooltip = stringManager.getString("settings.editor.interface.show_overlay.desc"),
            onReset = { tempShowOverlay = true; tempShowOverlayBool.set(true); hasPendingChanges = true }
        ) {
            if (ImGui.checkbox("##overlay", tempShowOverlayBool)) {
                tempShowOverlay = tempShowOverlayBool.get()
                hasPendingChanges = true
            }
        }

        val overlayArr = floatArrayOf(tempOverlaySize)
        MImGui.propertyRow(
            label = stringManager.getString("settings.editor.interface.overlay_size"),
            helpTooltip = stringManager.getString("settings.editor.interface.overlay_size.desc"),
            onReset = { tempOverlaySize = 0.225f; hasPendingChanges = true }
        ) {
            if (sliderFloat("##overlay_size", overlayArr, 0.05f, 0.5f)) {
                tempOverlaySize = overlayArr[0]
                hasPendingChanges = true
            }
        }
    }

    private fun renderAutoSave() {
        MImGui.propertyRow(
            label = stringManager.getString("settings.editor.autosave.enabled"),
            helpTooltip = stringManager.getString("settings.editor.autosave.enabled.desc"),
            onReset = { tempAutoSaveEnabled = true; hasPendingChanges = true }
        ) {
            val boolArr = ImBoolean(tempAutoSaveEnabled)
            if (ImGui.checkbox("##autosave_enabled", boolArr)) {
                tempAutoSaveEnabled = boolArr.get()
                hasPendingChanges = true
            }
        }
        val intervalArr = intArrayOf(tempAutoSaveInterval)
        MImGui.propertyRow(
            label = stringManager.getString("settings.editor.autosave.interval"),
            helpTooltip = stringManager.getString("settings.editor.autosave.interval.desc"),
            onReset = { tempAutoSaveInterval = 5; hasPendingChanges = true }
        ) {
            ImGui.beginDisabled(!tempAutoSaveEnabled)
            if (dragInt("##autosave_interval", intervalArr, 1f, 1, 60)) {
                tempAutoSaveInterval = intervalArr[0]
                hasPendingChanges = true
            }
            ImGui.endDisabled()
        }
    }

    private fun saveSettings() {
        if (tempLanguage != settingsManager.engine.editor.language) {
            settingsManager.updateEditorSettings(language = tempLanguage)
        }
        settingsManager.applyVSync(tempVSync)
        settingsManager.updateEditorSettings(
            theme = tempTheme,
            unitSystem = tempUnitSystem,
            showGamepadOverlay = tempShowOverlay,
            gamepadOverlaySize = tempOverlaySize
        )
        settingsManager.updateAutoSaveSettings(tempAutoSaveEnabled, tempAutoSaveInterval)
        hasPendingChanges = false
    }

    companion object {
        private fun matchesSearch(query: String, vararg terms: String): Boolean {
            if (query.isBlank()) return true
            return terms.any { it.contains(query, ignoreCase = true) }
        }
    }
}
