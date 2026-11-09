package com.pafoid.skate.engine.ui

import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.getComponent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

/** GPL-21: game options (e.g. the control scheme) as pause menu rows, saved in the game settings. */
class PauseMenuOptionsTest {

    private val dir: File = kotlin.io.path.createTempDirectory("options").toFile()
    private val store = GameSettingsStore(GameSettings.fileIn(dir), LoggerService())
    private val applied = mutableListOf<String>()
    private val mode = GameOption("demo.mode", listOf("a", "b", "c"), "a", { "mode.$it" }, { applied += it })

    @AfterEach
    fun cleanup() {
        runCatching { dir.deleteRecursively() }
    }

    private fun menu(options: GameOptions = GameOptions().also { it.register(mode) }) =
        PauseMenuSystem(setFrozen = {}, options = options, settings = { store }).also { it.init(Scene("Level")) }

    private fun row(system: PauseMenuSystem) =
        PauseMenu.find(system.menu, PauseMenu.OPTION_PREFIX + mode.id)!!.getComponent<UIButtonComponent>()!!

    @Test
    fun optionRow_SitsAfterLanguage_AndThePanelGrowsByOneRow() {
        val system = menu()

        val names = PauseMenu.definition(system.menu).map { it.name }
        assertEquals(names.indexOf(PauseMenu.LANGUAGE) + 1, names.indexOf(PauseMenu.OPTION_PREFIX + mode.id))
        val panel = PauseMenu.find(system.menu, PauseMenu.PANEL)!!.getComponent<UIElementComponent>()!!
        assertEquals(PauseMenu.PANEL_HEIGHT + PauseMenu.OPTION_ROW_HEIGHT, panel.height)
        assertEquals("mode.a", row(system).labelKey)
    }

    @Test
    fun click_CyclesSavesAndApplies() {
        val system = menu()

        row(system).onClick!!()

        assertEquals("b", applied.last())
        assertEquals("mode.b", row(system).labelKey)
        assertEquals("b", GameSettingsStore(store.file, LoggerService()).settings.options[mode.id], "saved to disk")
    }

    @Test
    fun sceneLoad_AppliesTheSavedChoice_AndForgetsInvalidOnes() {
        store.update { it.copy(options = mapOf(mode.id to "c")) }
        val system = menu()
        assertEquals("c", applied.last())
        assertEquals("mode.c", row(system).labelKey)

        store.update { it.copy(options = mapOf(mode.id to "gone")) }
        system.init(Scene("Restarted"))
        assertEquals("a", applied.last())
    }

    @Test
    fun withoutOptions_TheMenuIsUnchanged_AndOptionsMatchAcrossHosts() {
        assertEquals(PauseMenu.definition(PauseMenu.create()), PauseMenu.definition(menu(GameOptions()).menu))
        // Editor Play and the exported game install the same options, so their menus stay identical (GUI-12).
        assertEquals(PauseMenu.definition(menu().menu), PauseMenu.definition(menu().menu))
    }

    @Test
    fun optionsRegisteredAfterStart_AppearOnTheNextFrame() {
        val options = GameOptions()
        val system = menu(options)
        options.register(mode)

        system.update(1f / 60f)

        assertEquals("mode.a", row(system).labelKey)
        assertEquals("a", applied.last())
    }
}
