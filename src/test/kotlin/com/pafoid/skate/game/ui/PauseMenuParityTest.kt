package com.pafoid.skate.game.ui

import com.pafoid.skate.editor.events.PlayModeAction
import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.editor.systems.EditorPauseActions
import com.pafoid.skate.editor.systems.GameSettingsBinding
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.pack.PackedStrings
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.ui.FrameLimiter
import com.pafoid.skate.engine.ui.FrameRateOption
import com.pafoid.skate.engine.ui.GameSettings
import com.pafoid.skate.engine.ui.GameSettingsStore
import com.pafoid.skate.engine.ui.PauseMenu
import com.pafoid.skate.engine.ui.PauseMenuEntry
import com.pafoid.skate.engine.ui.PauseMenuEntry.Kind
import com.pafoid.skate.engine.ui.UICanvasComponent
import com.pafoid.skate.engine.ui.UIElementComponent
import com.pafoid.skate.engine.ui.UiInputFrame
import com.pafoid.skate.engine.ui.UiInputSystem
import com.pafoid.skate.engine.ui.UiLayoutSystem
import com.pafoid.skate.engine.ui.UiScreenSize
import com.pafoid.skate.game.GameStartup
import com.pafoid.skate.game.GameView
import com.pafoid.skate.game.input.SkateControls
import com.pafoid.skate.game.player.SkateGameplay
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * GUI-12, GUI-20: Esc in the exported game shows the same menu as editor Play. Each host is set up as its entry
 * point does it: both install the skate gameplay (Controls row) and the game strings; the editor attaches the
 * project's `game.settings.json` ([GameSettingsBinding]) and installs [EditorPauseActions]; the game gets the
 * settings the export writes (the editor's choices as defaults), then runs [GameStartup] and adds its own
 * Restart/Quit. Both get their menu from [Engine.createPauseMenu]. Every row, value, text and style must match;
 * only the host action differs.
 */
class PauseMenuParityTest {

    private val root: File = kotlin.io.path.createTempDirectory("parity").toFile()
    private var projects = 0

    /** A new project on disk; [editorSettings] is what the editor saved in its game.settings.json. */
    private fun project(editorSettings: GameSettings? = null, configure: Project.() -> Unit = {}): Project {
        val folder = File(root, "P${projects++}").apply { mkdirs() }
        val project = Project(name = "P", projectPath = File(folder, "P.skateproject").path, defaultScene = "", scenesPath = emptyList())
        project.configure()
        editorSettings?.let { s -> GameSettingsStore(GameSettings.fileIn(folder), LoggerService()).update { s } }
        return project
    }

    private class Host(screen: UiScreenSize, configure: Host.() -> Unit) {
        val engine = Engine().also { it.runtimePlaying = true }
        val hostActions = mutableListOf<String>()
        val pause = engine.createPauseMenu(defer = { it() })
        private var frame = UiInputFrame()
        val scene = Scene("Level")

        init {
            // As Engine.uiSystems: saved options apply when the settings store attaches (GPL-25).
            engine.gameLocale.onStoreChanged { pause.settingsChanged() }
            // Main.kt and GameMain both install the skate gameplay (its Controls row).
            SkateGameplay.install(engine)
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

        /** Every row and style of the menu, texts resolved in the host's game locale. */
        fun snapshot() = PauseMenu.snapshot(pause.menu, engine.gameStrings)

        /** What the scene shows besides the menu (a host-only HUD would differ here). */
        fun sceneObjects() = scene.children.filter { it !== pause.menu }.map { it.name }

        fun state() = "open=${pause.isOpen} paused=${engine.paused} locale=${engine.gameStrings.locale}"

        fun destroy() = engine.destroy()
    }

    /** As Editor.init and Main.kt: game strings from the engine folder, the opened project's game.settings.json. */
    private fun editorPlay(project: Project = project(), screen: UiScreenSize = UiScreenSize(1920, 1080)) = Host(screen) {
        engine.gameStrings.addStrings(Assets.Strings.GAME_STRINGS)
        GameSettingsBinding.install(engine.eventSystem, engine.gameLocale, engine.logger)
        engine.eventSystem.publish(ProjectEvent.Opened(project))
        EditorPauseActions.install(engine.pauseActions, engine.eventSystem)
        // EDT-45: Restart = Reset then Play, Quit = Stop.
        engine.eventSystem.subscribe(PlayModeAction.Reset::class) { hostActions += "restart" }
        engine.eventSystem.subscribe(PlayModeAction.Stop::class) { hostActions += "quit" }
    }

    /**
     * As an export of [project] then GameMain: game.settings.json as ProjectExporter writes it, strings from the
     * pack, [GameStartup], the store attached by installGameUi, and the game's Restart/Quit.
     */
    private fun exportedGame(project: Project = project(), screen: UiScreenSize = UiScreenSize(1920, 1080)) = Host(screen) {
        val editor = GameSettingsStore(GameSettings.fileIn(project.getProjectDirectory()), engine.logger).settings
        val out = File(project.getProjectDirectory(), "builds").apply { mkdirs() }
        val settings = GameSettingsStore(GameSettings.fileIn(out), engine.logger)
        settings.update {
            it.copy(supportedLanguages = project.supportedLanguages)
                .withEditorChoices(editor, project.supportedLanguages, project.startupLanguage)
        }
        engine.gameStrings.addStrings(PackedStrings.source(packedTables()))
        GameStartup.applyLanguage(project, settings)
        GameStartup.installFrameRate(engine.gameOptions, FrameLimiter(), project, settings)
        engine.gameLocale.attach(settings)
        engine.pauseActions.restart = { hostActions += "restart" }
        engine.pauseActions.quit = { hostActions += "quit" }
    }

    /** The game string tables as the pack carries them. */
    private fun packedTables(): Map<String, ByteArray> =
        PackedStrings.inputs(File(Assets.Strings.GAME_STRINGS)).associate { it.relativePath to it.bytes }

    private val hosts = mutableListOf<Host>()
    private fun Host.tracked() = also { hosts += it }

    @AfterEach
    fun cleanup() {
        hosts.forEach { it.destroy() }
        runCatching { root.deleteRecursively() }
    }

    @Test
    fun BothHosts_UseTheSameMenuDefinition() {
        val p = project()
        val editor = editorPlay(p).tracked()
        val game = exportedGame(p).tracked()

        val expected = listOf(
            PauseMenuEntry("Title", Kind.TEXT, "pause.title", 90f),
            PauseMenuEntry(PauseMenu.RESUME, Kind.BUTTON, "pause.resume", 80f),
            PauseMenuEntry(PauseMenu.RESTART, Kind.BUTTON, "pause.restart", 80f),
            PauseMenuEntry(PauseMenu.LANGUAGE, Kind.BUTTON, "pause.language", 80f),
            // ECS-18: every engine registers the Frame rate row (GUI-17), so editor Play shows it too.
            PauseMenuEntry(PauseMenu.OPTION_PREFIX + FrameRateOption.OPTION_ID, Kind.BUTTON, FrameRateOption.labelKey(FrameRateOption.PROJECT), 80f),
            PauseMenuEntry(PauseMenu.OPTION_PREFIX + SkateControls.OPTION_ID, Kind.BUTTON, "controls.ea_skate", 80f),
            PauseMenuEntry(PauseMenu.OPTION_PREFIX + ControlHints.OPTION_ID, Kind.BUTTON, "hints.on", 80f),
            PauseMenuEntry(PauseMenu.QUIT, Kind.BUTTON, "pause.quit", 80f),
            PauseMenuEntry("VolumeLabel", Kind.TEXT, "pause.volume", 48f),
            PauseMenuEntry(PauseMenu.VOLUME, Kind.SLIDER, "", 40f),
        )
        assertEquals(expected, PauseMenu.definition(editor.pause.menu))
        assertEquals(PauseMenu.definition(editor.pause.menu), PauseMenu.definition(game.pause.menu))
    }

    @Test
    fun BothHosts_ShowTheSameMenu_RowForRow_IncludingStylesAndTexts() {
        val p = project()
        val editor = editorPlay(p).tracked()
        val game = exportedGame(p).tracked()
        editor.esc()
        game.esc()

        val snapshot = editor.snapshot()
        assertEquals(snapshot, game.snapshot())
        // The texts are resolved, not raw keys.
        assertTrue(snapshot.any { "\"Resume\"" in it }, snapshot.joinToString("\n"))
    }

    @Test
    fun ExportedGame_StartsWithTheEditorsLanguageAndOptions() {
        // Project Settings set the game language to French; editor Play picked another scheme and a 60 FPS cap.
        val p = project(
            GameSettings(
                locale = "fr", localeChosen = true,
                options = mapOf(SkateControls.OPTION_ID to "thps", FrameRateOption.OPTION_ID to "60"),
            )
        ) { supportedLanguages = listOf("en", "fr") }
        val editor = editorPlay(p).tracked()
        val game = exportedGame(p).tracked()
        editor.esc()
        game.esc()

        assertEquals("fr", game.engine.gameStrings.locale)
        val snapshot = editor.snapshot()
        assertTrue(snapshot.any { "label=controls.thps" in it }, snapshot.joinToString("\n"))
        assertTrue(snapshot.any { "label=frameRate.60" in it }, snapshot.joinToString("\n"))
        assertEquals(snapshot, game.snapshot())
    }

    @Test
    fun ExportedGame_AddsNoHudOfItsOwn() {
        val p = project()
        val editor = editorPlay(p).tracked()
        val game = exportedGame(p).tracked()

        assertEquals(editor.sceneObjects(), game.sceneObjects())
    }

    @Test
    fun BothHosts_LayOutTheMenuTheSame_AtTheSizeTheyRender() {
        // A 2560x1440 fullscreen game now renders at that size (it was stuck at 1920x1080 before GUI-12) ...
        val gameSize = GameView.resize(2560 to 1440, 1920 to 1080)!!
        assertEquals(2560 to 1440, gameSize)
        // ... like an editor viewport of the same shape: the menu lands at the same place in canvas units.
        val p = project()
        val editor = editorPlay(p, UiScreenSize(1280, 720)).tracked()
        val game = exportedGame(p, UiScreenSize(gameSize.first, gameSize.second)).tracked()
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
        val frameRate = PauseMenu.OPTION_PREFIX + FrameRateOption.OPTION_ID
        val controls = PauseMenu.OPTION_PREFIX + SkateControls.OPTION_ID

        fun script(h: Host): List<String> = buildList {
            add(h.state())
            h.esc(); add(h.state())                               // open + freeze
            h.esc(); add(h.state())                               // Esc closes
            h.esc(); h.click(PauseMenu.RESUME); add(h.state())    // Resume closes
            h.esc(); h.click(PauseMenu.LANGUAGE); add(h.state())  // Language switches, menu stays open
            h.click(PauseMenu.LANGUAGE); add(h.state())
            h.click(frameRate); h.click(controls); add(h.state()) // option rows cycle, menu stays open
            addAll(h.snapshot())
            h.click(PauseMenu.RESTART); add(h.state())            // Restart closes and unfreezes
            h.esc(); h.click(PauseMenu.QUIT); add(h.state())
            add("host=${h.hostActions}")
        }

        val editorTrace = script(editor)
        assertEquals(editorTrace, script(game))
        assertEquals("host=[restart, quit]", editorTrace.last())
    }
}
