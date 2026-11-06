package com.pafoid.skate.game.ui

import com.pafoid.skate.editor.events.PlayModeAction
import com.pafoid.skate.editor.systems.EditorPauseActions
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.ui.PauseMenu
import com.pafoid.skate.engine.ui.PauseMenuEntry
import com.pafoid.skate.engine.ui.PauseMenuEntry.Kind
import com.pafoid.skate.engine.ui.UICanvasComponent
import com.pafoid.skate.engine.ui.UIElementComponent
import com.pafoid.skate.engine.ui.UiInputFrame
import com.pafoid.skate.engine.ui.UiInputSystem
import com.pafoid.skate.engine.ui.UiLayoutSystem
import com.pafoid.skate.engine.ui.UiScreenSize
import com.pafoid.skate.game.GameView
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * GUI-12: Esc in the exported game shows the same menu as editor Play. Each host is set up as its entry point does
 * it (the editor installs [EditorPauseActions]; GameMain adds the HUD and its own Restart/Quit), and both get their
 * menu from [Engine.createPauseMenu]. The menus must match in items, layout and behaviour; only the host action differs.
 */
class PauseMenuParityTest {

    private class Host(screen: UiScreenSize, configure: Host.() -> Unit) {
        val engine = Engine().also { it.runtimePlaying = true }
        val hostActions = mutableListOf<String>()
        val pause = engine.createPauseMenu(defer = { it() })
        private var frame = UiInputFrame()
        val scene = Scene("Level")

        init {
            configure()
            val input = UiInputSystem { frame }.also { it.onUnhandledCancel = pause::open }
            listOf(pause, UiLayoutSystem(screen = { screen }), input).forEach { engine.systemManager.addSystem(it) }
            engine.systemManager.start()
            engine.systemManager.loadScene(scene)
            scene.isRunning = true
            step()
        }

        fun step(f: UiInputFrame = UiInputFrame()) {
            frame = f
            engine.systemManager.update(1f / 60f)
        }

        fun esc() = step(UiInputFrame(cancel = true))

        fun click(name: String) {
            val r = PauseMenu.find(pause.menu, name)!!.getComponent<UIElementComponent>()!!.rect
            val s = pause.menu.getComponent<UICanvasComponent>()!!.scale
            val x = (r.x + r.w / 2f) * s
            val y = (r.y + r.h / 2f) * s
            step(UiInputFrame(x, y))
            step(UiInputFrame(x, y, pointerDown = true, pointerPressed = true))
            step(UiInputFrame(x, y, pointerReleased = true))
        }

        /** Layout in canvas units, so hosts with different pixel sizes compare equal. */
        fun layout() = PauseMenu.definition(pause.menu).map { e ->
            val r = PauseMenu.find(pause.menu, e.name)!!.getComponent<UIElementComponent>()!!.rect
            e.name to listOf(r.x, r.y, r.w, r.h)
        }

        fun state() = "open=${pause.isOpen} paused=${engine.paused} locale=${engine.gameStrings.locale}"

        fun destroy() = engine.destroy()
    }

    private fun editorPlay(screen: UiScreenSize = UiScreenSize(1920, 1080)) = Host(screen) {
        EditorPauseActions.install(engine.pauseActions, engine.eventSystem)
        // EDT-45: Restart = Reset then Play, Quit = Stop.
        engine.eventSystem.subscribe(PlayModeAction.Reset::class) { hostActions += "restart" }
        engine.eventSystem.subscribe(PlayModeAction.Stop::class) { hostActions += "quit" }
    }

    /** As GameMain.installGameUi: the HUD system, Restart = reload the launch state, Quit = close the window. */
    private fun exportedGame(screen: UiScreenSize = UiScreenSize(1920, 1080)) = Host(screen) {
        val hud = GameUiSystem(GameHudState())
        engine.systemManager.addSystem(hud)
        engine.pauseActions.restart = { hostActions += "restart"; hud.resetState() }
        engine.pauseActions.quit = { hostActions += "quit" }
    }

    private val hosts = mutableListOf<Host>()
    private fun Host.tracked() = also { hosts += it }

    @AfterEach
    fun cleanup() = hosts.forEach { it.destroy() }

    @Test
    fun BothHosts_UseTheSameMenuDefinition() {
        val editor = editorPlay().tracked()
        val game = exportedGame().tracked()

        val expected = listOf(
            PauseMenuEntry("Title", Kind.TEXT, "pause.title", 90f),
            PauseMenuEntry(PauseMenu.RESUME, Kind.BUTTON, "pause.resume", 80f),
            PauseMenuEntry(PauseMenu.RESTART, Kind.BUTTON, "pause.restart", 80f),
            PauseMenuEntry(PauseMenu.LANGUAGE, Kind.BUTTON, "pause.language", 80f),
            PauseMenuEntry(PauseMenu.QUIT, Kind.BUTTON, "pause.quit", 80f),
            PauseMenuEntry("VolumeLabel", Kind.TEXT, "pause.volume", 48f),
            PauseMenuEntry(PauseMenu.VOLUME, Kind.SLIDER, "", 40f),
        )
        assertEquals(expected, PauseMenu.definition(editor.pause.menu))
        assertEquals(PauseMenu.definition(editor.pause.menu), PauseMenu.definition(game.pause.menu))
    }

    @Test
    fun BothHosts_LayOutTheMenuTheSame_AtTheSizeTheyRender() {
        // A 2560x1440 fullscreen game now renders at that size (it was stuck at 1920x1080 before GUI-12) ...
        val gameSize = GameView.resize(2560 to 1440, 1920 to 1080)!!
        assertEquals(2560 to 1440, gameSize)
        // ... like an editor viewport of the same shape: the menu lands at the same place in canvas units.
        val editor = editorPlay(UiScreenSize(1280, 720)).tracked()
        val game = exportedGame(UiScreenSize(gameSize.first, gameSize.second)).tracked()
        editor.esc()
        game.esc()

        assertEquals(editor.layout(), game.layout())
    }

    @Test
    fun GameView_KeepsTheFramebuffer_WhenUnchangedOrMinimised() {
        assertNull(GameView.resize(1920 to 1080, 1920 to 1080))
        assertNull(GameView.resize(0 to 0, 1920 to 1080))
        assertEquals(1280 to 1024, GameView.resize(1280 to 1024, 1920 to 1080))
    }

    @Test
    fun BothHosts_BehaveTheSame_ForEveryMenuAction() {
        val editor = editorPlay().tracked()
        val game = exportedGame().tracked()

        fun script(h: Host): List<String> = buildList {
            add(h.state())
            h.esc(); add(h.state())                              // open + freeze
            h.esc(); add(h.state())                              // Esc closes
            h.esc(); h.click(PauseMenu.RESUME); add(h.state())   // Resume closes
            h.esc(); h.click(PauseMenu.LANGUAGE); add(h.state()) // Language switches, menu stays open
            h.click(PauseMenu.LANGUAGE); add(h.state())
            h.click(PauseMenu.RESTART); add(h.state())           // Restart closes and unfreezes
            h.esc(); h.click(PauseMenu.QUIT); add(h.state())
            add("host=${h.hostActions}")
        }

        val editorTrace = script(editor)
        assertEquals(editorTrace, script(game))
        assertEquals("host=[restart, quit]", editorTrace.last())
    }
}
