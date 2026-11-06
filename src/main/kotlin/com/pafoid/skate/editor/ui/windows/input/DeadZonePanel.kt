package com.pafoid.skate.editor.ui.windows.input

import com.pafoid.skate.editor.commands.input.snapshot
import com.pafoid.skate.editor.events.InputSettingsAction
import com.pafoid.skate.editor.imgui.ImGuiIds
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.ui.windows.GamepadLayout
import com.pafoid.skate.editor.ui.windows.InputTestingModel
import com.pafoid.skate.editor.ui.windows.StickReadout
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.input.DeadZoneMode
import com.pafoid.skate.engine.input.DeadZoneSettings
import com.pafoid.skate.engine.input.InputProvider
import com.pafoid.skate.engine.input.StickDeadZone
import com.pafoid.skate.engine.input.TriggerDeadZone
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.type.ImInt
import org.lwjgl.glfw.GLFW

/**
 * One dead-zone edit gesture becomes one undoable, saved change (EDT-51 §3.1): [begin] snapshots the settings when a
 * slider is grabbed, [end] publishes [InputSettingsAction.SetDeadZones] (the handler runs the command and saves the
 * project) only if the value was edited. Discrete edits (the mode combo) use [commit] directly. Pure.
 */
class DeadZoneEditSession(private val publish: (InputSettingsAction.SetDeadZones) -> Unit) {
    private var before: DeadZoneSettings? = null

    val editing: Boolean get() = before != null

    fun begin(zones: DeadZoneSettings) {
        if (before == null) before = zones.snapshot()
    }

    fun end(zones: DeadZoneSettings, edited: Boolean, label: String) {
        val start = before ?: return
        before = null
        if (edited) commit(start, zones, label)
    }

    fun commit(before: DeadZoneSettings, zones: DeadZoneSettings, label: String) =
        publish(InputSettingsAction.SetDeadZones(before, zones.snapshot(), label))
}

/**
 * Project Settings › Input (EDT-51 §3.1, moved from the Input Testing window): stick and trigger dead zones, response
 * curve, sensitivity and press threshold, with the live stick and trigger drawings beside the sliders. Edits apply live
 * and are committed as one command per gesture through [DeadZoneEditSession]. Works without a gamepad (values at rest).
 */
class DeadZonePanel(
    private val strings: StringManager,
    private val events: EventSystem,
    private val input: InputProvider,
    private val zones: () -> DeadZoneSettings?,
) {
    private var selectedPad = 0
    private val session = DeadZoneEditSession { events.publish(it) }

    private fun s(key: String) = strings.getString(key)

    private fun tip(key: String) {
        if (ImGui.isItemHovered()) ImGui.setTooltip(s(key))
    }

    fun render() {
        val zones = zones()
        if (zones == null) {
            MImGui.warningText(s("lbl.deadzone.no_project"))
            return
        }
        ImGui.textDisabled(s("lbl.deadzone.save_hint"))

        val pads = (GLFW.GLFW_JOYSTICK_1..GLFW.GLFW_JOYSTICK_LAST).filter { input.isJoystickPresent(it) }
        var axes: FloatArray? = null
        var buttons: BooleanArray? = null
        if (pads.isEmpty()) {
            MImGui.textDisabled(s("lbl.input_testing.no_gamepad"))
        } else {
            selectedPad = selectedPad.coerceIn(0, pads.lastIndex)
            val names = pads.map { "${it + 1}: ${GLFW.glfwGetJoystickName(it) ?: s("lbl.input_testing.unknown_device")}" }.toTypedArray()
            val pick = ImInt(selectedPad)
            if (ImGui.combo(ImGuiIds.stable(s("lbl.input_testing.active_gamepad"), "lbl.input_testing.active_gamepad"), pick, names)) selectedPad = pick.get()
            tip("tip.input_testing.active_gamepad")
            axes = input.getAxes(pads[selectedPad])
            buttons = input.getButtons(pads[selectedPad])
        }

        ImGui.separator()
        stick("left_stick", axes, 0, 1, zones.leftStick, zones)
        ImGui.separator()
        stick("right_stick", axes, 2, 3, zones.rightStick, zones)
        ImGui.separator()
        trigger("left_trigger", axes, 4, zones.leftTrigger, zones)
        trigger("right_trigger", axes, 5, zones.rightTrigger, zones)

        if (axes == null && buttons == null) return
        ImGui.separator()
        ImGui.text(s("lbl.input_testing.layout"))
        tip("tip.input_testing.layout")
        axes?.forEachIndexed { i, v -> ImGui.text(strings.getString("lbl.input_testing.axis_row", GamepadLayout.axisName(i, strings), i, v)) }
        buttons?.forEachIndexed { i, down ->
            ImGui.colorButton("##btn$i", if (down) 0.3f else 0.5f, if (down) 1f else 0.5f, if (down) 0.3f else 0.5f, 1f, 16f, 16f)
            ImGui.sameLine()
            ImGui.text(strings.getString("lbl.input_testing.button_row", GamepadLayout.buttonName(i, strings), i))
        }
    }

    private fun stick(id: String, axes: FloatArray?, xi: Int, yi: Int, zone: StickDeadZone, zones: DeadZoneSettings) {
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

        val modes = arrayOf(s("lbl.deadzone.mode.axial"), s("lbl.deadzone.mode.radial"))
        val mode = ImInt(zone.mode.ordinal)
        if (ImGui.combo("${s("lbl.deadzone.mode")}###$id", mode, modes)) {
            val before = zones.snapshot()
            zone.mode = DeadZoneMode.entries[mode.get()]
            session.commit(before, zones, s("lbl.deadzone.$id"))
        }
        tip("tip.deadzone.mode")
        zone.inner = slider(zones, id, "inner", zone.inner, 0f, 0.9f)
        zone.outer = slider(zones, id, "outer", zone.outer, 0f, 0.5f)
        zone.curve = slider(zones, id, "curve", zone.curve, 0.1f, 4f)
        zone.sensitivity = slider(zones, id, "sensitivity", zone.sensitivity, 0.1f, 5f)
    }

    private fun trigger(id: String, axes: FloatArray?, index: Int, zone: TriggerDeadZone, zones: DeadZoneSettings) {
        ImGui.text(s("lbl.deadzone.$id"))
        val (raw, processed) = InputTestingModel.readTrigger(axes, index, zone)
        ImGui.progressBar(processed.coerceIn(0f, 1f), 120f, 14f, "%.2f".format(processed))
        ImGui.sameLine()
        ImGui.text(s("lbl.input_testing.trigger_values").format(raw, processed) + if (zone.isPressed(raw)) "  [${s("lbl.input_testing.pressed")}]" else "")
        zone.inner = slider(zones, id, "inner", zone.inner, 0f, 0.9f)
        zone.outer = slider(zones, id, "outer", zone.outer, 0f, 0.5f)
        zone.curve = slider(zones, id, "curve", zone.curve, 0.1f, 4f)
        zone.pressThreshold = slider(zones, id, "press", zone.pressThreshold, 0.05f, 1f)
    }

    /** Slider that edits live and commits one command when the drag ends. */
    private fun slider(zones: DeadZoneSettings, id: String, field: String, value: Float, min: Float, max: Float): Float {
        val v = floatArrayOf(value)
        val key = if (field == "press") "lbl.deadzone.press_threshold" else "lbl.deadzone.$field"
        ImGui.sliderFloat("${s(key)}##$id.$field", v, min, max)
        if (ImGui.isItemActivated()) session.begin(zones)
        if (ImGui.isItemDeactivated()) {
            // The value of this frame is part of the edit, so apply it before the snapshot is taken.
            val edited = ImGui.isItemDeactivatedAfterEdit()
            val result = v[0]
            if (edited) assign(zones, id, field, result)
            session.end(zones, edited, s("lbl.deadzone.$id"))
        }
        tip("tip.deadzone.$field")
        return v[0]
    }

    private fun assign(zones: DeadZoneSettings, id: String, field: String, value: Float) {
        when (id) {
            "left_stick", "right_stick" -> {
                val z = if (id == "left_stick") zones.leftStick else zones.rightStick
                when (field) { "inner" -> z.inner = value; "outer" -> z.outer = value; "curve" -> z.curve = value; "sensitivity" -> z.sensitivity = value }
            }
            else -> {
                val z = if (id == "left_trigger") zones.leftTrigger else zones.rightTrigger
                when (field) { "inner" -> z.inner = value; "outer" -> z.outer = value; "curve" -> z.curve = value; "press" -> z.pressThreshold = value }
            }
        }
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
}
