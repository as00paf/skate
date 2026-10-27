package com.pafoid.skate.engine.assets

import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.JobSystem
import com.pafoid.skate.engine.events.AssetEvent
import kotlinx.coroutines.Job
import java.util.concurrent.ConcurrentHashMap

sealed interface AssetState {
    object Loading : AssetState
    object Ready : AssetState
    class Failed(val reason: String) : AssetState
}

/**
 * Reference-counted handle to an asset that is decoded on a worker and uploaded on the main thread.
 * [get] returns the placeholder until the asset is [AssetState.Ready] (and after a failure).
 */
class AssetTicket<T : Any> internal constructor(val key: String, private val placeholder: T?) {
    @Volatile var state: AssetState = AssetState.Loading
        internal set
    @Volatile internal var asset: T? = null
    @Volatile internal var job: Job? = null
    @Volatile internal var removed = false
    internal var refCount = 0
    internal var disposer: (Any) -> Unit = {}

    val isReady: Boolean get() = state === AssetState.Ready
    val references: Int get() = synchronized(this) { refCount }

    fun get(): T? = asset ?: placeholder
}

/**
 * Async asset pipeline: `decode` runs on a [JobSystem] worker (CPU only, no GL/AL), `upload` runs on the
 * main thread inside [JobSystem.update]. Requests for the same key share one load. When the last reference
 * is released the asset is disposed on the main thread; an in-flight load is cancelled.
 * Call [request]/[release] from the main thread.
 */
class AssetLoadService(
    private val jobSystem: JobSystem,
    private val eventSystem: EventSystem? = null,
    private val onError: (String) -> Unit = {}
) {
    private val tickets = ConcurrentHashMap<String, AssetTicket<*>>()

    val loadedCount: Int get() = tickets.size

    @Suppress("UNCHECKED_CAST")
    fun <D : Any, T : Any> request(
        key: String,
        placeholder: T? = null,
        decode: suspend () -> D,
        upload: (D) -> T,
        dispose: (T) -> Unit = {},
        discard: (D) -> Unit = {}
    ): AssetTicket<T> {
        val existing = tickets[key] as AssetTicket<T>?
        if (existing != null) {
            synchronized(existing) {
                if (!existing.removed) {
                    existing.refCount++
                    return existing
                }
            }
        }
        val ticket = AssetTicket(key, placeholder)
        ticket.refCount = 1
        tickets[key] = ticket
        ticket.job = jobSystem.runAsync {
            val data = try {
                decode()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                jobSystem.runOnMain { fail(ticket, e) }
                return@runAsync
            }
            jobSystem.runOnMain {
                if (ticket.removed) {
                    discard(data)
                    return@runOnMain
                }
                try {
                    ticket.asset = upload(data)
                    ticket.state = AssetState.Ready
                    eventSystem?.publish(AssetEvent.Loaded(key))
                } catch (e: Throwable) {
                    fail(ticket, e)
                }
            }
        }
        ticket.disposer = { (it as? T)?.let(dispose) }
        return ticket
    }

    fun <T : Any> release(ticket: AssetTicket<T>) {
        synchronized(ticket) {
            if (ticket.removed) return
            ticket.refCount--
            if (ticket.refCount > 0) return
            ticket.removed = true
        }
        tickets.remove(ticket.key, ticket)
        val loaded = ticket.asset
        if (loaded == null) {
            ticket.job?.cancel()
            return
        }
        val unload = {
            ticket.disposer(loaded)
            ticket.asset = null
            eventSystem?.publish(AssetEvent.Unloaded(ticket.key))
        }
        if (jobSystem.isMainThread()) unload() else jobSystem.runOnMain { unload() }
    }

    private fun fail(ticket: AssetTicket<*>, error: Throwable) {
        val reason = error.message ?: error::class.java.simpleName
        ticket.state = AssetState.Failed(reason)
        onError("Failed to load asset ${ticket.key}: $reason")
        eventSystem?.publish(AssetEvent.Failed(ticket.key, reason))
    }
}
