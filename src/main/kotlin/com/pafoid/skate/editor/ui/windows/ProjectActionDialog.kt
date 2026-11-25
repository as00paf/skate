package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.imgui.Tooltips
import com.pafoid.skate.editor.events.KeyBindingsAction
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.input.ActionBinding
import com.pafoid.skate.engine.input.ActionKind
import com.pafoid.skate.engine.input.ActionMap
import com.pafoid.skate.engine.input.ContextBlocking
import com.pafoid.skate.engine.input.InputContext
import com.pafoid.skate.engine.input.ProjectAction
import com.pafoid.skate.engine.input.ProjectActionSet
import com.pafoid.skate.engine.input.ProjectActions
import com.pafoid.skate.engine.input.ProjectContext
import imgui.ImGui
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import imgui.type.ImInt
import imgui.type.ImString

/**
 * Modal editor of one project action or context (INP-11): create, edit, duplicate and change id. On OK it publishes
 * the whole edited [ProjectActionSet] as a [KeyBindingsAction.EditProjectActions]; it never mutates anything itself.
 */
class ProjectActionDialog(
    private val strings: StringManager,
    private val events: EventSystem,
) {
    private enum class Mode(val labelKey: String) {
        NEW_ACTION("command.project_actions.add"),
        EDIT_ACTION("command.project_actions.edit"),
        DUPLICATE_ACTION("command.project_actions.duplicate"),
        CHANGE_ID("command.project_actions.change_id"),
        NEW_CONTEXT("command.project_actions.add_context"),
        EDIT_CONTEXT("command.project_actions.edit_context"),
    }

    private var mode: Mode? = null
    private var original: String? = null
    private var openRequested = false

    private val id = ImString(64)
    private val name = ImString(128)
    private val category = ImString(64)
    private var context = ProjectActionSet.BUILT_IN_CONTEXTS.first()
    private var kind = ActionKind.BUTTON
    private val publishEvents = ImBoolean(false)
    private var defaults: List<ActionBinding> = emptyList()
    private val priority = ImInt(0)
    private var blocking = ContextBlocking.CONSUME_BOUND
    private val activeAtStart = ImBoolean(false)

    private fun s(key: String) = strings.getString(key)

    fun newAction(contextId: String = ProjectActionSet.BUILT_IN_CONTEXTS.first()) =
        openAction(Mode.NEW_ACTION, ProjectAction("", context = contextId), null)

    fun editAction(action: ProjectAction) = openAction(Mode.EDIT_ACTION, action, action.id)

    fun duplicateAction(action: ProjectAction, taken: Set<String>) =
        openAction(Mode.DUPLICATE_ACTION, action.copy(id = ActionBindingsModel.duplicateId(action.id, taken)), null)

    fun changeId(action: ProjectAction) = openAction(Mode.CHANGE_ID, action, action.id)

    fun newContext() = openContext(Mode.NEW_CONTEXT, ProjectContext(""), null)

    fun editContext(context: ProjectContext) = openContext(Mode.EDIT_CONTEXT, context, context.id)

    private fun openAction(mode: Mode, action: ProjectAction, original: String?) {
        this.mode = mode
        this.original = original
        id.set(action.id); name.set(action.name); category.set(action.category)
        context = action.context; kind = action.kind; publishEvents.set(action.events); defaults = action.defaults
        openRequested = true
    }

    private fun openContext(mode: Mode, ctx: ProjectContext, original: String?) {
        this.mode = mode
        this.original = original
        id.set(ctx.id); name.set(ctx.name); priority.set(ctx.priority); blocking = ctx.blocking
        activeAtStart.set(ctx.activeAtStart)
        openRequested = true
    }

    fun render(map: ActionMap, actions: ProjectActions) {
        val mode = mode ?: return
        if (openRequested) {
            ImGui.openPopup(POPUP)
            openRequested = false
        }
        if (!ImGui.beginPopupModal(POPUP, ImGuiWindowFlags.AlwaysAutoResize)) return
        ImGui.text(s("lbl.keybindings.dialog.${mode.name.lowercase()}"))
        ImGui.separator()
        val set = actions.current
        val isContext = mode == Mode.NEW_CONTEXT || mode == Mode.EDIT_CONTEXT
        val error = if (isContext) contextFields(set) else actionFields(mode, map, actions, set)
        if (mode == Mode.CHANGE_ID) ImGui.textWrapped(s("msg.keybindings.change_id_warning"))
        error?.let { ImGui.textColored(0.9f, 0.4f, 0.3f, 1f, s(it)) }

        ImGui.separator()
        if (error != null) ImGui.beginDisabled()
        if (ImGui.button(s("btn.ok") + "##pa_ok")) {
            submit(mode, set)
            close()
        }
        tip("tip.keybindings.project_ok")
        if (error != null) ImGui.endDisabled()
        ImGui.sameLine()
        if (ImGui.button(s("btn.cancel") + "##pa_cancel")) close()
        tip("tip.keybindings.dialog_cancel")
        ImGui.endPopup()
    }

    private fun close() {
        mode = null
        ImGui.closeCurrentPopup()
    }

    /** Draws the action fields and returns the error key that blocks OK, if any. */
    private fun actionFields(mode: Mode, map: ActionMap, actions: ProjectActions, set: ProjectActionSet): String? {
        val idEditable = mode != Mode.EDIT_ACTION
        if (!idEditable) ImGui.beginDisabled()
        ImGui.inputText(s("lbl.keybindings.action_id") + "##pa_id", id)
        tip("lbl.keybindings.action_id.tooltip")
        if (!idEditable) ImGui.endDisabled()
        if (mode == Mode.CHANGE_ID) return idError(actions, set)

        ImGui.inputText(s("lbl.keybindings.action_name") + "##pa_name", name)
        tip("lbl.keybindings.action_name.tooltip")

        val contexts = ActionBindingsModel.contextChoices(map, set)
        if (ImGui.beginCombo(s("lbl.keybindings.action_context") + "###pa_context", contextName(context, set))) {
            for (c in contexts) if (ImGui.selectable(contextName(c, set) + "##pa_ctx_$c", c == context)) context = c
            ImGui.endCombo()
        }
        tip("lbl.keybindings.action_context.tooltip")

        ImGui.inputText(s("lbl.keybindings.action_category") + "##pa_category", category)
        tip("lbl.keybindings.action_category.tooltip")
        ImGui.sameLine()
        if (ImGui.beginCombo("###pa_category_pick", "", imgui.flag.ImGuiComboFlags.NoPreview)) {
            for (c in ActionBindingsModel.categorySuggestions(map)) if (ImGui.selectable("$c##pa_cat_$c")) category.set(c)
            ImGui.endCombo()
        }
        tip("tip.keybindings.category_pick")

        if (ImGui.beginCombo(s("lbl.keybindings.action_kind") + "###pa_kind", kindLabel(kind))) {
            for (k in ActionKind.entries) if (ImGui.selectable(kindLabel(k) + "##pa_kind_$k", k == kind)) kind = k
            ImGui.endCombo()
        }
        tip("lbl.keybindings.action_kind.tooltip")

        ImGui.checkbox(s("lbl.keybindings.action_events") + "##pa_events", publishEvents)
        tip("lbl.keybindings.action_events.tooltip")
        if (mode == Mode.NEW_ACTION) ImGui.textDisabled(s("msg.keybindings.bind_after_create"))
        return if (idEditable) idError(actions, set) else null
    }

    private fun idError(actions: ProjectActions, set: ProjectActionSet): String? {
        val taken = set.actions.map { it.id }.toSet() - setOfNotNull(original)
        return ActionBindingsModel.idErrorKey(id.get().trim(), actions.codeIds(), taken)
    }

    private fun contextFields(set: ProjectActionSet): String? {
        val idEditable = mode == Mode.NEW_CONTEXT
        if (!idEditable) ImGui.beginDisabled()
        ImGui.inputText(s("lbl.keybindings.context_id") + "##pc_id", id)
        tip("lbl.keybindings.context_id.tooltip")
        if (!idEditable) ImGui.endDisabled()
        ImGui.inputText(s("lbl.keybindings.context_name") + "##pc_name", name)
        tip("lbl.keybindings.context_name.tooltip")
        ImGui.inputInt(s("lbl.keybindings.context_priority") + "##pc_priority", priority)
        tip("lbl.keybindings.context_priority.tooltip")
        if (ImGui.beginCombo(s("lbl.keybindings.context_blocking") + "###pc_blocking", blockingLabel(blocking))) {
            for (b in ContextBlocking.entries) if (ImGui.selectable(blockingLabel(b) + "##pc_b_$b", b == blocking)) blocking = b
            ImGui.endCombo()
        }
        tip("lbl.keybindings.context_blocking.tooltip")
        ImGui.checkbox(s("lbl.keybindings.context_start") + "##pc_start", activeAtStart)
        tip("lbl.keybindings.context_start.tooltip")
        if (!idEditable) return null
        val newId = id.get().trim()
        return when {
            !ProjectActionSet.isValidId(newId) -> "msg.keybindings.id_invalid"
            newId in ProjectActionSet.BUILT_IN_CONTEXTS || set.context(newId) != null -> "msg.keybindings.id_taken"
            else -> null
        }
    }

    private fun submit(mode: Mode, set: ProjectActionSet) {
        val newId = id.get().trim()
        val action = ProjectAction(newId, name.get().trim(), context, category.get().trim(), kind, defaults, publishEvents.get())
        val (after, args) = when (mode) {
            Mode.NEW_ACTION, Mode.DUPLICATE_ACTION, Mode.EDIT_ACTION -> set.withAction(action) to listOf<Any>(newId)
            Mode.CHANGE_ID -> {
                val from = original ?: return
                ActionBindingsModel.changeId(set, from, newId) to listOf<Any>(from, newId)
            }
            Mode.NEW_CONTEXT, Mode.EDIT_CONTEXT ->
                set.withContext(ProjectContext(newId, name.get().trim(), priority.get(), blocking, activeAtStart.get())) to listOf<Any>(newId)
        }
        events.publish(KeyBindingsAction.EditProjectActions(after, mode.labelKey, args))
    }

    private fun contextName(id: String, set: ProjectActionSet) =
        ActionBindingsModel.contextLabel(InputContext(id), ::translated, set)

    private fun translated(key: String): String? = s(key).takeUnless { it.startsWith("!!") }

    private fun kindLabel(kind: ActionKind) = s("lbl.keybindings.kind.${kind.name.lowercase()}")

    private fun blockingLabel(b: ContextBlocking) = s("lbl.keybindings.blocking.${b.name.lowercase()}")

    private fun tip(key: String) = Tooltips.text(s(key))

    private companion object {
        const val POPUP = "kb_project_action_dialog"
    }
}
