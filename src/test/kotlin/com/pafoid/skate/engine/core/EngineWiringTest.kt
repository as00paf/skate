package com.pafoid.skate.engine.core

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.systems.CameraManager
import com.pafoid.skate.engine.ecs.systems.PlayerMotionSystem
import com.pafoid.skate.engine.events.WaterEntered
import com.pafoid.skate.engine.ui.FrameLimiter
import com.pafoid.skate.engine.ui.FrameRateOption
import com.pafoid.skate.engine.ui.GameOptions
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** ECS-18: engine-level wiring every host gets, without game-specific code. */
class EngineWiringTest {

    @Test
    fun FrameRate_EveryEngineShowsTheFrameRateRow() {
        val options = GameOptions()

        EngineWiring.registerFrameRate(options)

        val row = options.all.single()
        assertEquals(FrameRateOption.OPTION_ID, row.id)
        assertEquals(FrameRateOption.VALUES, row.values)
        // The editor's limiter is never paced; choosing any value must be harmless.
        FrameRateOption.VALUES.forEach(row.apply)
    }

    @Test
    fun FrameRate_GameMainRegistrationReplacesTheEditorRow() {
        val options = GameOptions()
        EngineWiring.registerFrameRate(options)
        val limiter = FrameLimiter()

        options.register(FrameRateOption.option(limiter, projectFps = 75))
        options.all.single().apply("120")

        assertEquals(1, options.all.size)
        assertEquals(120, limiter.targetFps)
    }

    @Test
    fun Water_ConnectedPlayerMotionHearsWaterEntered() {
        val events = EventSystem()
        val cameras = mockk<CameraManager>(relaxed = true).also { every { it.camera } returns CameraComponent() }
        val motion = spyk(PlayerMotionSystem(cameras))

        EngineWiring.connectWater(motion, events)
        val event = WaterEntered(GameObject("Pool"), GameObject("Player"))
        events.publish(event)

        verify(exactly = 1) { motion.onWaterEntered(event) }
    }
}
