package com.pafoid.skate.editor.animation.controllereditor

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.commands.animation.ControllerAction
import com.pafoid.skate.editor.commands.animation.ControllerEditCommand
import com.pafoid.skate.editor.commands.animation.CreateControllerCommand
import com.pafoid.skate.editor.commands.animation.SaveControllerCommand
import com.pafoid.skate.engine.animation.graph.AnimEventDef
import com.pafoid.skate.engine.animation.graph.AnimationControllerAsset
import com.pafoid.skate.engine.animation.graph.AnimationControllerAsset.Companion.ANY_STATE_NODE
import com.pafoid.skate.engine.animation.graph.AnimationStateMachine
import com.pafoid.skate.engine.animation.graph.ConditionOp
import com.pafoid.skate.engine.animation.graph.ControllerEditor
import com.pafoid.skate.engine.animation.graph.ControllerParameters
import com.pafoid.skate.engine.animation.graph.NodePos
import com.pafoid.skate.engine.animation.graph.ParameterDef
import com.pafoid.skate.engine.animation.graph.ParameterType
import com.pafoid.skate.engine.animation.graph.StateDef
import com.pafoid.skate.engine.animation.graph.TransitionCondition
import com.pafoid.skate.engine.animation.graph.TransitionDef
import com.pafoid.skate.engine.ecs.components.Animator
import java.io.File

/** A controller found for a path: its text, and the file to save to (null for a read-only engine default). */
data class ResolvedController(val path: String, val file: File?, val text: String)

/** A clip file in the project the clip picker offers (ANI-21 §3.2). [path] is the asset path stored in `clips`. */
data class ProjectClip(val name: String, val path: String)

/** Where a clip-picker entry comes from. */
enum class ClipSource { CHARACTER, CONTROLLER, PROJECT, POSE }

data class ClipOption(val name: String, val source: ClipSource, val path: String? = null)

/** States copied with Ctrl+C (ANI-21 Q8): pasted into this or another controller open in the window. */
data class CopiedStates(val states: List<StateDef>, val transitions: List<TransitionDef>, val positions: Map<String, NodePos>)

/**
 * State and actions of the Animator window (ANI-21 §5.1): the open controller, selection, box drags, connecting
 * states, the clip picker and every edit as a [ControllerEditCommand] through [execute] (so Undo/Redo and the Command
 * History see them). Refused edits (duplicate names, the last state, a parameter still in use) add no history and
 * leave their reason in [message]. No ImGui here: the window is a thin view and this class is unit-tested.
 */
class AnimatorWindowController(
    private val execute: (Command) -> Unit,
    /** Finds a controller by `Animator.controllerPath` (project file, absolute file or bundled resource). */
    private val resolve: (String) -> ResolvedController?,
    /** `Assets/Animations` of the open project, where new controllers and project copies go. */
    private val animationsDir: () -> File? = { null },
    /** Clip files in the project. */
    private val projectClips: () -> List<ProjectClip> = { emptyList() },
    /** After a successful save: rebuild the animators that use the file and refresh pickers (§5.4). */
    private val onSaved: (ControllerDocument) -> Unit = {},
    /** Points an Animator at a controller path (undoable `SetAnimatorControllerCommand`). */
    private val assignController: (Animator, String) -> Unit = { _, _ -> },
) {
    private val documents = HashMap<String, ControllerDocument>()

    var document: ControllerDocument? = null; private set
    val asset: AnimationControllerAsset? get() = document?.asset

    /** The character whose clips fill the clip picker and whose state machine play mode shows ("Preview on"). */
    var preview: Animator? = null

    val view = ControllerGraphView()

    /** Selected boxes: state names and [ANY_STATE_NODE]. */
    var selectedNodes: Set<String> = emptySet(); private set
    /** Selected transition (index in the asset's list), when an arrow is selected. */
    var selectedTransition: Int? = null; private set
    /** Source box while making a transition (right-click > Make transition, then click the target). */
    var connectFrom: String? = null; private set

    /** Last refused edit or failure, for the window. Cleared by the next successful action. */
    var message: String? = null; private set

    private var clipboard: CopiedStates? = null
    val canPaste: Boolean get() = clipboard?.states?.isNotEmpty() == true

    // ---- open, new, save --------------------------------------------------------------------------------------

    /** Opens the controller Animators refer to as [path]. Reopening keeps the document and its undo history. */
    fun open(path: String): Boolean {
        message = null
        val resolved = try { resolve(path) } catch (e: Exception) { null }
        if (resolved == null) { message = "Controller not found: $path"; return false }
        val key = keyOf(resolved)
        val doc = documents[key] ?: try {
            ControllerDocument(resolved.path, resolved.file?.absoluteFile, AnimationControllerAsset.fromJson(resolved.text))
                .also { documents[key] = it }
        } catch (e: Exception) {
            message = e.message ?: e.javaClass.simpleName
            return false
        }
        show(doc)
        return true
    }

    /** Opens the controller [animator] uses and makes it the preview. */
    fun openFor(animator: Animator): Boolean {
        preview = animator
        return animator.controllerPath?.let { open(it) } ?: false
    }

    fun close() { document = null; clearSelection(); connectFrom = null }

    private fun show(doc: ControllerDocument) {
        if (document !== doc) {
            document = doc
            clearSelection()
            connectFrom = null
            view.frameAll(positions(), 640f, 360f)
        }
    }

    private fun keyOf(r: ResolvedController) = r.file?.absoluteFile?.normalize()?.path ?: r.path

    /** Writes `Assets/Animations/<name>.controller.json` with one state and opens it (ANI-21 §5.2). */
    fun newController(name: String = "New Controller"): Boolean {
        val dir = animationsDir() ?: run { message = "Open a project first"; return false }
        val file = CreateControllerCommand.freeFile(dir, name)
        val asset = AnimationControllerAsset(
            file.name.removeSuffix(CreateControllerCommand.EXTENSION), "Idle", listOf(StateDef("Idle", "")),
            layout = mapOf("Idle" to NodePos(0f, 0f)),
        )
        return create(asset, file)
    }

    /**
     * An engine default is read-only (§5.2): this saves a copy in `Assets/Animations` and opens it. Point the
     * preview character at it with [usePreviewOnDocument].
     */
    fun makeProjectCopy(): Boolean {
        val doc = document ?: return false
        val dir = animationsDir() ?: run { message = "Open a project first"; return false }
        val file = CreateControllerCommand.freeFile(dir, doc.asset.name.ifBlank { doc.displayName.removeSuffix(CreateControllerCommand.EXTENSION) })
        return create(doc.asset, file)
    }

    private fun create(asset: AnimationControllerAsset, file: File): Boolean {
        val command = CreateControllerCommand(asset, file)
        execute(command)
        if (!command.wasSuccessful()) { message = command.getFailureReason(); return false }
        return open(file.absolutePath)
    }

    /** True when the preview character does not use the open controller yet (offer "Use on <character>"). */
    val canUseOnPreview: Boolean
        get() {
            val doc = document ?: return false
            val p = preview ?: return false
            return !doc.readOnly && !uses(p, doc)
        }

    fun usePreviewOnDocument() {
        val doc = document ?: return
        val p = preview ?: return
        if (!doc.readOnly) assignController(p, doc.file!!.absolutePath)
    }

    /** Saves the open controller; on success the characters using it are rebuilt (Q5: even in play mode). */
    fun save(): Boolean {
        val doc = document ?: return false
        if (doc.readOnly) { message = READ_ONLY; return false }
        val command = SaveControllerCommand(doc)
        execute(command)
        if (!command.wasSuccessful()) { message = command.getFailureReason(); return false }
        message = null
        onSaved(doc)
        return true
    }

    /** True when [animator] runs the controller of [doc]. */
    fun uses(animator: Animator, doc: ControllerDocument): Boolean {
        val path = animator.controllerPath ?: return false
        if (path == doc.path) return true
        val r = try { resolve(path) } catch (e: Exception) { null } ?: return false
        return documents[keyOf(r)] === doc || (doc.file != null && r.file?.absoluteFile?.normalize() == doc.file.absoluteFile.normalize())
    }

    // ---- derived data -----------------------------------------------------------------------------------------

    private var positionsCache: Pair<Long, Map<String, NodePos>>? = null
    private var positionsDoc: ControllerDocument? = null

    /** Box positions (stored, else laid out), cached per document revision. */
    fun positions(): Map<String, NodePos> {
        val doc = document ?: return emptyMap()
        positionsCache?.let { (rev, p) -> if (rev == doc.revision && positionsDoc === doc) return p }
        return ControllerGraphView.positions(doc.asset).also { positionsCache = doc.revision to it; positionsDoc = doc }
    }

    fun arrows(): List<ControllerGraphView.Arrow> = asset?.let { ControllerGraphView.arrows(it, positions()) } ?: emptyList()

    /** Declared parameters, then the undeclared ones the controller reads (ANI-21 Q4). */
    fun parameters(): List<ParameterDef> = asset?.let { ControllerParameters.effective(it) } ?: emptyList()

    fun isDeclared(name: String) = asset?.parameters?.any { it.name == name } == true

    /** A bool parameter named after a state: set by `Animator.play(name)` (§3.4). */
    fun isRequest(p: ParameterDef) = p.type == ParameterType.BOOL && asset?.states?.any { it.name == p.name } == true

    /** Clip picker entries: the preview character's clips, the controller's own, project files, then the poses. */
    fun clipOptions(): List<ClipOption> {
        val a = asset ?: return emptyList()
        val out = LinkedHashMap<String, ClipOption>()
        preview?.animations?.forEach { out.putIfAbsent(it.name, ClipOption(it.name, ClipSource.CHARACTER)) }
        val files = projectClips()
        files.filter { it.path in a.clips }.forEach { out.putIfAbsent(it.name, ClipOption(it.name, ClipSource.CONTROLLER, it.path)) }
        files.forEach { out.putIfAbsent(it.name, ClipOption(it.name, ClipSource.PROJECT, it.path)) }
        a.poses.keys.forEach { out.putIfAbsent(it, ClipOption(it, ClipSource.POSE)) }
        return out.values.toList()
    }

    /** Errors that stop the controller from running on the preview character (empty without a preview). */
    fun errors(): List<String> {
        val a = asset ?: return emptyList()
        val p = preview ?: return emptyList()
        val own = projectClips().filter { it.path in a.clips }.map { it.name }
        return a.issues((p.animations.map { it.name } + p.controllerClips.map { it.name } + own).toSet())
    }

    fun warnings(): List<String> = asset?.warnings() ?: emptyList()

    /** The preview character's state machine when it runs this controller (play mode view, §6). */
    val liveMachine: AnimationStateMachine?
        get() {
            val doc = document ?: return null
            val p = preview ?: return null
            return p.stateMachine?.takeIf { uses(p, doc) }
        }

    // ---- selection --------------------------------------------------------------------------------------------

    fun select(node: String, additive: Boolean = false) {
        selectedTransition = null
        selectedNodes = if (additive) (if (node in selectedNodes) selectedNodes - node else selectedNodes + node) else setOf(node)
    }

    fun selectNodes(nodes: Set<String>) { selectedTransition = null; selectedNodes = nodes }

    fun selectTransition(index: Int?) {
        selectedNodes = emptySet()
        selectedTransition = index?.takeIf { asset?.transitions?.indices?.contains(it) == true }
    }

    fun clearSelection() { selectedNodes = emptySet(); selectedTransition = null }

    /** The single selected state, for the inspector. */
    val selectedState: StateDef?
        get() = selectedNodes.singleOrNull()?.let { n -> asset?.states?.firstOrNull { it.name == n } }

    // ---- edits ------------------------------------------------------------------------------------------------

    private fun edit(action: ControllerAction, block: (ControllerEditor) -> Unit): Boolean {
        val doc = document ?: return false
        if (doc.readOnly) { message = READ_ONLY; return false }
        val editor = ControllerEditor(doc.asset)
        try {
            block(editor)
        } catch (e: IllegalArgumentException) {
            message = e.message
            return false
        }
        message = null
        if (editor.asset == doc.asset) return false
        execute(ControllerEditCommand(doc, doc.asset, editor.asset, action))
        return true
    }

    /** Adds "New State" (or a free variant) at [pos]; selects it. */
    fun addState(pos: NodePos, base: String = "New State"): String? {
        var name: String? = null
        edit(ControllerAction.ADD_STATE) { name = it.addState(base, "", pos) }
        name?.let { select(it) }
        return name
    }

    /** A clip dropped on the canvas: a state named after it that plays it (§3.2). */
    fun addStateFromClip(option: ClipOption, pos: NodePos): String? {
        var name: String? = null
        edit(ControllerAction.ADD_STATE) {
            name = it.addState(option.name, option.name, pos)
            option.path?.takeIf { option.source == ClipSource.PROJECT }?.let(it::addClipFile)
        }
        name?.let { select(it) }
        return name
    }

    fun renameState(old: String, new: String): Boolean {
        val trimmed = new.trim()
        if (trimmed == old) return false
        val ok = edit(ControllerAction.RENAME_STATE) { it.renameState(old, trimmed) }
        if (ok) selectedNodes = selectedNodes.map { if (it == old) trimmed else it }.toSet()
        return ok
    }

    /** Duplicates the selected states (Ctrl+D). Q3: without transitions, unless [withTransitions]. */
    fun duplicateSelection(withTransitions: Boolean = false): List<String> {
        val names = selectedNodes.filter { it != ANY_STATE_NODE }
        if (names.isEmpty()) return emptyList()
        val copies = mutableListOf<String>()
        edit(ControllerAction.DUPLICATE_STATE) { e -> names.forEach { copies += e.duplicateState(it, withTransitions) } }
        if (copies.isNotEmpty()) selectNodes(copies.toSet())
        return copies
    }

    /** Deletes the selected transition, or the selected states (with their transitions; Any State drops its own). */
    fun deleteSelection(): Boolean {
        selectedTransition?.let { index ->
            val ok = edit(ControllerAction.REMOVE_TRANSITION) { it.removeTransition(index) }
            if (ok) selectedTransition = null
            return ok
        }
        val names = selectedNodes
        if (names.isEmpty()) return false
        val ok = edit(ControllerAction.DELETE_STATE) { e ->
            if (ANY_STATE_NODE in names) {
                e.asset.transitions.withIndex().filter { it.value.from == null }.map { it.index }.reversed().forEach(e::removeTransition)
            }
            names.filter { it != ANY_STATE_NODE }.forEach(e::removeState)
        }
        if (ok) clearSelection()
        return ok
    }

    fun setDefault(state: String) = edit(ControllerAction.SET_DEFAULT) { it.setDefaultState(state) }

    fun setControllerName(name: String) = edit(ControllerAction.SET_CONTROLLER) { it.setName(name.trim()) }

    /** Sets the state's clip; a project file not yet listed is added to the controller's `clips` (§4.3). */
    fun setClip(state: String, option: ClipOption?) = edit(ControllerAction.SET_MOTION) { e ->
        e.updateState(state) { it.copy(motion = option?.name ?: "") }
        if (option?.source == ClipSource.PROJECT) option.path?.let(e::addClipFile)
    }

    fun updateState(state: String, change: (StateDef) -> StateDef) = edit(ControllerAction.SET_STATE) { it.updateState(state, change) }

    fun addEvent(state: String, time: Float = 0f, name: String = "event") =
        edit(ControllerAction.ADD_EVENT) { it.addEvent(state, AnimEventDef(time.coerceIn(0f, 1f), name)) }

    fun removeEvent(state: String, index: Int) = edit(ControllerAction.REMOVE_EVENT) { it.removeEvent(state, index) }

    fun updateEvent(state: String, index: Int, event: AnimEventDef) = edit(ControllerAction.SET_STATE) { e ->
        e.updateState(state) { s ->
            s.copy(events = s.events.mapIndexed { i, ev -> if (i == index) event.copy(time = event.time.coerceIn(0f, 1f)) else ev }.sortedBy { it.time })
        }
    }

    // ---- drags ------------------------------------------------------------------------------------------------

    private var dragStart: Pair<AnimationControllerAsset, Map<String, NodePos>>? = null

    /** Starts moving the selected boxes (or [node] alone when it is not selected). */
    fun beginDrag(node: String) {
        val doc = document ?: return
        if (doc.readOnly) return
        if (node !in selectedNodes) select(node)
        val pos = positions()
        dragStart = doc.asset to selectedNodes.mapNotNull { n -> pos[n]?.let { n to it } }.toMap()
    }

    /** Moves the dragged boxes by ([dx], [dy]) canvas units from where the drag started; previewed live. */
    fun dragTo(dx: Float, dy: Float) {
        val doc = document ?: return
        val (start, from) = dragStart ?: return
        doc.replace(start.copy(layout = start.layout + from.mapValues { (_, p) -> NodePos(p.x + dx, p.y + dy) }))
    }

    /** Ends the drag: one command for the whole move. */
    fun endDrag() {
        val doc = document ?: return
        val (start, _) = dragStart ?: return
        dragStart = null
        val after = doc.asset
        if (after == start) return
        doc.replace(start)
        execute(ControllerEditCommand(doc, start, after, ControllerAction.MOVE_STATES))
    }

    val dragging: Boolean get() = dragStart != null

    // ---- transitions ------------------------------------------------------------------------------------------

    fun startConnect(from: String) { if (document?.readOnly == false) connectFrom = from }
    fun cancelConnect() { connectFrom = null }

    /**
     * Ends "Make transition" on [to]. A new transition from a state waits for the end of the clip (it fires as
     * soon as it exists); from Any State it needs a condition first.
     */
    fun connectTo(to: String): Int? {
        val from = connectFrom ?: return null
        connectFrom = null
        if (to == ANY_STATE_NODE || to == from) return null
        val source = if (from == ANY_STATE_NODE) null else from
        val ok = edit(ControllerAction.ADD_TRANSITION) { it.addTransition(TransitionDef(source, to, hasExitTime = source != null)) }
        return if (ok) asset!!.transitions.lastIndex.also { selectTransition(it) } else null
    }

    /** "Go here when requested" (§3.4): [from] -> [to] when the bool named after [to] is on. */
    fun addRequestTransition(from: String, to: String): Int? {
        var index: Int? = null
        edit(ControllerAction.ADD_TRANSITION) { index = it.addRequestTransition(if (from == ANY_STATE_NODE) null else from, to) }
        index?.let { selectTransition(it) }
        return index
    }

    fun updateTransition(index: Int, change: (TransitionDef) -> TransitionDef) =
        edit(ControllerAction.SET_TRANSITION) { it.updateTransition(index, change) }

    /** Moves a transition up (-1) or down (+1) among the transitions checked from the same source (priority). */
    fun moveTransition(index: Int, direction: Int): Boolean {
        val a = asset ?: return false
        val source = a.transitions.getOrNull(index)?.from
        val siblings = a.transitions.indices.filter { a.transitions[it].from == source }
        val pos = siblings.indexOf(index)
        val target = siblings.getOrNull(pos + direction) ?: return false
        val ok = edit(ControllerAction.MOVE_TRANSITION) { it.moveTransition(index, target) }
        if (ok && selectedTransition == index) selectedTransition = target
        return ok
    }

    fun removeTransition(index: Int): Boolean {
        val ok = edit(ControllerAction.REMOVE_TRANSITION) { it.removeTransition(index) }
        if (ok) selectedTransition = null
        return ok
    }

    /** Transitions leaving [node] in evaluation order (the order the inspector lists). */
    fun transitionsFrom(node: String): List<Int> {
        val a = asset ?: return emptyList()
        val source = if (node == ANY_STATE_NODE) null else node
        return a.transitions.indices.filter { a.transitions[it].from == source }
    }

    /** Adds a condition on the first parameter (declaring a bool named after the target when there is none). */
    fun addCondition(index: Int): Boolean {
        val a = asset ?: return false
        val t = a.transitions.getOrNull(index) ?: return false
        val params = parameters()
        return edit(ControllerAction.SET_TRANSITION) { e ->
            val p = params.firstOrNull() ?: ParameterDef(t.to, ParameterType.BOOL).also { e.addParameter(it) }
            e.updateTransition(index) { it.copy(conditions = it.conditions + TransitionCondition(p.name, ControllerParameters.operatorsFor(p.type).first())) }
        }
    }

    fun removeCondition(index: Int, condition: Int) = edit(ControllerAction.SET_TRANSITION) { e ->
        e.updateTransition(index) { t -> t.copy(conditions = t.conditions.filterIndexed { i, _ -> i != condition }) }
    }

    /** Changes a condition; picking a parameter of another type switches to that type's first operator. */
    fun setCondition(index: Int, condition: Int, value: TransitionCondition) = edit(ControllerAction.SET_TRANSITION) { e ->
        val type = parameters().firstOrNull { it.name == value.parameter }?.type
        val fitted = if (type != null && value.op !in ControllerParameters.operatorsFor(type)) value.copy(op = ControllerParameters.operatorsFor(type).first()) else value
        e.updateTransition(index) { t -> t.copy(conditions = t.conditions.mapIndexed { i, c -> if (i == condition) fitted else c }) }
    }

    // ---- parameters -------------------------------------------------------------------------------------------

    fun addParameter(type: ParameterType): String? {
        val base = when (type) { ParameterType.FLOAT -> "newFloat"; ParameterType.BOOL -> "newBool"; ParameterType.TRIGGER -> "newTrigger" }
        val taken = parameters().map { it.name }.toSet()
        var name = base
        var n = 2
        while (name in taken) name = base + n++
        return if (edit(ControllerAction.ADD_PARAMETER) { it.addParameter(ParameterDef(name, type)) }) name else null
    }

    fun renameParameter(old: String, new: String): Boolean {
        val trimmed = new.trim()
        if (trimmed == old) return false
        return edit(ControllerAction.RENAME_PARAMETER) { it.renameParameter(old, trimmed) }
    }

    /** Changes type or default; conditions whose operator no longer fits are flagged by [warnings], not deleted. */
    fun setParameter(name: String, type: ParameterType, default: Float) =
        edit(ControllerAction.SET_PARAMETER) { it.updateParameter(name) { p -> p.copy(type = type, default = default) } }

    fun removeParameter(name: String) = edit(ControllerAction.REMOVE_PARAMETER) { it.removeParameter(name) }

    // ---- copy / paste (Q8) ------------------------------------------------------------------------------------

    fun copySelection(): Boolean {
        val a = asset ?: return false
        val names = selectedNodes.filter { it != ANY_STATE_NODE }.toSet()
        if (names.isEmpty()) return false
        val pos = positions()
        clipboard = CopiedStates(
            a.states.filter { it.name in names },
            a.transitions.filter { it.from in names && it.to in names },
            pos.filterKeys { it in names },
        )
        return true
    }

    fun paste(offset: NodePos = NodePos(40f, 40f)): Boolean {
        val c = clipboard ?: return false
        var renames: Map<String, String> = emptyMap()
        val ok = edit(ControllerAction.PASTE_STATES) { renames = it.pasteStates(c.states, c.transitions, c.positions, offset) }
        if (ok) selectNodes(renames.values.toSet())
        return ok
    }

    // ---- play mode (§6) ---------------------------------------------------------------------------------------

    /** Writes a live parameter value on the preview's running state machine: not undoable, not saved. */
    fun setLive(p: ParameterDef, value: Float) {
        val params = liveMachine?.parameters ?: return
        when (p.type) {
            ParameterType.FLOAT -> params.setFloat(p.name, value)
            ParameterType.BOOL -> params.setBool(p.name, value != 0f)
            ParameterType.TRIGGER -> if (value != 0f) params.setTrigger(p.name) else params.consumeTrigger(p.name)
        }
    }

    /** The live value of [p] (bools and triggers as 0/1), or null outside play mode. */
    fun liveValue(p: ParameterDef): Float? {
        val params = liveMachine?.parameters ?: return null
        return when (p.type) {
            ParameterType.FLOAT -> params.getFloat(p.name)
            ParameterType.BOOL -> if (params.getBool(p.name)) 1f else 0f
            ParameterType.TRIGGER -> if (params.hasTrigger(p.name)) 1f else 0f
        }
    }

    companion object {
        const val READ_ONLY = "This controller is an engine default: make a project copy to edit it"

        /** Operators offered for a condition on a parameter of [type] (ANI-21 §3.3). */
        fun operatorsFor(type: ParameterType?): List<ConditionOp> =
            type?.let(ControllerParameters::operatorsFor) ?: ConditionOp.entries
    }
}
