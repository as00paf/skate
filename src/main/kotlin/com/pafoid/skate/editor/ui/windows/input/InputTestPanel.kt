package com.pafoid.skate.editor.ui.windows.input

import com.pafoid.skate.editor.events.ConsoleAction
import com.pafoid.skate.editor.imgui.ImGuiIds
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.editor.ui.windows.ActionBindingsModel
import com.pafoid.skate.editor.ui.windows.GamepadLayout
import com.pafoid.skate.editor.ui.windows.InputContextRows
import com.pafoid.skate.editor.ui.windows.InputEventLog
import com.pafoid.skate.editor.ui.windows.InputLogAction
import com.pafoid.skate.editor.ui.windows.InputLogEntry
import com.pafoid.skate.editor.ui.windows.InputTestingModel
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.input.ActionBinding
import com.pafoid.skate.engine.input.ActionMap
import com.pafoid.skate.engine.input.InputContextStack
import com.pafoid.skate.engine.input.InputProvider
import com.pafoid.skate.engine.input.Keys
import imgui.ImGui
import imgui.flag.ImGuiTableFlags
import imgui.flag.ImGuiTreeNodeFlags
import org.lwjgl.glfw.GLFW

/**
 * Key Bindings › Test (EDT-51 §3.1, moved from the Input Testing window): what the editor receives right now (keys,
 * mouse, gamepads), the input context stack, and the event log whose Action column shows the bound actions each
 * press fired. Polls only while drawn.
 */
class InputTestPanel(
    private val strings: StringManager,
    private val events: EventSystem,
    private val input: InputProvider,
    private val actionMap: () -> ActionMap?,
    private val contexts: () -> InputContextStack?,
) {
    private val log = InputEventLog<InputLogEntry>()
    private val previousButtons = HashMap<Int, BooleanArray>()
    private val keysDown = BooleanArray(GLFW.GLFW_KEY_LAST + 1)
    private val mouseDown = BooleanArray(GLFW.GLFW_MOUSE_BUTTON_LAST + 1)

    private fun s(key: String) = strings.getString(key)
    private fun translated(key: String): String? = s(key).takeUnless { it.startsWith("!!") }

    private fun tip(key: String) {
        if (ImGui.isItemHovered()) ImGui.setTooltip(s(key))
    }

    fun render() {
        pollEvents()
        val pads = (GLFW.GLFW_JOYSTICK_1..GLFW.GLFW_JOYSTICK_LAST).filter { input.isJoystickPresent(it) }
        if (ImGui.collapsingHeader(ImGuiIds.stable(s("lbl.input_testing.devices"), "lbl.input_testing.devices"), ImGuiTreeNodeFlags.DefaultOpen)) renderDevices(pads)
        if (ImGui.collapsingHeader(ImGuiIds.stable(s("lbl.input_testing.contexts"), "lbl.input_testing.contexts"))) renderContexts()
        if (ImGui.collapsingHeader(ImGuiIds.stable(s("lbl.input_testing.event_log"), "lbl.input_testing.event_log"), ImGuiTreeNodeFlags.DefaultOpen)) renderLog()
    }

    private fun record(event: String, source: ActionBinding, down: Boolean) {
        // Actions fire on the press; a release only ends them, so it shows no action.
        val actions = if (down) InputTestingModel.actionsFor(actionMap(), contexts(), source) else emptyList()
        log.add(InputLogEntry("$event ${state(down)}", actions))
    }

    private fun pollEvents() {
        for (k in GLFW.GLFW_KEY_SPACE..GLFW.GLFW_KEY_LAST) {
            val down = input.isKeyPressed(k)
            if (down != keysDown[k]) record("${s("lbl.input_testing.keyboard")}: ${keyName(k)}", ActionBinding.Key(k), down)
            keysDown[k] = down
        }
        for (b in mouseDown.indices) {
            val down = input.isMouseButtonDown(b, true)
            if (down != mouseDown[b]) record("${s("lbl.input_testing.mouse")}: ${s("lbl.input_testing.button")} $b", ActionBinding.MouseButton(b), down)
            mouseDown[b] = down
        }
        for (jid in GLFW.GLFW_JOYSTICK_1..GLFW.GLFW_JOYSTICK_LAST) {
            if (!input.isJoystickPresent(jid)) {
                previousButtons.remove(jid)
                continue
            }
            val buttons = input.getButtons(jid)
            val previous = previousButtons[jid]
            buttons?.indices?.forEach { i ->
                val was = previous?.getOrNull(i) ?: false
                if (buttons[i] != was) {
                    record("${s("lbl.input_testing.gamepad")} ${jid + 1}: ${GamepadLayout.buttonName(i)}", ActionBinding.GamepadButton(i), buttons[i])
                }
            }
            previousButtons[jid] = buttons?.copyOf() ?: BooleanArray(0)
        }
    }

    private fun keyName(k: Int) = GLFW.glfwGetKeyName(k, 0) ?: Keys.nameOf(k)

    private fun state(down: Boolean) = s(if (down) "lbl.input_testing.pressed" else "lbl.input_testing.released")

    private fun renderDevices(pads: List<Int>) {
        ImGui.indent()
        ImGui.text(s("lbl.input_testing.keyboard"))
        val down = (GLFW.GLFW_KEY_SPACE..GLFW.GLFW_KEY_LAST).filter { keysDown[it] }.joinToString(", ") { keyName(it) }
        ImGui.textWrapped("  " + s("lbl.input_testing.keys_down").format(down.ifEmpty { "-" }))

        ImGui.text(s("lbl.input_testing.mouse"))
        ImGui.text("  " + s("lbl.input_testing.mouse_pos").format(input.getMouseViewPos().x, input.getMouseViewPos().y))
        ImGui.text("  " + s("lbl.input_testing.mouse_delta").format(input.getMouseDx(), input.getMouseDy()))
        ImGui.text("  " + s("lbl.input_testing.mouse_buttons").format(
            mouseDown.indices.filter { mouseDown[it] }.joinToString(", ").ifEmpty { "-" },
        ))

        ImGui.text(s("lbl.input_testing.gamepads"))
        if (pads.isEmpty()) MImGui.errorText("  " + s("lbl.input_testing.no_gamepad"))
        pads.forEach { jid ->
            MImGui.successText("  ${jid + 1}: ${GLFW.glfwGetJoystickName(jid) ?: s("lbl.input_testing.unknown_device")}")
            ImGui.sameLine()
            if (ImGui.smallButton(s("btn.input_testing.copy_info") + "##pad$jid")) {
                events.publish(
                    ConsoleAction.CopyToClipboard(
                        InputTestingModel.deviceInfo(jid, GLFW.glfwGetJoystickName(jid), input.getAxes(jid), input.getButtons(jid)),
                    ),
                )
            }
            tip("tip.input_testing.copy_info")
        }
        ImGui.unindent()
    }

    /** INP-10: the live input context stack, top first; entries under a modal (blocking "all") one are greyed out. */
    private fun renderContexts() {
        val stack = contexts()
        if (stack == null) {
            ImGui.textDisabled(s("lbl.input_testing.no_input_system"))
            return
        }
        for (row in InputContextRows.of(stack)) {
            val label = ActionBindingsModel.contextLabel(row.context, ::translated)
            val text = s("lbl.input_testing.context_row").format(
                label, row.priority, s("lbl.input_testing.blocking.${row.blocking.name.lowercase()}"), row.owner.ifEmpty { "-" },
            )
            if (row.silenced) ImGui.textDisabled(text) else ImGui.text(text)
        }
    }

    private fun actionName(action: InputLogAction): String {
        val name = actionMap()?.definition(action.actionId)?.let { translated(it.displayKey) } ?: action.actionId
        return if (action.live) name else s("lbl.input_testing.action_inactive").format(name)
    }

    private fun renderLog() {
        ImGui.indent()
        if (ImGui.checkbox(s("lbl.input_testing.pause"), log.paused)) log.paused = !log.paused
        ImGui.sameLine()
        if (strings.tipped(ImGui.button(s("btn.input_testing.clear")), "btn.input_testing.clear")) log.clear()
        ImGui.sameLine()
        if (strings.tipped(ImGui.button(s("btn.input_testing.copy_log")), "btn.input_testing.copy_log")) {
            events.publish(ConsoleAction.CopyToClipboard(log.lines.joinToString("\n") { it.line(::actionName) }))
        }
        val flags = ImGuiTableFlags.RowBg or ImGuiTableFlags.ScrollY or ImGuiTableFlags.BordersInnerV
        if (ImGui.beginTable("##inputLog", 2, flags, 0f, 180f)) {
            ImGui.tableSetupScrollFreeze(0, 1)
            ImGui.tableSetupColumn(s("lbl.input_testing.column.event"))
            ImGui.tableSetupColumn(s("lbl.input_testing.column.action"))
            ImGui.tableHeadersRow()
            log.lines.asReversed().forEach { entry ->
                ImGui.tableNextRow()
                ImGui.tableSetColumnIndex(0)
                ImGui.textUnformatted(entry.event)
                ImGui.tableSetColumnIndex(1)
                if (entry.actions.isEmpty()) ImGui.textDisabled("-")
                entry.actions.forEachIndexed { i, action ->
                    if (i > 0) ImGui.sameLine()
                    val text = actionName(action) + if (i < entry.actions.lastIndex) "," else ""
                    if (action.live) ImGui.textUnformatted(text) else ImGui.textDisabled(text)
                }
            }
            ImGui.endTable()
        }
        ImGui.unindent()
    }
}
