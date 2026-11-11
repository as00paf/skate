package com.pafoid.skate.editor.review

import com.pafoid.skate.editor.commands.objects.AudioComponentSettings
import com.pafoid.skate.editor.commands.objects.MaterialState
import com.pafoid.skate.editor.commands.objects.SetComponentFieldCommand
import com.pafoid.skate.editor.events.AudioAction
import com.pafoid.skate.editor.events.ComponentFieldAction
import com.pafoid.skate.editor.imgui.EnumLabels
import com.pafoid.skate.editor.imgui.components.MaterialEditSession
import com.pafoid.skate.editor.imgui.components.StringFields
import com.pafoid.skate.editor.imgui.components.flushAudioDrafts
import com.pafoid.skate.editor.imgui.components.trackAudioDraft
import com.pafoid.skate.editor.project.ExportWizard
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.editor.shell.ShellLine
import com.pafoid.skate.editor.shell.ShellSession
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.data.models.AlphaMode
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.audio.BusId
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.AudioComponent
import com.pafoid.skate.engine.ecs.components.Component
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** EDT-40: fixes from the review of round-16 editor work by other agents. */
class Round16ReviewFixesTest {

    private val events = EventSystem(mockk(relaxed = true))

    /** Data class on purpose: equal instances must not share a draft. */
    data class Named(var label: String = "a") : Component()

    private val labelField = Named::class.java.getDeclaredField("label")

    // EDT-36 ---------------------------------------------------------------------------------------------------------

    @Test
    fun StringField_BeingTyped_IsCommittedWhenItStopsBeingDrawn() {
        val got = mutableListOf<ComponentFieldAction.Set>()
        events.subscribe<ComponentFieldAction.Set> { got += it }
        val c = Named()
        StringFields.track(c, labelField, "typed", editing = true, frame = 10)
        StringFields.endFrame(events, frame = 10)
        assertTrue(got.isEmpty(), "still drawn this frame: nothing yet")

        StringFields.endFrame(events, frame = 11) // selection changed: not drawn in frame 11
        assertEquals(1, got.size)
        assertEquals("typed", got.single().value)
        StringFields.endFrame(events, frame = 12)
        assertEquals(1, got.size, "committed once, then forgotten")
    }

    @Test
    fun StringField_IdleDrafts_AreDroppedWithoutCommand_AndEqualComponentsDoNotShare() {
        val got = mutableListOf<ComponentFieldAction.Set>()
        events.subscribe<ComponentFieldAction.Set> { got += it }
        val a = Named(); val b = Named()
        StringFields.track(a, labelField, "x", editing = false, frame = 1)
        StringFields.track(b, labelField, "y", editing = true, frame = 1)
        StringFields.endFrame(events, frame = 2)
        assertEquals(1, got.size)
        assertTrue(got.single().component === b)
    }

    @Test
    fun SetComponentField_MarksTheSceneDirtyOnExecuteAndUndo() {
        val scene = Scene()
        val go = GameObject("go").also(scene::addChild)
        val c = Named().also { go.components.add(it); it.gameObject = go }
        val cmd = SetComponentFieldCommand(c, labelField, "b")
        cmd.execute(); assertTrue(scene.isDirty)
        scene.isDirty = false
        cmd.undo(); assertTrue(scene.isDirty)
        assertEquals("a", c.label)
    }

    // EDT-17 / EDT-14 ------------------------------------------------------------------------------------------------

    @Test
    fun MaterialGesture_KeepsItsTargetEvenIfSettledLater() {
        val session = MaterialEditSession()
        val m = Material()
        session.preview(m, MaterialState.of(m).copy(metallic = 0.9f), target = "Board")
        val edits = session.settle(anyItemActive = false)
        assertEquals("Board", edits.single().target)
    }

    @Test
    fun AudioDraft_NotDrawnAnyMore_IsCommittedOnce() {
        val got = mutableListOf<AudioAction.EditComponent>()
        events.subscribe<AudioAction.EditComponent> { got += it }
        val audio = AudioComponent()
        val changed = AudioComponentSettings.of(audio).copy(soundFilePath = "assets/Sounds/x.ogg")
        trackAudioDraft(audio, changed, frame = 5)
        flushAudioDrafts(events, frame = 5)
        assertTrue(got.isEmpty())
        flushAudioDrafts(events, frame = 6)
        flushAudioDrafts(events, frame = 7)
        assertEquals(1, got.size)
    }

    @Test
    fun EnumLabels_AreTranslatedWithFallback() {
        val fr = StringManager(mockk(relaxed = true), "en").apply {
            loadStrings(Assets.Strings.EDITOR_STRINGS); setLocale("fr")
        }
        assertEquals("Musique", EnumLabels.of(fr, BusId.MUSIC))
        assertEquals("Mélange (transparent)", EnumLabels.of(fr, AlphaMode.BLEND))
        assertEquals("NOT_A_KEY", EnumLabels.of(fr, Fallback.NOT_A_KEY))
    }

    private enum class Fallback { NOT_A_KEY }

    // EDT-09 ---------------------------------------------------------------------------------------------------------

    private fun project(dir: File) = Project(
        name = "P", projectPath = File(dir, "P.skateproject").path, defaultScene = "", scenesPath = emptyList(),
    )

    @Test
    fun ExportWizard_RelativeOutput_IsUnderTheProjectFolder(@TempDir dir: File) {
        val w = ExportWizard()
        w.outputDir = "out/build"
        assertEquals(File(dir, "out/build").path, w.options(project(dir)).outputDir)
        w.outputDir = File(dir, "abs").absolutePath
        assertEquals(File(dir, "abs").absolutePath, w.options(project(dir)).outputDir)
    }

    @Test
    fun ExportWizard_PathTheOsRejects_IsAnIssueNotACrash(@TempDir dir: File) {
        val w = ExportWizard()
        w.outputDir = "\"C:\\bad|path?\""
        val issues = w.issues(project(dir), gameJar = null)
        assertTrue(ExportWizard.Issue.INVALID_OUTPUT in issues || issues.isNotEmpty())
    }

    // EDT-08 ---------------------------------------------------------------------------------------------------------

    @Test
    fun Shell_CommandWaitingForInput_GetsEndOfInputInsteadOfHanging() {
        val session = ShellSession()
        val cmd = if (ShellSession.isWindows()) "set /p X=" else "read X"
        assertTrue(session.run(cmd, File(".").absoluteFile))
        assertTrue(session.await(10_000), "the command should not wait for keyboard input")
        session.shutdown()
    }

    @Test
    fun Shell_StatusLines_CarryAStringKey() {
        val session = ShellSession()
        session.run("echo hi", File(".").absoluteFile)
        assertTrue(session.await(10_000))
        val last = session.drain().last()
        assertEquals("shell.line.exited", last.key)
        assertEquals(0, last.args.first())
    }

    @Test
    fun Shell_PendingOutput_IsBoundedWhileNobodyDrains() {
        val session = ShellSession(maxPending = 20)
        val many = if (ShellSession.isWindows()) "for /L %i in (1,1,200) do @echo %i" else "seq 1 200"
        session.run(many, File(".").absoluteFile)
        assertTrue(session.await(20_000))
        val lines = session.drain()
        assertTrue(lines.size <= 20, "kept ${lines.size}")
        assertFalse(lines.any { it.kind == ShellLine.Kind.COMMAND }, "oldest lines (the echoed command) were dropped")
    }
}
