package com.pafoid.skate.game.input

import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.gameplay.ActionPresets
import com.pafoid.skate.engine.ui.GameLocale
import com.pafoid.skate.engine.ui.GameOptions
import com.pafoid.skate.engine.ui.GameSettings
import com.pafoid.skate.engine.ui.GameSettingsStore
import com.pafoid.skate.engine.ui.PauseMenuSystem
import com.pafoid.skate.game.player.SkateGameplay
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * GPL-25: the control scheme saved in `game.settings.json` is the one that runs from the first frame, in both hosts,
 * not only the one the pause menu shows. Wired as `Engine.uiSystems` does: the pause menu reads the game locale's store
 * and is told when it changes.
 */
class SavedControlSchemeStartupTest {

    private val logger = LoggerService().apply { consoleSink = { _, _ -> } }
    private val dir: File = createTempDirectory("gpl25").toFile()
    private val locale = GameLocale(StringManager(logger))
    private val options = GameOptions()
    private val pauseMenu = PauseMenuSystem(setFrozen = {}, options = options, settings = { locale.store })

    init {
        SkateGameplay.register()
        SkateControls.select(ControlSchemes.DEFAULT.id)
        options.register(SkateControls.option())
        locale.onStoreChanged { pauseMenu.settingsChanged() }
    }

    @AfterEach
    fun cleanup() {
        SkateControls.select(ControlSchemes.DEFAULT.id)
        dir.deleteRecursively()
    }

    private fun savedStore(scheme: ControlScheme) = GameSettingsStore(GameSettings.fileIn(dir), logger).apply {
        update { it.copy(options = mapOf(SkateControls.OPTION_ID to scheme.id)) }
    }

    @Test
    fun exportedGame_SceneLoadsBeforeTheSettingsAttach_SavedSchemeRunsBeforeTheFirstFrame() {
        val store = savedStore(ControlSchemes.THPS)
        pauseMenu.init(Scene("MainScene")) // ProjectManager.openProject: no store yet
        assertEquals(ControlSchemes.DEFAULT, SkateControls.scheme)
        val presets = ActionPresets.revision

        locale.attach(store) // installGameUi, still before window.show's first engine.update

        assertEquals(ControlSchemes.THPS, SkateControls.scheme)
        assertNotEquals(presets, ActionPresets.revision, "the skate bindings are redefined on the first frame")
        assertEquals(ControlSchemes.THPS.id, pauseMenu.valueOf(options.all.single()))
    }

    @Test
    fun editor_ProjectOpensBeforePlay_SavedSchemeRunsAtOnceAndOnPlay() {
        locale.attach(savedStore(ControlSchemes.SESSION)) // project opened
        assertEquals(ControlSchemes.SESSION, SkateControls.scheme)

        SkateControls.select(ControlSchemes.DEFAULT.id)
        pauseMenu.init(Scene("MainScene")) // Play
        assertEquals(ControlSchemes.SESSION, SkateControls.scheme)
    }

    @Test
    fun closingTheProject_GoesBackToTheDefaultScheme() {
        locale.attach(savedStore(ControlSchemes.SKATER_XL))
        locale.detach()
        assertEquals(ControlSchemes.DEFAULT, SkateControls.scheme)
    }
}
