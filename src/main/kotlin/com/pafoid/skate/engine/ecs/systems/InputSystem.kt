package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.events.ActionEvent
import com.pafoid.skate.engine.events.ActionPhase
import com.pafoid.skate.engine.events.EngineAction
import com.pafoid.skate.engine.events.GamepadConnected
import com.pafoid.skate.engine.events.GamepadDisconnected
import com.pafoid.skate.engine.input.ActionInputReader
import com.pafoid.skate.engine.input.ActionMap
import com.pafoid.skate.engine.input.InputContext
import com.pafoid.skate.engine.input.InputContextStack
import com.pafoid.skate.engine.input.InputHistory
import com.pafoid.skate.engine.input.InputMappings
import com.pafoid.skate.engine.input.InputProvider
import com.pafoid.skate.engine.input.InputReader
import com.pafoid.skate.engine.input.ProviderRawInput
import com.pafoid.skate.engine.input.RumbleManager
import com.pafoid.skate.engine.input.UiNavigationActions

/**
 * Device and action plumbing (INP-10): refreshes gamepads (hot-plug events), advances action edges, samples the
 * [history], publishes opt-in [ActionEvent]s and ticks rumble. It knows no game actions; gameplay code reads
 * [reader] (e.g. `CharacterInputSampler`).
 */
class InputSystem(
    val inputProvider: InputProvider,
    private val eventSystem: EventSystem,
) : System(priority = ExecutionPriority.EARLY) {

    /** Keeps reading actions while paused so the pause menu can be driven. */
    override val runsWhilePaused get() = true

    /**
     * Action definitions and live bindings: the engine's UI navigation plus whatever presets and games register.
     * Rebind here, then call `mappings.storeBindings(actionMap)` to persist.
     */
    val actionMap: ActionMap = UiNavigationActions.register(ActionMap())

    private val actionReader =
        ActionInputReader(ProviderRawInput(inputProvider), actionMap, deadZones = { mappings.deadZones })

    /** Editor-independent key/action read API for scripts and runtime code. */
    val reader: InputReader get() = actionReader

    /** Prioritized input contexts; the base stack is `gameplay` with `ui` above it. Push/pop to change. */
    val contexts: InputContextStack get() = actionReader.contexts

    /** Readable contexts. */
    @Deprecated("Push/pop on contexts instead", ReplaceWith("contexts"))
    val activeContexts: Set<InputContext> get() = contexts.resolve { emptySet() }.keys

    /** Time history of tracked 2D sources (gestures); sampled once per update, nothing tracked by default. */
    val history = InputHistory()
    private var time = 0f

    /** Controller rumble. Unsupported (no-op) until a backend is assigned: GLFW has no haptics API. */
    val rumble = RumbleManager()

    var mappings: InputMappings = InputMappings()
        set(value) {
            field = value
            actionMap.loadProfile(value.bindings)
        }

    private var eventActions: List<String> = emptyList()
    private var eventActionsRevision = -1
    private val eventDown = HashMap<String, Boolean>()

    override fun init(scene: Scene) {
        super.init(scene)
        inputProvider.initializeGamepad()
        inputProvider.gamepadListener.registry.addListener { e ->
            if (e.connected) {
                eventSystem.publish(GamepadConnected(e.jid, e.name))
            } else {
                rumble.stopAll()
                eventSystem.publish(GamepadDisconnected(e.jid))
            }
        }
        eventDown.clear()

        eventSystem.subscribe<EngineAction.ApplyMappings> { event -> mappings = event.mappings }
    }

    override fun update(dt: Float) {
        inputProvider.refreshGamepadState()
        actionReader.update()
        time += dt
        history.sample(time)
        rumble.update(dt)
        publishActionEvents()
    }

    private fun publishActionEvents() {
        if (eventActionsRevision != actionMap.revision) {
            eventActions = actionMap.definitions().filter { it.publishEvents }.map { it.id }
            eventActionsRevision = actionMap.revision
        }
        for (id in eventActions) {
            val down = reader.isActionDown(id)
            val was = eventDown[id] ?: false
            if (down && !was) eventSystem.publish(ActionEvent(id, ActionPhase.STARTED, reader.actionValue(id)))
            else if (!down && was) eventSystem.publish(ActionEvent(id, ActionPhase.CANCELED, 0f))
            eventDown[id] = down
        }
    }
}
