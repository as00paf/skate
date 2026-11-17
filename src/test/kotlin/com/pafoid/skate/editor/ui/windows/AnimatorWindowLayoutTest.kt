package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.app.Editor
import com.pafoid.skate.editor.animation.controllereditor.AnimatorWindowController
import com.pafoid.skate.editor.animation.controllereditor.ResolvedController
import com.pafoid.skate.editor.imgui.ImGuiStyleManager
import com.pafoid.skate.editor.imgui.loadFonts
import com.pafoid.skate.editor.imgui.theme.ThemeLibrary
import com.pafoid.skate.editor.settings.AppearanceSettings
import com.pafoid.skate.editor.settings.Density
import com.pafoid.skate.editor.settings.EditorSettings
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.animation.graph.AnimEventDef
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
import io.mockk.mockk
import io.mockk.every
import org.joml.Matrix4f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * ANI-25: draws the Animator window (real ImGui frames with the editor's fonts and the real EN strings, no GPU) at a
 * small size, with every EDT-76 density and the largest UI scale, through every panel state, and checks that no
 * panel lays anything out past its right edge: nothing scrolls sideways, so such an item would not be shown.
 * Rows must wrap and panels scroll vertically instead.
 */
class AnimatorWindowLayoutTest {

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
        ImGuiStyleManager.library = ThemeLibrary.load(File(Assets.Themes.DIR))
    }

    @AfterEach
    fun teardown() {
        ImGuiStyleManager.uiScale = { 1f }
        ImGuiStyleManager.appearance = { AppearanceSettings.THEME_ONLY }
        ImGuiStyleManager.library = ThemeLibrary(emptyList())
        ImGuiStyleManager.invalidate()
        ImGui.destroyContext()
    }

    private val asset = AnimationControllerAsset(
        "Skater locomotion", "standing idle",
        listOf(
            StateDef("standing idle", "idle", events = listOf(AnimEventDef(0.25f, "left foot down"), AnimEventDef(0.75f, "right foot down"))),
            StateDef("running fast", ""), StateDef("jump", "idle"),
        ),
        listOf(
            TransitionDef("standing idle", "running fast", listOf(
                TransitionCondition("horizontalSpeed", ConditionOp.GREATER, 1.5f),
                TransitionCondition("grounded", ConditionOp.TRUE),
                TransitionCondition("jumpRequested", ConditionOp.TRIGGER),
            )),
            TransitionDef("running fast", "standing idle", hasExitTime = true),
            TransitionDef("standing idle", "running fast", listOf(TransitionCondition("grounded", ConditionOp.TRUE))),
            TransitionDef(null, "jump", listOf(TransitionCondition("jumpRequested", ConditionOp.TRIGGER)), exceptFrom = listOf("running fast")),
        ),
        parameters = listOf(
            ParameterDef("horizontalSpeed", ParameterType.FLOAT, 1f), ParameterDef("grounded", ParameterType.BOOL),
            ParameterDef("jumpRequested", ParameterType.TRIGGER),
        ),
    )

    private class Case(val name: String, val prepare: (AnimatorWindowController) -> Unit)

    private val cases = listOf(
        Case("controller settings") { it.clearSelection() },
        Case("state") { it.select("standing idle") },
        Case("state without clip") { it.select("running fast") },
        Case("any state") { it.select(ANY_STATE_NODE) },
        Case("multi select") { it.selectNodes(setOf("standing idle", "jump")) },
        Case("transition with conditions") { it.selectTransition(0) },
        Case("transition with exit time") { it.selectTransition(1) },
        Case("any-state transition") { it.selectTransition(3) },
        Case("connecting") { it.startConnect("standing idle") },
    )

    private fun strings(): StringManager = StringManager(mockk(relaxed = true), "en").apply { loadStrings(Assets.Strings.EDITOR_STRINGS) }

    /** Draws every case at [width] x [height]; returns the overflows found. */
    private fun overflows(density: Density, scale: Float, width: Float, height: Float): List<String> {
        ImGuiStyleManager.uiScale = { scale }
        ImGuiStyleManager.appearance = { AppearanceSettings(density = density) }
        ImGuiStyleManager.invalidate()
        ImGuiStyleManager.sync("dark")
        val engine = mockk<Engine>(relaxed = true) { every { stringManager } returns strings() }
        val projects = mockk<ProjectManager>(relaxed = true) { every { getProjectDirectory() } returns dir }
        val file = File(dir, "c.controller.json").apply { writeText(asset.toJson()) }
        val undo = UndoRedoManager(mockk(relaxed = true), mockk(relaxed = true))
        val controller = AnimatorWindowController(
            execute = { undo.executeCommand(it) },
            resolve = { p ->
                when {
                    File(p).isFile -> ResolvedController(p, File(p), File(p).readText())
                    else -> ResolvedController(p, null, asset.toJson())
                }
            },
            animationsDir = { File(dir, "Assets/Animations") },
        )
        val window = AnimatorWindow(engine, mockk<Editor>(relaxed = true), projects, controller).also { it.isOpen.set(true) }
        val found = mutableListOf<String>()
        var case = ""
        var measuring = false
        window.layoutProbe = { panel, overflow -> if (measuring && overflow > 0.5f) found += "$case: $panel overflows by ${"%.1f".format(overflow)} px" }

        fun draw(name: String) {
            case = "$density x$scale ${width.toInt()}x${height.toInt()} $name"
            repeat(3) { frame ->
                measuring = frame == 2 // the layout settles over the first frames (content size, scrollbars)
                ImGui.newFrame()
                ImGui.setNextWindowPos(0f, 0f)
                ImGui.setNextWindowSize(width, height)
                window.imgui()
                ImGui.render()
            }
        }

        draw("empty page")
        controller.open(file.absolutePath)
        for (c in cases) { c.prepare(controller); draw(c.name); controller.cancelConnect() }

        // Play mode: live values replace the defaults.
        val character = Animator(animations = mutableListOf(Animation("idle", emptyList(), 1f, "idle.glb")))
        character.controllerPath = file.absolutePath
        character.resolveController(LoggerService(), Skeleton(Bone(0, "root", Matrix4f()), 1))
        controller.preview = character
        controller.clearSelection()
        draw("live")

        // A built-in controller: the read-only banner.
        controller.open("engine:Animations/c.controller.json")
        draw("read only")
        return found.distinct()
    }

    @Test
    fun EveryItemFits_AtTheSmallestSizeAndEveryDensity() {
        val found = mutableListOf<String>()
        for (density in listOf(Density.COMPACT, Density.COMFORTABLE, Density.SPACIOUS)) {
            for (scale in listOf(1f, EditorSettings.MAX_UI_SCALE)) {
                found += overflows(density, scale, SMALL_W, SMALL_H)
                found += overflows(density, scale, NORMAL_W, NORMAL_H)
            }
        }
        assertTrue(found.isEmpty(), "Items laid out past a panel's right edge (not shown):\n" + found.joinToString("\n"))
    }

    private companion object {
        /** About the bottom dock zone of a 1280 x 720 editor. */
        const val SMALL_W = 640f
        const val SMALL_H = 360f
        /** A usual bottom dock zone. */
        const val NORMAL_W = 1280f
        const val NORMAL_H = 420f
    }
}
