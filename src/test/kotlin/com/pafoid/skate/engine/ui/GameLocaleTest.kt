package com.pafoid.skate.engine.ui

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.pack.PackedStrings
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.systems.SystemManager
import com.pafoid.skate.engine.getComponent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * GUI-10: the game's language is its own. The pause menu's Language button switches runtime text only; the editor's
 * strings and locale stay as they are, and the choice is saved in the game settings.
 */
class GameLocaleTest {
    private val logger = LoggerService().apply { consoleSink = { _, _ -> } }
    private val dir: File = createTempDirectory("gui10").toFile()

    // As Engine + Editor set them up: editor text in one manager, runtime UI text in the other.
    private val editorStrings = StringManager(logger).apply {
        loadStrings(Assets.Strings.EDITOR_STRINGS)
        setLocale("en")
    }
    private val gameStrings = StringManager(logger).apply { addStrings(Assets.Strings.GAME_STRINGS) }
    private val gameLocale = GameLocale(gameStrings)

    @AfterEach
    fun cleanup() {
        dir.deleteRecursively()
    }

    private val manager = SystemManager()
    private var frame = UiInputFrame()
    private val pause = PauseMenuSystem(setFrozen = { manager.paused = it }, toggleLanguage = gameLocale::toggle)
    private val scene = Scene("Level")

    private fun play() {
        val input = UiInputSystem { frame }.also { it.onUnhandledCancel = pause::open }
        listOf(pause, UiLayoutSystem(screen = { UiScreenSize(1920, 1080) }), input).forEach { manager.addSystem(it) }
        manager.start()
        manager.loadScene(scene)
        scene.isRunning = true
        step()
    }

    private fun step(f: UiInputFrame = UiInputFrame()) {
        frame = f
        manager.update(1f / 60f)
    }

    private fun clickLanguage() {
        step(UiInputFrame(cancel = true))
        val go = PauseMenu.find(pause.menu, PauseMenu.LANGUAGE)!!
        val r = go.getComponent<UIElementComponent>()!!.rect
        val s = pause.menu.getComponent<UICanvasComponent>()!!.scale
        val x = (r.x + r.w / 2f) * s
        val y = (r.y + r.h / 2f) * s
        step(UiInputFrame(x, y))
        step(UiInputFrame(x, y, pointerDown = true, pointerPressed = true))
        step(UiInputFrame(x, y, pointerReleased = true))
    }

    @Test
    fun LanguageInPlay_SwitchesRuntimeText_AndLeavesTheEditorAlone() {
        gameLocale.attach(GameSettingsStore(GameSettings.fileIn(dir), logger))
        val editorTitle = editorStrings.getString("menu.file")
        assertEquals("Paused", gameStrings.getString("pause.title"))
        play()

        clickLanguage()

        assertEquals("fr", gameStrings.locale)
        assertEquals("Pause", gameStrings.getString("pause.title"))
        assertEquals("en", editorStrings.locale, "a game action changed the editor's language")
        assertEquals(editorTitle, editorStrings.getString("menu.file"))

        // Stop: nothing changes either way.
        scene.isRunning = false
        step()
        assertEquals("fr", gameStrings.locale)
        assertEquals("en", editorStrings.locale)
    }

    @Test
    fun LanguageChoice_IsSaved_AndTheNextPlayStartsWithIt() {
        val file = GameSettings.fileIn(dir)
        gameLocale.attach(GameSettingsStore(file, logger))
        gameLocale.toggle()
        assertTrue(file.isFile)

        // Reopening the project (or relaunching the game) reads the game's locale back.
        val again = GameLocale(StringManager(logger).apply { addStrings(Assets.Strings.GAME_STRINGS) })
        again.attach(GameSettingsStore(file, logger))
        assertEquals("fr", again.strings.locale)
        assertEquals("Pause", again.strings.getString("pause.title"))
    }

    @Test
    fun ExportedGame_PackedTables_SwitchLanguageToo() {
        // GameMain: the tables come from game.bin (AST-07), the settings file sits next to it.
        val packed = PackedStrings.inputs(File(Assets.Strings.GAME_STRINGS)).associate { it.relativePath to it.bytes }
        val strings = StringManager(logger).apply { addStrings(PackedStrings.source(packed)) }
        val locale = GameLocale(strings).apply { attach(GameSettingsStore(GameSettings.fileIn(dir), logger)) }
        assertEquals("Paused", strings.getString("pause.title"))
        locale.toggle()
        assertEquals("Pause", strings.getString("pause.title"))
        locale.toggle()
        assertEquals("Paused", strings.getString("pause.title"))
    }

    @Test
    fun MissingOrBrokenSettings_FallBackToEnglish() {
        assertEquals(GameSettings.DEFAULT_LOCALE, GameSettingsStore(File(dir, "none.json"), logger).settings.locale)
        val broken = GameSettings.fileIn(dir).apply { writeText("{not json") }
        assertEquals(GameSettings.DEFAULT_LOCALE, GameSettingsStore(broken, logger).settings.locale)
    }
}
