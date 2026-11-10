package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.data.EditorShortcuts
import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.editor.systems.SelectionLogic
import imgui.ImGui

/**
 * The objects waiting for a delete confirmation (EDT-62). Requests made in the same frame (a multi-selection) form one
 * question. An object whose ancestor is also requested is dropped, since deleting the ancestor removes it too.
 */
class DeleteObjectsConfirmation {
    class Request(val gameObject: GameObject, val scene: Scene)

    private val requests = mutableListOf<Request>()

    val pending: List<Request> get() = requests

    fun request(gameObject: GameObject, scene: Scene) {
        if (requests.any { it.gameObject === gameObject || isAncestor(it.gameObject, gameObject) }) return
        requests.removeAll { isAncestor(gameObject, it.gameObject) }
        requests += Request(gameObject, scene)
    }

    /** Confirm: returns the objects to delete and clears the request. */
    fun confirm(): List<Request> = requests.toList().also { requests.clear() }

    fun cancel() = requests.clear()

    /** The message: its string key and arguments (object name or count, then the number of children deleted with it). */
    fun message(): Pair<String, List<Any>> {
        val children = requests.sumOf { descendantCount(it.gameObject) }
        val single = requests.singleOrNull()
        return when {
            single != null && children == 0 -> "dialog.delete_objects.one" to listOf(single.gameObject.name)
            single != null -> "dialog.delete_objects.one_with_children" to listOf(single.gameObject.name, children)
            else -> "dialog.delete_objects.many" to listOf(requests.size, children)
        }
    }

    companion object {
        /** Every object below [gameObject], at any depth. */
        fun descendantCount(gameObject: GameObject): Int = gameObject.children.sumOf { 1 + descendantCount(it) }

        private fun isAncestor(ancestor: GameObject, of: GameObject): Boolean {
            var node = of.parent
            while (node != null) {
                if (node === ancestor) return true
                node = node.parent
            }
            return false
        }
    }
}

/**
 * Deleting an object asks first (EDT-62), whatever the path: the Hierarchy and Viewport menus, the Delete key, Search
 * Anywhere. [ViewportAction.Delete] with `confirm = true` lands here; Delete runs [ViewportAction.DeleteConfirmed]
 * (one undo step for all the objects), Cancel and Esc drop the request. Cut deletes without asking (`confirm = false`).
 */
class DeleteObjectsDialog(
    private val eventSystem: EventSystem,
    strings: StringManager,
    val confirmation: DeleteObjectsConfirmation = DeleteObjectsConfirmation(),
) : EditorModal(strings, Icons.TRASH) {

    init {
        // A delete on an object of a multi-selection asks about the whole selection (EDT-74).
        eventSystem.subscribe<ViewportAction.Delete> { e ->
            if (e.confirm) SelectionLogic.targets(e.scene, e.gameObject).forEach { confirmation.request(it, e.scene) }
        }
    }

    override val titleKey: String = "dialog.delete_objects.title"

    override val wantsOpen: Boolean get() = confirmation.pending.isNotEmpty()

    override fun content() {
        if (confirmation.pending.isEmpty()) {
            ImGui.closeCurrentPopup()
            return
        }
        val (key, args) = confirmation.message()
        ImGui.pushTextWrapPos(ImGui.getFontSize() * 28f)
        ImGui.textWrapped(strings.getString(key, *args.toTypedArray()))
        ImGui.spacing()
        ImGui.textDisabled(strings.getString("dialog.delete_objects.undo_hint", EditorShortcuts.UNDO.display()))
        ImGui.popTextWrapPos()
    }

    override fun buttons() = listOf(
        FooterButton("btn.cancel", FooterButton.Role.CANCEL) { confirmation.cancel() },
        FooterButton("dialog.delete_objects.delete", FooterButton.Role.DANGER) {
            // One undo step for everything confirmed together (EDT-74).
            confirmation.confirm().groupBy { it.scene }.forEach { (scene, requests) ->
                eventSystem.publish(ViewportAction.DeleteConfirmed(requests.map { it.gameObject }, scene))
            }
        },
    )
}
