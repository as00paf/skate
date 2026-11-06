package com.pafoid.skate.engine.input

/** What an [InputContextStack] entry does to the contexts below it. */
enum class ContextBlocking {
    /** Lower contexts keep working (HUD-style overlays). */
    NONE,

    /** Physical sources bound in this context are hidden from lower contexts; other sources pass through. */
    CONSUME_BOUND,

    /** Every lower context is silenced (modal: pause menu, text entry, cutscene). */
    ALL,
}

/** Opaque token returned by [InputContextStack.push]; pop with it. */
class ContextHandle internal constructor(internal val entry: InputContextStack.Entry)

/**
 * Prioritized stack of active input contexts (INP-10). Higher [Entry.priority] is on top; equal priorities are
 * ordered by push time (the later push is on top). The same context may be pushed by several owners and stays
 * active until every handle is popped. Pure and not thread safe; mutate from the main thread.
 */
class InputContextStack {

    class Entry internal constructor(
        val context: InputContext,
        val priority: Int,
        val blocking: ContextBlocking,
        val owner: String,
        internal val sequence: Long,
    ) {
        override fun toString() = "Entry($context, priority=$priority, $blocking, owner='$owner')"
    }

    private val stack = ArrayList<Entry>()
    private var nextSequence = 0L

    /** Incremented on every push/pop so readers can cache their resolution. */
    var revision: Int = 0
        private set

    fun push(
        context: InputContext,
        priority: Int = 0,
        blocking: ContextBlocking = ContextBlocking.CONSUME_BOUND,
        owner: String = "",
    ): ContextHandle {
        val entry = Entry(context, priority, blocking, owner, nextSequence++)
        val at = stack.indexOfFirst { below(it, entry) }.let { if (it < 0) stack.size else it }
        stack.add(at, entry)
        revision++
        return ContextHandle(entry)
    }

    /** Removes exactly the entry of [handle]; false when it was already popped. */
    fun pop(handle: ContextHandle): Boolean = stack.remove(handle.entry).also { if (it) revision++ }

    /** All entries, top first (including those silenced by an [ContextBlocking.ALL] entry above). */
    fun entries(): List<Entry> = stack.toList()

    fun isActive(context: InputContext): Boolean {
        for (e in stack) {
            if (e.context == context) return true
            if (e.blocking == ContextBlocking.ALL) return false
        }
        return false
    }

    /**
     * Readable contexts, top first, each mapped to the physical sources ([ActionBinding.physicalSourceId]) hidden
     * from it by [ContextBlocking.CONSUME_BOUND] entries above its topmost occurrence. Contexts under an
     * [ContextBlocking.ALL] entry are absent. [boundSources] gives the sources a context binds.
     */
    fun resolve(boundSources: (InputContext) -> Set<String>): Map<InputContext, Set<String>> {
        val out = LinkedHashMap<InputContext, Set<String>>()
        val consumed = HashSet<String>()
        for (e in stack) {
            if (e.context !in out) out[e.context] = if (consumed.isEmpty()) emptySet() else consumed.toSet()
            when (e.blocking) {
                ContextBlocking.NONE -> Unit
                ContextBlocking.CONSUME_BOUND -> consumed += boundSources(e.context)
                ContextBlocking.ALL -> break
            }
        }
        return out
    }

    private fun below(existing: Entry, new: Entry): Boolean =
        existing.priority < new.priority || (existing.priority == new.priority && existing.sequence < new.sequence)

    companion object {
        /**
         * Priority of the base `ui` entry: above any game context (a ride or vehicle context consumes sources from the
         * contexts *below* it, and must not take the menu's confirm or navigation away). `ui` blocks nothing, so
         * gameplay is unaffected; a modal menu pushes its own blocking entry higher still.
         */
        const val UI_PRIORITY = 1000

        /** The engine's base stack: `gameplay` with `ui` on top passing everything through. */
        fun withDefaults(): InputContextStack = InputContextStack().apply {
            push(InputContext.GAMEPLAY, owner = "engine")
            push(InputContext.UI, priority = UI_PRIORITY, blocking = ContextBlocking.NONE, owner = "engine")
        }
    }
}
