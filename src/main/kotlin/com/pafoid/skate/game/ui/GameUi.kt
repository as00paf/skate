package com.pafoid.skate.game.ui

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.systems.System
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.ui.UICanvasComponent
import com.pafoid.skate.engine.ui.UIElementComponent
import com.pafoid.skate.engine.ui.PauseMenu
import com.pafoid.skate.engine.ui.UITextComponent

/** Live values shown by the HUD. Score and trick name are the trick HUD's ([SkateHudSystem], GPL-21). */
class GameHudState(
    var elapsedSeconds: Float = 0f,
)

/** The sample game's HUD (score, trick name, timer); the pause menu is the engine's [com.pafoid.skate.engine.ui.PauseMenu]. */
object GameUi {
    const val HUD_NAME = "HUD"
    const val SCORE_NAME = "ScoreText"
    const val TIMER_NAME = "TimerText"
    const val TRICK_NAME = "TrickText"

    fun createHud(): GameObject = GameObject(HUD_NAME).apply {
        addComponent(UICanvasComponent(sortOrder = 0))
        addComponent(UIElementComponent().stretched())
        addChild(text(SCORE_NAME, "hud.score", 48f, 0f).also { it.element().placed(0f, 0f, 40f, 40f, 600f, 64f) })
        addChild(text(TIMER_NAME, "hud.timer", 48f, 1f).also { it.element().placed(1f, 0f, -40f, 40f, 300f, 64f) })
        addChild(text(TRICK_NAME, "", 72f, 0.5f).also { it.element().placed(0.5f, 1f, 0f, -120f, 1000f, 96f) })
    }

    private fun GameObject.element(): UIElementComponent = getComponent<UIElementComponent>()!!

    private fun text(name: String, key: String, size: Float, alignX: Float) = GameObject(name).apply {
        addComponent(UIElementComponent())
        addComponent(UITextComponent(key = key, fontSize = size, alignX = alignX))
    }

    fun formatTime(seconds: Float): String {
        val total = seconds.toInt().coerceAtLeast(0)
        return "%02d:%02d".format(total / 60, total % 60)
    }
}

/**
 * Drives the sample game's HUD: refreshes its text from [state] and advances the timer while playing. It is a
 * simulation system, so the timer stops while the pause menu is open. The HUD follows the scene when it is reloaded
 * (Restart).
 */
class GameUiSystem(
    val state: GameHudState,
    val hud: GameObject = GameUi.createHud(),
) : System(priority = ExecutionPriority.DEFAULT) {

    override fun init(scene: Scene) {
        super.init(scene)
        if (hud.parent !== scene) scene.addChild(hud)
    }

    /** Back to a fresh run (pause menu Restart). */
    fun resetState() {
        state.elapsedSeconds = 0f
        refreshHud()
    }

    override fun update(dt: Float) {
        if (scene.isRunning) state.elapsedSeconds += dt
        refreshHud()
    }

    fun refreshHud() {
        textOf(GameUi.TIMER_NAME)?.args = listOf(GameUi.formatTime(state.elapsedSeconds))
    }

    private fun textOf(name: String) = PauseMenu.find(hud, name)?.getComponent<UITextComponent>()
}
