package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.data.EditorInputMappings
import com.pafoid.skate.editor.data.EditorShortcut
import com.pafoid.skate.editor.data.EditorShortcuts
import com.pafoid.skate.editor.data.KeyNames
import com.pafoid.skate.editor.data.ShortcutGroup
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.imgui.tippedWith
import imgui.flag.ImGuiTableColumnFlags
import imgui.flag.ImGuiTableFlags
import imgui.internal.ImGui.beginTable
import imgui.internal.ImGui.endTable
import imgui.internal.ImGui.tableHeadersRow
import imgui.internal.ImGui.tableNextRow
import imgui.internal.ImGui.tableSetColumnIndex
import imgui.internal.ImGui.tableSetupColumn
import com.pafoid.skate.editor.imgui.EditorDialog
import com.pafoid.skate.editor.imgui.FooterButton
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.EditorSettingsManager
import com.pafoid.skate.editor.input.BindingProfileStore
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.ui.windows.input.InputTestPanel
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.systems.InputSystem
import com.pafoid.skate.engine.input.ProviderRawInput
import java.io.File
import imgui.internal.ImGui.combo
import imgui.internal.ImGui.isKeyPressed
import imgui.internal.ImGui.separator
import imgui.internal.ImGui.text
import imgui.internal.ImGui.button
import imgui.type.ImInt
import org.lwjgl.glfw.GLFW

class KeyBindingsWindow(
    engine: Engine,
    private val settingsManager: EditorSettingsManager,
    private val stringManager: StringManager,
    private val projectManager: ProjectManager
) : EditorDialog("window.keybindings", stringManager, 780f, 520f) {

    override val icon: String = Icons.KEYBOARD
    override val relatedWindows = listOf("window.editor_settings", "window.project_settings")

    private var keyBindingTab = TAB_ACTIONS  // 0=Game actions, 1=Editor, 2=Camera, 3=Hierarchy, 4=Fixed, 5=Gamepad, 6=Test

    /** Esc pressed this frame to cancel a key capture: it must not also close the dialog through the footer. */
    private var escapeUsedByCapture = false

    private val actionPanel = ActionBindingsPanel(
        stringManager,
        engine.eventSystem,
        { engine.systemManager.getSystem<InputSystem>()?.actionMap.takeIf { projectManager.hasProject() } },
        ProviderRawInput(engine.inputProvider),
        { projectManager.getProjectDirectory()?.let { BindingProfileStore(File(it, BindingProfileStore.DIRECTORY)) } },
    )

    /** Key Bindings › Test: devices, contexts and the event log with its Action column (EDT-52I). */
    private val testPanel = InputTestPanel(
        stringManager,
        engine.eventSystem,
        engine.inputProvider,
        { engine.systemManager.getSystem<InputSystem>()?.actionMap },
        { engine.systemManager.getSystem<InputSystem>()?.contexts },
    )

    /** Read live: settings load after start-up and replace the object. */
    private val inputMappings: EditorInputMappings get() = settingsManager.editorSettings.editorInputMappings

    /** The shortcut waiting for a key press, or null. */
    private var capturing: EditorShortcut? = null

    /** Opens the dialog on the Test tab (Tools › Test Input…, EDT-51 §3.1). */
    fun openTestTab() {
        keyBindingTab = TAB_TEST
        capturing = null
        open()
    }

    /**
     * Standard footer (EDT-51 §2.6): [Reset to defaults] on the rebindable editor tabs, then [Close]. Close does not
     * close on the Esc that cancelled a key capture.
     */
    override fun footerButtons(): List<FooterButton> = footer(
        tab = keyBindingTab,
        escapeUsedByCapture = escapeUsedByCapture,
        onResetToDefaults = {
            val mappings = inputMappings
            mappings.resetToDefault()
            settingsManager.updateInputMappings(mappings)
            capturing = null
        },
        onClose = { capturing = null },
    )

    override fun content() {
        escapeUsedByCapture = isKeyPressed(GLFW.GLFW_KEY_ESCAPE) && (capturing != null || actionPanel.isCapturing)

        val tabNames = arrayOf(
            stringManager.getString("lbl.keybindings.tab_actions"),
            stringManager.getString(ShortcutGroup.EDITOR.labelKey),
            stringManager.getString(ShortcutGroup.CAMERA.labelKey),
            stringManager.getString(ShortcutGroup.HIERARCHY.labelKey),
            stringManager.getString("tab.keybindings.fixed"),
            stringManager.getString("tab.keybindings.gamepad"),
            stringManager.getString("tab.keybindings.test"),
        )
        val tabSelector = ImInt(keyBindingTab)
        if (combo("##TabSelector", tabSelector, tabNames, tabNames.size)) {
            keyBindingTab = tabSelector.get()
            capturing = null
        }

        separator()

        when (keyBindingTab) {
            TAB_ACTIONS -> actionPanel.render()
            TAB_EDITOR -> renderGroup(ShortcutGroup.EDITOR)
            TAB_CAMERA -> {
                renderGroup(ShortcutGroup.CAMERA)
                MImGui.textDisabled(stringManager.getString("lbl.keybindings.camera_look_note"))
            }
            TAB_HIERARCHY -> renderGroup(ShortcutGroup.HIERARCHY)
            TAB_FIXED -> renderFixed()
            TAB_GAMEPAD -> text(stringManager.getString("lbl.keybindings.gamepad_moved"))
            TAB_TEST -> renderTest()
        }

        capture()
    }

    /** Key Bindings › Test (EDT-51 §3.1): live devices and the event log with the action each event fired. */
    private fun renderTest() = testPanel.render()

    override fun onClosed() {
        capturing = null
    }

    /** Takes the next key for [capturing]; Esc cancels, modifier keys alone are ignored. */
    private fun capture() {
        val shortcut = capturing ?: return
        val binding = shortcut.binding ?: return
        if (isKeyPressed(GLFW.GLFW_KEY_ESCAPE)) {
            capturing = null
            return
        }
        for (key in GLFW.GLFW_KEY_SPACE..GLFW.GLFW_KEY_LAST) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key in KeyNames.modifiers || !isKeyPressed(key)) continue
            val mappings = inputMappings
            binding.set(mappings, binding.get(mappings).copy(keyboardKey = key))
            settingsManager.updateInputMappings(mappings)
            capturing = null
            return
        }
    }

    /** Rebindable shortcuts of [group]: label, key button, and a warning when another shortcut uses the same combo. */
    private fun renderGroup(group: ShortcutGroup) {
        val mappings = inputMappings
        val conflicts = EditorShortcuts.conflicts(mappings)
        if (!beginTable("##bindings_${group.name}", 3, ImGuiTableFlags.RowBg or ImGuiTableFlags.SizingStretchProp)) return
        tableSetupColumn(stringManager.getString("lbl.keybindings.column.action"))
        tableSetupColumn(stringManager.getString("lbl.keybindings.column.key"), ImGuiTableColumnFlags.WidthFixed, 160f)
        tableSetupColumn("", ImGuiTableColumnFlags.WidthStretch)
        tableHeadersRow()
        EditorShortcuts.rebindable.filter { it.group == group }.forEach { shortcut ->
            tableNextRow()
            tableSetColumnIndex(0)
            text(stringManager.getString(shortcut.labelKey))
            tableSetColumnIndex(1)
            val label = if (capturing === shortcut) stringManager.getString("lbl.keybindings.press_key") else shortcut.display(mappings)
            if (stringManager.tippedWith(button("$label###bind_${shortcut.id}", 150f, 0f), "lbl.keybindings.rebind.tooltip")) {
                capturing = shortcut
            }
            tableSetColumnIndex(2)
            val others = conflicts.mapNotNull { (a, b) -> if (a === shortcut) b else if (b === shortcut) a else null }
            if (others.isNotEmpty()) {
                MImGui.errorText(stringManager.getString("lbl.keybindings.conflict", others.joinToString { stringManager.getString(it.labelKey) }))
            }
        }
        endTable()
    }

    /** Standard shortcuts that cannot be rebound. */
    private fun renderFixed() {
        MImGui.textDisabled(stringManager.getString("lbl.keybindings.fixed_note"))
        if (!beginTable("##bindings_fixed", 2, ImGuiTableFlags.RowBg)) return
        tableSetupColumn(stringManager.getString("lbl.keybindings.column.action"))
        tableSetupColumn(stringManager.getString("lbl.keybindings.column.key"), ImGuiTableColumnFlags.WidthFixed, 160f)
        tableHeadersRow()
        EditorShortcuts.fixedShortcuts.forEach { shortcut ->
            tableNextRow()
            tableSetColumnIndex(0)
            text(stringManager.getString(shortcut.labelKey))
            tableSetColumnIndex(1)
            text(shortcut.display())
        }
        endTable()
    }

    companion object {
        const val TAB_ACTIONS = 0
        const val TAB_EDITOR = 1
        const val TAB_CAMERA = 2
        const val TAB_HIERARCHY = 3
        const val TAB_FIXED = 4
        const val TAB_GAMEPAD = 5
        const val TAB_TEST = 6

        /**
         * Window target that opens this dialog on its Test tab ([openTestTab]): publish `WindowAction.Show(TEST_TAB)`.
         * Used by Search Anywhere's Test Input command and the Shift+I shortcut (EDT-51 §3.1).
         */
        const val TEST_TAB = "window.keybindings#test"

        /** The footer for [tab]: Reset to defaults only on the rebindable editor tabs; Close ignores a capture's Esc. */
        internal fun footer(
            tab: Int,
            escapeUsedByCapture: Boolean,
            onResetToDefaults: () -> Unit = {},
            onClose: () -> Unit = {},
        ): List<FooterButton> = buildList {
            if (tab in TAB_EDITOR..TAB_HIERARCHY) {
                add(FooterButton("btn.reset_to_defaults", FooterButton.Role.SECONDARY, closes = false, onClick = onResetToDefaults))
            }
            add(FooterButton("btn.close", FooterButton.Role.CANCEL, closes = !escapeUsedByCapture, onClick = onClose))
        }
    }
}
