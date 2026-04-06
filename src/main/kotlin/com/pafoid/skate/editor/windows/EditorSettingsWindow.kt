package com.pafoid.skate.editor.windows

import com.pafoid.skate.editor.imgui.IWindow
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.input.EditorInputMappings
import com.pafoid.skate.engine.utils.UnitSystem
import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiWindowFlags
import imgui.internal.ImGui.begin
import imgui.internal.ImGui.button
import imgui.internal.ImGui.collapsingHeader
import imgui.internal.ImGui.combo
import imgui.internal.ImGui.dragFloat
import imgui.internal.ImGui.dragInt
import imgui.internal.ImGui.end
import imgui.internal.ImGui.inputText
import imgui.internal.ImGui.sameLine
import imgui.internal.ImGui.separator
import imgui.internal.ImGui.setNextWindowPos
import imgui.internal.ImGui.setNextWindowSize
import imgui.type.ImBoolean
import imgui.type.ImInt
import imgui.type.ImString
import org.lwjgl.glfw.GLFW

/**
 * Window for configuring editor preferences: language, key bindings, display.
 */
class EditorSettingsWindow(
    private val settingsManager: SettingsManager,
    private val stringManager: StringManager
) : IWindow {

    private val searchBuffer = ImString("", 128)

    private var tempVSync = true
    private var tempFullscreen = false
    private var tempMSAA = 4
    private var tempLanguage = "en"

    // ImBoolean wrappers for checkboxes
    private val tempVSyncBool = ImBoolean(true)
    private val tempFullscreenBool = ImBoolean(false)

    // Track pending save state for Apply button
    private var hasPendingChanges = false

    override fun imgui(pOpen: ImBoolean?) {
        if (pOpen?.get() == false) return

        if (!hasPendingChanges) syncTempSettings()

        val viewport = ImGui.getMainViewport()
        val defaultWidth = viewport.workSizeX * 0.5f
        setNextWindowPos(viewport.centerX, viewport.centerY, ImGuiCond.FirstUseEver, 0.5f, 0.5f)
        setNextWindowSize(defaultWidth, 500f, ImGuiCond.FirstUseEver)

        // Pass pOpen to begin() so ImGui shows the (X) close button
        if (begin(stringManager.getString("window.editor_settings"), pOpen, ImGuiWindowFlags.NoDocking)) {
            // Search bar
            ImGui.pushItemWidth(-1f)
            inputText(stringManager.getString("settings.search.placeholder"), searchBuffer)
            ImGui.popItemWidth()
            separator()

            val query = searchBuffer.get().lowercase()

            // General section
            if (matchesSearch(query, "general", "language")) {
                if (collapsingHeader(stringManager.getString("settings.editor.general"))) {
                    renderGeneralSection()
                    separator()
                }
            }

            // Key Bindings section
            if (matchesSearch(query, "key", "binding", "gizmo", "translate", "rotate", "scale", "select", "measure", "deselect")) {
                if (collapsingHeader(stringManager.getString("settings.editor.keybindings"))) {
                    renderKeyBindingsSection()
                    separator()
                }
            }

            // Display section
            if (matchesSearch(query, "display", "vsync", "fullscreen", "msaa", "sync", "anti-aliasing")) {
                if (collapsingHeader(stringManager.getString("settings.editor.display"))) {
                    renderDisplaySection()
                    separator()
                }
            }

            // Interface section (Coming Soon)
            if (matchesSearch(query, "interface", "theme", "overlay", "unit", "autosave")) {
                if (collapsingHeader(stringManager.getString("settings.editor.interface"))) {
                    renderInterfaceSection()
                    separator()
                }
            }

            // OK / Cancel / Apply buttons
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
            tempVSync = hw.display.vsync
            tempFullscreen = hw.display.fullscreen
            tempMSAA = hw.graphics.msaa
            tempLanguage = settingsManager.engine.editor.language
            tempVSyncBool.set(tempVSync)
            tempFullscreenBool.set(tempFullscreen)
        }
    }

    private fun renderGeneralSection() {
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

    private fun renderKeyBindingsSection() {
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

    private fun renderDisplaySection() {
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

        MImGui.propertyRow(
            label = stringManager.getString("settings.editor.display.fullscreen"),
            helpTooltip = stringManager.getString("settings.editor.display.fullscreen.desc"),
            onReset = { tempFullscreen = false; tempFullscreenBool.set(false); hasPendingChanges = true }
        ) {
            if (ImGui.checkbox("##fullscreen", tempFullscreenBool)) {
                tempFullscreen = tempFullscreenBool.get()
                hasPendingChanges = true
            }
        }

        val msaaOptions = arrayOf(
            stringManager.getString("settings.editor.display.msaa.none"),
            "2", "4", "8"
        )
        val msaaValues = arrayOf(0, 2, 4, 8)
        val msaaIdx = msaaValues.indexOf(tempMSAA).coerceAtLeast(0)
        val msaaSelector = ImInt(msaaIdx)
        MImGui.propertyRow(
            label = stringManager.getString("settings.editor.display.msaa"),
            helpTooltip = stringManager.getString("settings.editor.display.msaa.desc"),
            onReset = { tempMSAA = 4; hasPendingChanges = true }
        ) {
            if (combo("##msaa", msaaSelector, msaaOptions, msaaOptions.size)) {
                tempMSAA = msaaValues[msaaSelector.get()]
                hasPendingChanges = true
            }
        }

        MImGui.textDisabled(stringManager.getString("settings.restart_note"))
    }

    private fun renderInterfaceSection() {
        MImGui.comingSoonRow(stringManager.getString("settings.editor.interface.theme"))
        MImGui.comingSoonRow(stringManager.getString("settings.editor.interface.show_overlay"))
        MImGui.comingSoonRow(stringManager.getString("settings.editor.interface.overlay_size"))
        MImGui.comingSoonRow(stringManager.getString("settings.editor.interface.unit_system"))
        MImGui.comingSoonRow(stringManager.getString("settings.editor.interface.autosave_enabled"))
        MImGui.comingSoonRow(stringManager.getString("settings.editor.interface.autosave_interval"))
    }

    private fun saveSettings() {
        if (tempLanguage != settingsManager.engine.editor.language) {
            settingsManager.updateEditorSettings(language = tempLanguage)
        }
        settingsManager.applyVSync(tempVSync)
        settingsManager.applyFullscreen(tempFullscreen)
        hasPendingChanges = false
    }

    private fun resetToDefaults() {
        tempVSync = true; tempVSyncBool.set(true)
        tempFullscreen = false; tempFullscreenBool.set(false)
        tempMSAA = 4
        tempLanguage = "en"
        hasPendingChanges = true
    }

    companion object {
        private fun matchesSearch(query: String, vararg terms: String): Boolean {
            if (query.isBlank()) return true
            return terms.any { it.contains(query, ignoreCase = true) }
        }
    }
}
