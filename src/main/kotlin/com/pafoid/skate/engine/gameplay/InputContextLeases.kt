package com.pafoid.skate.engine.gameplay

import com.pafoid.skate.engine.input.ContextHandle
import com.pafoid.skate.engine.input.InputContextStack

/**
 * Input contexts held on [stack] on behalf of owners (characters), INP-10. Each owner holds a list of
 * [InputContextRequest]s; [hold] pushes and pops so the stack matches it. The engine calls [releaseAll] when play
 * stops or a scene loads, and [retainOnly] each frame, so a context can never outlive the character that asked for it.
 * Pure; main thread only.
 */
class InputContextLeases(private val stack: InputContextStack) {

    private class Lease(val requests: List<InputContextRequest>, val handles: List<ContextHandle>)

    private val leases = HashMap<Any, Lease>()

    /** Owners that hold at least one context. */
    val owners: Set<Any> get() = leases.keys

    fun hold(owner: Any, requests: List<InputContextRequest>) {
        val current = leases[owner]
        if (current?.requests == requests) return
        current?.handles?.forEach(stack::pop)
        if (requests.isEmpty()) {
            leases.remove(owner)
            return
        }
        val tag = "character:${System.identityHashCode(owner)}"
        leases[owner] = Lease(requests, requests.map { stack.push(it.context, it.priority, it.blocking, tag) })
    }

    fun release(owner: Any) = hold(owner, emptyList())

    /** Releases the leases of every owner not in [owners] (characters that left the scene). */
    fun retainOnly(owners: Set<Any>) {
        leases.keys.filter { it !in owners }.forEach(::release)
    }

    fun releaseAll() = leases.keys.toList().forEach(::release)
}
