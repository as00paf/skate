package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.commands.objects.MaterialState
import com.pafoid.skate.engine.assets.data.models.Material
import java.util.IdentityHashMap

/**
 * Live material editing for the inspector (EDT-17), free of ImGui so it can be tested.
 *
 * [preview] writes a draft straight into the material so the viewport updates while a slider is dragged, and keeps
 * the values from before the gesture. [settle] turns each finished gesture (no widget active any more) into one
 * [Edit], which the inspector publishes as a single undoable command. Materials are keyed by identity: [Material]
 * is a data class whose hash changes as it is edited.
 */
class MaterialEditSession {

    /** One finished gesture on [material]: [before] → [after]. */
    data class Edit(val material: Material, val before: MaterialState, val after: MaterialState)

    private class Pending(val before: MaterialState, var draft: MaterialState)

    private val pending = IdentityHashMap<Material, Pending>()

    /** What the inspector should show for [material]: the in-progress draft, else its current values. */
    fun current(material: Material): MaterialState = pending[material]?.draft ?: MaterialState.of(material)

    fun isEditing(material: Material): Boolean = material in pending

    /** Applies [draft] (clamped) to [material] now; the first preview of a gesture remembers the prior values. */
    fun preview(material: Material, draft: MaterialState) {
        val valid = draft.validated()
        val entry = pending.getOrPut(material) { Pending(MaterialState.of(material), valid) }
        entry.draft = valid
        valid.applyTo(material)
    }

    /** Ends the gestures once no widget is active; returns the edits that changed something. */
    fun settle(anyItemActive: Boolean): List<Edit> {
        if (anyItemActive || pending.isEmpty()) return emptyList()
        val edits = pending.mapNotNull { (material, p) -> Edit(material, p.before, p.draft).takeIf { p.before != p.draft } }
        pending.clear()
        return edits
    }
}
