package com.pafoid.skate.engine.input

/** Raw device state. Implementations must not depend on the editor. */
interface RawInput {
    fun isKeyDown(key: Int): Boolean
    fun wasKeyPressed(key: Int): Boolean
    fun isMouseButtonDown(button: Int): Boolean
    fun wasMouseButtonPressed(button: Int): Boolean
    fun mouseX(): Float
    fun mouseY(): Float
    fun mouseDeltaX(): Float
    fun mouseDeltaY(): Float
    fun scrollY(): Float
    fun isGamepadConnected(pad: Int = 0): Boolean
    fun gamepadAxis(axis: Int, pad: Int = 0): Float
    fun isGamepadButtonDown(button: Int, pad: Int = 0): Boolean
    fun wasGamepadButtonPressed(button: Int, pad: Int = 0): Boolean
}

/** Read-only input API for scripts and runtime code: raw keys plus named actions. */
interface InputReader : RawInput {
    /** Analog value of the action, 0 when unknown or inactive. */
    fun actionValue(name: String): Float
    fun isActionDown(name: String): Boolean
    /** True only on the first frame the action is down (see [ActionInputReader.update]). */
    fun wasActionPressed(name: String): Boolean
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
    private val deadZones: () -> DeadZoneSettings = { DeadZoneSettings() }
) : InputReader, RawInput by raw {

    /** Contexts whose actions are read; the others report 0. */
    val activeContexts: MutableSet<InputContext> = mutableSetOf(InputContext.GAMEPLAY, InputContext.UI)

    private val wasDown = HashMap<String, Boolean>()
    private val downNow = HashMap<String, Boolean>()
    private val watched = HashSet<String>()
    private val stick = org.joml.Vector2f()

    /**
     * Call once per frame, before readers run, to advance action edge detection.
     * Only actions already queried through [wasActionPressed] are sampled, so unused actions cost nothing.
     */
    fun update() {
        wasDown.clear(); wasDown.putAll(downNow)
        downNow.clear()
        for (name in watched) downNow[name] = isActionDown(name)
    }

    private fun bindingsOf(name: String): List<ActionBinding> {
        val def = actions.definition(name) ?: return emptyList()
        return if (def.context in activeContexts) actions.bindings(name) else emptyList()
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
