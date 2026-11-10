package com.pafoid.skate.editor.animation.clipeditor

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.commands.CommandNames
import com.pafoid.skate.editor.commands.animation.ClipEditCommand
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.animation.edit.KeyRef
import com.pafoid.skate.engine.animation.edit.SkAnimFormat
import com.pafoid.skate.engine.animation.edit.SkClip
import com.pafoid.skate.engine.animation.edit.SkEvent
import com.pafoid.skate.engine.animation.edit.SkKey
import com.pafoid.skate.engine.animation.edit.SkTrack
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.AnimationChannel
import com.pafoid.skate.engine.assets.data.models.animations.AnimationPath.ROTATION
import com.pafoid.skate.engine.assets.data.models.animations.AnimationPath.TRANSLATION
import com.pafoid.skate.engine.assets.data.models.animations.AnimationSampler
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.InterpolationType
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.core.StringManager
import io.mockk.mockk
import org.joml.Matrix4f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** ANI-14 acceptance: a clip is scrubbed, keyed, retimed and saved, every edit undoable (no ImGui, no GL). */
class AnimationEditorControllerTest {
    @TempDir lateinit var dir: File

    private val undo = UndoRedoManager(mockk(relaxed = true), mockk(relaxed = true))
    private val executed = ArrayList<Command>()
    private val skeleton = Skeleton(Bone(0, "Hips", Matrix4f().translation(0f, 1f, 0f)).also { hips ->
        hips.addChild(Bone(1, "Spine", Matrix4f().translation(0f, 0.3f, 0f)))
    }, 2)

    private fun key(t: Float, x: Float) = SkKey(t, listOf(x, 0f, 0f))
    private val walk = SkClip(
        name = "walk", fps = 10f, duration = 2f,
        tracks = listOf(SkTrack("Hips", TRANSLATION, keys = listOf(key(0f, 0f), key(1f, 2f), key(2f, 0f)))),
        events = listOf(SkEvent(0.5f, "step")),
    )

    private fun file(clip: SkClip = walk) = File(dir, "${clip.name}.skanim").apply { writeText(SkAnimFormat.encode(clip)) }

    private fun controller(rig: Skeleton? = skeleton, load: (File) -> Animation = { error("no") }) = AnimationEditorController(
        execute = { executed += it; undo.executeCommand(it) },
        rigFor = { rig },
        loadAnimationFile = load,
    )

    private fun opened(clip: SkClip = walk) = controller().also { assertTrue(it.open(file(clip)), it.error) }

    private fun hipsTimes(c: AnimationEditorController) = c.clip!!.track("Hips", TRANSLATION)!!.keys.map { it.time }

    @Test
    fun Open_ReadsTheClipAndReportsBadFiles() {
        val c = opened()
        assertEquals("walk", c.clip!!.name)
        assertFalse(c.document!!.dirty)
        val bad = File(dir, "bad.skanim").apply { writeText("{ nope") }
        assertFalse(c.open(bad))
        assertTrue(c.error!!.contains("Invalid .skanim"), c.error)
        assertEquals("walk", c.clip!!.name, "a failed open keeps the current clip")
    }

    @Test
    fun Scrub_SnapsToFramesAndStaysInTheClip() {
        val c = opened()
        c.seek(0.53f); assertEquals(0.5f, c.time, 1e-6f)
        c.snap = false; c.seek(0.53f); assertEquals(0.53f, c.time, 1e-6f)
        c.seek(9f); assertEquals(2f, c.time)
        c.seek(-1f); assertEquals(0f, c.time)
        c.stepFrames(3); assertEquals(0.3f, c.time, 1e-6f)
        c.toEnd(); assertEquals(2f, c.time)
    }

    @Test
    fun Playback_LoopsOrStopsAtTheEnd() {
        val c = opened()
        c.togglePlay(); c.speed = 2f
        c.tick(0.75f); assertEquals(1.5f, c.time, 1e-5f)
        c.tick(0.5f); assertEquals(0.5f, c.time, 1e-5f, "a looping clip wraps")
        c.setLoop(false)
        c.tick(2f); assertEquals(2f, c.time); assertFalse(c.playing, "a one-shot clip stops at its end")
        c.togglePlay(); assertEquals(0f, c.time, "play again from the start")
    }

    @Test
    fun DragKeys_IsOneUndoStep() {
        val c = opened()
        c.selectKey(KeyRef("Hips", TRANSLATION, 1f))
        c.beginKeyDrag()
        c.dragKeys(0.12f); c.dragKeys(0.26f); c.dragKeys(0.31f)
        assertEquals(listOf(0f, 1.3f, 2f), hipsTimes(c).map { Math.round(it * 10) / 10f }, "previewed live, snapped to frames")
        c.endDrag()
        assertEquals(1, executed.size, "the gesture is one command")
        assertEquals("move_keys", (executed.single() as ClipEditCommand).action)
        assertEquals(setOf(KeyRef("Hips", TRANSLATION, c.selection.single().time)), c.selection)
        assertTrue(c.document!!.dirty)

        undo.undo()
        assertEquals(listOf(0f, 1f, 2f), hipsTimes(c))
        undo.redo()
        assertEquals(1.3f, hipsTimes(c)[1], 1e-5f)

        c.selectKey(KeyRef("Hips", TRANSLATION, 0f)); c.beginKeyDrag(); c.dragKeys(0.5f); c.cancelDrag()
        assertEquals(0f, hipsTimes(c)[0], "Esc puts the keys back")
        c.beginKeyDrag(); c.endDrag()
        assertEquals(1, executed.size, "a click without a move adds no history")
    }

    @Test
    fun KeyBones_AddsThePoseAtThePlayhead_Undoable() {
        val c = opened()
        c.seek(0.5f)
        c.selectBone("Hips")
        assertEquals(3, c.selection.size, "selecting a bone selects its keys")
        c.selectBone("Spine", additive = true)
        c.keySelectedBones()
        val hips = c.clip!!.track("Hips", TRANSLATION)!!
        assertEquals(4, hips.keys.size)
        assertEquals(1f, hips.keys[1].value[0], 1e-5f, "the key holds the played value")
        assertNotNull(c.clip!!.track("Spine", ROTATION), "a bone without tracks gets a rest-pose key")
        assertEquals(setOf(KeyRef("Hips", TRANSLATION, 0.5f), KeyRef("Spine", ROTATION, 0.5f)), c.selection)
        undo.undo()
        assertEquals(walk, c.clip)
        c.refresh()
        assertTrue(c.selection.isEmpty(), "keys removed by Undo leave the selection")
    }

    @Test
    fun KeyValueTimeDeleteCopyPaste() {
        val c = opened()
        val mid = KeyRef("Hips", TRANSLATION, 1f)
        c.setKeyValue(mid, floatArrayOf(3f, 0f, 0f))
        assertEquals(3f, c.clip!!.track("Hips", TRANSLATION)!!.keys[1].value[0])
        c.setKeyTime(mid, 1.24f)
        assertEquals(1.2f, hipsTimes(c)[1], 1e-5f)
        c.selectKey(KeyRef("Hips", TRANSLATION, hipsTimes(c)[1]))
        c.copySelection()
        c.seek(0.5f); c.paste()
        assertEquals(4, hipsTimes(c).size)
        c.deleteSelectedKeys()
        assertEquals(3, hipsTimes(c).size)
        assertEquals(listOf("set_key", "move_keys", "paste_keys", "delete_keys"), executed.map { (it as ClipEditCommand).action })
        repeat(4) { undo.undo() }
        assertEquals(walk, c.clip)
    }

    @Test
    fun RetimeAndTrim_AreUndoable() {
        val c = opened()
        c.seek(1f)
        c.retimeClip(4f)
        assertEquals(4f, c.clip!!.duration)
        assertEquals(listOf(0f, 2f, 4f), hipsTimes(c))
        assertEquals(1f, c.clip!!.events.single().time)
        assertEquals(2f, c.time, "the playhead stays on the same pose")
        c.setDuration(3f)
        assertEquals(listOf(0f, 2f), hipsTimes(c))
        c.setFps(24f)
        assertEquals(24f, c.clip!!.fps)
        repeat(3) { undo.undo() }
        assertEquals(walk, c.clip)
        c.selectKey(KeyRef("Hips", TRANSLATION, 0f)); c.selectKey(KeyRef("Hips", TRANSLATION, 1f), additive = true)
        c.scaleSelection(1.5f)
        assertEquals(listOf(0f, 1.5f, 2f), hipsTimes(c))
    }

    @Test
    fun Events_AddDragRenameDelete() {
        val c = opened()
        c.seek(1.5f)
        c.addEvent("  land ")
        assertEquals(listOf("step", "land"), c.clip!!.events.map { it.name })
        assertEquals(1, c.selectedEvent)
        c.beginEventDrag(0); c.dragEventTo(0.83f); c.dragEventTo(1.87f); c.endDrag()
        assertEquals(listOf(SkEvent(1.5f, "land"), SkEvent(1.9f, "step")), c.clip!!.events.map { it.copy(time = Math.round(it.time * 10) / 10f) })
        c.renameEvent(1, "kick")
        c.deleteEvent(0)
        assertEquals(listOf("kick"), c.clip!!.events.map { it.name })
        assertEquals(listOf("add_event", "move_event", "rename_event", "delete_event"), executed.map { (it as ClipEditCommand).action })
        repeat(4) { undo.undo() }
        assertEquals(walk.events, c.clip!!.events)
    }

    @Test
    fun Save_WritesTheFile_AndUndoMakesItDirtyAgain() {
        val c = opened()
        val f = c.document!!.file
        c.selectAllKeys(); c.deleteSelectedKeys()
        c.save()
        assertNull(c.error)
        assertFalse(c.document!!.dirty)
        assertEquals(c.clip, SkAnimFormat.decode(f.readText()))
        assertFalse(File(f.path + ".tmp").exists())
        undo.undo()
        assertTrue(c.document!!.dirty, "undo after a save: unsaved changes again")

        f.setReadOnly()
        c.save()
        assertNotNull(c.error, "a failed save is reported")
        assertTrue(c.document!!.dirty)
        f.setWritable(true)
    }

    @Test
    fun ReopeningAClip_KeepsItsDocumentSoUndoStillApplies() {
        val c = opened()
        val doc = c.document
        c.setLoop(false)
        c.close()
        assertNull(c.clip)
        c.open(doc!!.file)
        assertTrue(c.document === doc)
        undo.undo()
        assertTrue(c.clip!!.loop)
    }

    @Test
    fun EditCopy_MakesAnSkanimNextToAnImportedClip() {
        val source = File(dir, "kickflip.glb").apply { writeBytes(ByteArray(4)) }
        val sampler = AnimationSampler(floatArrayOf(0f, 1f), floatArrayOf(0f, 0f, 0f, 1f, 2f, 3f), InterpolationType.LINEAR, 3)
        val c = controller(load = { Animation("kickflip", listOf(AnimationChannel(sampler, "Hips", TRANSLATION)), 1f, it.path) })
        assertTrue(c.open(source), c.error)
        assertEquals(File(dir, "kickflip.skanim").absolutePath, c.document!!.file.path)
        assertEquals(2, c.clip!!.track("Hips", TRANSLATION)!!.keys.size)
        assertEquals(4, source.length(), "the imported file is untouched")
        assertTrue(c.open(source))
        assertEquals("kickflip 2.skanim", c.document!!.file.name, "another copy never overwrites")
    }

    @Test
    fun Preview_EvaluatesTheEditedClipOnTheRig() {
        val c = opened()
        c.seek(1f)
        val (rig, locals) = c.pose()!!
        assertEquals(2, rig.boneCount)
        val joints = SkeletonPreview().joints(rig, locals)
        assertEquals(2f, joints[0].x, 1e-5f, "Hips translated by the clip")
        assertEquals(1.3f, joints[1].y, 1e-5f, "Spine follows its parent")
        c.setKeyValue(KeyRef("Hips", TRANSLATION, 1f), floatArrayOf(5f, 0f, 0f))
        assertEquals(5f, SkeletonPreview().joints(rig, c.pose()!!.second)[0].x, 1e-5f, "the preview sees edits")
        assertNull(controller(rig = null).also { it.open(file()) }.pose(), "no rig, no preview")
    }

    @Test
    fun EveryEditAction_HasATranslatedName() {
        val strings = StringManager(mockk(relaxed = true), "en").apply { loadStrings(com.pafoid.skate.engine.assets.Assets.Strings.EDITOR_STRINGS) }
        val doc = ClipDocument(File(dir, "x.skanim"), walk)
        listOf("key_bones", "delete_keys", "set_key", "move_keys", "scale_keys", "paste_keys", "move_event", "add_event",
            "rename_event", "delete_event", "set_loop", "set_fps", "retime_clip", "set_duration").forEach { action ->
            val command = ClipEditCommand(doc, walk, walk, action)
            assertTrue(strings.hasString(command.displayNameKey), command.displayNameKey)
            assertEquals(strings.getString(command.displayNameKey), CommandNames.displayName(command, strings))
        }
    }
}
