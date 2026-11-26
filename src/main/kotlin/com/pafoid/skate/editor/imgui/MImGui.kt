package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.imgui.data.Color
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.editor.imgui.components.FieldRange
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiKey
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiTableColumnFlags
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImInt
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import java.io.File
import kotlin.math.max

object MImGui {
    /** Confirmation message wrap width, in font heights (keeps the dialog readable at any DPI). */

    /** Editor strings for the few helpers that show their own text; set by [ImGuiLayer] at startup. */
    var strings: com.pafoid.skate.engine.core.StringManager? = null

    private fun tr(key: String, fallback: String): String = strings?.getString(key) ?: fallback


    /** Kept for callers outside the inspectors; rows now size their label column with [InspectorLayout] (EDT-100). */
    const val SENSIBILITY_SCALE = 0.005f
    const val SENSIBILITY_ROTATION = 0.1f
    private const val SENSIBILITY = 0.01f

    /**
     * A labelled value row (EDT-100): label and [control] side by side, the label column sized by [InspectorLayout]
     * from the width the row has, or stacked (label above, [control] full width) when the window is too narrow.
     * [control] gets the full width of its side (`pushItemWidth(-1)`). A label longer than its column is shortened with
     * "..." and shows in full as a tooltip. No table: the last item after the row is still [control]'s, so callers can
     * check `isItemDeactivatedAfterEdit` or attach a tooltip after it. [labelExtra] draws right after the label.
     */
    fun row(label: String, id: String = label, labelExtra: () -> Unit = {}, control: () -> Unit) {
        ImGui.pushID(id)
        val startX = ImGui.getCursorPosX()
        val layout = InspectorLayout.row(ImGui.getContentRegionAvailX(), ImGui.getFontSize().toFloat())
        if (layout.stacked) {
            ImGui.pushTextWrapPos(0f)
            ImGui.textUnformatted(label)
            ImGui.popTextWrapPos()
            labelExtra()
        } else {
            ImGui.alignTextToFramePadding()
            val shown = InspectorLayout.fit(label, layout.labelWidth - ImGui.getStyle().itemSpacingX) { ImGui.calcTextSize(it).x }
            ImGui.textUnformatted(shown)
            if (shown != label) Tooltips.always(label)
            labelExtra()
            ImGui.sameLine(startX + layout.labelWidth)
        }
        ImGui.pushItemWidth(-1f)
        control()
        ImGui.popItemWidth()
        report(id)
        ImGui.popID()
    }

    /**
     * `sameLine()` when an item [width] pixels wide still fits on the current line, else nothing (the item goes on the
     * next line). For rows of buttons in a window that can be narrow (EDT-100).
     */
    fun sameLineIfFits(width: Float) {
        val spacing = ImGui.getStyle().itemSpacingX
        val right = ImGui.getWindowPosX() + ImGui.getWindowContentRegionMaxX()
        if (ImGui.getItemRectMaxX() + spacing + width <= right) ImGui.sameLine()
    }

    /**
     * A button no wider than the space left on its line (EDT-100): a label too long for a narrow window is cut inside
     * the button instead of pushing it past the edge. Wrap it in `tipped` as usual so the full text stays readable.
     */
    fun fitButton(label: String): Boolean {
        val avail = ImGui.getContentRegionAvailX()
        return if (buttonWidth(label) > avail) ImGui.button(label, avail.coerceAtLeast(ImGui.getFrameHeight()), 0f) else ImGui.button(label)
    }

    /**
     * A framed tree node whose [label] never widens the window (EDT-100): ImGui sizes a tree node by its label, so a
     * long one made a narrow inspector wider than itself. The node is drawn with a hidden label and the text painted
     * into its frame, shortened with "..." (full text as a tooltip). Call `treePop()` when it returns true.
     */
    fun treeHeader(label: String, id: String, flags: Int = 0): Boolean {
        val open = ImGui.treeNodeEx("##$id", flags or imgui.flag.ImGuiTreeNodeFlags.Framed)
        val style = ImGui.getStyle()
        val textX = ImGui.getItemRectMinX() + ImGui.getFontSize() + style.framePaddingX * 2f
        val right = ImGui.getItemRectMaxX() - style.framePaddingX
        val shown = InspectorLayout.fit(label, right - textX) { ImGui.calcTextSize(it).x }
        ImGui.getWindowDrawList().addText(textX, ImGui.getItemRectMinY() + style.framePaddingY, ImGui.getColorU32(ImGuiCol.Text), shown)
        if (shown != label) Tooltips.always(label)
        return open
    }

    /** The width of a button labelled [label] (its `##id` part hidden). */
    fun buttonWidth(label: String): Float = ImGui.calcTextSize(label, true).x + ImGui.getStyle().framePaddingX * 2f

    /** Text that wraps at the window's edge (EDT-100), for hints and read-outs in narrow windows. */
    fun wrappedText(text: String) {
        ImGui.pushTextWrapPos(0f)
        ImGui.textUnformatted(text)
        ImGui.popTextWrapPos()
    }

    /** Reports the last item to [InspectorLayout.probe] with the area it had (the guard test's hook). */
    private fun report(id: String) {
        val probe = InspectorLayout.probe ?: return
        val clip = ImGui.getWindowDrawList()
        probe(InspectorLayout.Field(id, ImGui.getItemRectMinX(), ImGui.getItemRectMaxX(), clip.clipRectMinX,
            ImGui.getWindowPosX() + ImGui.getWindowContentRegionMaxX()))
    }

    /**
     * [count] axis fields (X, Y, Z, W): a coloured reset button and a number field each, on one line when they fit,
     * else one per line ([InspectorLayout.axes]). [set] gets each changed value; returns true when one changed.
     */
    private fun axisFields(count: Int, get: (Int) -> Float, set: (Int, Float) -> Unit, reset: (Int) -> Unit, sens: Float): Boolean {
        val height = ImGui.getFrameHeight()
        val button = height + 3f
        val spacing = ImGui.getStyle().itemInnerSpacingX
        val layout = InspectorLayout.axes(ImGui.getContentRegionAvailX(), count, button, spacing, ImGui.getFontSize().toFloat())
        var changed = false
        for (i in 0 until count) {
            if (i > 0 && layout.oneLine) ImGui.sameLine(0f, spacing)
            pushAxisColors(i)
            if (ImGui.button(AXES[i], button, height)) {
                reset(i)
                changed = true
            }
            ImGui.popStyleColor(3)
            ImGui.sameLine(0f, 0f)
            ImGui.setNextItemWidth(layout.fieldWidth)
            val v = floatArrayOf(get(i))
            if (ImGui.dragFloat("##${AXES[i]}", v, sens)) {
                set(i, v[0])
                changed = true
            }
            report("axis_${AXES[i]}")
        }
        return changed
    }

    private val AXES = arrayOf("X", "Y", "Z", "W")

    /** A label and a [Vector2f] with per-axis reset buttons. */
    fun drawVec2Control(label: String, values: Vector2f, resetValue: Float = 0f, sens: Float = SENSIBILITY) {
        row(label) {
            axisFields(2, { values[it] }, { i, v -> values.setComponent(i, v) }, { values.setComponent(it, resetValue) }, sens)
        }
    }

    /** A label and a [Vector3f] with per-axis reset buttons. */
    fun drawVec3Control(label: String, values: Vector3f, resetValue: Float = 0f, sens: Float = SENSIBILITY) {
        row(label) {
            axisFields(3, { values[it] }, { i, v -> values.setComponent(i, v) }, { values.setComponent(it, resetValue) }, sens)
        }
    }

    private var uniformScaling: Boolean = true

    /**
     * A Transform vector (translation, rotation or scale). For [scale], the Uniform toggle next to the label makes
     * every axis follow the one being edited or reset.
     */
    fun drawVec3TransformControl(
        label: String,
        values: Vector3f,
        resetValue: Float = 0f,
        sens: Float = SENSIBILITY,
        scale: Boolean = false
    ) {
        val uniform = { uniformScaling && scale }
        row(label, labelExtra = {
            if (scale) {
                // EDT-100: a small link toggle that fits the label column; its name is the tooltip.
                ImGui.sameLine()
                val icon = if (uniformScaling) Icons.LINK else Icons.UNLOCK
                if (ImGui.smallButton("$icon###uniform_scaling")) uniformScaling = !uniformScaling
                Tooltips.always(tr("lbl.transform.uniform", "Uniform"))
            }
        }) {
            axisFields(
                3,
                get = { values[it] },
                set = { i, v ->
                    if (uniform()) {
                        val diff = v - values[i]
                        for (a in 0..2) values.setComponent(a, if (a == i) v else values[a] + diff)
                    } else values.setComponent(i, v)
                },
                reset = { i -> if (uniform()) values.set(resetValue) else values.setComponent(i, resetValue) },
                sens = sens,
            )
        }
    }

    fun sliderFloat(value: Float, label: String, min: Float = 0f, max: Float = 1f): Float {
        val valueArr = floatArrayOf(value)
        row(label) { ImGui.sliderFloat("##slider_float", valueArr, min, max) }
        return valueArr[0]
    }

    /** A labelled checkbox in the shared row (EDT-100): its label sits on the left like every other value. */
    fun checkbox(label: String, value: Boolean, id: String = label): Boolean {
        var clicked = false
        row(label, id) { clicked = ImGui.checkbox("##value", value) }
        return clicked
    }

    /**
     * Any labelled control in the shared row (EDT-100). [control] draws one widget with a hidden label (`##id`) and
     * may check `isItemDeactivatedAfterEdit` right after it; returns what [control] returns.
     */
    fun <T> field(label: String, id: String = label, control: () -> T): T {
        var result: T? = null
        row(label, id) { result = control() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    /** A labelled drag-float. */
    fun dragFloat(label: String, value: Float, range: FieldRange = FieldRange.NONE): Float {
        val valArray = floatArrayOf(value)
        row(label) { dragFloatRanged("##dragFloat", valArray, 0.1f, range) }
        return valArray[0]
    }

    /** A drag-float honouring [range]: bounded and stepped by its metadata (ECS-29). */
    fun dragFloatRanged(id: String, values: FloatArray, defaultSpeed: Float, range: FieldRange): Boolean {
        if (!range.bounded) return ImGui.dragFloat(id, values, range.floatSpeed(defaultSpeed))
        return ImGui.dragFloat(id, values, range.floatSpeed(defaultSpeed), range.clamp(-Float.MAX_VALUE), range.clamp(Float.MAX_VALUE))
    }

    /** A drag-int honouring [range]. */
    fun dragIntRanged(id: String, values: IntArray, defaultSpeed: Float, range: FieldRange): Boolean {
        if (!range.bounded) return ImGui.dragInt(id, values, range.floatSpeed(defaultSpeed))
        return ImGui.dragInt(id, values, range.floatSpeed(defaultSpeed), range.clamp(Int.MIN_VALUE), range.clamp(Int.MAX_VALUE))
    }

    /** A labelled drag-int. */
    fun dragInt(label: String, value: Int, range: FieldRange = FieldRange.NONE): Int {
        val valArray = intArrayOf(value)
        row(label) { dragIntRanged("##dragInt", valArray, 1f, range) }
        return valArray[0]
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Enum<*>> enumDropdown(label: String, value: T): T {
        val index = ImInt(value.ordinal)
        val constants = value.declaringJavaClass.enumConstants
        val names = strings?.let { s -> constants.map { EnumLabels.of(s, it as Enum<*>) } } ?: constants.map { it.toString() }
        row(label) { ImGui.combo("##$label", index, names.toTypedArray(), names.size) }
        return constants[index.get()] as T
    }

    fun textureDropdown(label: String, value: Texture?, assetsManager: AssetsManager, defaultValue: String): Texture? {
        val values = assetsManager.getTextures()
        val labels = values.map { texture ->
            texture.filePath?.let { File(it).nameWithoutExtension } ?: tr("lbl.texture.embedded", "Embedded Texture")
        }.toMutableList()
        if (value == null) labels.add(0, defaultValue)
        val index = ImInt(max(0, values.indexOf(value)))

        var removed = false
        var result = false
        row(label) {
            // EDT-86: the combo leaves room for Delete at the current style (it was cut off at the right end).
            val remove = "${Icons.TRASH}##remove"
            val trailing = TrailingButtons.begin(remove)
            result = ImGui.combo("##$label", index, labels.toTypedArray(), labels.size)
            trailing.next()
            removed = ImGui.button(remove)
            strings?.let { Tooltips.show(it, "lbl.texture.remove.tooltip") }
        }
        return when {
            removed -> null
            result -> if (value == null) values.getOrNull(index.get() - 1) else values.getOrNull(index.get())
            else -> value
        }
    }

    /** A labelled RGBA color picker. */
    fun colorPicker4(label: String, color: Vector4f): Boolean {
        var res = false
        row(label) {
            val imColor = floatArrayOf(color.x, color.y, color.z, color.w)
            if (ImGui.colorEdit4("##colorPicker", imColor)) {
                color.set(imColor[0], imColor[1], imColor[2], imColor[3])
                res = true
            }
        }
        return res
    }

    /** A labelled RGB color picker. */
    fun colorPicker3(label: String, color: Vector3f): Boolean {
        var res = false
        row(label) {
            val imColor = floatArrayOf(color.x, color.y, color.z)
            if (ImGui.colorEdit3("##colorPicker3", imColor)) {
                color.set(imColor[0], imColor[1], imColor[2])
                res = true
            }
        }
        return res
    }

    /** Renders text with a custom RGBA color. */
    fun coloredText(text: String, r: Float, g: Float, b: Float, a: Float = 1f) {
        ImGui.pushStyleColor(ImGuiCol.Text, r, g, b, a)
        ImGui.text(text)
        ImGui.popStyleColor()
    }

    /** Renders a warning message in yellow/orange. */
    fun warningText(text: String) {
        coloredText(text, 1f, 0.8f, 0f)
    }

    /** Renders an error message in red. */
    fun errorText(text: String) {
        coloredText(text, 1f, 0.3f, 0.3f)
    }

    /** Renders a success message in green. */
    fun successText(text: String) {
        coloredText(text, 0.3f, 0.9f, 0.3f)
    }

    /** Renders disabled/placeholder text in gray. */
    fun textDisabled(text: String) {
        coloredText(text, 0.5f, 0.5f, 0.5f)
    }

    /**
     * Renders a square icon button with optional tooltip and active/disabled states.
     *
     * @param icon Icon string (e.g. Icons.PLUS)
     * @param size Button width and height
     * @param tooltip Tooltip on hover
     * @param active Highlight green
     * @param disabled Gray out and prevent click
     * @return true if clicked
     */
    fun iconButton(
        icon: String,
        size: Float = 30f,
        tooltip: String? = null,
        active: Boolean = false,
        disabled: Boolean = false
    ): Boolean {
        if (disabled) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.3f, 0.3f, 0.3f, 0.5f)
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.4f, 0.4f, 0.4f, 0.5f)
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.5f, 0.5f, 0.5f, 0.5f)
        } else if (active) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f)
        }

        val clicked = ImGui.button(icon, size, size)

        when {
            disabled -> ImGui.popStyleColor(3)
            active -> ImGui.popStyleColor(1)
        }

        if (tooltip != null) Tooltips.text(tooltip)

        return clicked && !disabled
    }

    /** Renders a labeled separator via ImGui.separatorText. */
    fun labeledSeparator(label: String) {
        ImGui.separatorText(label)
    }

    /** Renders a simple separator with optional leading spacing. */
    fun separator(spacing: Float = 4f) {
        if (spacing > 0) ImGui.dummy(0f, spacing)
        ImGui.separator()
    }

    /**
     * Renders a collapsible tree-node section.
     *
     * @param title Section header (shown)
     * @param id Stable ImGui ID for the open state; pass the string key when [title] is translated
     * @param defaultOpen Whether open by default
     * @param content Lambda rendered when section is open
     */
    fun section(
        title: String,
        defaultOpen: Boolean = true,
        id: String = title,
        content: () -> Unit
    ) {
        val flags = imgui.flag.ImGuiTreeNodeFlags.FramePadding or
                    imgui.flag.ImGuiTreeNodeFlags.SpanAvailWidth

        if (defaultOpen) {
            if (ImGui.treeNodeEx(id, flags, title)) {
                content()
                ImGui.treePop()
            }
        } else {
            if (ImGui.treeNodeEx(id, flags or imgui.flag.ImGuiTreeNodeFlags.DefaultOpen, title)) {
                content()
                ImGui.treePop()
            }
        }
    }

    /** Renders a toolbar group — tight spacing followed by a separator. */
    fun toolbarGroup(content: () -> Unit) {
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 2f, 2f)
        content()
        ImGui.popStyleVar()
        ImGui.sameLine()
        ImGui.separator()
        ImGui.sameLine()
    }

    /** A labelled drag-float3 for Vector3 data (e.g. gravity). */
    fun dragFloat3(label: String, values: FloatArray, speed: Float = 0.01f): Boolean {
        var changed = false
        row(label) { changed = ImGui.dragFloat3("##dragFloat3", values, speed) }
        return changed
    }

    /** A labelled color-edit-3 for RGB colors. */
    fun colorEdit3(label: String, color: FloatArray): Boolean {
        var changed = false
        row(label) { changed = ImGui.colorEdit3("##colorEdit3", color) }
        return changed
    }



    /** Push 3 style colors for a given axis index: 0=X red, 1=Y green, 2=Z blue. */
    private fun pushAxisColors(axis: Int) {
        when (axis) {
            0 -> {
                ImGui.pushStyleColor(ImGuiCol.Button, 0.8f, 0.1f, 0.15f, 1f)
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.9f, 0.2f, 0.2f, 1f)
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.8f, 0.1f, 0.15f, 1f)
            }
            1 -> {
                ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.7f, 0.2f, 1f)
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.3f, 0.8f, 0.3f, 1f)
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.2f, 0.7f, 0.2f, 1f)
            }
            2 -> {
                ImGui.pushStyleColor(ImGuiCol.Button, 0.1f, 0.25f, 0.8f, 1f)
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.2f, 0.35f, 0.9f, 1f)
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.1f, 0.25f, 0.8f, 1f)
            }
        }
    }

    /** A settings row: [label] (its [helpTooltip] on hover), the [widget], and an optional reset button. */
    fun propertyRow(
        label: String,
        helpTooltip: String? = null,
        onReset: (() -> Unit)? = null,
        widget: () -> Unit
    ) {
        val resetLabel = "↺##reset_$label"
        row(label, labelExtra = { if (helpTooltip != null) Tooltips.always(helpTooltip) }) {
            // EDT-86: a reset button gets its room first; the widget takes the rest instead of pushing it out.
            val trailing = onReset?.let { TrailingButtons.begin(resetLabel) }
            trailing?.let { ImGui.pushItemWidth(it.layout.fieldWidth) }
            widget()
            trailing?.let { ImGui.popItemWidth() }
            if (onReset != null) {
                trailing?.next()
                if (ImGui.button(resetLabel)) onReset()
            }
        }
    }

    /** Read-only settings row: [label] and its [value] as text. */
    fun propertyRowReadOnly(label: String, value: String, helpTooltip: String? = null) {
        row(label, labelExtra = { if (helpTooltip != null) Tooltips.always(helpTooltip) }) {
            ImGui.pushTextWrapPos(0f)
            textDisabled(value)
            ImGui.popTextWrapPos()
        }
    }

}
