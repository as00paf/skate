package com.pafoid.skate.engine.render.shader

/**
 * Watches the files loaded shaders depend on (AST-26 §5.5) and reports which changed. GL-free: the caller recompiles
 * on its own (GL) thread.
 *
 * Only the [watch]ed ids are checked, each through [fingerprint], which re-resolves the id: an edit, an override
 * appearing or disappearing, or a contract fix all change it. A change is reported once it has been stable for two
 * polls, so a file caught half-written by an editor is not compiled. [folderSignature] covers overrides added where
 * no watched id would see them (a new `.meta` in `Assets/Shaders`): when it changes, [onFolderChanged] runs first
 * (e.g. to drop the override index) and every id is checked again.
 */
class ShaderHotReloader(
    private val intervalMs: Long = 500,
    private val clock: () -> Long = System::currentTimeMillis,
    private val fingerprint: (String) -> String,
    private val folderSignature: () -> String = { "" },
    private val onFolderChanged: () -> Unit = {},
    private val onChanged: (changed: Set<String>) -> Unit,
) {
    private val stamps = HashMap<String, String>()
    private val pending = HashMap<String, String>()
    private var folder: String? = null
    private var lastPoll = Long.MIN_VALUE

    /** Ids currently watched. */
    val watched: Set<String> get() = stamps.keys

    /** Starts watching [ids] (their current state is the baseline); ids already watched keep their baseline. */
    fun watch(ids: Collection<String>) {
        for (id in ids) if (id !in stamps) stamps[id] = fingerprint(id)
    }

    /** Stops watching ids not in [ids] (unloaded shaders). */
    fun retain(ids: Set<String>) {
        stamps.keys.retainAll(ids)
        pending.keys.retainAll(ids)
    }

    /** Call once per frame; checks at most every [intervalMs]. */
    fun poll() {
        val now = clock()
        if (lastPoll != Long.MIN_VALUE && now - lastPoll < intervalMs) return
        lastPoll = now
        val signature = folderSignature()
        if (folder != null && signature != folder) onFolderChanged()
        folder = signature

        val changed = LinkedHashSet<String>()
        for ((id, old) in stamps.entries.toList()) {
            val current = fingerprint(id)
            if (current == old) {
                pending.remove(id)
                continue
            }
            if (pending[id] == current) { // the same new state two polls in a row: settled
                pending.remove(id)
                stamps[id] = current
                changed += id
            } else {
                pending[id] = current
            }
        }
        if (changed.isNotEmpty()) onChanged(changed)
    }
}
