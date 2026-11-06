package com.pafoid.skate.engine.input

/**
 * Raw device state as the running game sees it, identical in the editor's game view and a standalone build
 * (contract: docs/design/INP-09.md). Implementations must not depend on the editor.
 */
interface RawInput {
    fun isKeyDown(key: Int): Boolean
    fun wasKeyPressed(key: Int): Boolean
    /** Held, for a press that started inside the game view (it stays held if the pointer then leaves the view). */
    fun isMouseButtonDown(button: Int): Boolean
    /** First frame of such a press. */
    fun wasMouseButtonPressed(button: Int): Boolean
    /** Pointer in game render-target pixels (top-left origin, not clamped), the same pixel picking and runtime UI use. */
    fun mouseX(): Float
    fun mouseY(): Float
    /** Cursor motion this frame in window (screen) units, unscaled by view or target size; for look sensitivity. */
    fun mouseDeltaX(): Float
    fun mouseDeltaY(): Float
    fun scrollY(): Float
    fun isGamepadConnected(pad: Int = 0): Boolean
    fun gamepadAxis(axis: Int, pad: Int = 0): Float
    fun isGamepadButtonDown(button: Int, pad: Int = 0): Boolean
    fun wasGamepadButtonPressed(button: Int, pad: Int = 0): Boolean
    /** True while the cursor is captured (hidden and locked) for mouse look; then only deltas are meaningful. */
    fun isCursorCaptured(): Boolean = false
}

/** Read-only input API for scripts and runtime code: raw keys plus named actions. */
interface InputReader : RawInput {
    /** Analog value of the action, 0 when unknown or inactive. */
    fun actionValue(name: String): Float
    fun isActionDown(name: String): Boolean
    /** True only on the first frame the action is down (see [ActionInputReader.update]). */
    fun wasActionPressed(name: String): Boolean
    /** True when [context] is on the context stack and not silenced by a modal entry above it. */
    fun isContextActive(context: InputContext): Boolean
}

/** GLFW key codes, exposed so callers need no LWJGL import. */
object Keys {
    const val SPACE = 32
    const val A = 65; const val D = 68; const val S = 83; const val W = 87
    const val E = 69; const val Q = 81; const val R = 82
    const val ESCAPE = 256; const val ENTER = 257; const val TAB = 258
    const val RIGHT = 262; const val LEFT = 263; const val DOWN = 264; const val UP = 265
    const val LEFT_SHIFT = 340; const val LEFT_CONTROL = 341

    fun nameOf(key: Int): String = when (key) {
        SPACE -> "Space"; ESCAPE -> "Escape"; ENTER -> "Enter"; TAB -> "Tab"
        RIGHT -> "Right"; LEFT -> "Left"; DOWN -> "Down"; UP -> "Up"
        LEFT_SHIFT -> "Left Shift"; LEFT_CONTROL -> "Left Ctrl"
        in 48..57, in 65..90 -> key.toChar().toString()
        else -> "Key $key"
    }
}

class ActionInputReader(
    private val raw: RawInput,
    val actions: ActionMap = ActionMap(),
    private val pressThreshold: Float = 0.5f,
    private val deadZones: () -> DeadZoneSettings = { DeadZoneSettings() },
    /** Which contexts are read and which sources each one sees (INP-10). */
    val contexts: InputContextStack = InputContextStack.withDefaults(),
) : InputReader, RawInput by raw {

    /** Readable contexts (read-only view of [contexts]). */
    @Deprecated("Push/pop on contexts instead", ReplaceWith("contexts"))
    val activeContexts: Set<InputContext> get() = resolution().keys

    private val wasDown = HashMap<String, Boolean>()
    private val downNow = HashMap<String, Boolean>()
    private val watched = HashSet<String>()
    private val stick = org.joml.Vector2f()

    private var resolved: Map<InputContext, Set<String>> = emptyMap()
    private var resolvedStack = -1
    private var resolvedMap = -1
    private var lastUpdateKey = 0L

    /**
     * Call once per frame, before readers run, to advance action edge detection.
     * Only actions already queried through [wasActionPressed] are sampled, so unused actions cost nothing.
     * When the context stack or the bindings changed since the last call, an action that is already held does not
     * report a press: a press that carries over stays held, but a new press is needed for [wasActionPressed].
     */
    fun update() {
        val key = (contexts.revision.toLong() shl 32) or (actions.revision.toLong() and 0xffffffffL)
        val changed = key != lastUpdateKey
        lastUpdateKey = key
        wasDown.clear(); wasDown.putAll(downNow)
        downNow.clear()
        for (name in watched) {
            val down = isActionDown(name)
            downNow[name] = down
            if (changed && down) wasDown[name] = true
        }
    }

    override fun isContextActive(context: InputContext): Boolean = context in resolution()

    private fun resolution(): Map<InputContext, Set<String>> {
        if (resolvedStack != contexts.revision || resolvedMap != actions.revision) {
            resolved = contexts.resolve { ctx ->
                actions.definitions(ctx).flatMapTo(HashSet()) { d -> actions.bindings(d.id).map { it.physicalSourceId() } }
            }
            resolvedStack = contexts.revision
            resolvedMap = actions.revision
        }
        return resolved
    }

    private fun bindingsOf(name: String): List<ActionBinding> {
        val def = actions.definition(name) ?: return emptyList()
        val hidden = resolution()[def.context] ?: return emptyList()
        val all = actions.bindings(name)
        return if (hidden.isEmpty()) all else all.filter { it.physicalSourceId() !in hidden }
    }
    override fun actionValue(name: String): Float {
        var best = 0f
        for (b in bindingsOf(name)) {
            val v = valueOf(b)
            if (kotlin.math.abs(v) > kotlin.math.abs(best)) best = v
        }
        return best
    }

    override fun isActionDown(name: String): Boolean = bindingsOf(name).any { isDown(it) }

    override fun wasActionPressed(name: String): Boolean {
        if (watched.add(name)) downNow[name] = isActionDown(name)
        return (downNow[name] ?: false) && wasDown[name] != true
    }

    private fun isDown(b: ActionBinding): Boolean = when (b) {
        is ActionBinding.Key -> raw.isKeyDown(b.key)
        is ActionBinding.MouseButton -> raw.isMouseButtonDown(b.button)
        is ActionBinding.GamepadButton -> raw.isGamepadButtonDown(b.button)
        is ActionBinding.GamepadAxis -> when (b.axis) {
            4 -> deadZones().leftTrigger.isPressed(raw.gamepadAxis(4))
            5 -> deadZones().rightTrigger.isPressed(raw.gamepadAxis(5))
            else -> kotlin.math.abs(valueOf(b)) >= pressThreshold
        }
    }

    private fun valueOf(b: ActionBinding): Float = when (b) {
        is ActionBinding.Key -> if (raw.isKeyDown(b.key)) b.scale else 0f
        is ActionBinding.MouseButton -> if (raw.isMouseButtonDown(b.button)) b.scale else 0f
        is ActionBinding.GamepadButton -> if (raw.isGamepadButtonDown(b.button)) b.scale else 0f
        is ActionBinding.GamepadAxis -> processedAxis(b.axis) * b.scale
    }

    /** Raw axis passed through the dead zone of the stick/trigger that owns it. */
    private fun processedAxis(axis: Int): Float {
        val zones = deadZones()
        return when (axis) {
            0, 1 -> {
                zones.leftStick.apply(raw.gamepadAxis(0), raw.gamepadAxis(1), stick)
                if (axis == 0) stick.x else stick.y
            }
            2, 3 -> {
                zones.rightStick.apply(raw.gamepadAxis(2), raw.gamepadAxis(3), stick)
                if (axis == 2) stick.x else stick.y
            }
            4 -> zones.leftTrigger.apply(raw.gamepadAxis(4))
            5 -> zones.rightTrigger.apply(raw.gamepadAxis(5))
            else -> raw.gamepadAxis(axis)
        }
    }
}
