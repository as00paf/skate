package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.imgui.Tooltips
import com.pafoid.skate.editor.imgui.label
import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.editor.commands.input.BindingOperation
import com.pafoid.skate.editor.events.ConsoleAction
import com.pafoid.skate.editor.events.KeyBindingsAction
import com.pafoid.skate.editor.input.BindingProfileStore
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.input.ActionBinding
import com.pafoid.skate.engine.input.ActionConflict
import com.pafoid.skate.engine.input.ActionMap
import com.pafoid.skate.engine.input.ActionOrigin
import com.pafoid.skate.engine.input.BindingProfile
import com.pafoid.skate.engine.input.ConflictResolution
import com.pafoid.skate.engine.input.InputContext
import com.pafoid.skate.engine.input.ProjectActionSet
import com.pafoid.skate.engine.input.ProjectActions
import com.pafoid.skate.engine.input.RawInput
import imgui.ImGui
import imgui.type.ImString

/**
 * Rebinding UI for the INP-03 action maps (keyboard, mouse and gamepad). It only reads the map; every change is
 * published as a [KeyBindingsAction] and executed as an undoable command.
 */
class ActionBindingsPanel(
    private val strings: StringManager,
    private val events: EventSystem,
    private val actionMap: () -> ActionMap?,
    private val raw: RawInput,
    private val profileStore: () -> BindingProfileStore?,
    /** The project's own actions (INP-11); null hides the project action tools. */
    private val projectActions: () -> ProjectActions? = { null },
) {
    private class CaptureTarget(val actionId: String, val index: Int?, val capture: BindingCapture)
    private class PendingConflict(val operation: BindingOperation.Rebind, val conflicts: List<ActionConflict>)

    private val search = ImString(64)
    private val profileName = ImString("Custom", 64)
    private var capturing: CaptureTarget? = null
    private var conflict: PendingConflict? = null
    private var openConflict = false
    private var mouseTarget: Pair<String, Int?>? = null
    private var profiles: List<String> = emptyList()
    private var profilesStale = true
    private var status: String? = null
    private val dialog = ProjectActionDialog(strings, events)
    private var pendingDelete: String? = null
    private var openDelete = false

    /** A click-to-rebind capture is waiting for input; the dialog must not treat that key as its own (Esc). */
    val isCapturing: Boolean get() = capturing != null

    private fun s(key: String) = strings.getString(key)

    /** Null when the key is missing, so unknown actions and categories fall back to their ids. */
    private fun translated(key: String): String? = s(key).takeUnless { it.startsWith("!!") }

    private fun tip(key: String) {
        Tooltips.text(s(key))
    }

    private fun send(operation: BindingOperation) = events.publish(KeyBindingsAction.Apply(operation))

    fun render() {
        val map = actionMap()
        if (map == null) {
            ImGui.textDisabled(s("lbl.keybindings.no_actions"))
            return
        }
        pollCapture(map)
        renderToolbar(map)
        ImGui.separator()
        renderGroups(map)
        projectActions()?.let { actions ->
            renderContexts(actions)
            dialog.render(map, actions)
            renderDeleteDialog(actions)
        }
        renderMousePopup()
        renderConflictDialog()
    }

    private fun project(): ProjectActionSet? = projectActions()?.current

    private fun nameOf(map: ActionMap, id: String): String =
        map.definition(id)?.let { ActionBindingsModel.actionName(it, ::translated) } ?: id

    private fun editProject(after: ProjectActionSet, labelKey: String, vararg args: Any) =
        events.publish(KeyBindingsAction.EditProjectActions(after, labelKey, args.toList()))

    private fun renderToolbar(map: ActionMap) {
        if (projectActions() != null) {
            if (ImGui.button(s("btn.keybindings.new_action") + "##kb_new_action")) dialog.newAction()
            tip("tip.keybindings.new_action")
            ImGui.sameLine()
        }
        ImGui.setNextItemWidth(220f)
        ImGui.inputTextWithHint("##kb_search", s("lbl.keybindings.search_hint"), search)
        tip("tip.keybindings.search")
        ImGui.sameLine()
        if (ImGui.button(s("btn.keybindings.reset_all") + "##kb_reset_all")) {
            send(BindingOperation.Reset(map.names.toList(), "cmd.keybindings.reset_all"))
        }
        tip("tip.keybindings.reset_all")

        if (profilesStale) {
            profiles = profileStore()?.names() ?: emptyList()
            profilesStale = false
        }
        ImGui.text(s("lbl.keybindings.profile"))
        ImGui.sameLine()
        ImGui.setNextItemWidth(160f)
        if (ImGui.beginCombo("##kb_profile", s("lbl.keybindings.profile_load"))) {
            if (profiles.isEmpty()) ImGui.textDisabled(s("lbl.keybindings.no_profiles"))
            for (name in profiles) {
                if (ImGui.selectable("$name##kb_prof_$name")) loadProfile(name)
            }
            ImGui.endCombo()
        }
        tip("tip.keybindings.profile_load")
        ImGui.sameLine()
        ImGui.setNextItemWidth(140f)
        ImGui.inputText("##kb_profile_name", profileName)
        ImGui.sameLine()
        if (ImGui.button(s("btn.keybindings.profile_save") + "##kb_prof_save")) {
            events.publish(KeyBindingsAction.SaveProfile(profileName.get()))
            profilesStale = true
            status = s("msg.keybindings.profile_saved")
        }
        tip("tip.keybindings.profile_save")
        ImGui.sameLine()
        if (ImGui.button(s("btn.keybindings.profile_delete") + "##kb_prof_delete")) {
            events.publish(KeyBindingsAction.DeleteProfile(profileName.get()))
            profilesStale = true
        }
        tip("tip.keybindings.profile_delete")
        ImGui.sameLine()
        if (ImGui.button(s("btn.keybindings.profile_export") + "##kb_prof_export")) {
            events.publish(ConsoleAction.CopyToClipboard(map.toProfile(profileName.get()).toJson()))
            status = s("msg.keybindings.profile_copied")
        }
        tip("tip.keybindings.profile_export")
        ImGui.sameLine()
        if (ImGui.button(s("btn.keybindings.profile_import") + "##kb_prof_import")) importClipboard()
        tip("tip.keybindings.profile_import")
        status?.let { ImGui.textDisabled(it) }
    }

    private fun loadProfile(name: String) {
        val result = profileStore()?.load(name) ?: return
        result.onSuccess {
            send(BindingOperation.LoadProfile(it))
            profileName.set(it.name)
            status = null
        }.onFailure { status = s("msg.keybindings.profile_invalid") }
    }

    private fun importClipboard() {
        val text = ImGui.getClipboardText()
        runCatching { BindingProfile.fromJson(text ?: "") }
            .onSuccess {
                send(BindingOperation.LoadProfile(it))
                status = s("msg.keybindings.profile_imported")
            }
            .onFailure { status = s("msg.keybindings.profile_invalid") }
    }

    private fun renderGroups(map: ActionMap) {
        val groups = ActionBindingsModel.groups(
            map, search.get(),
            nameOf = { ActionBindingsModel.actionName(it, ::translated) },
            categoryOf = { categoryLabel(it.category) },
            bindingLabel = { ActionBindingsModel.label(it, strings) },
        )
        if (groups.isEmpty()) ImGui.textDisabled(s("lbl.keybindings.no_match"))
        for (group in groups) {
            val title = "${contextLabel(group.context)} / ${group.categoryLabel}###kb_group_${group.context}_${group.category}"
            val open = ImGui.collapsingHeader(title, imgui.flag.ImGuiTreeNodeFlags.DefaultOpen)
            ImGui.sameLine()
            if (ImGui.smallButton(s("btn.keybindings.reset_category") + "##kb_reset_${group.context}_${group.category}")) {
                send(BindingOperation.Reset(group.rows.map { it.definition.id }, "command.bindings.reset_category", listOf(group.categoryLabel)))
            }
            tip("tip.keybindings.reset_category")
            if (!open) continue
            for (row in group.rows) renderRow(row)
        }
    }

    private fun renderRow(row: ActionRow) {
        val id = row.definition.id
        ImGui.text(row.name + if (row.customized) " *" else "")
        if (row.customized) Tooltips.text(s("tip.keybindings.customized"))
        ImGui.sameLine()
        renderOrigin(row)
        ImGui.sameLine(260f)
        row.bindings.forEachIndexed { i, binding ->
            val active = capturing?.let { it.actionId == id && it.index == i } == true
            val text = if (active) s("lbl.keybindings.press_input") else ActionBindingsModel.label(binding, strings)
            if (ImGui.button("$text##kb_${id}_$i")) startCapture(id, i, binding)
            tip("tip.keybindings.rebind")
            ImGui.sameLine()
            if (ImGui.smallButton("x##kb_${id}_rm_$i")) send(BindingOperation.Remove(id, i))
            tip("tip.keybindings.remove")
            ImGui.sameLine()
            renderSharedHint(id, binding)
        }
        val addActive = capturing?.let { it.actionId == id && it.index == null } == true
        if (addActive) {
            if (ImGui.button(s("btn.cancel") + "##kb_${id}_cancel")) capturing = null
            tip("tip.keybindings.cancel")
        } else {
            if (ImGui.button("+##kb_${id}_add")) ImGui.openPopup(contextPopupName(id))
            tip("tip.keybindings.add")
        }
        renderAddMenu(id)
        ImGui.sameLine()
        if (ImGui.smallButton(s("btn.keybindings.clear") + "##kb_${id}_clear")) send(BindingOperation.Clear(id))
        tip("tip.keybindings.clear")
        if (row.customized) {
            ImGui.sameLine()
            if (ImGui.smallButton(s("btn.keybindings.reset") + "##kb_${id}_reset")) send(BindingOperation.Reset(listOf(id), "command.bindings.reset_action", listOf(id)))
            tip("tip.keybindings.reset")
        }
    }

    /** INP-11: every row shows where it is defined; project actions get Edit, Duplicate, Change id and Delete. */
    private fun renderOrigin(row: ActionRow) {
        val id = row.definition.id
        if (row.definition.origin != ActionOrigin.PROJECT) {
            ImGui.textDisabled(s("lbl.keybindings.origin.code"))
            Tooltips.text(s("tip.keybindings.origin.code"))
            return
        }
        if (ImGui.smallButton(s("lbl.keybindings.origin.project") + "##kb_${id}_menu")) ImGui.openPopup("##kb_menu_$id")
        tip("tip.keybindings.origin.project")
        if (!ImGui.beginPopup("##kb_menu_$id")) return
        val set = project()
        val action = set?.action(id)
        if (set != null && action != null) {
            if (strings.tipped(ImGui.menuItem(s("menu.keybindings.edit")), "menu.keybindings.edit")) dialog.editAction(action)
            if (strings.tipped(ImGui.menuItem(s("menu.keybindings.duplicate")), "menu.keybindings.duplicate")) {
                dialog.duplicateAction(action, set.actions.map { it.id }.toSet() + (actionMap()?.names ?: emptySet()))
            }
            if (strings.tipped(ImGui.menuItem(s("menu.keybindings.change_id")), "menu.keybindings.change_id")) dialog.changeId(action)
            ImGui.separator()
            if (strings.tipped(ImGui.menuItem(s("menu.keybindings.delete")), "menu.keybindings.delete")) {
                pendingDelete = id
                openDelete = true
            }
        }
        ImGui.endPopup()
    }

    private fun renderDeleteDialog(actions: ProjectActions) {
        val id = pendingDelete ?: return
        if (openDelete) {
            ImGui.openPopup(DELETE_POPUP)
            openDelete = false
        }
        if (!ImGui.beginPopupModal(DELETE_POPUP, imgui.flag.ImGuiWindowFlags.AlwaysAutoResize)) return
        ImGui.text(strings.getString("msg.keybindings.delete_confirm", actionMap()?.let { nameOf(it, id) } ?: id))
        ImGui.textDisabled(s("msg.keybindings.delete_scripts"))
        if (ImGui.button(s("menu.keybindings.delete") + "##kb_delete_ok")) {
            editProject(actions.current.withoutAction(id), "command.project_actions.delete", id)
            pendingDelete = null
            ImGui.closeCurrentPopup()
        }
        tip("menu.keybindings.delete.tooltip")
        ImGui.sameLine()
        if (ImGui.button(s("btn.cancel") + "##kb_delete_cancel")) {
            pendingDelete = null
            ImGui.closeCurrentPopup()
        }
        tip("tip.keybindings.dialog_cancel")
        ImGui.endPopup()
    }

    /** INP-11: built-in contexts read-only, project contexts editable. A context still used by an action cannot be deleted. */
    private fun renderContexts(actions: ProjectActions) {
        if (!ImGui.collapsingHeader(strings.label("lbl.keybindings.contexts"))) return
        val set = actions.current
        for (id in ProjectActionSet.BUILT_IN_CONTEXTS.sortedBy { InputContext(it).displayOrder }) {
            ImGui.bulletText(contextLabel(InputContext(id)))
            ImGui.sameLine()
            ImGui.textDisabled(s("lbl.keybindings.origin.built_in"))
            Tooltips.text(s("tip.keybindings.origin.built_in"))
        }
        for (ctx in set.contexts) {
            ImGui.bulletText(contextLabel(InputContext(ctx.id)))
            ImGui.sameLine()
            ImGui.textDisabled(strings.getString("lbl.keybindings.context_summary", ctx.id, ctx.priority, s("lbl.keybindings.blocking.${ctx.blocking.name.lowercase()}")) +
                if (ctx.activeAtStart) " " + s("lbl.keybindings.context_start_tag") else "")
            ImGui.sameLine()
            if (ImGui.smallButton(s("menu.keybindings.edit") + "##kb_ctx_edit_${ctx.id}")) dialog.editContext(ctx)
            tip("menu.keybindings.edit.tooltip")
            ImGui.sameLine()
            val users = ActionBindingsModel.contextUsers(set, ctx.id)
            if (users.isNotEmpty()) ImGui.beginDisabled()
            if (ImGui.smallButton(s("menu.keybindings.delete") + "##kb_ctx_delete_${ctx.id}")) {
                editProject(set.withoutContext(ctx.id), "command.project_actions.delete_context", ctx.id)
            }
            if (users.isNotEmpty()) ImGui.endDisabled()
            Tooltips.text(if (users.isEmpty()) s("tip.keybindings.delete_context") else strings.getString("tip.keybindings.context_in_use", users.joinToString(", ")))
        }
        if (ImGui.button(s("btn.keybindings.new_context") + "##kb_new_context")) dialog.newContext()
        tip("tip.keybindings.new_context")
    }

    /** INP-10: the same input is bound in another context; the higher-priority one takes it while active. */
    private fun renderSharedHint(id: String, binding: ActionBinding) {
        val map = actionMap() ?: return
        val shared = ActionBindingsModel.sharedWith(map, id, binding)
        if (shared.isEmpty()) return
        ImGui.textDisabled("(!)")
        if (ImGui.isItemHovered()) {
            val names = shared.map { it.actionId }.distinct().joinToString(", ") { other ->
                val def = map.definition(other)
                val name = nameOf(map, other)
                def?.let { "$name (${contextLabel(it.context)})" } ?: name
            }
            Tooltips.always(strings.getString("tip.keybindings.shared", names))
        }
        ImGui.sameLine()
    }

    private fun contextPopupName(id: String) = "##kb_add_$id"

    private fun renderAddMenu(id: String) {
        if (!ImGui.beginPopup(contextPopupName(id))) return
        if (strings.tipped(ImGui.menuItem(s("lbl.keybindings.add_keyboard")), "lbl.keybindings.add_keyboard")) startCapture(id, null, BindingCapture.Kind.KEYBOARD)
        if (strings.tipped(ImGui.menuItem(s("lbl.keybindings.add_mouse")), "lbl.keybindings.add_mouse")) mouseTarget = id to null
        if (strings.tipped(ImGui.menuItem(s("lbl.keybindings.add_gamepad")), "lbl.keybindings.add_gamepad")) startCapture(id, null, BindingCapture.Kind.GAMEPAD)
        ImGui.endPopup()
    }

    private fun startCapture(id: String, index: Int?, binding: ActionBinding) {
        when (binding) {
            is ActionBinding.Key -> startCapture(id, index, BindingCapture.Kind.KEYBOARD)
            is ActionBinding.MouseButton -> mouseTarget = id to index
            else -> startCapture(id, index, BindingCapture.Kind.GAMEPAD)
        }
    }

    private fun startCapture(id: String, index: Int?, kind: BindingCapture.Kind) {
        capturing = CaptureTarget(id, index, BindingCapture(raw, kind))
    }

    private fun pollCapture(map: ActionMap) {
        val target = capturing ?: return
        val binding = target.capture.poll() ?: return
        capturing = null
        submit(map, target.actionId, target.index, binding)
    }

    private fun submit(map: ActionMap, id: String, index: Int?, captured: ActionBinding) {
        val old = index?.let { map.bindings(id).getOrNull(it) }
        val binding = if (old != null && captured !is ActionBinding.GamepadAxis && captured::class == old::class) {
            captured.withScale(old.scale)
        } else captured
        val operation = BindingOperation.Rebind(id, index, binding)
        val conflicts = map.findConflicts(id, binding)
        if (conflicts.isEmpty()) send(operation) else {
            conflict = PendingConflict(operation, conflicts)
            openConflict = true
        }
    }

    private fun renderMousePopup() {
        val target = mouseTarget
        if (target != null) {
            ImGui.openPopup(MOUSE_POPUP)
            mouseTarget = null
            pendingMouse = target
        }
        if (!ImGui.beginPopup(MOUSE_POPUP)) return
        val (id, index) = pendingMouse ?: run { ImGui.endPopup(); return }
        MOUSE_BUTTON_KEYS.forEachIndexed { button, key ->
            if (ImGui.menuItem(s(key))) {
                actionMap()?.let { submit(it, id, index, ActionBinding.MouseButton(button)) }
            }
        }
        ImGui.endPopup()
    }

    private var pendingMouse: Pair<String, Int?>? = null

    private fun renderConflictDialog() {
        val pending = conflict ?: return
        if (openConflict) {
            ImGui.openPopup(CONFLICT_POPUP)
            openConflict = false
        }
        if (!ImGui.beginPopupModal(CONFLICT_POPUP, imgui.flag.ImGuiWindowFlags.AlwaysAutoResize)) return
        val map = actionMap()
        ImGui.text(s("lbl.keybindings.conflict_intro"))
        pending.conflicts.map { it.actionId }.distinct().forEach { other ->
            val name = map?.let { nameOf(it, other) } ?: other
            ImGui.bulletText("$name (${ActionBindingsModel.label(pending.operation.binding, strings)})")
        }
        if (pending.operation.index != null && ImGui.button(s("btn.keybindings.conflict_swap") + "##kb_swap")) {
            resolve(pending, ConflictResolution.SWAP)
        }
        if (pending.operation.index != null) { tip("tip.keybindings.conflict_swap"); ImGui.sameLine() }
        if (ImGui.button(s("btn.keybindings.conflict_unbind") + "##kb_unbind")) resolve(pending, ConflictResolution.UNBIND_OTHERS)
        tip("tip.keybindings.conflict_unbind")
        ImGui.sameLine()
        if (ImGui.button(s("btn.cancel") + "##kb_conflict_cancel")) {
            conflict = null
            ImGui.closeCurrentPopup()
        }
        ImGui.endPopup()
    }

    private fun resolve(pending: PendingConflict, resolution: ConflictResolution) {
        send(pending.operation.copy(resolution = resolution))
        conflict = null
        ImGui.closeCurrentPopup()
    }

    private fun contextLabel(context: InputContext) = ActionBindingsModel.contextLabel(context, ::translated, project())

    private fun categoryLabel(category: String): String {
        return translated("lbl.keybindings.category.${category.lowercase()}") ?: category
    }

    private companion object {
        const val MOUSE_POPUP = "##kb_mouse_popup"
        /** Menu labels of mouse buttons 0..4 (GLFW order). */
        val MOUSE_BUTTON_KEYS = ActionBindingsModel.MOUSE_BUTTON_KEYS
        const val CONFLICT_POPUP = "kb_conflict_dialog"
        const val DELETE_POPUP = "kb_delete_action_dialog"
    }
}
