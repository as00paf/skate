package com.pafoid.skate.engine.scripting.runtime

import com.pafoid.skate.engine.input.ContextBlocking
import com.pafoid.skate.engine.input.ContextHandle
import com.pafoid.skate.engine.input.InputContext
import com.pafoid.skate.engine.input.InputContextStack
import com.pafoid.skate.engine.input.InputReader
import com.pafoid.skate.engine.scripting.api.ScriptInput

/**
 * Exposes the engine [InputReader] to scripts. Contexts are per script: [ScriptRuntime] wraps this shared reader in a
 * [ScriptInputScope] for each script instance, so this unscoped view does not push contexts itself.
 */
class ReaderScriptInput(private val reader: InputReader) : ScriptInput {
    private val lastDown = HashMap<Int, Boolean>()

    override fun isKeyDown(key: Int): Boolean = reader.isKeyDown(key).also { lastDown[key] = it }

    override fun isKeyPressed(key: Int): Boolean = reader.wasKeyPressed(key)

    /** The reader has no release edge, so this compares with the state seen on the previous query of the key. */
    override fun isKeyReleased(key: Int): Boolean {
        val was = lastDown[key] ?: false
        val now = reader.isKeyDown(key)
        lastDown[key] = now
        return was && !now
    }

    override fun isMouseButtonDown(button: Int): Boolean = reader.isMouseButtonDown(button)

    override fun action(name: String): Float = reader.actionValue(name)
    override fun isActionDown(name: String): Boolean = reader.isActionDown(name)
    override fun wasActionPressed(name: String): Boolean = reader.wasActionPressed(name)
    override fun pushContext(context: String, priority: Int, blocking: String): Int = -1
    override fun popContext(handle: Int): Boolean = false
}

/**
 * One script instance's input: reads go to [base]; contexts it pushes on [stack] are its own and are all popped by
 * [releaseContexts] (the runtime calls it when the script is destroyed, hot reloaded or fails).
 */
class ScriptInputScope(
    private val base: ScriptInput,
    private val stack: InputContextStack?,
    private val owner: String,
) : ScriptInput by base {

    private val handles = LinkedHashMap<Int, ContextHandle>()
    private var nextHandle = 1

    /** Contexts this script currently holds. */
    val heldCount: Int get() = handles.size

    override fun pushContext(context: String, priority: Int, blocking: String): Int {
        val stack = stack ?: return -1
        require(context.isNotBlank()) { "pushContext: context id is blank" }
        val handle = nextHandle++
        handles[handle] = stack.push(InputContext(context), priority, parseBlocking(blocking), owner)
        return handle
    }

    override fun popContext(handle: Int): Boolean {
        val h = handles.remove(handle) ?: return false
        return stack?.pop(h) ?: false
    }

    fun releaseContexts() {
        handles.values.forEach { stack?.pop(it) }
        handles.clear()
    }

    companion object {
        /** "none", "consume" or "all" (case-insensitive); anything else is a script error. */
        fun parseBlocking(text: String): ContextBlocking = when (text.trim().lowercase()) {
            "none" -> ContextBlocking.NONE
            "consume", "consume_bound" -> ContextBlocking.CONSUME_BOUND
            "all" -> ContextBlocking.ALL
            else -> throw IllegalArgumentException("pushContext: blocking must be \"none\", \"consume\" or \"all\", was \"$text\"")
        }
    }
}
