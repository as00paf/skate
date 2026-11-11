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
import com.pafoid.skate.editor.settings.AppearanceSettings
import com.pafoid.skate.editor.settings.Density
import com.pafoid.skate.editor.settings.Spacing
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.settings.EditorCameraSettings
import com.pafoid.skate.editor.settings.EditorSettings
import com.pafoid.skate.editor.settings.GizmoSnapSettings
import com.pafoid.skate.editor.systems.EditorSettingsManager
import com.pafoid.skate.editor.tools.external.BlenderTestResult
import com.pafoid.skate.editor.tools.external.BlenderVersion
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
    /** Looks for Blender with the typed path (EDT-66; `BlenderSync.test`); the result arrives on the main thread. */
    private val testBlender: (String?, (BlenderTestResult) -> Unit) -> Unit = { _, _ -> },
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
    private var tempShowTooltips = true
    private var tempUiScale = 1f
    private var tempAppearance = AppearanceSettings()
    private var tempLoadLastProject = true
    private var tempSnap = GizmoSnapSettings()
    private var tempCamera = EditorCameraSettings()
    private val tempBlenderPath = ImString("", 512)
    private var tempBlenderTimeout = 120
    private var tempBlenderImportOnOpen = true
    private var blenderTest: BlenderTestResult? = null
    private var blenderTesting = false

    private val tempShowOverlayBool = ImBoolean(true)

    private val categories: List<Category> by lazy {
        listOf(
            Category("general", "settings.editor.general", arrayOf("general", "language", "unit", "metric", "imperial", "startup", "project", "last")) { renderGeneral() },
            Category("keybindings", "settings.editor.keybindings", arrayOf("key", "binding", "gizmo", "translate", "rotate", "scale", "select", "measure", "deselect", "camera", "reset")) { renderKeyBindings() },
            Category("gamepad_overlay", "settings.editor.gamepad_overlay", arrayOf("gamepad", "overlay", "show", "size", "scale")) { renderGamepadOverlay() },
            Category("interface", "settings.editor.interface", arrayOf("interface", "theme", "tooltip", "scale", "font", "size", "text", "appearance", "density", "padding", "spacing", "rounding", "compact", "spacious", "menu")) { renderInterface() },
            Category("scene_view", "settings.editor.scene_view", arrayOf("scene", "view", "snap", "gizmo", "grid", "camera", "speed", "sensitivity", "mouse")) { renderSceneView() },
            Category("autosave", "settings.editor.autosave", arrayOf("auto", "save", "autosave", "interval")) { renderAutoSave() },
            Category("script_editor", "settings.editor.script_editor", arrayOf("script", "completion", "popup", "delay", "autocomplete")) { renderScriptEditor() },
            Category("blender", "settings.editor.blender", arrayOf("blender", "blend", "import", "model", "path", "timeout")) { renderBlender() },
        )
    }

    /** Standard footer (EDT-51 §2.6): [Cancel] [Apply] [OK]; Apply only while there are unsaved edits. */
    override fun footerButtons(): List<FooterButton> = listOf(
        FooterButton("btn.cancel", FooterButton.Role.CANCEL) {
            clearPreviews()
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
        clearPreviews()
        hasPendingChanges = false
    }

    /** Theme, appearance and font size show live while edited (EDT-07, EDT-76); this goes back to the saved ones. */
    private fun clearPreviews() {
        ImGuiStyleManager.preview = null
        ImGuiStyleManager.previewAppearance = null
        ImGuiStyleManager.previewUiScale = null
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
            tempShowTooltips = editor.showTooltips
            tempUiScale = editor.fontSize
            tempAppearance = editor.appearance
            tempLoadLastProject = editor.loadLastProjectOnStartup
            tempSnap = editor.gizmoSnap
            tempCamera = editor.camera
            tempBlenderPath.set(editor.blenderPath.orEmpty())
            tempBlenderTimeout = editor.blenderTimeoutSeconds
            tempBlenderImportOnOpen = editor.blenderImportOnOpen
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

        boolRow("settings.editor.general.load_last_project", tempLoadLastProject, default = true) { tempLoadLastProject = it }
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
        // EDT-07: the themes shipped in src/main/resources/editor/themes; picking one previews it live until OK/Apply or Cancel.
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

        boolRow("settings.editor.interface.show_tooltips", tempShowTooltips, default = true) { tempShowTooltips = it }
        floatRow(
            "settings.editor.interface.ui_scale", tempUiScale, 1f,
            EditorSettings.MIN_UI_SCALE, EditorSettings.MAX_UI_SCALE, "%.2fx",
        ) { tempUiScale = it; ImGuiStyleManager.previewUiScale = it }

        renderAppearance()
    }

    /** Spacing presets, the spacing values and corner rounding over the theme (EDT-76); previewed live. */
    private fun renderAppearance() {
        ImGui.spacing()
        ImGui.separator()
        ImGui.text(stringManager.getString("settings.editor.interface.appearance"))

        val densities = Density.entries
        val labels = densities.map { stringManager.getString(it.labelKey) }.toTypedArray()
        val sel = ImInt(densities.indexOf(tempAppearance.density))
        MImGui.propertyRow(
            label = stringManager.getString("settings.editor.interface.density"),
            helpTooltip = stringManager.getString("settings.editor.interface.density.desc"),
            onReset = { setAppearance(tempAppearance.withDensity(Density.COMFORTABLE)) },
        ) {
            if (combo("##density", sel, labels, labels.size)) setAppearance(tempAppearance.withDensity(densities[sel.get()]))
        }

        val themeStyle = ImGuiStyleManager.library.resolve(tempTheme).style
        val shown = tempAppearance.spacing ?: AppearanceSettings.spacingOf(themeStyle)
        val defaults = Density.COMFORTABLE.spacing!!
        pairRow("settings.editor.interface.window_padding", shown.windowPaddingX, shown.windowPaddingY, defaults.windowPaddingX, defaults.windowPaddingY) { s, x, y ->
            s.copy(windowPaddingX = x, windowPaddingY = y)
        }
        pairRow("settings.editor.interface.frame_padding", shown.framePaddingX, shown.framePaddingY, defaults.framePaddingX, defaults.framePaddingY) { s, x, y ->
            s.copy(framePaddingX = x, framePaddingY = y)
        }
        pairRow("settings.editor.interface.item_spacing", shown.itemSpacingX, shown.itemSpacingY, defaults.itemSpacingX, defaults.itemSpacingY) { s, x, y ->
            s.copy(itemSpacingX = x, itemSpacingY = y)
        }

        val rounding = floatArrayOf(tempAppearance.rounding ?: themeStyle.rounding)
        MImGui.propertyRow(
            label = stringManager.getString("settings.editor.interface.rounding"),
            helpTooltip = stringManager.getString("settings.editor.interface.rounding.desc"),
            onReset = { setAppearance(tempAppearance.copy(rounding = null)) },
        ) {
            if (sliderFloat("##rounding", rounding, AppearanceSettings.MIN_ROUNDING, AppearanceSettings.MAX_ROUNDING, "%.0f px")) {
                setAppearance(tempAppearance.copy(rounding = rounding[0]))
            }
        }
    }

    /** An X/Y spacing row; editing it switches the density to Custom. The reset restores the default preset's values. */
    private fun pairRow(key: String, x: Float, y: Float, defaultX: Float, defaultY: Float, set: (Spacing, Float, Float) -> Spacing) {
        val themeSpacing = AppearanceSettings.spacingOf(ImGuiStyleManager.library.resolve(tempTheme).style)
        MImGui.propertyRow(
            label = stringManager.getString(key),
            helpTooltip = stringManager.getString("$key.desc"),
            onReset = { setAppearance(tempAppearance.withCustom(themeSpacing) { set(it, defaultX, defaultY) }) },
        ) {
            val v = floatArrayOf(x, y)
            if (ImGui.sliderFloat2("##$key", v, Spacing.MIN, Spacing.MAX, "%.0f px")) {
                setAppearance(tempAppearance.withCustom(themeSpacing) { set(it, v[0], v[1]) })
            }
        }
    }

    private fun setAppearance(next: AppearanceSettings) {
        tempAppearance = next.sanitized()
        ImGuiStyleManager.previewAppearance = tempAppearance
        hasPendingChanges = true
    }

    /** Gizmo snap steps and the editor camera (EDT-57). */
    private fun renderSceneView() {
        ImGui.text(stringManager.getString("settings.editor.scene_view.snap"))
        floatRow(
            "settings.editor.scene_view.snap_translate", tempSnap.translate, GizmoSnapSettings.DEFAULT_TRANSLATE,
            GizmoSnapSettings.MIN_STEP, GizmoSnapSettings.MAX_TRANSLATE, "%.3f",
        ) { tempSnap = tempSnap.copy(translate = it) }
        floatRow(
            "settings.editor.scene_view.snap_rotate", tempSnap.rotateDegrees, GizmoSnapSettings.DEFAULT_ROTATE_DEGREES,
            GizmoSnapSettings.MIN_STEP, GizmoSnapSettings.MAX_ROTATE_DEGREES, "%.1f°",
        ) { tempSnap = tempSnap.copy(rotateDegrees = it) }
        floatRow(
            "settings.editor.scene_view.snap_scale", tempSnap.scale, GizmoSnapSettings.DEFAULT_SCALE,
            GizmoSnapSettings.MIN_STEP, GizmoSnapSettings.MAX_SCALE, "%.3f",
        ) { tempSnap = tempSnap.copy(scale = it) }
        floatRow(
            "settings.editor.scene_view.snap_ui", tempSnap.ui, GizmoSnapSettings.DEFAULT_UI,
            GizmoSnapSettings.MIN_UI, GizmoSnapSettings.MAX_UI, "%.0f",
        ) { tempSnap = tempSnap.copy(ui = it) }

        ImGui.spacing()
        ImGui.text(stringManager.getString("settings.editor.scene_view.camera"))
        floatRow(
            "settings.editor.scene_view.camera_speed", tempCamera.moveSpeed, EditorCameraSettings.DEFAULT_MOVE_SPEED,
            EditorCameraSettings.MIN_MOVE_SPEED, EditorCameraSettings.MAX_MOVE_SPEED, "%.1f",
        ) { tempCamera = tempCamera.copy(moveSpeed = it) }
        floatRow(
            "settings.editor.scene_view.look_sensitivity", tempCamera.lookSensitivity, EditorCameraSettings.DEFAULT_LOOK_SENSITIVITY,
            EditorCameraSettings.MIN_LOOK_SENSITIVITY, EditorCameraSettings.MAX_LOOK_SENSITIVITY, "%.2fx",
        ) { tempCamera = tempCamera.copy(lookSensitivity = it) }
    }

    /** A checkbox row with its explanation (`<key>.desc`) and a reset to [default]. */
    private fun boolRow(key: String, value: Boolean, default: Boolean, set: (Boolean) -> Unit) {
        MImGui.propertyRow(
            label = stringManager.getString(key),
            helpTooltip = stringManager.getString("$key.desc"),
            onReset = { set(default); hasPendingChanges = true },
        ) {
            val flag = ImBoolean(value)
            if (checkbox("##$key", flag)) {
                set(flag.get())
                hasPendingChanges = true
            }
        }
    }

    /** A slider row from [min] to [max] with its explanation (`<key>.desc`) and a reset to [default]. */
    private fun floatRow(key: String, value: Float, default: Float, min: Float, max: Float, format: String, set: (Float) -> Unit) {
        MImGui.propertyRow(
            label = stringManager.getString(key),
            helpTooltip = stringManager.getString("$key.desc"),
            onReset = { set(default); hasPendingChanges = true },
        ) {
            val v = floatArrayOf(value)
            if (sliderFloat("##$key", v, min, max, format)) {
                set(v[0].coerceIn(min, max))
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

    /** `.blend` import (AST-19, EDT-66): Blender's path with Browse and Test, the timeout and import on open. */
    private fun renderBlender() {
        MImGui.propertyRow(
            label = stringManager.getString("settings.editor.blender.path"),
            helpTooltip = stringManager.getString("settings.editor.blender.path.desc"),
            onReset = { tempBlenderPath.set(""); blenderTest = null; hasPendingChanges = true },
        ) {
            if (ImGui.inputTextWithHint("##blender_path", stringManager.getString("settings.editor.blender.path.hint"), tempBlenderPath)) {
                blenderTest = null
                hasPendingChanges = true
            }
        }
        if (stringManager.tipped(button(stringManager.label("btn.blender.browse", "${Icons.FOLDER_OPEN} ")), "btn.blender.browse")) browseBlender()
        sameLine()
        ImGui.beginDisabled(blenderTesting)
        if (stringManager.tipped(button(stringManager.label("btn.blender.test", "${Icons.PLAY} ")), "btn.blender.test")) {
            blenderTesting = true
            blenderTest = null
            testBlender(tempBlenderPath.get().trim().ifBlank { null }) { result ->
                blenderTesting = false
                blenderTest = result
            }
        }
        ImGui.endDisabled()
        when {
            blenderTesting -> MImGui.textDisabled(stringManager.getString("settings.editor.blender.testing"))
            else -> blenderTest?.let { result ->
                val (text, ok) = BlenderTestMessage.of(result)
                val message = stringManager.getString(text.first, *text.second.toTypedArray())
                if (ok) MImGui.textDisabled("${Icons.CHECK} $message") else MImGui.warningText("${Icons.INFO} $message")
            }
        }
        ImGui.spacing()

        val timeout = intArrayOf(tempBlenderTimeout)
        MImGui.propertyRow(
            label = stringManager.getString("settings.editor.blender.timeout"),
            helpTooltip = stringManager.getString("settings.editor.blender.timeout.desc"),
            onReset = { tempBlenderTimeout = EditorSettings.DEFAULT_BLENDER_TIMEOUT; hasPendingChanges = true },
        ) {
            if (dragInt("##blender_timeout", timeout, 1f, EditorSettings.MIN_BLENDER_TIMEOUT, EditorSettings.MAX_BLENDER_TIMEOUT, "%d s")) {
                tempBlenderTimeout = timeout[0].coerceIn(EditorSettings.MIN_BLENDER_TIMEOUT, EditorSettings.MAX_BLENDER_TIMEOUT)
                hasPendingChanges = true
            }
        }
        boolRow("settings.editor.blender.import_on_open", tempBlenderImportOnOpen, default = true) { tempBlenderImportOnOpen = it }
    }

    private fun browseBlender() {
        val chooser = javax.swing.JFileChooser(tempBlenderPath.get().takeIf { it.isNotBlank() }?.let { java.io.File(it).parentFile })
        chooser.fileSelectionMode = javax.swing.JFileChooser.FILES_ONLY
        if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
            tempBlenderPath.set(chooser.selectedFile.absolutePath)
            blenderTest = null
            hasPendingChanges = true
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
        settingsManager.update {
            it.copy(
                showTooltips = tempShowTooltips,
                fontSize = tempUiScale,
                appearance = tempAppearance,
                loadLastProjectOnStartup = tempLoadLastProject,
                gizmoSnap = tempSnap,
                camera = tempCamera,
                blenderPath = tempBlenderPath.get().trim().ifBlank { null },
                blenderTimeoutSeconds = tempBlenderTimeout,
                blenderImportOnOpen = tempBlenderImportOnOpen,
            )
        }
        clearPreviews()
        hasPendingChanges = false
    }

    companion object {
        private fun matchesSearch(query: String, vararg terms: String): Boolean {
            if (query.isBlank()) return true
            return terms.any { it.contains(query, ignoreCase = true) }
        }
    }
}

/** The Editor Settings line for a Blender Test result (EDT-66): (string key, format args) and whether it is good news. */
object BlenderTestMessage {
    fun of(result: BlenderTestResult): Pair<Pair<String, List<String>>, Boolean> = when (result) {
        is BlenderTestResult.Found ->
            ("settings.editor.blender.test.found" to listOf(result.install.version.toString(), result.install.executable.path)) to true
        is BlenderTestResult.PathUnusable ->
            ("settings.editor.blender.test.path_unusable" to listOf(result.path, BlenderVersion.MINIMUM.toString(), result.install.executable.path)) to false
        is BlenderTestResult.NotFound ->
            ("settings.editor.blender.test.not_found" to listOf(BlenderVersion.MINIMUM.toString())) to false
    }
}
