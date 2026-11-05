package com.pafoid.skate.editor.events

import com.pafoid.skate.editor.commands.objects.MaterialState
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.events.Event

/** Material inspector actions (EDT-17); [com.pafoid.skate.editor.ui.handlers.MaterialActionHandler] runs them as commands. */
sealed class MaterialAction(eventName: String) : Event(eventName) {
    /** One finished edit of [material]'s values; the inspector already previewed [after]. */
    class Edit(val material: Material, val before: MaterialState, val after: MaterialState, val targetName: String?) :
        MaterialAction("material.edit")

    /** Gives [component] its own material, copied from the model's first part (or the default material). */
    class CreateOverride(val component: RenderComponent) : MaterialAction("material.create_override")

    /** Drops [component]'s material and asset link: the model's own part materials render again. */
    class RemoveOverride(val component: RenderComponent) : MaterialAction("material.remove_override")

    /** Asks for a `.skmat` path, writes the material there and links it. */
    class SaveAs(val component: RenderComponent) : MaterialAction("material.save_as")

    /** Overwrites the linked `.skmat` with the current values. */
    class Save(val component: RenderComponent) : MaterialAction("material.save")

    /** Asks for a `.skmat` file and uses (and links) the material it holds. */
    class Load(val component: RenderComponent) : MaterialAction("material.load")

    /** Keeps the current values but stops reloading them from the linked `.skmat`. */
    class Unlink(val component: RenderComponent) : MaterialAction("material.unlink")
}
