package com.pafoid.skate.editor.windows

import com.pafoid.skate.editor.imgui.IWindow
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.input.EditorInputMappings
import com.pafoid.skate.engine.input.InputMappings
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
import imgui.internal.ImGui.isKeyPressed
import imgui.internal.ImGui.sameLine
import imgui.internal.ImGui.separator
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

    // Keybinding state
    private var tempMappings = EditorInputMappings()
    private var tempCameraMappings = InputMappings()
    private var rebindingAction: String? = null

    private var tempLanguage = "en"
    private var tempTheme = "Islands Dark"
    private var tempUnitSystem = UnitSystem.METRIC
    private var tempShowOverlay = true
    private var tempOverlaySize = 0.225f
    private var tempAutoSaveEnabled = true
    private var tempAutoSaveInterval = 5

    private val tempShowOverlayBool = ImBoolean(true)

    private val categories: List<Category> by lazy {
        listOf(
            Category("general", "settings.editor.general", arrayOf("general", "language", "unit", "metric", "imperial")) { renderGeneral() },
            Category("keybindings", "settings.editor.keybindings", arrayOf("key", "binding", "gizmo", "translate", "rotate", "scale", "select", "measure", "deselect", "camera", "reset")) { renderKeyBindings() },
            Category("gamepad_overlay", "settings.editor.gamepad_overlay", arrayOf("gamepad", "overlay", "show", "size", "scale")) { renderGamepadOverlay() },
            Category("interface", "settings.editor.interface", arrayOf("interface", "theme")) { renderInterface() },
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

            // Handle key rebinding
            rebindingAction?.let { action ->
                for (i in 0..348) {
                    if (isKeyPressed(i)) {
                        assignKeyBinding(action, i)
                        rebindingAction = null
                        hasPendingChanges = true
                        break
                    }
                }
            }

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
                rebindingAction = null
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
            val editor = settingsManager.engine.editor
            val autoSave = settingsManager.engine.autoSave
            tempLanguage = editor.language
            tempTheme = editor.theme
            tempUnitSystem = editor.unitSystem
            tempShowOverlay = editor.showGamepadOverlay
            tempShowOverlayBool.set(tempShowOverlay)
            tempOverlaySize = editor.gamepadOverlaySize
            tempAutoSaveEnabled = autoSave.enabled
            tempAutoSaveInterval = autoSave.intervalMinutes
            // Copy input mappings
            tempMappings = editor.editorInputMappings.copy()
            // Load saved camera mappings or use defaults
            tempCameraMappings = settingsManager.loadInputMappings() ?: InputMappings()
        }
    }

    private fun renderGeneral() {
        // Language
        val languages = arrayOf(
            stringManager.getString("settings.editor.language.english"),
            stringManager.getString("settings.editor.language.french")
        )
        val langCodes = arrayOf("en", "fr")
        val currentIdx = langCodes.indexOf(tempLanguage).coerceAtLeast(0)
        val selector = ImInt(currentIdx)
        MImGui.propertyRow(label = stringManager.getString("settings.editor.language")) {
            if (combo("##language", selector, languages, languages.size)) {
                tempLanguage = langCodes[selector.get()]
                hasPendingChanges = true
            }
        }

        // Unit System
        val unitOptions = UnitSystem.entries.map { it.name }.toTypedArray()
        val unitSel = ImInt(tempUnitSystem.ordinal)
        MImGui.propertyRow(label = stringManager.getString("settings.editor.interface.unit_system")) {
            if (combo("##unit", unitSel, unitOptions, unitOptions.size)) {
                tempUnitSystem = UnitSystem.entries[unitSel.get()]
                hasPendingChanges = true
            }
        }
    }

    // ─── Key Bindings ───

    private fun renderKeyBindings() {
        // Editor section
        ImGui.text(stringManager.getString("lbl.keybindings.editor_section"))
        separator()
        drawBindRow(stringManager.getString("lbl.keybindings.translate"), tempMappings.gizmoTranslate.keyboardKey, "editorGizmoTranslate")
        drawBindRow(stringManager.getString("lbl.keybindings.rotate"), tempMappings.gizmoRotate.keyboardKey, "editorGizmoRotate")
        drawBindRow(stringManager.getString("lbl.keybindings.scale"), tempMappings.gizmoScale.keyboardKey, "editorGizmoScale")
        drawBindRow(stringManager.getString("lbl.keybindings.select"), tempMappings.gizmoSelect.keyboardKey, "editorGizmoSelect")
        drawBindRow(stringManager.getString("lbl.keybindings.measure"), tempMappings.measureTool.keyboardKey, "editorMeasure")
        drawBindRow(stringManager.getString("lbl.keybindings.deselect"), tempMappings.deselectAll.keyboardKey, "editorDeselect")
        
        separator()
        // Camera section
        ImGui.text(stringManager.getString("lbl.keybindings.camera_section"))
        separator()
        drawBindRow(stringManager.getString("lbl.keybindings.camera_reset"), tempCameraMappings.cameraReset.keyboardKey, "cameraReset")
        separator()
        ImGui.text(stringManager.getString("lbl.keybindings.camera_look"))
        MImGui.textDisabled(stringManager.getString("lbl.keybindings.camera_look_note"))
    }

    private fun drawBindRow(label: String, currentValue: Int, bindAction: String) {
        ImGui.text(label)
        ImGui.sameLine(200f)

        val keyName = getKeyName(currentValue)
        val btnText = if (rebindingAction == bindAction) stringManager.getString("lbl.keybindings.press_key") else keyName

        if (button("$btnText##$bindAction", 120f, 0f)) {
            rebindingAction = bindAction
        }
    }

    private fun assignKeyBinding(action: String, key: Int) {
        when (action) {
            "editorGizmoTranslate" -> tempMappings.gizmoTranslate.keyboardKey = key
            "editorGizmoRotate" -> tempMappings.gizmoRotate.keyboardKey = key
            "editorGizmoScale" -> tempMappings.gizmoScale.keyboardKey = key
            "editorGizmoSelect" -> tempMappings.gizmoSelect.keyboardKey = key
            "editorMeasure" -> tempMappings.measureTool.keyboardKey = key
            "editorDeselect" -> tempMappings.deselectAll.keyboardKey = key
            "cameraReset" -> tempCameraMappings.cameraReset.keyboardKey = key
        }
    }

    private fun getKeyName(key: Int): String {
        return when (key) {
            GLFW.GLFW_KEY_ESCAPE -> "Esc"
            GLFW.GLFW_KEY_ENTER -> "Enter"
            GLFW.GLFW_KEY_TAB -> "Tab"
            GLFW.GLFW_KEY_BACKSPACE -> "Backspace"
            GLFW.GLFW_KEY_INSERT -> "Insert"
            GLFW.GLFW_KEY_DELETE -> "Del"
            GLFW.GLFW_KEY_RIGHT -> "Right"
            GLFW.GLFW_KEY_LEFT -> "Left"
            GLFW.GLFW_KEY_DOWN -> "Down"
            GLFW.GLFW_KEY_UP -> "Up"
            GLFW.GLFW_KEY_PAGE_UP -> "PgUp"
            GLFW.GLFW_KEY_PAGE_DOWN -> "PgDn"
            GLFW.GLFW_KEY_HOME -> "Home"
            GLFW.GLFW_KEY_END -> "End"
            else -> {
                val name = GLFW.glfwGetKeyName(key, 0)
                name?.uppercase() ?: "Key $key"
            }
        }
    }

    private fun renderGamepadOverlay() {
        MImGui.propertyRow(
            label = stringManager.getString("settings.editor.interface.show_overlay"),
            helpTooltip = stringManager.getString("settings.editor.interface.show_overlay.desc")
        ) {
            if (ImGui.checkbox("##overlay", tempShowOverlayBool)) {
                tempShowOverlay = tempShowOverlayBool.get()
                hasPendingChanges = true
            }
        }

        val overlayArr = floatArrayOf(tempOverlaySize)
        MImGui.propertyRow(
            label = stringManager.getString("settings.editor.interface.overlay_size"),
            helpTooltip = stringManager.getString("settings.editor.interface.overlay_size.desc")
        ) {
            if (sliderFloat("##overlay_size", overlayArr, 0.05f, 0.5f)) {
                tempOverlaySize = overlayArr[0]
                hasPendingChanges = true
            }
        }
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
            helpTooltip = stringManager.getString("settings.editor.interface.theme.desc")
        ) {
            if (combo("##theme", themeSel, themeOptions, themeOptions.size)) {
                tempTheme = themeValues[themeSel.get()]
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
        settingsManager.updateEditorSettings(
            theme = tempTheme,
            unitSystem = tempUnitSystem,
            showGamepadOverlay = tempShowOverlay,
            gamepadOverlaySize = tempOverlaySize
        )
        // Save keybindings
        settingsManager.updateEditorSettings(editorInputMappings = tempMappings)
        // Save camera mappings
        settingsManager.updateInputMappings(tempCameraMappings)
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
