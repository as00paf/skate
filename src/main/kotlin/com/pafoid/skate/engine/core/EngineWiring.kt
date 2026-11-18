package com.pafoid.skate.engine.core

import com.pafoid.skate.engine.ecs.systems.PlayerMotionSystem
import com.pafoid.skate.engine.ui.FrameLimiter
import com.pafoid.skate.engine.ui.FrameRateOption
import com.pafoid.skate.engine.ui.GameOptions

/** Engine-level wiring every host gets (ECS-18), kept out of [Engine] so it can be tested without a GL context. */
object EngineWiring {

    /**
     * The Frame rate row (GUI-17) for every host, so editor Play shows the same pause menu as the exported game
     * (GUI-12). The limiter here is never paced: the editor keeps its own frame loop. `GameMain` registers the option
     * again with its real limiter, which replaces this one (same id).
     */
    fun registerFrameRate(options: GameOptions) {
        options.register(FrameRateOption.option(FrameLimiter(), projectFps = 0))
    }

    /** Water reactions (PHY-16 `WaterEntered`) for every game: the player's water response (GPL-32). */
    fun connectWater(playerMotion: PlayerMotionSystem, eventSystem: EventSystem): PlayerMotionSystem =
        playerMotion.also { it.listenTo(eventSystem) }
}
