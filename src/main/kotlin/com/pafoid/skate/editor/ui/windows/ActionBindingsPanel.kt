package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.commands.input.BindingOperation
import com.pafoid.skate.editor.events.ConsoleAction
import com.pafoid.skate.editor.events.KeyBindingsAction
import com.pafoid.skate.editor.input.BindingProfileStore
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.input.ActionBinding
import com.pafoid.skate.engine.input.ActionConflict
import com.pafoid.skate.engine.input.ActionMap
import com.pafoid.skate.engine.input.BindingProfile
import com.pafoid.skate.engine.input.ConflictResolution
import com.pafoid.skate.engine.input.InputContext
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

    private fun s(key: String) = strings.getString(key)

    /** Null when the key is missing, so unknown actions and categories fall back to their ids. */
    private fun translated(key: String): String? = s(key).takeUnless { it.startsWith("!!") }

    private fun tip(key: String) {
        if (ImGui.isItemHovered()) ImGui.setTooltip(s(key))
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
        renderMousePopup()
        renderConflictDialog()
    }

    private fun renderToolbar(map: ActionMap) {
        ImGui.setNextItemWidth(220f)
        ImGui.inputTextWithHint("##kb_search", s("lbl.keybindings.search_hint"), search)
        tip("tip.keybindings.search")
        ImGui.sameLine()
        if (ImGui.button(s("btn.keybindings.reset_all") + "##kb_reset_all")) {
            send(BindingOperation.Reset(map.names.toList(), "Reset all bindings"))
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
            nameOf = { translated(it.displayKey) ?: it.id },
            categoryOf = { categoryLabel(it.category) },
        )
        if (groups.isEmpty()) ImGui.textDisabled(s("lbl.keybindings.no_match"))
        for (group in groups) {
            val title = "${contextLabel(group.context)} / ${group.categoryLabel}##kb_group_${group.context}_${group.category}"
            val open = ImGui.collapsingHeader(title, imgui.flag.ImGuiTreeNodeFlags.DefaultOpen)
            ImGui.sameLine()
            if (ImGui.smallButton(s("btn.keybindings.reset_category") + "##kb_reset_${group.context}_${group.category}")) {
                send(BindingOperation.Reset(group.rows.map { it.definition.id }, "Reset ${group.category} bindings"))
            }
            tip("tip.keybindings.reset_category")
            if (!open) continue
            for (row in group.rows) renderRow(row)
        }
    }

    private fun renderRow(row: ActionRow) {
        val id = row.definition.id
        ImGui.text(row.name + if (row.customized) " *" else "")
        if (row.customized && ImGui.isItemHovered()) ImGui.setTooltip(s("tip.keybindings.customized"))
        ImGui.sameLine(220f)
        row.bindings.forEachIndexed { i, binding ->
            val active = capturing?.let { it.actionId == id && it.index == i } == true
            val text = if (active) s("lbl.keybindings.press_input") else ActionBindingsModel.label(binding)
            if (ImGui.button("$text##kb_${id}_$i")) startCapture(id, i, binding)
            tip("tip.keybindings.rebind")
            ImGui.sameLine()
            if (ImGui.smallButton("x##kb_${id}_rm_$i")) send(BindingOperation.Remove(id, i))
            tip("tip.keybindings.remove")
            ImGui.sameLine()
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
            if (ImGui.smallButton(s("btn.keybindings.reset") + "##kb_${id}_reset")) send(BindingOperation.Reset(listOf(id), "Reset $id bindings"))
            tip("tip.keybindings.reset")
        }
    }

    private fun contextPopupName(id: String) = "##kb_add_$id"

    private fun renderAddMenu(id: String) {
        if (!ImGui.beginPopup(contextPopupName(id))) return
        if (ImGui.menuItem(s("lbl.keybindings.add_keyboard"))) startCapture(id, null, BindingCapture.Kind.KEYBOARD)
        if (ImGui.menuItem(s("lbl.keybindings.add_mouse"))) mouseTarget = id to null
        if (ImGui.menuItem(s("lbl.keybindings.add_gamepad"))) startCapture(id, null, BindingCapture.Kind.GAMEPAD)
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
        val names = listOf("Left", "Right", "Middle", "Button 4", "Button 5")
        names.forEachIndexed { button, name ->
            if (ImGui.menuItem("Mouse $name")) {
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
            val name = map?.definition(other)?.let { translated(it.displayKey) } ?: other
            ImGui.bulletText("$name (${ActionBindingsModel.label(pending.operation.binding)})")
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

    private fun contextLabel(context: InputContext) = s("lbl.keybindings.context.${context.name.lowercase()}")

    private fun categoryLabel(category: String): String {
        return translated("lbl.keybindings.category.${category.lowercase()}") ?: category
    }

    private companion object {
        const val MOUSE_POPUP = "##kb_mouse_popup"
        const val CONFLICT_POPUP = "kb_conflict_dialog"
    }
}
