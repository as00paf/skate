package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.data.EditorShortcuts
import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
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
 * (one undoable command per object), Cancel and Esc drop the request. Cut deletes without asking (`confirm = false`).
 */
class DeleteObjectsDialog(
    private val eventSystem: EventSystem,
    strings: StringManager,
    val confirmation: DeleteObjectsConfirmation = DeleteObjectsConfirmation(),
) : EditorModal(strings, Icons.TRASH) {

    init {
        eventSystem.subscribe<ViewportAction.Delete> { if (it.confirm) confirmation.request(it.gameObject, it.scene) }
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
            confirmation.confirm().forEach { eventSystem.publish(ViewportAction.DeleteConfirmed(it.gameObject, it.scene)) }
        },
    )
}
