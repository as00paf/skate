package com.pafoid.skate.game.ui

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.systems.System
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.ui.UIButtonComponent
import com.pafoid.skate.engine.ui.UICanvasComponent
import com.pafoid.skate.engine.ui.UIElementComponent
import com.pafoid.skate.engine.ui.UIImageComponent
import com.pafoid.skate.engine.ui.UISliderComponent
import com.pafoid.skate.engine.ui.UITextComponent
import com.pafoid.skate.engine.ui.UiLayoutGroup
import com.pafoid.skate.engine.ui.UiLayoutKind
import org.joml.Vector4f

/** Live values shown by the HUD. */
class GameHudState(
    var score: Int = 0,
    /** StringManager key of the trick being performed (e.g. `trick.kickflip`); empty hides the text. */
    var trickKey: String = "",
    var elapsedSeconds: Float = 0f,
)

/** The sample game's HUD (score, trick name, timer) and pause menu, built from the engine UI components. */
object GameUi {
    const val HUD_NAME = "HUD"
    const val PAUSE_MENU_NAME = "PauseMenu"
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

    fun createPauseMenu(): GameObject = GameObject(PAUSE_MENU_NAME).apply {
        addComponent(UICanvasComponent(sortOrder = 100, visible = false, modal = true))
        addComponent(UIElementComponent().stretched())
        addChild(GameObject("Dim").apply {
            addComponent(UIElementComponent().stretched())
            addComponent(UIImageComponent(tint = Vector4f(0f, 0f, 0f, 0.6f)))
        })
        addChild(GameObject("Panel").apply {
            addComponent(
                UIElementComponent(
                    layout = UiLayoutGroup(
                        UiLayoutKind.VERTICAL,
                        paddingLeft = 40f, paddingTop = 40f, paddingRight = 40f, paddingBottom = 40f,
                        spacing = 24f, fillCross = true,
                    )
                ).placed(0.5f, 0.5f, 0f, 0f, 640f, 744f)
            )
            addComponent(UIImageComponent(tint = Vector4f(0.1f, 0.1f, 0.14f, 0.95f)))
            addChild(text("Title", "pause.title", 64f, 0.5f).also { it.element().height = 90f })
            addChild(button("Resume", "pause.resume"))
            addChild(button("Restart", "pause.restart"))
            addChild(button("Language", "pause.language"))
            addChild(button("Quit", "pause.quit"))
            addChild(text("VolumeLabel", "pause.volume", 36f, 0f).also { it.element().height = 48f })
            addChild(GameObject("Volume").apply {
                addComponent(UIElementComponent(height = 40f))
                addComponent(UISliderComponent(0f, 1f, 1f, 0.05f))
            })
        })
    }

    private fun GameObject.element(): UIElementComponent = getComponent<UIElementComponent>()!!

    private fun text(name: String, key: String, size: Float, alignX: Float) = GameObject(name).apply {
        addComponent(UIElementComponent())
        addComponent(UITextComponent(key = key, fontSize = size, alignX = alignX))
    }

    private fun button(name: String, labelKey: String) = GameObject(name).apply {
        addComponent(UIElementComponent(height = 80f))
        addComponent(UIButtonComponent(labelKey = labelKey, fontSize = 44f))
    }

    fun formatTime(seconds: Float): String {
        val total = seconds.toInt().coerceAtLeast(0)
        return "%02d:%02d".format(total / 60, total % 60)
    }
}

/**
 * Drives the sample game's UI: refreshes HUD text from [state], advances the timer while playing and
 * handles the pause menu (cancel on the HUD opens it; Resume/cancel closes it).
 */
class GameUiSystem(
    val state: GameHudState,
    private val hud: GameObject,
    private val pauseMenu: GameObject,
    private val onPauseChanged: (Boolean) -> Unit = {},
    private val onRestart: () -> Unit = {},
    private val onQuit: () -> Unit = {},
    private val onVolumeChanged: (Float) -> Unit = {},
    private val onLanguageToggle: () -> Unit = {},
) : System(priority = ExecutionPriority.DEFAULT) {

    var paused = false
        private set

    override fun init(scene: Scene) {
        super.init(scene)
        bind()
    }

    private fun bind() {
        hud.getComponent<UICanvasComponent>()?.onCancel = { setPaused(true) }
        pauseMenu.getComponent<UICanvasComponent>()?.onCancel = { setPaused(false) }
        button("Resume")?.onClick = { setPaused(false) }
        button("Restart")?.onClick = {
            state.score = 0
            state.elapsedSeconds = 0f
            state.trickKey = ""
            setPaused(false)
            onRestart()
        }
        button("Quit")?.onClick = onQuit
        button("Language")?.onClick = onLanguageToggle
        find(pauseMenu, "Volume")?.getComponent<UISliderComponent>()?.onValueChanged = onVolumeChanged
    }

    fun setPaused(value: Boolean) {
        if (paused == value) return
        paused = value
        pauseMenu.getComponent<UICanvasComponent>()?.visible = value
        onPauseChanged(value)
    }

    override fun update(dt: Float) {
        if (!paused && scene.isRunning) state.elapsedSeconds += dt
        refreshHud()
    }

    fun refreshHud() {
        textOf(GameUi.SCORE_NAME)?.args = listOf(state.score.toString())
        textOf(GameUi.TIMER_NAME)?.args = listOf(GameUi.formatTime(state.elapsedSeconds))
        textOf(GameUi.TRICK_NAME)?.key = state.trickKey
    }

    private fun textOf(name: String) = find(hud, name)?.getComponent<UITextComponent>()

    private fun button(name: String) = find(pauseMenu, name)?.getComponent<UIButtonComponent>()

    private fun find(root: GameObject, name: String): GameObject? {
        for (c in root.children) {
            if (c.name == name) return c
            find(c, name)?.let { return it }
        }
        return null
    }

    companion object {
        /** Adds the HUD and pause menu to [scene]. */
        fun attach(scene: Scene, hud: GameObject = GameUi.createHud(), pause: GameObject = GameUi.createPauseMenu()) {
            scene.addChild(hud)
            scene.addChild(pause)
        }
    }
}
