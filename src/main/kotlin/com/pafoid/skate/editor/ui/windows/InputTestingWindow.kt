package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.commands.input.snapshot
import com.pafoid.skate.editor.events.ConsoleAction
import com.pafoid.skate.editor.events.InputSettingsAction
import com.pafoid.skate.editor.imgui.EditorDialog
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.input.DeadZoneMode
import com.pafoid.skate.engine.input.DeadZoneSettings
import com.pafoid.skate.engine.input.StickDeadZone
import com.pafoid.skate.engine.input.TriggerDeadZone
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.type.ImInt
import org.lwjgl.glfw.GLFW

/**
 * Single place to inspect input devices and tune dead zones, response curves, sensitivity and trigger thresholds.
 * Edits mutate the project's live settings (so they apply in-game immediately) and are committed as one undoable
 * command per edit through [InputSettingsAction.SetDeadZones].
 */
class InputTestingWindow(
    private val engine: Engine,
    private val projectManager: ProjectManager,
) : EditorDialog("window.input_testing", engine.stringManager) {
    private val strings = engine.stringManager
    private val input = engine.inputProvider
    private val events = engine.eventSystem

    private val log = InputEventLog()
    private var selectedPad = 0
    private val previousButtons = HashMap<Int, BooleanArray>()
    private val keysDown = BooleanArray(GLFW.GLFW_KEY_LAST + 1)
    private val mouseDown = BooleanArray(GLFW.GLFW_MOUSE_BUTTON_LAST + 1)

    private var editBefore: DeadZoneSettings? = null

    private fun s(key: String) = strings.getString(key)

    private fun tip(key: String) {
        if (ImGui.isItemHovered()) ImGui.setTooltip(s(key))
    }

    override fun content() {
        pollEvents()

        val zones = projectManager.currentProject?.gameplaySettings?.inputMappings?.deadZones
        val pads = (GLFW.GLFW_JOYSTICK_1..GLFW.GLFW_JOYSTICK_LAST).filter { input.isJoystickPresent(it) }

        if (ImGui.collapsingHeader(s("lbl.input_testing.devices"))) renderDevices(pads)
        if (ImGui.collapsingHeader(s("lbl.input_testing.gamepad_tuning"))) renderGamepad(pads, zones)
        if (ImGui.collapsingHeader(s("lbl.input_testing.event_log"))) renderLog()
    }

    private fun pollEvents() {
        for (k in GLFW.GLFW_KEY_SPACE..GLFW.GLFW_KEY_LAST) {
            val down = input.isKeyPressed(k)
            if (down != keysDown[k]) log.add("${s("lbl.input_testing.keyboard")}: ${GLFW.glfwGetKeyName(k, 0) ?: "Key $k"} ${state(down)}")
            keysDown[k] = down
        }
        for (b in mouseDown.indices) {
            val down = input.isMouseButtonDown(b, true)
            if (down != mouseDown[b]) log.add("${s("lbl.input_testing.mouse")}: ${s("lbl.input_testing.button")} $b ${state(down)}")
            mouseDown[b] = down
        }
        for (jid in GLFW.GLFW_JOYSTICK_1..GLFW.GLFW_JOYSTICK_LAST) {
            if (!input.isJoystickPresent(jid)) {
                previousButtons.remove(jid)
                continue
            }
            val buttons = input.getButtons(jid)
            InputTestingModel.buttonChanges(previousButtons[jid], buttons).forEach { (name, down) ->
                log.add("${s("lbl.input_testing.gamepad")} ${jid + 1}: $name ${state(down)}")
            }
            previousButtons[jid] = buttons?.copyOf() ?: BooleanArray(0)
        }
    }

    private fun state(down: Boolean) = s(if (down) "lbl.input_testing.pressed" else "lbl.input_testing.released")

    private fun renderDevices(pads: List<Int>) {
        ImGui.indent()
        ImGui.text(s("lbl.input_testing.keyboard"))
        val down = (GLFW.GLFW_KEY_SPACE..GLFW.GLFW_KEY_LAST).filter { keysDown[it] }
            .joinToString(", ") { GLFW.glfwGetKeyName(it, 0) ?: "#$it" }
        ImGui.textWrapped("  " + s("lbl.input_testing.keys_down").format(down.ifEmpty { "-" }))

        ImGui.text(s("lbl.input_testing.mouse"))
        ImGui.text("  " + s("lbl.input_testing.mouse_pos").format(input.getMouseScreenX(), input.getMouseScreenY()))
        ImGui.text("  " + s("lbl.input_testing.mouse_delta").format(input.getMouseDx(), input.getMouseDy()))
        ImGui.text("  " + s("lbl.input_testing.mouse_buttons").format(
            mouseDown.indices.filter { mouseDown[it] }.joinToString(", ").ifEmpty { "-" },
        ))

        ImGui.text(s("lbl.input_testing.gamepads"))
        if (pads.isEmpty()) MImGui.errorText("  " + s("lbl.input_testing.no_gamepad"))
        pads.forEach { jid ->
            MImGui.successText("  ${jid + 1}: ${GLFW.glfwGetJoystickName(jid) ?: s("lbl.input_testing.unknown_device")}")
        }
        if (pads.isNotEmpty() && ImGui.button(s("btn.input_testing.copy_info"))) {
            val jid = pads.getOrElse(selectedPad) { pads.first() }
            events.publish(
                ConsoleAction.CopyToClipboard(
                    InputTestingModel.deviceInfo(jid, GLFW.glfwGetJoystickName(jid), input.getAxes(jid), input.getButtons(jid)),
                ),
            )
        }
        if (pads.isNotEmpty()) tip("tip.input_testing.copy_info")
        ImGui.unindent()
    }

    private fun renderGamepad(pads: List<Int>, zones: DeadZoneSettings?) {
        ImGui.indent()
        if (pads.isEmpty()) {
            MImGui.errorText(s("lbl.input_testing.no_gamepad"))
            ImGui.unindent()
            return
        }
        selectedPad = selectedPad.coerceIn(0, pads.lastIndex)
        val names = pads.map { "${it + 1}: ${GLFW.glfwGetJoystickName(it) ?: s("lbl.input_testing.unknown_device")}" }.toTypedArray()
        val pick = ImInt(selectedPad)
        if (ImGui.combo(s("lbl.input_testing.active_gamepad"), pick, names)) selectedPad = pick.get()
        tip("tip.input_testing.active_gamepad")
        val jid = pads[selectedPad]
        val axes = input.getAxes(jid)
        val buttons = input.getButtons(jid)

        if (zones == null) MImGui.warningText(s("lbl.deadzone.no_project")) else ImGui.textDisabled(s("lbl.deadzone.save_hint"))
        val left = zones?.leftStick ?: StickDeadZone(inner = 0.15f)
        val right = zones?.rightStick ?: StickDeadZone(inner = 0.1f)
        val lt = zones?.leftTrigger ?: TriggerDeadZone()
        val rt = zones?.rightTrigger ?: TriggerDeadZone()

        ImGui.separator()
        stick("left_stick", axes, 0, 1, left, zones)
        ImGui.separator()
        stick("right_stick", axes, 2, 3, right, zones)
        ImGui.separator()
        trigger("left_trigger", axes, 4, lt, zones)
        trigger("right_trigger", axes, 5, rt, zones)

        ImGui.separator()
        ImGui.text(s("lbl.input_testing.layout"))
        tip("tip.input_testing.layout")
        axes?.forEachIndexed { i, v -> ImGui.text("  ${GamepadLayout.axisName(i)} (axis $i): %.3f".format(v)) }
        buttons?.forEachIndexed { i, down ->
            ImGui.colorButton("##btn$i", if (down) 0.3f else 0.5f, if (down) 1f else 0.5f, if (down) 0.3f else 0.5f, 1f, 16f, 16f)
            ImGui.sameLine()
            ImGui.text("${GamepadLayout.buttonName(i)} (button $i)")
        }
        ImGui.unindent()
    }

    private fun stick(id: String, axes: FloatArray?, xi: Int, yi: Int, zone: StickDeadZone, zones: DeadZoneSettings?) {
        ImGui.text(s("lbl.deadzone.$id"))
        val r = InputTestingModel.readStick(axes, xi, yi, zone)
        drawStick(r, zone)
        ImGui.sameLine()
        ImGui.beginGroup()
        ImGui.text(s("lbl.input_testing.raw_values").format(r.rawX, r.rawY))
        ImGui.text(s("lbl.input_testing.processed_values").format(r.processed.x, r.processed.y))
        if (r.rawMagnitude < zone.inner) MImGui.textDisabled(s("lbl.input_testing.within_deadzone"))
        else MImGui.successText(s("lbl.input_testing.active"))
        ImGui.endGroup()
        if (zones == null) return

        val modes = arrayOf(s("lbl.deadzone.mode.axial"), s("lbl.deadzone.mode.radial"))
        val mode = ImInt(zone.mode.ordinal)
        if (ImGui.combo("${s("lbl.deadzone.mode")}##$id", mode, modes)) {
            val before = zones.snapshot()
            zone.mode = DeadZoneMode.entries[mode.get()]
            commit(before, zones, s("lbl.deadzone.$id"))
        }
        tip("tip.deadzone.mode")
        zone.inner = slider(zones, id, "inner", zone.inner, 0f, 0.9f, s("lbl.deadzone.$id"))
        zone.outer = slider(zones, id, "outer", zone.outer, 0f, 0.5f, s("lbl.deadzone.$id"))
        zone.curve = slider(zones, id, "curve", zone.curve, 0.1f, 4f, s("lbl.deadzone.$id"))
        zone.sensitivity = slider(zones, id, "sensitivity", zone.sensitivity, 0.1f, 5f, s("lbl.deadzone.$id"))
    }

    private fun trigger(id: String, axes: FloatArray?, index: Int, zone: TriggerDeadZone, zones: DeadZoneSettings?) {
        ImGui.text(s("lbl.deadzone.$id"))
        val (raw, processed) = InputTestingModel.readTrigger(axes, index, zone)
        ImGui.progressBar(processed.coerceIn(0f, 1f), 120f, 14f, "%.2f".format(processed))
        ImGui.sameLine()
        ImGui.text(s("lbl.input_testing.trigger_values").format(raw, processed) + if (zone.isPressed(raw)) "  [${s("lbl.input_testing.pressed")}]" else "")
        if (zones == null) return
        zone.inner = slider(zones, id, "inner", zone.inner, 0f, 0.9f, s("lbl.deadzone.$id"))
        zone.outer = slider(zones, id, "outer", zone.outer, 0f, 0.5f, s("lbl.deadzone.$id"))
        zone.curve = slider(zones, id, "curve", zone.curve, 0.1f, 4f, s("lbl.deadzone.$id"))
        zone.pressThreshold = slider(zones, id, "press", zone.pressThreshold, 0.05f, 1f, s("lbl.deadzone.$id"))
    }

    /** Slider that edits live and commits one command when the drag ends. */
    private fun slider(zones: DeadZoneSettings, id: String, field: String, value: Float, min: Float, max: Float, label: String): Float {
        val v = floatArrayOf(value)
        val key = if (field == "press") "lbl.deadzone.press_threshold" else "lbl.deadzone.$field"
        ImGui.sliderFloat("${s(key)}##$id.$field", v, min, max)
        if (ImGui.isItemActivated() && editBefore == null) editBefore = zones.snapshot()
        if (ImGui.isItemDeactivated()) {
            val before = editBefore
            editBefore = null
            if (before != null && ImGui.isItemDeactivatedAfterEdit()) commit(before, zones, label)
        }
        tip("tip.deadzone.$field")
        return v[0]
    }

    private fun commit(before: DeadZoneSettings, zones: DeadZoneSettings, label: String) {
        events.publish(InputSettingsAction.SetDeadZones(before, zones.snapshot(), label))
    }

    private fun drawStick(r: StickReadout, zone: StickDeadZone) {
        val size = 100f
        val c = size / 2
        val pos = ImGui.getCursorScreenPos()
        val dl = ImGui.getWindowDrawList()
        val cx = pos.x + c
        val cy = pos.y + c
        dl.addRectFilled(pos.x, pos.y, pos.x + size, pos.y + size, ImGui.getColorU32(ImGuiCol.FrameBg))
        val grey = ImGui.getColorU32(0.5f, 0.5f, 0.5f, 1f)
        val inner = zone.inner * c
        if (zone.mode == DeadZoneMode.AXIAL) dl.addRect(cx - inner, cy - inner, cx + inner, cy + inner, grey)
        else dl.addCircle(cx, cy, inner, grey, 24)
        val outer = (1f - zone.outer) * c
        if (zone.mode == DeadZoneMode.AXIAL) dl.addRect(cx - outer, cy - outer, cx + outer, cy + outer, grey)
        else dl.addCircle(cx, cy, outer, grey, 24)
        // raw (orange) and processed (green); screen Y grows downward, raw Y up is negative already
        dl.addCircleFilled(cx + r.rawX * c, cy + r.rawY * c, 4f, ImGui.getColorU32(1f, 0.5f, 0f, 1f))
        dl.addCircleFilled(cx + r.processed.x * c, cy - r.processed.y * c, 3f, ImGui.getColorU32(0.3f, 1f, 0.3f, 1f))
        dl.addRect(pos.x, pos.y, pos.x + size, pos.y + size, ImGui.getColorU32(ImGuiCol.Border))
        ImGui.dummy(size, size)
        tip("tip.input_testing.stick_visual")
    }

    private fun renderLog() {
        ImGui.indent()
        if (ImGui.checkbox(s("lbl.input_testing.pause"), log.paused)) log.paused = !log.paused
        ImGui.sameLine()
        if (ImGui.button(s("btn.input_testing.clear"))) log.clear()
        ImGui.sameLine()
        if (ImGui.button(s("btn.input_testing.copy_log"))) events.publish(ConsoleAction.CopyToClipboard(log.lines.joinToString("\n")))
        ImGui.beginChild("##inputLog", 0f, 150f, true)
        log.lines.asReversed().forEach { ImGui.textUnformatted(it) }
        ImGui.endChild()
        ImGui.unindent()
    }
}
