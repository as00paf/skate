package com.pafoid.skate.engine.ui

import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.getComponent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Properties

/** GUI-17: the player's frame-rate option overrides the project's target FPS and paces the game loop. */
class FrameRateTest {

    private val dir: File = kotlin.io.path.createTempDirectory("framerate").toFile()

    @AfterEach
    fun cleanup() {
        runCatching { dir.deleteRecursively() }
    }

    // --- choice -> cap ---

    @Test
    fun resolve_ProjectKeepsTheProjectCap_OthersOverrideIt() {
        assertEquals(90, FrameRateOption.resolve(FrameRateOption.PROJECT, 90))
        assertEquals(0, FrameRateOption.resolve(FrameRateOption.PROJECT, 0), "uncapped project stays uncapped")
        assertEquals(30, FrameRateOption.resolve("30", 90))
        assertEquals(144, FrameRateOption.resolve("144", 0))
        assertEquals(0, FrameRateOption.resolve(FrameRateOption.UNCAPPED, 90))
    }

    @Test
    fun resolve_MissingOrUnknownChoice_UsesTheProjectCap() {
        assertEquals(60, FrameRateOption.resolve(null, 60))
        assertEquals(60, FrameRateOption.resolve("fast", 60))
        assertEquals(60, FrameRateOption.resolve("-5", 60))
    }

    @Test
    fun saved_DefaultsToProject_AndIgnoresUnknownValues() {
        assertEquals(FrameRateOption.PROJECT, FrameRateOption.saved(GameSettings()))
        assertEquals("120", FrameRateOption.saved(GameSettings(options = mapOf(FrameRateOption.OPTION_ID to "120"))))
        assertEquals(FrameRateOption.PROJECT, FrameRateOption.saved(GameSettings(options = mapOf(FrameRateOption.OPTION_ID to "75"))))
    }

    @Test
    fun values_AreTheV1List() {
        assertEquals(listOf("project", "30", "60", "120", "144", "uncapped"), FrameRateOption.VALUES)
    }

    // --- pause menu: persists and changes the cap ---

    @Test
    fun pauseMenuClick_SavesTheChoice_AndChangesTheCap_AndARestartKeepsIt() {
        val store = GameSettingsStore(GameSettings.fileIn(dir), LoggerService())
        val limiter = FrameLimiter()
        val option = FrameRateOption.option(limiter, projectFps = 90)
        val menu = PauseMenuSystem(setFrozen = {}, options = GameOptions().also { it.register(option) }, settings = { store })
        menu.init(Scene("Level"))
        assertEquals(90, limiter.targetFps, "project cap by default")

        val row = PauseMenu.find(menu.menu, PauseMenu.OPTION_PREFIX + FrameRateOption.OPTION_ID)!!.getComponent<UIButtonComponent>()!!
        row.onClick!!()

        assertEquals(30, limiter.targetFps)
        assertEquals("frameRate.30", row.labelKey)
        // The next game start reads game.settings.json from disk.
        val reloaded = GameSettingsStore(store.file, LoggerService())
        assertEquals("30", reloaded.settings.options[FrameRateOption.OPTION_ID])
        val next = FrameLimiter()
        FrameRateOption.option(next, projectFps = 90).apply(FrameRateOption.saved(reloaded.settings))
        assertEquals(30, next.targetFps)
    }

    @Test
    fun cyclingThroughEveryValue_EndsBackOnTheProjectCap() {
        val limiter = FrameLimiter()
        val option = FrameRateOption.option(limiter, projectFps = 50)
        val caps = mutableListOf<Int>()
        var value = option.default
        repeat(FrameRateOption.VALUES.size) {
            value = option.next(value)
            option.apply(value)
            caps += limiter.targetFps
        }
        assertEquals(listOf(30, 60, 120, 144, 0, 50), caps)
    }

    @Test
    fun everyValue_HasALabelInEnglishAndFrench() {
        for (table in listOf("hud.properties", "hud_fr.properties")) {
            val props = Properties().apply {
                File("assets/Data/strings/game/$table").reader(Charsets.UTF_8).use { load(it) }
            }
            FrameRateOption.VALUES.forEach { v ->
                assertTrue(props.getProperty(FrameRateOption.labelKey(v)).isNullOrBlank().not(), "$table: ${FrameRateOption.labelKey(v)}")
            }
        }
    }

    // --- limiter ---

    private class FakeClock {
        var now = 1_000_000_000L
        val sleeps = mutableListOf<Long>()
        fun limiter(fps: Int) = FrameLimiter({ now }, { sleeps += it; now += it }).also { it.targetFps = fps }
    }

    private val ms = 1_000_000L

    @Test
    fun uncapped_NeverSleeps() {
        val clock = FakeClock()
        val limiter = clock.limiter(0)
        repeat(5) { clock.now += ms; limiter.endFrame() }
        assertEquals(emptyList<Long>(), clock.sleeps)
    }

    @Test
    fun capped_WaitsOutTheRestOfEachFrame() {
        val clock = FakeClock()
        val limiter = clock.limiter(50) // 20 ms per frame
        limiter.endFrame() // first frame starts the cadence
        val start = clock.now
        repeat(3) {
            clock.now += 5 * ms // 5 ms of work
            limiter.endFrame()
        }
        assertEquals(listOf(15 * ms, 15 * ms, 15 * ms), clock.sleeps)
        assertEquals(start + 60 * ms, clock.now, "3 frames take 3 slots")
    }

    @Test
    fun slightlyLateFrame_KeepsTheCadence_FarBehind_DoesNotRushToCatchUp() {
        val clock = FakeClock()
        val limiter = clock.limiter(50)
        limiter.endFrame()

        clock.now += 25 * ms // 5 ms late
        assertEquals(0L, limiter.endFrame())
        clock.now += 5 * ms
        limiter.endFrame()
        assertEquals(10 * ms, clock.sleeps.last(), "next slot still ends at 40 ms")

        clock.now += 200 * ms // a long hitch
        limiter.endFrame()
        clock.now += 5 * ms
        limiter.endFrame()
        assertEquals(15 * ms, clock.sleeps.last(), "a fresh 20 ms slot, not a burst of short frames")
    }

    @Test
    fun changingTheCap_TakesEffectOnTheNextFrame() {
        val clock = FakeClock()
        val limiter = clock.limiter(50)
        limiter.endFrame()
        clock.now += 5 * ms
        limiter.endFrame()
        assertEquals(15 * ms, clock.sleeps.last())

        limiter.targetFps = 100 // 10 ms per frame
        clock.now += 2 * ms
        limiter.endFrame() // new cadence starts here
        clock.now += 2 * ms
        limiter.endFrame()
        assertEquals(8 * ms, clock.sleeps.last())
    }

    @Test
    fun negativeCap_IsUncapped() {
        val limiter = FrameLimiter({ 0L }, { error("must not sleep") })
        limiter.targetFps = -10
        assertEquals(0, limiter.targetFps)
        assertEquals(0L, limiter.endFrame())
    }
}
