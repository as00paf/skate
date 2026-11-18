package com.pafoid.skate.architecture

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Paths
import kotlin.io.path.readText

/**
 * ECS-18: Engine (which needs a GL context, so is not built in unit tests) wires the frame-rate row and water events
 * for every game; games must not add their own water wiring.
 */
class EngineWaterWiringGuardTest {
    private val root = Paths.get(System.getProperty("user.dir")).toAbsolutePath().resolve("src/main/kotlin/com/pafoid/skate")
    private val engine = root.resolve("engine/core/Engine.kt").readText()

    @Test
    fun EngineWiring_RegistersTheFrameRateRow() {
        assertTrue(engine.contains("GameOptions().also(EngineWiring::registerFrameRate)"))
    }

    @Test
    fun EngineWiring_AudioSystemGetsTheEventSystem() {
        assertTrue(
            Regex("""AudioSystem\([^)]*localPath,\s*eventSystem\)""").containsMatchIn(engine),
            "Engine must pass eventSystem to AudioSystem so splashes hear WaterEntered (AUD-01)",
        )
    }

    @Test
    fun EngineWiring_PlayerMotionSystemListensToWater() {
        assertTrue(Regex("""EngineWiring\.connectWater\(\s*PlayerMotionSystem\(""").containsMatchIn(engine))
    }

    @Test
    fun EngineWiring_GameCodeDoesNotWireWaterItself() {
        val game = root.resolve("game").toFile().walkTopDown().filter { it.isFile && it.extension == "kt" }
        val offenders = game.filter { Regex("""PlayerMotionSystem[^\n]*\.listenTo\(""").containsMatchIn(it.readText()) }.map { it.name }.toList()
        assertFalse(offenders.isNotEmpty(), "Water wiring belongs in Engine (ECS-18): $offenders")
    }
}
