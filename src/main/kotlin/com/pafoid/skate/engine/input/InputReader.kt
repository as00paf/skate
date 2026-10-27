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
}

sealed class ActionBinding {
    data class Key(val key: Int, val scale: Float = 1f) : ActionBinding()
    data class MouseButton(val button: Int, val scale: Float = 1f) : ActionBinding()
    data class GamepadButton(val button: Int, val scale: Float = 1f) : ActionBinding()
    data class GamepadAxis(
        val axis: Int,
        val scale: Float = 1f,
        val deadZone: Float = 0.15f
    ) : ActionBinding()
}

class ActionMap {
    private val actions = LinkedHashMap<String, MutableList<ActionBinding>>()

    val names: Set<String> get() = actions.keys

    fun bind(action: String, binding: ActionBinding): ActionMap {
        actions.getOrPut(action) { mutableListOf() }.add(binding)
        return this
    }

    fun clear(action: String) {
        actions.remove(action)
    }

    fun bindings(action: String): List<ActionBinding> = actions[action] ?: emptyList()

    /** Binds the gamepad part of [mappings] to the standard action names. */
    fun applyMappings(mappings: InputMappings) {
        listOf("jump", "sprint", "crouch", "moveX", "moveY", "lookX", "lookY").forEach(::clear)
        fun gamepad(action: String, b: InputBinding, scale: Float = 1f) {
            if (b.gamepadAxis >= 0) {
                val s = if (b.inverted) -scale else scale
                bind(action, ActionBinding.GamepadAxis(b.gamepadAxis, s))
            }
            if (b.gamepadButton >= 0) bind(action, ActionBinding.GamepadButton(b.gamepadButton))
            if (b.keyboardKey >= 0) bind(action, ActionBinding.Key(b.keyboardKey))
        }
        gamepad("jump", mappings.jump)
        gamepad("sprint", mappings.sprint)
        gamepad("crouch", mappings.crouch)
        gamepad("moveX", mappings.moveRight)
        // GLFW reports stick-up as negative
        gamepad("moveY", mappings.moveUp, -1f)
        gamepad("lookX", mappings.cameraLookX)
        gamepad("lookY", mappings.cameraLookY, -1f)
    }
}

class ActionInputReader(
    private val raw: RawInput,
    val actions: ActionMap = ActionMap(),
    private val pressThreshold: Float = 0.5f
) : InputReader, RawInput by raw {

    private val wasDown = HashMap<String, Boolean>()
    private val downNow = HashMap<String, Boolean>()
    private val watched = HashSet<String>()

    /**
     * Call once per frame, before readers run, to advance action edge detection.
     * Only actions already queried through [wasActionPressed] are sampled, so unused actions cost nothing.
     */
    fun update() {
        wasDown.clear(); wasDown.putAll(downNow)
        downNow.clear()
        for (name in watched) downNow[name] = isActionDown(name)
    }

    override fun actionValue(name: String): Float {
        var best = 0f
        for (b in actions.bindings(name)) {
            val v = valueOf(b)
            if (kotlin.math.abs(v) > kotlin.math.abs(best)) best = v
        }
        return best
    }

    override fun isActionDown(name: String): Boolean = kotlin.math.abs(actionValue(name)) >= pressThreshold

    override fun wasActionPressed(name: String): Boolean {
        if (watched.add(name)) downNow[name] = isActionDown(name)
        return (downNow[name] ?: false) && wasDown[name] != true
    }

    private fun valueOf(b: ActionBinding): Float = when (b) {
        is ActionBinding.Key -> if (raw.isKeyDown(b.key)) b.scale else 0f
        is ActionBinding.MouseButton -> if (raw.isMouseButtonDown(b.button)) b.scale else 0f
        is ActionBinding.GamepadButton -> if (raw.isGamepadButtonDown(b.button)) b.scale else 0f
        is ActionBinding.GamepadAxis -> {
            val v = raw.gamepadAxis(b.axis)
            if (kotlin.math.abs(v) < b.deadZone) 0f else v * b.scale
        }
    }
}
