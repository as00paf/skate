package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.editor.imgui.label
import com.pafoid.skate.editor.data.EditorInputMappings
import com.pafoid.skate.editor.imgui.EditorDialog
import com.pafoid.skate.editor.imgui.DialogFooter
import com.pafoid.skate.editor.imgui.FooterButton
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.ImGuiStyleManager
import com.pafoid.skate.editor.imgui.theme.EditorTheme
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.settings.ShadowQuality
import com.pafoid.skate.editor.systems.EditorSettingsManager
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.utils.UnitSystem
import imgui.ImGui
import imgui.ImVec2
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiSelectableFlags
import imgui.internal.ImGui.beginChild
import imgui.internal.ImGui.button
import imgui.internal.ImGui.checkbox
import imgui.internal.ImGui.combo
import imgui.internal.ImGui.dragInt
import imgui.internal.ImGui.endChild
import imgui.internal.ImGui.inputText
import imgui.internal.ImGui.isKeyPressed
import imgui.internal.ImGui.sameLine
import imgui.internal.ImGui.separator
import imgui.internal.ImGui.sliderFloat
import imgui.type.ImBoolean
import imgui.type.ImInt
import imgui.type.ImString
import org.lwjgl.glfw.GLFW

class EditorSettingsWindow(
    private val settingsManager: EditorSettingsManager,
    private val stringManager: StringManager,
    private val openKeyBindings: () -> Unit = {},
) : EditorDialog("window.editor_settings", stringManager, 760f, 550f) {

    override val icon: String = Icons.GEAR
    override val relatedWindows = listOf("window.keybindings", "window.project_settings")

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

    private var tempLanguage = "en"
    private var tempTheme = EditorTheme.DEFAULT_ID
    private var tempUnitSystem = UnitSystem.METRIC
    private var tempShowOverlay = true
    private var tempOverlaySize = 0.225f
    private var tempAutoSaveEnabled = true
    private var tempAutoSaveInterval = 5
    private var tempCompletionAuto = true
    private var tempCompletionDelay = 200
    private var tempShadowQuality = ShadowQuality.MEDIUM

    private val tempShowOverlayBool = ImBoolean(true)

    private val categories: List<Category> by lazy {
        listOf(
            Category("general", "settings.editor.general", arrayOf("general", "language", "unit", "metric", "imperial")) { renderGeneral() },
            Category("keybindings", "settings.editor.keybindings", arrayOf("key", "binding", "gizmo", "translate", "rotate", "scale", "select", "measure", "deselect", "camera", "reset")) { renderKeyBindings() },
            Category("gamepad_overlay", "settings.editor.gamepad_overlay", arrayOf("gamepad", "overlay", "show", "size", "scale")) { renderGamepadOverlay() },
            Category("interface", "settings.editor.interface", arrayOf("interface", "theme")) { renderInterface() },
            Category("graphics", "settings.editor.graphics", arrayOf("graphics", "shadow", "quality")) { renderGraphics() },
            Category("autosave", "settings.editor.autosave", arrayOf("auto", "save", "autosave", "interval")) { renderAutoSave() },
            Category("script_editor", "settings.editor.script_editor", arrayOf("script", "completion", "popup", "delay", "autocomplete")) { renderScriptEditor() }
        )
    }

    /** Standard footer (EDT-51 §2.6): [Cancel] [Apply] [OK]; Apply only while there are unsaved edits. */
    override fun footerButtons(): List<FooterButton> = listOf(
        FooterButton("btn.cancel", FooterButton.Role.CANCEL) {
            ImGuiStyleManager.preview = null
            hasPendingChanges = false
            syncTempSettings()
        },
        FooterButton("btn.apply", FooterButton.Role.APPLY, enabled = hasPendingChanges, closes = false) { saveSettings() },
        FooterButton("btn.ok", FooterButton.Role.PRIMARY) { saveSettings() },
    )

    override fun content() {
        if (!hasPendingChanges) syncTempSettings()

        val avail = ImVec2()
        ImGui.getContentRegionAvail(avail)
        val windowW = avail.x
        val leftW = windowW * 0.3f
        val rightW = (windowW - leftW) - ImGui.getStyle().itemSpacingX
        val contentH = avail.y - DialogFooter.height()

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
                ImGui.pushStyleColor(ImGuiCol.Header, 0.25f, 0.45f, 0.75f, 1f)
            }
            val label = stringManager.getString(cat.labelKey)
            if (ImGui.selectable(label, isSelected, ImGuiSelectableFlags.SpanAllColumns)) {
                // Pending edits (and the theme preview) survive switching category until OK/Apply/Cancel.
                selectedCategoryId = cat.id
            }
            if (isSelected) {
                ImGui.popStyleColor()
            }
        }
        endChild()

        sameLine()

        beginChild("rightPane", rightW, contentH)
        val selected = categories.find { it.id == selectedCategoryId }
        selected?.render()
        endChild()
    }

    /** Closed without OK/Apply, by any path: drop the theme preview and unsaved edits (EDT-40). */
    override fun onClosed() {
        ImGuiStyleManager.preview = null
        hasPendingChanges = false
    }

    private fun syncTempSettings() {
        if (!hasPendingChanges) {
            val editor = settingsManager.editorSettings
            tempLanguage = editor.language
            tempTheme = editor.theme
            tempUnitSystem = editor.unitSystem
            tempShowOverlay = editor.showGamepadOverlay
            tempShowOverlayBool.set(tempShowOverlay)
            tempOverlaySize = editor.gamepadOverlaySize
            tempAutoSaveEnabled = editor.autoSaveEnabled
            tempAutoSaveInterval = editor.autorSaveIntervalMinutes
            tempCompletionAuto = editor.completionAutoPopup
            tempCompletionDelay = editor.completionDelayMs
            tempShadowQuality = editor.graphics.shadowQuality
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

    /** Key bindings live in the Key Bindings window (EDT-10); one editor for them, not two. */
    private fun renderKeyBindings() {
        MImGui.textDisabled(stringManager.getString("settings.editor.keybindings.moved"))
        ImGui.spacing()
        if (stringManager.tipped(button(stringManager.label("menu.settings.keybindings")), "menu.settings.keybindings")) openKeyBindings()
    }

    private fun renderGamepadOverlay() {
        MImGui.propertyRow(
            label = stringManager.getString("settings.editor.interface.show_overlay"),
            helpTooltip = stringManager.getString("settings.editor.interface.show_overlay.desc")
        ) {
            if (checkbox("##overlay", tempShowOverlayBool)) {
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
        // EDT-07: the themes shipped in assets/themes; picking one previews it live until OK/Apply or Cancel.
        val themes = ImGuiStyleManager.library.themes
        val themeOptions = themes.map { stringManager.getString(it.nameKey) }.toTypedArray()
        val themeValues = themes.map { it.id }.toTypedArray()
        val themeIdx = themeValues.indexOf(tempTheme).coerceAtLeast(0)
        val themeSel = ImInt(themeIdx)
        MImGui.propertyRow(
            label = stringManager.getString("settings.editor.interface.theme"),
            helpTooltip = stringManager.getString("settings.editor.interface.theme.desc")
        ) {
            if (combo("##theme", themeSel, themeOptions, themeOptions.size)) {
                tempTheme = themeValues[themeSel.get()]
                ImGuiStyleManager.preview = tempTheme
                hasPendingChanges = true
            }
        }
    }

    private fun renderGraphics() {
        val labels = ShadowQuality.entries.map { stringManager.getString(shadowQualityKey(it)) }.toTypedArray()
        val selected = ImInt(tempShadowQuality.ordinal)
        MImGui.propertyRow(
            label = stringManager.getString("settings.editor.graphics.shadow_quality"),
            helpTooltip = stringManager.getString("settings.editor.graphics.shadow_quality.desc"),
            onReset = { tempShadowQuality = ShadowQuality.MEDIUM; hasPendingChanges = true }
        ) {
            if (combo("##shadow_quality", selected, labels, labels.size)) {
                tempShadowQuality = ShadowQuality.entries[selected.get()]
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
            if (checkbox("##autosave_enabled", boolArr)) {
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

    private fun renderScriptEditor() {
        MImGui.propertyRow(
            label = stringManager.getString("settings.editor.completion.auto"),
            helpTooltip = stringManager.getString("settings.editor.completion.auto.desc"),
            onReset = { tempCompletionAuto = true; hasPendingChanges = true }
        ) {
            val flag = ImBoolean(tempCompletionAuto)
            if (checkbox("##completion_auto", flag)) {
                tempCompletionAuto = flag.get()
                hasPendingChanges = true
            }
        }
        val delay = intArrayOf(tempCompletionDelay)
        MImGui.propertyRow(
            label = stringManager.getString("settings.editor.completion.delay"),
            helpTooltip = stringManager.getString("settings.editor.completion.delay.desc"),
            onReset = { tempCompletionDelay = 200; hasPendingChanges = true }
        ) {
            ImGui.beginDisabled(!tempCompletionAuto)
            if (dragInt("##completion_delay", delay, 5f, 0, 2000)) {
                tempCompletionDelay = delay[0]
                hasPendingChanges = true
            }
            ImGui.endDisabled()
        }
    }

    private fun saveSettings() {
        if (tempLanguage != settingsManager.editorSettings.language) {
            settingsManager.updateEditorSettings(language = tempLanguage)
        }
        settingsManager.updateEditorSettings(
            theme = tempTheme,
            unitSystem = tempUnitSystem,
            showGamepadOverlay = tempShowOverlay,
            gamepadOverlaySize = tempOverlaySize
        )
        settingsManager.updateAutoSaveSettings(tempAutoSaveEnabled, tempAutoSaveInterval)
        settingsManager.updateCompletionSettings(tempCompletionAuto, tempCompletionDelay)
        settingsManager.updateShadowQuality(tempShadowQuality)
        ImGuiStyleManager.preview = null
        hasPendingChanges = false
    }

    companion object {
        /** String key for a shadow quality option, shared with the Render Graph window. */
        fun shadowQualityKey(quality: ShadowQuality): String = "settings.graphics.shadow_quality.${quality.name.lowercase()}"

        private fun matchesSearch(query: String, vararg terms: String): Boolean {
            if (query.isBlank()) return true
            return terms.any { it.contains(query, ignoreCase = true) }
        }
    }
}
