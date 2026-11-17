package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.app.Editor
import com.pafoid.skate.editor.animation.clipeditor.AnimationEditorController
import com.pafoid.skate.editor.imgui.ImGuiStyleManager
import com.pafoid.skate.editor.imgui.loadFonts
import com.pafoid.skate.editor.imgui.theme.ThemeLibrary
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.animation.edit.CurveTangent
import com.pafoid.skate.engine.animation.edit.KeyRef
import com.pafoid.skate.engine.animation.edit.SkAnimFormat
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.data.models.animations.AnimationPath
import com.pafoid.skate.engine.assets.loaders.AnimationLoader
import com.pafoid.skate.engine.assets.loaders.AssimpLoader
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * ANI-15: renders the real Animation editor (real ImGui frames, no GPU) on a mocap clip (walking.fbx on the skater
 * rig) through the dope sheet, the curve editor (rotation and translation curves, handles, a range) and the cleanup
 * tools. ImGui aborts on unbalanced scopes, so finished frames mean the layout code is sound.
 */
class AnimationEditorWindowRenderTest {
    @TempDir lateinit var dir: File

    @BeforeEach
    fun setup() {
        ImGui.createContext()
        val io = ImGui.getIO()
        io.iniFilename = null
        io.loadFonts(javaClass.getResourceAsStream(Assets.Fonts.FONTS_FILE)!!.readAllBytes())
        io.setDisplaySize(1600f, 1000f)
        io.deltaTime = 1f / 60f
        ImGuiStyleManager.invalidate()
        ImGuiStyleManager.library = ThemeLibrary.load(File(Assets.Themes.DIR))
        ImGuiStyleManager.sync("dark")
    }

    @AfterEach
    fun teardown() {
        ImGuiStyleManager.library = ThemeLibrary(emptyList())
        ImGui.destroyContext()
    }

    private fun frames(window: AnimationEditorWindow, n: Int = 2) = repeat(n) {
        ImGui.newFrame()
        ImGui.setNextWindowPos(20f, 20f)
        ImGui.setNextWindowSize(1400f, 900f)
        window.imgui()
        ImGui.render()
    }

    @Test
    fun MocapClip_RendersInEveryView() {
        val strings = mockk<StringManager>(relaxed = true)
        every { strings.getString(any()) } answers { firstArg() }
        every { strings.getString(any(), *anyVararg()) } answers { firstArg() }
        val engine = mockk<Engine>(relaxed = true) { every { stringManager } returns strings }
        val projects = mockk<ProjectManager>(relaxed = true) { every { getProjectDirectory() } returns dir }
        val skeleton = AssimpLoader(mockk(relaxed = true), mockk(relaxed = true)).loadModel("assets/Models/james.glb").skeleton!!
        val walk = SkAnimFormat.fromAnimation(AnimationLoader().loadAnimations("assets/Animations/walking.fbx")[0])
        val file = File(dir, "walking.skanim").apply { writeText(SkAnimFormat.encode(walk)) }
        val undo = UndoRedoManager(mockk(relaxed = true), mockk(relaxed = true))
        val controller = AnimationEditorController(execute = { undo.executeCommand(it) }, rigFor = { skeleton })
        val window = AnimationEditorWindow(engine, mockk<Editor>(relaxed = true), projects, controller).also { it.isOpen.set(true) }

        frames(window) // no clip
        assertTrue(controller.open(file), controller.error)
        frames(window) // dope sheet + cleanup tools
        val hips = walk.tracks.first { it.bone.endsWith("Hips") }.bone
        controller.selectBone(hips)
        window.showCurves()
        frames(window, 3) // curves of the hips' first track
        controller.curveTrack = hips to AnimationPath.ROTATION
        frames(window) // Euler curves
        controller.curveTrack = hips to AnimationPath.TRANSLATION
        val key = controller.shownCurveTrack()!!.keys[5]
        controller.selectKey(KeyRef(hips, AnimationPath.TRANSLATION, key.time))
        controller.setTangent(CurveTangent.BROKEN)
        controller.setRange(0.2f, 0.6f)
        frames(window) // handles and range drawn
        controller.clearSelection()
        frames(window) // nothing selected: every track offered
        window.showCurves(false)
        frames(window)
        assertEquals(1, undo.getUndoCount(), "drawing alone never edits (only the tangent change)")
    }
}
