package com.pafoid.skate.engine.ui

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.systems.System
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.ecs.systems.SystemPhase
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.input.ContextBlocking
import com.pafoid.skate.engine.input.ContextHandle
import com.pafoid.skate.engine.input.InputContext
import com.pafoid.skate.engine.input.InputContextStack
import org.joml.Vector4f

/** What Restart and Quit do on the current host (GUI-09); everything else in the pause menu is shared. */
class PauseMenuActions(
    /** Game: reload the scene as it was at launch. Editor: restart Play from the pre-play snapshot. */
    var restart: () -> Unit = {},
    /** Game: close the window. Editor: Stop. */
    var quit: () -> Unit = {},
)

/** One row of the pause menu panel, see [PauseMenu.definition]. */
data class PauseMenuEntry(val name: String, val kind: Kind, val textKey: String, val height: Float) {
    enum class Kind { TEXT, BUTTON, SLIDER, OTHER }
}

/** The runtime pause menu: a modal canvas with Resume, Restart, Language, Quit and a volume slider. */
object PauseMenu {
    const val NAME = "PauseMenu"
    const val RESUME = "Resume"
    const val RESTART = "Restart"
    const val LANGUAGE = "Language"
    const val QUIT = "Quit"
    const val VOLUME = "Volume"

    fun create(): GameObject = GameObject(NAME).apply {
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
            addChild(text("Title", "pause.title", 64f).also { it.getComponent<UIElementComponent>()!!.height = 90f })
            addChild(button(RESUME, "pause.resume"))
            addChild(button(RESTART, "pause.restart"))
            addChild(button(LANGUAGE, "pause.language"))
            addChild(button(QUIT, "pause.quit"))
            addChild(text("VolumeLabel", "pause.volume", 36f, alignX = 0f).also { it.getComponent<UIElementComponent>()!!.height = 48f })
            addChild(GameObject(VOLUME).apply {
                addComponent(UIElementComponent(height = 40f))
                addComponent(UISliderComponent(0f, 1f, 1f, 0.05f))
            })
        })
    }

    /**
     * What [menu] shows, in panel order: each entry's name, widget kind, text key and height. Editor Play and the
     * exported game must produce the same list (GUI-12).
     */
    fun definition(menu: GameObject): List<PauseMenuEntry> {
        val panel = find(menu, "Panel") ?: return emptyList()
        return panel.children.map { go ->
            val height = go.getComponent<UIElementComponent>()?.height ?: 0f
            val button = go.getComponent<UIButtonComponent>()
            val text = go.getComponent<UITextComponent>()
            val slider = go.getComponent<UISliderComponent>()
            when {
                button != null -> PauseMenuEntry(go.name, PauseMenuEntry.Kind.BUTTON, button.labelKey, height)
                slider != null -> PauseMenuEntry(go.name, PauseMenuEntry.Kind.SLIDER, "", height)
                text != null -> PauseMenuEntry(go.name, PauseMenuEntry.Kind.TEXT, text.key, height)
                else -> PauseMenuEntry(go.name, PauseMenuEntry.Kind.OTHER, "", height)
            }
        }
    }

    fun find(root: GameObject, name: String): GameObject? {
        for (c in root.children) {
            if (c.name == name) return c
            find(c, name)?.let { return it }
        }
        return null
    }

    private fun text(name: String, key: String, size: Float, alignX: Float = 0.5f) = GameObject(name).apply {
        addComponent(UIElementComponent())
        addComponent(UITextComponent(key = key, fontSize = size, alignX = alignX))
    }

    private fun button(name: String, labelKey: String) = GameObject(name).apply {
        addComponent(UIElementComponent(height = 80f))
        addComponent(UIButtonComponent(labelKey = labelKey, fontSize = 44f))
    }
}

/**
 * Runs the [PauseMenu] in any playing scene, identically in the exported game and in editor Play (GUI-09).
 *
 * The menu object is added to the scene only while it plays (so it never ends up in the editor's pre-play snapshot)
 * and removed when Play stops. Cancel (Esc / gamepad B) that no game canvas handles opens it ([open] is wired to
 * [UiInputSystem.onUnhandledCancel]); cancel or Resume closes it. Opening calls [setFrozen] (true), which pauses the
 * simulation, animation and audio; closing unfreezes. Restart and Quit go through [defer] so the host can run them
 * after the frame, outside the system update (they may switch scenes or stop Play).
 *
 * While open, the menu also holds `ui` on the input context stack from [inputContexts] with [ContextBlocking.ALL]
 * (INP-10), so gameplay, character and script contexts read nothing, and keys held when it closes are not new presses.
 */
class PauseMenuSystem(
    private val setFrozen: (Boolean) -> Unit,
    val actions: PauseMenuActions = PauseMenuActions(),
    private val toggleLanguage: () -> Unit = {},
    private val volume: () -> Float = { 1f },
    private val setVolume: (Float) -> Unit = {},
    private val defer: (() -> Unit) -> Unit = { it() },
    private val inputContexts: () -> InputContextStack? = { null },
) : System(priority = ExecutionPriority.LATE) {

    override val phase = SystemPhase.LATE_UPDATE
    override val runsBefore get() = listOf(UiLayoutSystem::class)
    override val runsWhilePaused get() = true

    val menu: GameObject = PauseMenu.create()
    private val canvas = menu.getComponent<UICanvasComponent>()!!
    private val slider = PauseMenu.find(menu, PauseMenu.VOLUME)!!.getComponent<UISliderComponent>()!!

    val isOpen: Boolean get() = canvas.visible

    private var modalInput: ContextHandle? = null

    init {
        canvas.onCancel = { close() }
        button(PauseMenu.RESUME).onClick = { close() }
        button(PauseMenu.RESTART).onClick = {
            close()
            defer { actions.restart() }
        }
        button(PauseMenu.QUIT).onClick = { defer { actions.quit() } }
        button(PauseMenu.LANGUAGE).onClick = { toggleLanguage() }
        slider.onValueChanged = { setVolume(it) }
    }

    private fun button(name: String) = PauseMenu.find(menu, name)!!.getComponent<UIButtonComponent>()!!

    override fun init(scene: Scene) {
        super.init(scene)
        // SystemManager.loadScene clears the pause, so the menu must not stay open over the new scene.
        close()
        // Another scene (Restart reloads it): the menu moves there on the next playing frame.
        if (menu.parent != null && menu.parent !== scene) menu.parent?.removeChild(menu)
    }

    /** Opens the menu and freezes the game; ignored when the scene is not playing. */
    fun open() {
        if (isOpen || !scene.isRunning) return
        attach()
        slider.value = volume().coerceIn(slider.min, slider.max)
        canvas.visible = true
        setFrozen(true)
        if (modalInput == null) {
            modalInput = inputContexts()?.push(InputContext.UI, MODAL_PRIORITY, ContextBlocking.ALL, owner = "pause-menu")
        }
    }

    fun close() {
        releaseInput()
        if (!isOpen) return
        canvas.visible = false
        setFrozen(false)
    }

    private fun releaseInput() {
        modalInput?.let { inputContexts()?.pop(it) }
        modalInput = null
    }

    fun toggle() = if (isOpen) close() else open()

    override fun update(dt: Float) {
        if (scene.isRunning) {
            attach()
        } else {
            // Play stopped (editor Stop, or the toolbar's pause): the menu goes away with it.
            close()
            if (menu.parent === scene) scene.removeChild(menu)
        }
    }

    private fun attach() {
        if (menu.parent !== scene) scene.addChild(menu)
    }

    override fun destroy() {
        releaseInput()
        menu.parent?.removeChild(menu)
        super.destroy()
    }

    companion object {
        /** Above any gameplay, character or script context. */
        const val MODAL_PRIORITY = 1_000_000
    }
}
