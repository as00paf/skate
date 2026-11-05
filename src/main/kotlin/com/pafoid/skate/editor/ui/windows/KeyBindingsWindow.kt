package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.editor.imgui.label
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
import com.pafoid.skate.editor.imgui.EditorWindow
import com.pafoid.skate.editor.systems.EditorSettingsManager
import com.pafoid.skate.editor.input.BindingProfileStore
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.systems.InputSystem
import com.pafoid.skate.engine.input.ProviderRawInput
import java.io.File
import imgui.flag.ImGuiWindowFlags
import imgui.internal.ImGui.begin
import imgui.internal.ImGui.button
import imgui.internal.ImGui.combo
import imgui.internal.ImGui.end
import imgui.internal.ImGui.isKeyPressed
import imgui.internal.ImGui.sameLine
import imgui.internal.ImGui.separator
import imgui.internal.ImGui.text
import imgui.type.ImInt
import org.lwjgl.glfw.GLFW

class KeyBindingsWindow(
    engine: Engine,
    private val settingsManager: EditorSettingsManager,
    private val stringManager: StringManager,
    private val projectManager: ProjectManager
) : EditorWindow("window.keybindings") {

    private var keyBindingTab = 0  // 0=Game actions, 1=Editor, 2=Camera, 3=Hierarchy, 4=Fixed, 5=Gamepad

    private val actionPanel = ActionBindingsPanel(
        stringManager,
        engine.eventSystem,
        { engine.systemManager.getSystem<InputSystem>()?.actionMap.takeIf { projectManager.hasProject() } },
        ProviderRawInput(engine.inputProvider),
        { projectManager.getProjectDirectory()?.let { BindingProfileStore(File(it, BindingProfileStore.DIRECTORY)) } },
    )

    /** Read live: settings load after start-up and replace the object. */
    private val inputMappings: EditorInputMappings get() = settingsManager.editorSettings.editorInputMappings

    /** The shortcut waiting for a key press, or null. */
    private var capturing: EditorShortcut? = null

    override fun imgui() {
        if (!isOpen.get()) return

        imgui.ImGui.setNextWindowSize(780f, 520f, imgui.flag.ImGuiCond.FirstUseEver)
        if (begin(stringManager.label("window.keybindings"), ImGuiWindowFlags.None)) {
            val tabNames = arrayOf(
                stringManager.getString("lbl.keybindings.tab_actions"),
                stringManager.getString(ShortcutGroup.EDITOR.labelKey),
                stringManager.getString(ShortcutGroup.CAMERA.labelKey),
                stringManager.getString(ShortcutGroup.HIERARCHY.labelKey),
                stringManager.getString("tab.keybindings.fixed"),
                stringManager.getString("tab.keybindings.gamepad"),
            )
            val tabSelector = ImInt(keyBindingTab)
            if (combo("##TabSelector", tabSelector, tabNames, tabNames.size)) {
                keyBindingTab = tabSelector.get()
                capturing = null
            }

            separator()

            when (keyBindingTab) {
                0 -> actionPanel.render()
                1 -> renderGroup(ShortcutGroup.EDITOR)
                2 -> {
                    renderGroup(ShortcutGroup.CAMERA)
                    MImGui.textDisabled(stringManager.getString("lbl.keybindings.camera_look_note"))
                }
                3 -> renderGroup(ShortcutGroup.HIERARCHY)
                4 -> renderFixed()
                5 -> text(stringManager.getString("lbl.keybindings.gamepad_moved"))
            }

            separator()
            if (button(stringManager.getString("btn.close"))) {
                isOpen.set(false)
                capturing = null
            }
            if (keyBindingTab in 1..3) {
                sameLine()
                if (stringManager.tipped(button(stringManager.getString("btn.reset_to_defaults")), "btn.reset_to_defaults")) {
                    val mappings = inputMappings
                    mappings.resetToDefault()
                    settingsManager.updateInputMappings(mappings)
                    capturing = null
                }
            }

            capture()
        }
        end()
    }

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
}
