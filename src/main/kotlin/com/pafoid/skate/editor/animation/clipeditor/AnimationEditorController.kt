package com.pafoid.skate.editor.animation.clipeditor

import com.pafoid.skate.editor.animation.mocap.TargetRig
import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.commands.animation.ClipEditCommand
import com.pafoid.skate.editor.commands.animation.CreateAnimationClipCommand
import com.pafoid.skate.editor.commands.animation.SaveAnimationClipCommand
import com.pafoid.skate.engine.animation.edit.ClipEditing
import com.pafoid.skate.engine.animation.edit.ClipEvaluator
import com.pafoid.skate.engine.animation.edit.ClipRig
import com.pafoid.skate.engine.animation.edit.CopiedKeys
import com.pafoid.skate.engine.animation.edit.KeyEditing
import com.pafoid.skate.engine.animation.edit.KeyRef
import com.pafoid.skate.engine.animation.edit.SkAnimFormat
import com.pafoid.skate.engine.animation.edit.SkClip
import com.pafoid.skate.engine.animation.edit.SkEvent
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.AnimationPath
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import org.joml.Matrix4f
import java.io.File
import kotlin.math.abs

/**
 * State and actions of the Animation editor window (ANI-14, ANI-12 §2.1): one open `.skanim` clip, the playhead,
 * playback, key and event selection, and every edit as a [ClipEditCommand] through [execute] (so Undo/Redo and the
 * Command History see them). Drags preview live on the document and commit one command on release.
 * No ImGui here: the window is a thin view and this class is unit-tested.
 */
class AnimationEditorController(
    private val execute: (Command) -> Unit,
    private val rigFor: (TargetRig) -> Skeleton?,
    /** Reads an imported clip (FBX, glTF, ...) for "Edit a copy". */
    private val loadAnimationFile: (File) -> Animation = { error("Cannot read ${it.name}") },
) {
    /** Open documents by file, so reopening a clip keeps its undo history coherent. */
    private val documents = HashMap<String, ClipDocument>()

    var document: ClipDocument? = null; private set
    val clip: SkClip? get() = document?.clip

    /** Playhead in seconds. */
    var time = 0f; private set
    var playing = false; private set
    /** Playback speed multiplier (preview only, not saved). */
    var speed = 1f
        set(value) { field = value.coerceIn(0.05f, 4f) }
    /** Snap the playhead and key moves to the clip's frames. */
    var snap = true
    var rig = TargetRig.SKATER
        set(value) { if (field != value) { field = value; rigCache = null } }

    var selection: Set<KeyRef> = emptySet(); private set
    var selectedBones: Set<String> = emptySet(); private set
    var selectedEvent: Int? = null; private set
    /** Last problem, for the window (open/save errors). */
    var error: String? = null; private set

    private var clipboard: CopiedKeys? = null
    val canPaste: Boolean get() = clipboard?.keys?.isNotEmpty() == true

    // ---- open ------------------------------------------------------------------------------------------------

    /** Opens a `.skanim` clip. Any other animation file opens as an editable copy next to it ([editCopy]). */
    fun open(file: File): Boolean {
        error = null
        if (!SkAnimFormat.isSkAnim(file.path)) return editCopy(file)
        val key = file.absoluteFile.normalize().path
        val doc = documents[key] ?: try {
            ClipDocument(file.absoluteFile, SkAnimFormat.decode(file.readText())).also { documents[key] = it }
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
            return false
        }
        show(doc)
        return true
    }

    /**
     * Imported clips stay read-only (ANI-12 §2.2): this writes a `.skanim` copy next to [source] and opens it.
     */
    fun editCopy(source: File): Boolean {
        val animation = try { loadAnimationFile(source) } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
            return false
        }
        val base = animation.name.ifBlank { source.nameWithoutExtension }
        val target = CreateAnimationClipCommand.freeFile(source.absoluteFile.parentFile, base)
        val copy = SkAnimFormat.fromAnimation(animation).copy(name = target.nameWithoutExtension)
        execute(CreateAnimationClipCommand(copy, target))
        return target.isFile && open(target)
    }

    fun close() {
        document = null
        playing = false
        clearSelection()
    }

    private fun show(doc: ClipDocument) {
        if (document !== doc) {
            document = doc
            time = 0f
            playing = false
            clearSelection()
        }
    }

    // ---- playback --------------------------------------------------------------------------------------------

    fun seek(t: Float) {
        val c = clip ?: return
        val clamped = t.coerceIn(0f, c.duration)
        time = if (snap) ClipEditing.snap(clamped, c.fps).coerceIn(0f, c.duration) else clamped
    }

    fun stepFrames(frames: Int) {
        val c = clip ?: return
        playing = false
        val frame = Math.round(time * c.fps) + frames
        time = (frame / c.fps).coerceIn(0f, c.duration)
    }

    fun toStart() { playing = false; time = 0f }
    fun toEnd() { playing = false; time = clip?.duration ?: 0f }

    fun togglePlay() {
        val c = clip ?: return
        if (!playing && !c.loop && time >= c.duration) time = 0f
        playing = !playing
    }

    /** Advances playback by [dt] seconds of real time; looping clips wrap, others stop at the end. */
    fun tick(dt: Float) {
        val c = clip ?: return
        if (!playing) return
        time += dt * speed
        if (time >= c.duration) {
            if (c.loop) time %= c.duration else { time = c.duration; playing = false }
        }
    }

    // ---- selection -------------------------------------------------------------------------------------------

    fun clearSelection() { selection = emptySet(); selectedBones = emptySet(); selectedEvent = null }

    fun selectKey(ref: KeyRef, additive: Boolean = false) {
        selection = if (additive) (if (ref in selection) selection - ref else selection + ref) else setOf(ref)
        selectedEvent = null
    }

    /** Box selection over the given track rows between two times. */
    fun selectBox(tracks: Collection<Pair<String, AnimationPath>>, t0: Float, t1: Float, additive: Boolean = false) {
        val c = clip ?: return
        val hit = ClipEditing.keysIn(c, tracks, t0, t1)
        selection = if (additive) selection + hit else hit
        selectedEvent = null
    }

    fun selectAllKeys() { clip?.let { selection = ClipEditing.refsOf(it).toSet() } }

    /** Selects a bone (track tree or a joint in the preview) and all its keys. */
    fun selectBone(bone: String, additive: Boolean = false) {
        val c = clip ?: return
        selectedBones = if (additive) (if (bone in selectedBones) selectedBones - bone else selectedBones + bone) else setOf(bone)
        val refs = c.tracks.filter { it.bone == bone }.flatMap { t -> t.keys.map { KeyRef(t.bone, t.path, it.time) } }
        selection = if (additive) selection + refs else refs.toSet()
        selectedEvent = null
    }

    fun selectEvent(index: Int?) { selectedEvent = index; if (index != null) selection = emptySet() }

    /** The selected key when exactly one is selected (the key inspector). */
    val singleKey: KeyRef? get() = selection.singleOrNull()?.takeIf { ref -> clip?.let { c -> ClipEditing.resolve(c, setOf(ref)).isNotEmpty() } == true }

    // ---- key edits -------------------------------------------------------------------------------------------

    /** "Key selected bones": keys the selected bones with the pose they have at the playhead. */
    fun keySelectedBones() {
        val c = clip ?: return
        if (selectedBones.isEmpty()) return
        val (next, keyed) = ClipEditing.keyBones(c, selectedBones, time)
        commit(c, next, "key_bones")
        selection = keyed
    }

    fun deleteSelectedKeys() {
        val c = clip ?: return
        if (selection.isEmpty()) return
        commit(c, ClipEditing.deleteKeys(c, selection), "delete_keys")
        selection = emptySet()
    }

    /** Sets the value of a key (rotations as a quaternion `[x, y, z, w]`, normalized here). */
    fun setKeyValue(ref: KeyRef, value: FloatArray) {
        val c = clip ?: return
        commit(c, ClipEditing.setKeyValue(c, ref, value), "set_key")
    }

    /** Moves the single selected key to [newTime] (the inspector's Time field). */
    fun setKeyTime(ref: KeyRef, newTime: Float) {
        val c = clip ?: return
        val (next, moved) = ClipEditing.moveKeys(c, setOf(ref), snapped(newTime) - ref.time)
        commit(c, next, "move_keys")
        selection = moved
    }

    /** Scales the selected keys' times by [factor] around the first selected key. */
    fun scaleSelection(factor: Float) {
        val c = clip ?: return
        if (selection.size < 2 || factor <= 0f) return
        val (next, scaled) = ClipEditing.scaleKeys(c, selection, selection.minOf { it.time }, factor)
        commit(c, next, "scale_keys")
        selection = scaled
    }

    fun copySelection() { clip?.let { clipboard = ClipEditing.copy(it, selection) } }

    fun cutSelection() { copySelection(); deleteSelectedKeys() }

    fun paste() {
        val c = clip ?: return
        val copied = clipboard ?: return
        val (next, pasted) = ClipEditing.paste(c, copied, time)
        commit(c, next, "paste_keys")
        selection = pasted
    }

    // ---- drags (one command per gesture) ----------------------------------------------------------------------

    private var dragBase: SkClip? = null
    private var dragSelection: Set<KeyRef> = emptySet()
    private var dragEvent: Int = -1
    val dragging: Boolean get() = dragBase != null

    fun beginKeyDrag() {
        val c = clip ?: return
        if (selection.isEmpty()) return
        dragBase = c; dragSelection = selection; dragEvent = -1
    }

    /** Moves the dragged keys [dt] seconds from where the drag started. */
    fun dragKeys(dt: Float) {
        val base = dragBase ?: return
        val doc = document ?: return
        val step = if (snap) ClipEditing.snap(dt, base.fps) else dt
        val (next, moved) = ClipEditing.moveKeys(base, dragSelection, step)
        doc.replace(next)
        selection = moved
    }

    fun beginEventDrag(index: Int) {
        val c = clip ?: return
        if (index !in c.events.indices) return
        dragBase = c; dragEvent = index; selectedEvent = index; selection = emptySet()
    }

    fun dragEventTo(t: Float) {
        val base = dragBase ?: return
        val doc = document ?: return
        if (dragEvent < 0) return
        val name = base.events[dragEvent]
        val next = KeyEditing.moveEvent(base, dragEvent, snapped(t))
        doc.replace(next)
        selectedEvent = next.events.indexOfFirst { it.name == name.name && abs(it.time - snapped(t).coerceIn(0f, base.duration)) < 1e-4f }
            .takeIf { it >= 0 }
    }

    /** Ends a drag: one undo step for the whole gesture (nothing when the drag moved nothing). */
    fun endDrag() {
        val base = dragBase ?: return
        val doc = document ?: return
        dragBase = null
        val result = doc.clip
        if (result == base) return
        doc.replace(base)
        commit(base, result, if (dragEvent >= 0) "move_event" else "move_keys")
    }

    fun cancelDrag() {
        val base = dragBase ?: return
        dragBase = null
        document?.replace(base)
        selection = dragSelection
    }

    // ---- events ----------------------------------------------------------------------------------------------

    fun addEvent(name: String) {
        val c = clip ?: return
        val clean = name.trim().ifEmpty { return }
        val next = KeyEditing.addEvent(c, SkEvent(time, clean))
        commit(c, next, "add_event")
        selectedEvent = next.events.indexOfFirst { it.name == clean && abs(it.time - time) < 1e-4f }.takeIf { it >= 0 }
    }

    fun renameEvent(index: Int, name: String) {
        val c = clip ?: return
        val clean = name.trim()
        if (index !in c.events.indices || clean.isEmpty() || c.events[index].name == clean) return
        commit(c, c.copy(events = c.events.toMutableList().also { it[index] = it[index].copy(name = clean) }), "rename_event")
    }

    fun deleteEvent(index: Int) {
        val c = clip ?: return
        if (index !in c.events.indices) return
        commit(c, KeyEditing.removeEvent(c, index), "delete_event")
        selectedEvent = null
    }

    // ---- clip properties -------------------------------------------------------------------------------------

    fun setLoop(loop: Boolean) { clip?.let { if (it.loop != loop) commit(it, it.copy(loop = loop), "set_loop") } }

    fun setFps(fps: Float) {
        val c = clip ?: return
        if (fps <= 0f || fps == c.fps) return
        commit(c, c.copy(fps = fps), "set_fps")
    }

    /** Retimes the whole clip to [seconds]: every key and event is stretched (slower or faster motion). */
    fun retimeClip(seconds: Float) {
        val c = clip ?: return
        if (seconds <= 0f || abs(seconds - c.duration) < 1e-5f) return
        val f = seconds / c.duration
        commit(c, ClipEditing.retimeClip(c, seconds), "retime_clip")
        selection = selection.map { it.copy(time = it.time * f) }.toSet()
        time = (time * f).coerceIn(0f, seconds)
    }

    /** Trims or extends the clip to [seconds] without moving keys; keys past the end are dropped. */
    fun setDuration(seconds: Float) {
        val c = clip ?: return
        if (seconds <= 0f || abs(seconds - c.duration) < 1e-5f) return
        commit(c, ClipEditing.setDuration(c, seconds), "set_duration")
        selection = selection.filter { it.time <= seconds + 1e-4f }.toSet()
        time = time.coerceAtMost(seconds)
    }

    fun save() {
        val doc = document ?: return
        val command = SaveAnimationClipCommand(doc)
        execute(command)
        error = command.getFailureReason()
    }

    // ---- preview ---------------------------------------------------------------------------------------------

    private var rigCache: Pair<Skeleton, ClipRig>? = null
    private var evaluatorCache: Triple<ClipDocument, Long, ClipEvaluator>? = null

    /** The preview rig, or null when the chosen rig is not available (no selected skeleton). */
    fun previewRig(): ClipRig? {
        val skeleton = runCatching { rigFor(rig) }.getOrNull() ?: return null
        rigCache?.let { (s, r) -> if (s === skeleton) return r }
        return ClipRig(skeleton).also { rigCache = skeleton to it }
    }

    /** Bone locals of the clip at the playhead on [previewRig] (the runtime composition, ANI-13). */
    fun pose(): Pair<ClipRig, Array<Matrix4f>>? {
        val doc = document ?: return null
        val rigNow = previewRig() ?: return null
        val cached = evaluatorCache
        val evaluator = if (cached != null && cached.first === doc && cached.second == doc.revision && rigCache?.second === rigNow) cached.third
        else ClipEvaluator(doc.clip, rigNow).also { evaluatorCache = Triple(doc, doc.revision, it) }
        return rigNow to evaluator.locals(time)
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    private fun snapped(t: Float): Float = clip?.let { if (snap) ClipEditing.snap(t, it.fps) else t } ?: t

    private fun commit(before: SkClip, after: SkClip, action: String) {
        val doc = document ?: return
        if (before == after) return
        execute(ClipEditCommand(doc, before, after, action))
        pruneSelection()
    }

    private var seenRevision = -1L

    /**
     * Drops selected keys and events that no longer exist and keeps the playhead inside the clip. The window calls it
     * every frame: Undo/Redo change the clip from outside this class. Cheap when nothing changed.
     */
    fun refresh() {
        val doc = document ?: return
        if (doc.revision == seenRevision) return
        pruneSelection()
    }

    private fun pruneSelection() {
        val doc = document ?: return
        seenRevision = doc.revision
        val c = doc.clip
        if (selection.isNotEmpty()) {
            val valid = ClipEditing.refsOf(c)
            selection = selection.mapNotNull { r -> valid.firstOrNull { it.bone == r.bone && it.path == r.path && abs(it.time - r.time) <= 1e-4f } }.toSet()
        }
        if (selectedEvent != null && selectedEvent !in c.events.indices) selectedEvent = null
        time = time.coerceIn(0f, c.duration)
    }
}
