package com.pafoid.skate.engine.physics3d.jolt

import com.github.stephengold.joltjni.Body
import com.github.stephengold.joltjni.CollideShapeResult
import com.github.stephengold.joltjni.ContactManifold
import com.github.stephengold.joltjni.CustomContactListener
import com.github.stephengold.joltjni.enumerate.ValidateResult
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.physics3d.LongSet
import com.pafoid.skate.engine.physics3d.PhysicsContactListener
import org.joml.Vector3f

/**
 * Turns Jolt contact callbacks into the engine's collision and trigger events, with the Bullet backend's semantics:
 * one `onCollisionStarted` per GameObject pair when it starts touching, `onTriggerEnter`/`onTriggerExit` per
 * (trigger, other) pair, all dispatched on the stepping thread after the step.
 *
 * Jolt calls the listener on worker threads, so callbacks only append to a locked buffer. The touching set is rebuilt
 * each step from the "added" and "persisted" callbacks rather than tracked through "removed": Jolt reports a contact
 * as removed when its bodies fall asleep, while Bullet keeps reporting it. A pair whose two bodies are both asleep
 * therefore stays touching until one of them wakes up without the contact being seen again.
 */
internal class JoltContacts(private val handleOf: (Int) -> JoltRigidBodyHandle?) : AutoCloseable {

    // --- worker-thread side: callback records ---

    private val lock = Any()
    private var count = 0
    private var kinds = IntArray(INITIAL_RECORDS)
    private var ids1 = IntArray(INITIAL_RECORDS)
    private var ids2 = IntArray(INITIAL_RECORDS)
    private var data = FloatArray(INITIAL_RECORDS * FLOATS)

    val listener = object : CustomContactListener() {
        override fun onContactValidate(
            body1Va: Long, body2Va: Long, baseOffsetX: Double, baseOffsetY: Double, baseOffsetZ: Double, collisionResultVa: Long,
        ): Int {
            guarded {
                val result = CollideShapeResult(collisionResultVa)
                val on1 = result.contactPointOn1
                val on2 = result.contactPointOn2
                val bx = baseOffsetX.toFloat(); val by = baseOffsetY.toFloat(); val bz = baseOffsetZ.toFloat()
                record(
                    KIND_POINT, Body(body1Va).id, Body(body2Va).id,
                    on1.x + bx, on1.y + by, on1.z + bz, on2.x + bx, on2.y + by, on2.z + bz,
                )
            }
            return ValidateResult.AcceptAllContactsForThisBodyPair.ordinal
        }

        override fun onContactAdded(body1Va: Long, body2Va: Long, manifoldVa: Long, settingsVa: Long) {
            guarded {
                val body1 = Body(body1Va)
                val body2 = Body(body2Va)
                val normal = ContactManifold(manifoldVa).worldSpaceNormal
                record(KIND_ADDED, body1.id, body2.id, normal.x, normal.y, normal.z, impulseEstimate(body1, body2, normal.x, normal.y, normal.z))
            }
        }

        override fun onContactPersisted(body1Va: Long, body2Va: Long, manifoldVa: Long, settingsVa: Long) {
            guarded { record(KIND_SEEN, Body(body1Va).id, Body(body2Va).id) }
        }
    }

    /** An exception escaping into Jolt's worker thread would take the process down. */
    private inline fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (_: Throwable) {
        }
    }

    private fun record(
        kind: Int, id1: Int, id2: Int,
        f0: Float = 0f, f1: Float = 0f, f2: Float = 0f, f3: Float = 0f, f4: Float = 0f, f5: Float = 0f,
    ) = synchronized(lock) {
        if (count == kinds.size) grow()
        kinds[count] = kind
        ids1[count] = id1
        ids2[count] = id2
        val base = count * FLOATS
        data[base] = f0; data[base + 1] = f1; data[base + 2] = f2
        data[base + 3] = f3; data[base + 4] = f4; data[base + 5] = f5
        count++
    }

    private fun grow() {
        val size = kinds.size * 2
        kinds = kinds.copyOf(size)
        ids1 = ids1.copyOf(size)
        ids2 = ids2.copyOf(size)
        data = data.copyOf(size * FLOATS)
    }

    // --- stepping-thread side ---

    private var touchingPrev = LongSet()
    private var touchingNow = LongSet()
    private val events = ArrayList<PendingEvent>()
    private var eventCount = 0
    private val tempPoint = Vector3f()
    private val tempNormal = Vector3f()
    private val tempOther = Vector3f()

    /** Works out which pairs started or stopped touching during the step just taken and reports them to [listener]. */
    fun afterStep(listener: PhysicsContactListener?) {
        val records = synchronized(lock) { count }
        touchingNow.clear()
        for (i in 0 until records) {
            if (kinds[i] == KIND_POINT) continue
            val key = pairKey(ids1[i], ids2[i])
            if (key != 0L) touchingNow.add(key)
        }
        // Sleeping pairs: Jolt stops reporting them, Bullet does not
        touchingPrev.forEach { key ->
            if (!touchingNow.contains(key) && bothAsleep(key)) touchingNow.add(key)
        }

        eventCount = 0
        touchingNow.forEach { key -> if (!touchingPrev.contains(key)) started(key, records) }
        touchingPrev.forEach { key -> if (!touchingNow.contains(key)) ended(key) }
        synchronized(lock) { count = 0 }
        touchingPrev = touchingNow.also { touchingNow = touchingPrev }

        // Dispatch last: listeners may add or remove bodies, which updates the sets above
        if (listener == null) return
        for (i in 0 until eventCount) {
            val e = events[i]
            when (e.kind) {
                EVENT_COLLISION -> listener.onCollisionStarted(e.a, e.b, e.point, e.normal, e.impulse)
                EVENT_ENTER -> listener.onTriggerEnter(e.a, e.b)
                else -> listener.onTriggerExit(e.a, e.b)
            }
        }
    }

    /**
     * Drops the pairs of a body leaving the world. If it was inside a trigger, that trigger reports an exit right away;
     * a removed trigger reports nothing (same as Bullet).
     */
    fun forget(handle: JoltRigidBodyHandle, listener: PhysicsContactListener?) {
        val go = handle.gameObject
        val id = handle.id
        val exits = ArrayList<GameObject>(0)
        touchingPrev.forEach { key ->
            if (lowId(key) != id && highId(key) != id) return@forEach
            val other = handleOf(if (lowId(key) == id) highId(key) else lowId(key))
            val trigger = other?.gameObject
            if (go != null && trigger != null && other.isTrigger && !handle.isTrigger) exits += trigger
        }
        touchingPrev.retainWhere { lowId(it) != id && highId(it) != id }
        touchingNow.retainWhere { lowId(it) != id && highId(it) != id }
        // Report after the sets are consistent: the listener may remove more bodies
        if (go != null) exits.forEach { listener?.onTriggerExit(it, go) }
    }

    /** Keeps a rebuilt body's pairs (Bullet keys pairs by GameObject, so a rebuild reports nothing). */
    fun rebind(oldId: Int, newId: Int) {
        val moved = ArrayList<Long>(0)
        touchingPrev.forEach { key -> if (lowId(key) == oldId || highId(key) == oldId) moved += key }
        if (moved.isEmpty()) return
        touchingPrev.retainWhere { lowId(it) != oldId && highId(it) != oldId }
        moved.forEach { key ->
            val other = if (lowId(key) == oldId) highId(key) else lowId(key)
            pairKey(newId, other).takeIf { it != 0L }?.let { touchingPrev.add(it) }
        }
    }

    private fun started(key: Long, records: Int) {
        val h1 = handleOf(lowId(key)) ?: return
        val h2 = handleOf(highId(key)) ?: return
        val go1 = h1.gameObject ?: return
        val go2 = h2.gameObject ?: return
        when {
            h1.isTrigger && h2.isTrigger -> return
            h1.isTrigger -> queue(EVENT_ENTER, go1, go2)
            h2.isTrigger -> queue(EVENT_ENTER, go2, go1)
            else -> {
                // a is the moving body (the lower ID if both move); the normal points away from b
                val aIsFirst = h1.motionType != BodyType.Static
                val (a, b) = if (aIsFirst) go1 to go2 else go2 to go1
                val bId = if (aIsFirst) h2.id else h1.id
                val impulse = contactDetails(key, bId, records, aIsFirst)
                queue(EVENT_COLLISION, a, b).also {
                    it.point.set(tempPoint)
                    it.normal.set(tempNormal)
                    it.impulse = impulse
                }
            }
        }
    }

    private fun ended(key: Long) {
        val h1 = handleOf(lowId(key)) ?: return
        val h2 = handleOf(highId(key)) ?: return
        val go1 = h1.gameObject ?: return
        val go2 = h2.gameObject ?: return
        when {
            h1.isTrigger && h2.isTrigger -> return
            h1.isTrigger -> queue(EVENT_EXIT, go1, go2)
            h2.isTrigger -> queue(EVENT_EXIT, go2, go1)
        }
    }

    /**
     * Fills [tempPoint] (the contact point on body [bId]) and [tempNormal] (pointing away from b) from this step's
     * records for [key], and returns the impulse estimate. Falls back to the midpoint and an up normal if Jolt reported
     * the pair without a fresh collision (e.g. a woken pair).
     */
    private fun contactDetails(key: Long, bId: Int, records: Int, aIsFirst: Boolean): Float {
        var impulse = 0f
        var haveNormal = false
        var havePoint = false
        for (i in 0 until records) {
            if (pairKey(ids1[i], ids2[i]) != key) continue
            val base = i * FLOATS
            if (kinds[i] == KIND_ADDED && !haveNormal) {
                // Jolt's normal runs from its body 1 (lower ID) to body 2; flip it when a is body 1
                val sign = if (aIsFirst == (ids1[i] == lowId(key))) -1f else 1f
                tempNormal.set(data[base] * sign, data[base + 1] * sign, data[base + 2] * sign)
                impulse = data[base + 3]
                haveNormal = true
            } else if (kinds[i] == KIND_POINT && !havePoint) {
                val offset = if (ids1[i] == bId) 0 else 3
                tempPoint.set(data[base + offset], data[base + offset + 1], data[base + offset + 2])
                havePoint = true
            }
            if (haveNormal && havePoint) break
        }
        if (!haveNormal) tempNormal.set(0f, 1f, 0f)
        if (!havePoint) midpoint(key)
        return impulse
    }

    private fun midpoint(key: Long) {
        val h1 = handleOf(lowId(key))
        val h2 = handleOf(highId(key))
        if (h1 == null || h2 == null) {
            tempPoint.zero()
            return
        }
        h1.getLocation(tempPoint)
        h2.getLocation(tempOther)
        tempPoint.add(tempOther).mul(0.5f)
    }

    private fun bothAsleep(key: Long): Boolean {
        val h1 = handleOf(lowId(key)) ?: return false
        val h2 = handleOf(highId(key)) ?: return false
        return !h1.body.isActive && !h2.body.isActive
    }

    private fun queue(kind: Int, a: GameObject, b: GameObject): PendingEvent {
        if (eventCount == events.size) events.add(PendingEvent())
        return events[eventCount++].also {
            it.kind = kind
            it.a = a
            it.b = b
        }
    }

    override fun close() = listener.close()

    private class PendingEvent {
        var kind = 0
        lateinit var a: GameObject
        lateinit var b: GameObject
        val point = Vector3f()
        val normal = Vector3f()
        var impulse = 0f
    }

    internal companion object {
        const val INITIAL_RECORDS = 256
        const val FLOATS = 6
        const val KIND_POINT = 0
        const val KIND_ADDED = 1
        const val KIND_SEEN = 2
        const val EVENT_COLLISION = 0
        const val EVENT_ENTER = 1
        const val EVENT_EXIT = 2

        /** Order-independent key of two body IDs (unsigned), lower ID in the high half; 0 for a body with itself. */
        fun pairKey(id1: Int, id2: Int): Long {
            val a = id1.toLong() and 0xFFFFFFFFL
            val b = id2.toLong() and 0xFFFFFFFFL
            if (a == b) return 0L
            return if (a < b) (a shl 32) or b else (b shl 32) or a
        }

        fun lowId(key: Long): Int = (key ushr 32).toInt()
        fun highId(key: Long): Int = key.toInt()

        /**
         * Estimated collision impulse (N·s): approach speed along [nx],[ny],[nz] (body 1 → body 2) times the pair's
         * effective mass. Jolt only knows the solved impulse after the step; this is what the event reports.
         */
        fun impulseEstimate(body1: Body, body2: Body, nx: Float, ny: Float, nz: Float): Float {
            if (body1.isSensor || body2.isSensor) return 0f
            val inv1 = inverseMass(body1)
            val inv2 = inverseMass(body2)
            val v1 = body1.linearVelocity
            val v2 = body2.linearVelocity
            return impulse((v2.x - v1.x) * nx + (v2.y - v1.y) * ny + (v2.z - v1.z) * nz, inv1, inv2)
        }

        /** Native-free core of [impulseEstimate]: [relativeNormalSpeed] < 0 means the bodies approach. */
        fun impulse(relativeNormalSpeed: Float, inverseMass1: Float, inverseMass2: Float): Float {
            val inverseSum = inverseMass1 + inverseMass2
            if (inverseSum <= 0f || relativeNormalSpeed >= 0f) return 0f
            return -relativeNormalSpeed / inverseSum
        }

        private fun inverseMass(body: Body): Float = if (body.isDynamic) body.motionProperties.inverseMass else 0f
    }
}
