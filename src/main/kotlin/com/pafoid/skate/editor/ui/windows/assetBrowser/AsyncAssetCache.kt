package com.pafoid.skate.editor.ui.windows.assetBrowser

import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Per-tab cache for Asset Browser previews (AST-23). [get] never blocks: the first call schedules [decode] on an IO
 * worker and returns null (the tab draws a placeholder); [pump], called once per frame on the main thread, uploads at
 * most [uploadsPerFrame] decoded results, so opening a tab with hundreds of files never stalls a frame.
 *
 * Main thread: [get], [pump], [state], [clear]. Worker: [decode] only.
 */
class AsyncAssetCache<K : Any, D : Any, T : Any>(
    private val io: (() -> Unit) -> Unit,
    private val decode: (K) -> D,
    private val upload: (K, D) -> T,
    private val dispose: (T) -> Unit = {},
    private val discard: (D) -> Unit = {},
    private val uploadsPerFrame: Int = 4,
) {
    sealed interface State<out T> {
        object Loading : State<Nothing>
        class Ready<T>(val value: T) : State<T>
        class Failed(val reason: String) : State<Nothing>
    }

    private class Decoded<K, D>(val key: K, val generation: Int, val data: D?, val error: Throwable?)

    private val states = HashMap<K, State<T>>()
    private val decoded = ConcurrentLinkedQueue<Decoded<K, D>>()

    @Volatile
    private var generation = 0

    /** Number of results waiting for [pump]. */
    val pending: Int get() = decoded.size

    /** The uploaded value of [key], or null while it loads (scheduling the load on first use) or after it failed. */
    fun get(key: K): T? {
        when (val state = states[key]) {
            is State.Ready -> return state.value
            is State.Failed, State.Loading -> return null
            null -> Unit
        }
        states[key] = State.Loading
        val gen = generation
        io {
            val result = try {
                Decoded(key, gen, decode(key), null)
            } catch (e: Throwable) {
                Decoded<K, D>(key, gen, null, e)
            }
            decoded += result
        }
        return null
    }

    fun state(key: K): State<T>? = states[key]

    /** Uploads up to [uploadsPerFrame] decoded results (failures don't count). Returns how many it uploaded. */
    fun pump(): Int {
        var uploaded = 0
        while (uploaded < uploadsPerFrame) {
            val next = decoded.poll() ?: break
            if (next.generation != generation || states[next.key] !== State.Loading) {
                next.data?.let(discard)
                continue
            }
            if (next.data == null) {
                states[next.key] = State.Failed(next.error?.message ?: next.error?.javaClass?.simpleName ?: "decode failed")
                continue
            }
            states[next.key] = try {
                State.Ready(upload(next.key, next.data)).also { uploaded++ }
            } catch (e: Exception) {
                State.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
        return uploaded
    }

    /** Disposes every uploaded value and forgets all keys; results still decoding are discarded when they arrive. */
    fun clear() {
        generation++
        states.values.forEach { if (it is State.Ready) dispose(it.value) }
        states.clear()
        generateSequence { decoded.poll() }.forEach { d -> d.data?.let(discard) }
    }
}
