package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.app.Editor
import com.pafoid.skate.editor.animation.controllereditor.AnimatorWindowController
import com.pafoid.skate.editor.animation.controllereditor.ResolvedController
import com.pafoid.skate.editor.imgui.ImGuiStyleManager
import com.pafoid.skate.editor.imgui.loadFonts
import com.pafoid.skate.editor.imgui.theme.ThemeLibrary
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.animation.graph.AnimationControllerAsset
import com.pafoid.skate.engine.animation.graph.AnimationControllerAsset.Companion.ANY_STATE_NODE
import com.pafoid.skate.engine.animation.graph.ConditionOp
import com.pafoid.skate.engine.animation.graph.ParameterDef
import com.pafoid.skate.engine.animation.graph.ParameterType
import com.pafoid.skate.engine.animation.graph.StateDef
import com.pafoid.skate.engine.animation.graph.TransitionCondition
import com.pafoid.skate.engine.animation.graph.TransitionDef
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.components.Animator
import imgui.ImGui
import io.mockk.every
import io.mockk.mockk
import org.joml.Matrix4f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * ANI-21c: renders the real Animator window (real ImGui frames, no GPU) through every inspector branch, the read-only
 * banner and the live (play mode) view. ImGui aborts on unbalanced scopes (disabled blocks, popups, IDs), so a
 * finished frame means the window's layout code is sound.
 */
class AnimatorWindowRenderTest {

    @TempDir
    lateinit var dir: File

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

    private val asset = AnimationControllerAsset(
        "c", "idle", listOf(StateDef("idle", "idle"), StateDef("run", ""), StateDef("jump", "idle")),
        listOf(
            TransitionDef("idle", "run", listOf(TransitionCondition("speed", ConditionOp.GREATER, 1f))),
            TransitionDef("run", "idle", hasExitTime = true),
            TransitionDef("idle", "run", listOf(TransitionCondition("speed", ConditionOp.TRUE))),
            TransitionDef(null, "jump", listOf(TransitionCondition("kick", ConditionOp.TRIGGER)), exceptFrom = listOf("run")),
        ),
        parameters = listOf(ParameterDef("speed", ParameterType.FLOAT, 1f), ParameterDef("on", ParameterType.BOOL)),
    )

    private fun frames(window: AnimatorWindow, n: Int = 2) = repeat(n) {
        ImGui.newFrame()
        ImGui.setNextWindowPos(20f, 20f)
        ImGui.setNextWindowSize(1400f, 700f)
        window.imgui()
        ImGui.render()
    }

    @Test
    fun EveryPanel_Renders() {
        val strings = mockk<StringManager>(relaxed = true)
        every { strings.getString(any()) } answers { firstArg() }
        every { strings.getString(any(), *anyVararg()) } answers { firstArg() }
        val engine = mockk<Engine>(relaxed = true) { every { stringManager } returns strings }
        val projects = mockk<ProjectManager>(relaxed = true) { every { getProjectDirectory() } returns dir }
        val file = File(dir, "c.controller.json").apply { writeText(asset.toJson()) }
        val undo = UndoRedoManager(mockk(relaxed = true), mockk(relaxed = true))
        val controller = AnimatorWindowController(
            execute = { undo.executeCommand(it) },
            resolve = { p ->
                when {
                    File(p).isFile -> ResolvedController(p, File(p), File(p).readText())
                    p == Assets.Configs.SKATER_CONTROLLER -> ResolvedController(p, null, com.pafoid.skate.engine.gameplay.CharacterConfigs.readText(p)!!)
                    else -> null
                }
            },
            animationsDir = { File(dir, "Assets/Animations") },
        )
        val window = AnimatorWindow(engine, mockk<Editor>(relaxed = true), projects, controller).also { it.isOpen.set(true) }

        frames(window) // no controller: the empty page
        assertTrue(controller.open(file.absolutePath))
        frames(window) // nothing selected: controller settings
        controller.select("idle"); frames(window)
        controller.select("run"); frames(window)
        controller.select(ANY_STATE_NODE); frames(window)
        controller.selectNodes(setOf("idle", "run")); frames(window)
        controller.selectTransition(0); frames(window) // shares its arrow with transition 2
        controller.selectTransition(3); frames(window) // Any State, except-from list
        controller.startConnect("idle"); frames(window); controller.cancelConnect()

        // Play mode: the preview runs this controller.
        val character = Animator(animations = mutableListOf(Animation("idle", emptyList(), 1f, "idle.glb")))
        character.controllerPath = file.absolutePath
        assertTrue(character.resolveController(LoggerService(), Skeleton(Bone(0, "root", Matrix4f()), 1)))
        controller.preview = character
        controller.clearSelection()
        character.stateMachine!!.parameters.setFloat("speed", 2f)
        character.stateMachine!!.update(0.1f)
        frames(window, 3)
        assertEquals("run", controller.liveMachine!!.nextState ?: controller.liveMachine!!.currentState)

        // A built-in controller: read-only banner.
        assertTrue(controller.open(Assets.Configs.SKATER_CONTROLLER))
        controller.select("idle")
        frames(window)
        assertEquals(0, undo.getUndoCount(), "drawing alone never edits")
    }
}
