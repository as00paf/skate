package com.pafoid.skate.game.ui

import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.PlayerController
import com.pafoid.skate.engine.ecs.systems.System
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.events.TrickCompleted
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.ui.UITextComponent
import com.pafoid.skate.game.player.SkateBehavior
import com.pafoid.skate.game.player.SkateControllerConfig
import com.pafoid.skate.game.player.TrickType

/**
 * Trick feedback in editor Play and the exported game (GPL-21): every HUD text named [TRICK_TEXT] shows the trick
 * in the air, then the one just landed for [showSec]; every [SCORE_TEXT] shows the run's score. Each landed trick
 * adds its points ([com.pafoid.skate.game.player.TrickTuning.points]) and publishes [TrickCompleted]. A new scene
 * (Play, Restart) starts a new run.
 */
class SkateHudSystem(
    private val events: EventSystem? = null,
    private val showSec: Float = 1.5f,
) : System(priority = ExecutionPriority.DEFAULT) {

    var score = 0
        private set

    /** String key on the trick text right now ("" hides it). */
    var trickKey = ""
        private set

    private var seenLanded = 0
    private var rider: SkateBehavior? = null
    private var shownFor = 0f

    override fun init(scene: Scene) {
        super.init(scene)
        score = 0
        trickKey = ""
        rider = null
        shownFor = 0f
    }

    override fun update(dt: Float) {
        if (!scene.isRunning) return
        val behavior = findRider() ?: return
        if (behavior !== rider) {
            rider = behavior
            seenLanded = behavior.landedTricks
        }
        val inAir = behavior.trick
        when {
            behavior.landedTricks > seenLanded -> {
                seenLanded = behavior.landedTricks
                behavior.lastLandedTrick?.let(::landed)
            }
            inAir != null && inAir != TrickType.OLLIE -> {
                trickKey = inAir.nameKey
                shownFor = 0f
            }
            trickKey.isNotEmpty() -> {
                shownFor += dt
                if (shownFor >= showSec) trickKey = ""
            }
        }
        writeTexts(scene)
    }

    private fun landed(trick: TrickType) {
        val points = (rider?.config ?: SkateControllerConfig()).tricks.points(trick)
        score += points
        trickKey = trick.nameKey
        shownFor = 0f
        events?.publish(TrickCompleted(trick.nameKey, points, 1f))
    }

    private fun findRider(): SkateBehavior? =
        scene.children.firstNotNullOfOrNull { it.getComponent<PlayerController>()?.behavior as? SkateBehavior }

    private fun writeTexts(root: GameObject) {
        for (child in root.children) {
            when (child.name) {
                TRICK_TEXT -> child.getComponent<UITextComponent>()?.key = trickKey
                SCORE_TEXT -> child.getComponent<UITextComponent>()?.args = listOf(score.toString())
            }
            writeTexts(child)
        }
    }

    companion object {
        const val TRICK_TEXT = GameUi.TRICK_NAME
        const val SCORE_TEXT = GameUi.SCORE_NAME
    }
}
